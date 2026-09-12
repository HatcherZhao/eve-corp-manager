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

package top.continew.admin.eve.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.eve.mapper.EveSyncJobMapper;
import top.continew.admin.eve.model.entity.EveSyncJobDO;
import top.continew.admin.eve.model.enums.EveSyncJobState;
import top.continew.admin.eve.model.enums.EveSyncModule;
import top.continew.admin.eve.model.enums.EveSyncTargetType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 维护自动同步任务、并发领取租约、成功排期和失败退避。
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveSyncJobService {

    private static final int CLAIM_CANDIDATE_LIMIT = 20;
    private static final Duration DEFAULT_CLAIM_LEASE = Duration.ofMinutes(20);
    private static final Duration MINIMUM_MANUAL_COOLDOWN = Duration.ofMinutes(1);
    private static final List<Duration> FAILURE_BACKOFFS = List.of(Duration.ofMinutes(5), Duration
        .ofMinutes(15), Duration.ofMinutes(45), Duration.ofHours(2), Duration.ofHours(6));
    private static final Set<String> PERMANENT_AUTHORIZATION_FAILURES = Set
        .of("AUTHORIZATION_REVOKED", "AUTHORIZATION_INVALID", "REFRESH_TOKEN_REVOKED", "INVALID_GRANT", "MISSING_SCOPE", "REAUTHORIZATION_REQUIRED", "FORBIDDEN", "UNAUTHORIZED");

    private final EveSyncJobMapper syncJobMapper;

    /**
     * 确保指定目标具备全部模块任务；并发创建由唯一键收敛为同一条记录。
     *
     * @return 按输入模块顺序返回已存在或新建的任务
     */
    @Transactional(rollbackFor = Exception.class)
    public List<EveSyncJobDO> ensureJobs(Long tenantId,
                                         EveSyncTargetType targetType,
                                         Long targetRefId,
                                         Collection<EveSyncModule> modules) {
        return ensureJobs(tenantId, targetType, targetRefId, modules, utcNow());
    }

    /**
     * 确保目标具备任务，并将首次执行延迟到指定时间。
     *
     * <p>只影响新任务，已有任务的退避和上游缓存约束绝不被发现扫描覆盖。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public List<EveSyncJobDO> ensureJobs(Long tenantId,
                                         EveSyncTargetType targetType,
                                         Long targetRefId,
                                         Collection<EveSyncModule> modules,
                                         LocalDateTime initialRunAt) {
        requireIdentity(tenantId, targetType, targetRefId);
        Objects.requireNonNull(modules, "modules must not be null");
        Objects.requireNonNull(initialRunAt, "initialRunAt must not be null");
        List<EveSyncJobDO> jobs = new ArrayList<>();
        for (EveSyncModule module : modules) {
            Objects.requireNonNull(module, "module must not be null");
            EveSyncJobDO job = syncJobMapper.selectByIdentity(tenantId, targetType, targetRefId, module);
            if (job == null) {
                job = createJob(tenantId, targetType, targetRefId, module, initialRunAt);
            }
            jobs.add(job);
        }
        return jobs;
    }

    /** 使用默认租约领取一条全局到期任务。 */
    public Optional<EveSyncJobDO> claimDueJob() {
        return claimDueJob(utcNow(), DEFAULT_CLAIM_LEASE);
    }

    /**
     * 原子领取一条全局到期任务。
     *
     * <p>先读候选再条件更新，多个调度实例看到同一候选时只有一个实例能取得租约。</p>
     */
    public Optional<EveSyncJobDO> claimDueJob(LocalDateTime now, Duration leaseDuration) {
        Objects.requireNonNull(now, "now must not be null");
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
        for (EveSyncJobDO candidate : syncJobMapper.selectDueCandidates(now, CLAIM_CANDIDATE_LIMIT)) {
            String claimToken = UUID.randomUUID().toString();
            int claimed = syncJobMapper.claim(candidate.getId(), candidate.getTenantId(), claimToken, now, now
                .plus(leaseDuration));
            if (claimed == 1) {
                return Optional.ofNullable(syncJobMapper.selectClaimed(candidate.getId(), candidate
                    .getTenantId(), claimToken));
            }
        }
        return Optional.empty();
    }

    /** 按模块默认间隔和可选上游过期时间完成成功排期。 */
    public boolean completeSuccess(EveSyncJobDO job, LocalDateTime sourceExpiresAt) {
        return completeSuccess(job, sourceExpiresAt, job.getModule().getDefaultInterval(), utcNow());
    }

    /** 按指定完成时间提交成功结果，便于批处理和可重复测试。 */
    public boolean completeSuccess(EveSyncJobDO job, LocalDateTime sourceExpiresAt, LocalDateTime finishedAt) {
        return completeSuccess(job, sourceExpiresAt, job.getModule().getDefaultInterval(), finishedAt);
    }

    /**
     * 按模块配置的成功间隔和上游缓存有效期提交成功排期。
     *
     * <p>上游 Expires 始终是下限，不能被运营配置或手动同步提前绕过。</p>
     */
    public boolean completeSuccess(EveSyncJobDO job,
                                   LocalDateTime sourceExpiresAt,
                                   Duration interval,
                                   LocalDateTime finishedAt) {
        requireClaimed(job);
        Objects.requireNonNull(finishedAt, "finishedAt must not be null");
        if (interval == null || interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("interval must be positive");
        }
        LocalDateTime cooldownUntil = finishedAt.plus(MINIMUM_MANUAL_COOLDOWN);
        if (sourceExpiresAt != null && sourceExpiresAt.isAfter(cooldownUntil)) {
            cooldownUntil = sourceExpiresAt;
        }
        LocalDateTime nextRunAt = finishedAt.plus(interval);
        if (sourceExpiresAt != null && sourceExpiresAt.isAfter(nextRunAt)) {
            nextRunAt = sourceExpiresAt;
        }
        return syncJobMapper.completeSuccess(job.getId(), job.getTenantId(), job
            .getClaimToken(), finishedAt, nextRunAt, cooldownUntil) == 1;
    }

    /**
     * 在页面直接完成同步后，仅重排后续自动任务。
     *
     * <p>此方法不领取、等待或取消任何自动任务，因此不会阻塞本次手动同步；运行中的任务继续由其
     * 租约持有者收尾，避免无令牌更新破坏并发安全。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void scheduleAfterManualSuccess(Long tenantId,
                                           EveSyncTargetType targetType,
                                           Long targetRefId,
                                           EveSyncModule module,
                                           Duration interval,
                                           LocalDateTime sourceExpiresAt) {
        requireIdentity(tenantId, targetType, targetRefId);
        Objects.requireNonNull(module, "module must not be null");
        if (interval == null || interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("interval must be positive");
        }
        LocalDateTime finishedAt = utcNow();
        LocalDateTime nextRunAt = finishedAt.plus(interval);
        if (sourceExpiresAt != null && sourceExpiresAt.isAfter(nextRunAt)) {
            nextRunAt = sourceExpiresAt;
        }
        LocalDateTime cooldownUntil = finishedAt.plus(MINIMUM_MANUAL_COOLDOWN);
        ensureJobs(tenantId, targetType, targetRefId, List.of(module), nextRunAt);
        syncJobMapper
            .completeManualSuccess(tenantId, targetType, targetRefId, module, finishedAt, nextRunAt, cooldownUntil);
    }

    /** 根据失败分类自动判断是否需要暂停，其余失败按固定阶梯退避。 */
    public boolean completeFailure(EveSyncJobDO job, String failureCode) {
        String normalizedCode = normalizeFailureCode(failureCode);
        return completeFailure(job, normalizedCode, PERMANENT_AUTHORIZATION_FAILURES
            .contains(normalizedCode), utcNow());
    }

    /** 允许调用方明确指出授权失败是否永久，避免依赖上游错误文本推断。 */
    public boolean completeFailure(EveSyncJobDO job, String failureCode, boolean permanentAuthorizationFailure) {
        return completeFailure(job, failureCode, permanentAuthorizationFailure, utcNow());
    }

    /** 按指定完成时间提交失败结果，便于批处理和可重复测试。 */
    public boolean completeFailure(EveSyncJobDO job,
                                   String failureCode,
                                   boolean permanentAuthorizationFailure,
                                   LocalDateTime finishedAt) {
        requireClaimed(job);
        Objects.requireNonNull(finishedAt, "finishedAt must not be null");
        String normalizedCode = normalizeFailureCode(failureCode);
        int consecutiveFailures = Math.max(0, Objects.requireNonNullElse(job.getConsecutiveFailures(), 0)) + 1;
        EveSyncJobState state = permanentAuthorizationFailure ? EveSyncJobState.PAUSED : EveSyncJobState.PENDING;
        Duration backoff = FAILURE_BACKOFFS.get(Math.min(consecutiveFailures, FAILURE_BACKOFFS.size()) - 1);
        LocalDateTime nextRunAt = permanentAuthorizationFailure ? finishedAt : finishedAt.plus(backoff);
        LocalDateTime cooldownUntil = permanentAuthorizationFailure ? null : nextRunAt;
        return syncJobMapper.completeFailure(job.getId(), job.getTenantId(), job
            .getClaimToken(), state, finishedAt, nextRunAt, cooldownUntil, consecutiveFailures, normalizedCode) == 1;
    }

    private EveSyncJobDO createJob(Long tenantId,
                                   EveSyncTargetType targetType,
                                   Long targetRefId,
                                   EveSyncModule module,
                                   LocalDateTime now) {
        EveSyncJobDO created = new EveSyncJobDO();
        created.setTenantId(tenantId);
        created.setTargetType(targetType);
        created.setTargetRefId(targetRefId);
        created.setModule(module);
        created.setState(EveSyncJobState.PENDING);
        created.setNextRunAt(now);
        created.setConsecutiveFailures(0);
        created.setCreateUser(1L);
        created.setDeleted(0L);
        try {
            syncJobMapper.insert(created);
            return created;
        } catch (DuplicateKeyException ignored) {
            return syncJobMapper.selectByIdentity(tenantId, targetType, targetRefId, module);
        }
    }

    private static void requireIdentity(Long tenantId, EveSyncTargetType targetType, Long targetRefId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(targetRefId, "targetRefId must not be null");
    }

    private static void requireClaimed(EveSyncJobDO job) {
        Objects.requireNonNull(job, "job must not be null");
        if (job.getId() == null || job.getTenantId() == null || job.getModule() == null || job
            .getClaimToken() == null || !EveSyncJobState.RUNNING.equals(job.getState())) {
            throw new IllegalArgumentException("job must hold an active claim");
        }
    }

    private static String normalizeFailureCode(String failureCode) {
        String normalized = failureCode == null || failureCode.isBlank()
            ? "SYNC_REJECTED"
            : failureCode.trim().toUpperCase(Locale.ROOT);
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    private static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
