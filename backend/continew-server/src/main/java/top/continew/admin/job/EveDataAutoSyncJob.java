/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package top.continew.admin.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.continew.admin.eve.client.SerenityEsiClientException;
import top.continew.admin.eve.client.SerenityTokenClientException;
import top.continew.admin.eve.config.EveAutoSyncProperties;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.entity.EveSyncJobDO;
import top.continew.admin.eve.model.enums.EveSyncModule;
import top.continew.admin.eve.model.enums.EveSyncTargetType;
import top.continew.admin.eve.service.EveCorporationAssetService;
import top.continew.admin.eve.service.EveCorporationMemberService;
import top.continew.admin.eve.service.EveCorporationStructureService;
import top.continew.admin.eve.service.EveGameMailService;
import top.continew.admin.eve.service.EveGameNotificationService;
import top.continew.admin.eve.service.EveMiningLedgerService;
import top.continew.admin.eve.service.EveMoonExtractionService;
import top.continew.admin.eve.service.EveSyncJobService;
import top.continew.starter.extension.tenant.util.TenantUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 以持久化队列低频拉取 EVE 业务数据的统一调度入口。
 *
 * <p>任务表控制目标频率、缓存有效期、租约和失败退避；本任务只负责发现目标和领取少量到期工作。
 * 多实例借助 Redis 调度锁与数据库领取令牌，不会在服务恢复时同时全量请求国服。</p>
 *
 * @author zhaoyuqing
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "eve.serenity.sso", name = "enabled", havingValue = "true")
public class EveDataAutoSyncJob {

    private static final String MAIL_READ_SCOPE = "esi-mail.read_mail.v1";
    private static final String NOTIFICATION_READ_SCOPE = "esi-characters.read_notifications.v1";
    private static final String DISPATCH_LOCK = "eve:serenity:auto-sync:dispatch";
    private static final List<EveSyncModule> CORPORATION_MODULES = List
        .of(EveSyncModule.MEMBER_ROSTER, EveSyncModule.STRUCTURES, EveSyncModule.MOON_EXTRACTIONS, EveSyncModule.ASSETS, EveSyncModule.MINING_LEDGER);

    private final EveAutoSyncProperties properties;
    private final RedissonClient redissonClient;
    private final EveSyncJobService syncJobService;
    private final EveCorporationMapper corporationMapper;
    private final EveAuthorizationMapper authorizationMapper;
    private final EveCharacterMapper characterMapper;
    private final EveCorporationAssetService assetService;
    private final EveCorporationStructureService structureService;
    private final EveCorporationMemberService memberService;
    private final EveMoonExtractionService moonExtractionService;
    private final EveMiningLedgerService miningLedgerService;
    private final EveGameMailService gameMailService;
    private final EveGameNotificationService gameNotificationService;

    /** 每轮仅由一个实例发现、领取并执行少量任务，调度频次本身不等于上游请求频次。 */
    @Scheduled(fixedDelayString = "${eve.serenity.auto-sync.scan-interval:PT1M}")
    public void synchronize() {
        if (!properties.isEnabled()) {
            return;
        }
        RLock lock = redissonClient.getLock(DISPATCH_LOCK);
        if (!lock.tryLock()) {
            return;
        }
        try {
            discoverJobs();
            for (int index = 0; index < Math.max(1, properties.getMaxJobsPerCycle()); index++) {
                if (syncJobService.claimDueJob().map(this::execute).isEmpty()) {
                    return;
                }
            }
        } catch (RuntimeException e) {
            log.warn("EVE 自动同步调度轮次异常", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 为有效军团和具备读取授权的角色创建首次带随机抖动的任务。 */
    private void discoverJobs() {
        for (EveCorporationDO corporation : corporationMapper.selectAllActiveForAutoSync()) {
            TenantUtils.execute(corporation.getTenantId(), () -> syncJobService.ensureJobs(corporation
                .getTenantId(), EveSyncTargetType.CORPORATION, corporation.getId(), CORPORATION_MODULES, firstRunAt()));
        }
        for (EveAuthorizationDO authorization : authorizationMapper.selectActiveByScopeForAutoSync(MAIL_READ_SCOPE)) {
            TenantUtils.execute(authorization.getTenantId(), () -> syncJobService.ensureJobs(authorization
                .getTenantId(), EveSyncTargetType.CHARACTER, authorization.getCharacterRefId(), List
                    .of(EveSyncModule.GAME_MAIL), firstRunAt()));
        }
        for (EveAuthorizationDO authorization : authorizationMapper
            .selectActiveByScopeForAutoSync(NOTIFICATION_READ_SCOPE)) {
            TenantUtils.execute(authorization.getTenantId(), () -> syncJobService.ensureJobs(authorization
                .getTenantId(), EveSyncTargetType.CHARACTER, authorization.getCharacterRefId(), List
                    .of(EveSyncModule.GAME_NOTIFICATIONS), firstRunAt()));
        }
    }

    /** 执行已领取任务，并只持久化安全的分类错误码。 */
    private EveSyncJobDO execute(EveSyncJobDO job) {
        try {
            LocalDateTime sourceExpiresAt = synchronizeTarget(job);
            syncJobService.completeSuccess(job, sourceExpiresAt, intervalFor(job.getModule()), utcNow());
        } catch (RuntimeException e) {
            String failureCode = failureCode(e);
            syncJobService.completeFailure(job, failureCode, isPermanentAuthorizationFailure(e), utcNow());
            log.warn("EVE 自动同步任务失败，tenantId={}, targetType={}, targetRefId={}, module={}, failureCode={}, errorType={}", job
                .getTenantId(), job.getTargetType(), job.getTargetRefId(), job.getModule(), failureCode, e.getClass()
                    .getSimpleName());
        }
        return job;
    }

    /** 将队列模块映射到明确参数化的业务同步入口。 */
    private LocalDateTime synchronizeTarget(EveSyncJobDO job) {
        AtomicReference<LocalDateTime> sourceExpiresAt = new AtomicReference<>();
        TenantUtils.execute(job.getTenantId(), () -> sourceExpiresAt.set(synchronizeInTenant(job)));
        return sourceExpiresAt.get();
    }

    /** 在目标租户上下文内调用同步服务，确保 ORM 的租户隔离仍然生效。 */
    private LocalDateTime synchronizeInTenant(EveSyncJobDO job) {
        if (EveSyncTargetType.CORPORATION.equals(job.getTargetType())) {
            return switch (job.getModule()) {
                case ASSETS -> assetService.syncForCorporation(job.getTenantId(), job.getTargetRefId())
                    .sourceExpiresAt();
                case STRUCTURES -> structureService.syncForCorporation(job.getTenantId(), job.getTargetRefId())
                    .sourceExpiresAt();
                case MEMBER_ROSTER, MEMBER_TRACKING -> memberService.syncForCorporation(job.getTenantId(), job
                    .getTargetRefId()).rosterSourceExpiresAt();
                case MOON_EXTRACTIONS -> moonExtractionService.syncForCorporation(job.getTenantId(), job
                    .getTargetRefId()).sourceExpiresAt();
                case MINING_LEDGER -> miningLedgerService.syncForCorporation(job.getTenantId(), job.getTargetRefId())
                    .sourceExpiresAt();
                case GAME_MAIL, GAME_NOTIFICATIONS -> throw new IllegalArgumentException("军团任务不能同步个人通信数据");
            };
        }
        if (EveSyncTargetType.CHARACTER.equals(job.getTargetType()) && (EveSyncModule.GAME_MAIL.equals(job
            .getModule()) || EveSyncModule.GAME_NOTIFICATIONS.equals(job.getModule()))) {
            EveCharacterDO character = characterMapper.selectActiveByTenantAndRefId(job.getTenantId(), job
                .getTargetRefId());
            if (character == null) {
                throw new IllegalArgumentException("邮箱同步目标角色已不可用");
            }
            return EveSyncModule.GAME_MAIL.equals(job.getModule())
                ? gameMailService.syncForCharacter(job.getTenantId(), character.getUserId(), job.getTargetRefId())
                    .sourceExpiresAt()
                : gameNotificationService.syncForCharacter(job.getTenantId(), character.getUserId(), job
                    .getTargetRefId()).sourceExpiresAt();
        }
        throw new IllegalArgumentException("同步目标类型与模块不匹配");
    }

    /** 读取配置的业务频率；实际排期仍取配置时间与上游 Expires 的较晚者。 */
    private Duration intervalFor(EveSyncModule module) {
        return switch (module) {
            case ASSETS -> properties.getAssetsInterval();
            case STRUCTURES -> properties.getStructuresInterval();
            case MEMBER_ROSTER, MEMBER_TRACKING -> properties.getMembersInterval();
            case MOON_EXTRACTIONS -> properties.getMoonExtractionsInterval();
            case MINING_LEDGER -> properties.getMiningLedgerInterval();
            case GAME_MAIL -> properties.getMailInterval();
            case GAME_NOTIFICATIONS -> properties.getNotificationsInterval();
        };
    }

    /** 为首次任务加抖动，防止重启后所有军团在同一秒发起请求。 */
    private LocalDateTime firstRunAt() {
        Duration jitter = properties.getStartupJitter();
        if (jitter == null || jitter.isNegative() || jitter.isZero()) {
            return utcNow();
        }
        return utcNow().plus(Duration.ofMillis(ThreadLocalRandom.current().nextLong(jitter.toMillis() + 1)));
    }

    /** 上游异常只存固定分类，绝不记录令牌或响应正文。 */
    private static String failureCode(RuntimeException exception) {
        if (exception instanceof SerenityEsiClientException esiException) {
            return esiException.getFailureCode().name();
        }
        if (exception instanceof SerenityTokenClientException tokenException) {
            return tokenException.getFailureCode().name();
        }
        return "SYNC_REJECTED";
    }

    /** 401/403 或刷新令牌失效只有续期链路明确失败后才暂停等待重新授权。 */
    private static boolean isPermanentAuthorizationFailure(RuntimeException exception) {
        return exception instanceof SerenityEsiClientException esiException && ("UNAUTHORIZED".equals(esiException
            .getFailureCode()
            .name()) || "FORBIDDEN".equals(esiException.getFailureCode()
                .name())) || exception instanceof SerenityTokenClientException tokenException && ("INVALID_GRANT"
                    .equals(tokenException.getFailureCode().name()) || "UNAUTHORIZED".equals(tokenException
                        .getFailureCode()
                        .name()));
    }

    private static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
