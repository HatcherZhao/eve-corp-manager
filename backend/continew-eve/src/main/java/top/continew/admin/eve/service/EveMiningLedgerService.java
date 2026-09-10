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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.client.SerenityEsiClient;
import top.continew.admin.eve.client.SerenityEsiClientException;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterRoleSnapshotMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.mapper.EveCorporationStructureMapper;
import top.continew.admin.eve.mapper.EveMiningLedgerMapper;
import top.continew.admin.eve.mapper.EveMiningObserverMapper;
import top.continew.admin.eve.mapper.EveMiningSyncRunMapper;
import top.continew.admin.eve.model.EveMeContextResp;
import top.continew.admin.eve.model.EveMiningLedgerResp;
import top.continew.admin.eve.model.EveMiningLedgerSummaryResp;
import top.continew.admin.eve.model.EveMiningSyncResp;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.entity.EveCorporationStructureDO;
import top.continew.admin.eve.model.entity.EveMiningLedgerDO;
import top.continew.admin.eve.model.entity.EveMiningObserverDO;
import top.continew.admin.eve.model.entity.EveMiningSyncRunDO;
import top.continew.admin.eve.model.entity.EveStaticTypeReferenceDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.model.serenity.SerenityCorporationMiningLedgerResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationMiningObserverResponse;
import top.continew.admin.eve.model.serenity.SerenityEsiPagedResponse;
import top.continew.admin.eve.model.serenity.SerenityUniverseNameResponse;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 管理军团采矿观察者账本的完整同步、筛选与汇总。
 *
 * <p>账本只使用当前租户中同时具备采矿 Scope 和 Accountant 游戏角色的一条授权。同步必须先完成
 * 观察者和全部观察者明细分页，再在同一事务中发布，避免半份国服结果覆盖既有历史。</p>
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EveMiningLedgerService {

    private static final String MINING_SCOPE = "esi-industry.read_corporation_mining.v1";
    private static final int MAX_UPSTREAM_PAGES = 1000;
    private static final int MAX_TOTAL_ENTRIES = 100_000;
    private static final int UNIVERSE_NAME_BATCH_SIZE = 1000;

    private final EveContextService contextService;
    private final EveCorporationMapper corporationMapper;
    private final EveCorporationStructureMapper structureMapper;
    private final EveMiningObserverMapper observerMapper;
    private final EveMiningLedgerMapper ledgerMapper;
    private final EveMiningSyncRunMapper syncRunMapper;
    private final EveAuthorizationMapper authorizationMapper;
    private final EveCharacterRoleSnapshotMapper roleSnapshotMapper;
    private final EveAuthorizationLifecycleService authorizationLifecycleService;
    private final EvePermissionRefreshService permissionRefreshService;
    private final EveStaticReferenceService staticReferenceService;
    private final SerenityEsiClient esiClient;
    private final RedissonClient redissonClient;
    private final EveDataFreshnessService dataFreshnessService;

    /** 分页查询当前军团已发布的账本明细。 */
    public PageResp<EveMiningLedgerResp> page(int page,
                                              int size,
                                              Long observerId,
                                              Long characterId,
                                              Integer typeId,
                                              LocalDate fromDate,
                                              LocalDate toDate,
                                              String keyword) {
        validateDateRange(fromDate, toDate);
        EveCorporationDO corporation = requireCurrentCorporation(UserContextHolder.getContext().getTenantId());
        LambdaQueryWrapper<EveMiningLedgerDO> query = buildQuery(corporation
            .getId(), observerId, characterId, typeId, fromDate, toDate, keyword)
            .orderByDesc(EveMiningLedgerDO::getRecordedAt, EveMiningLedgerDO::getQuantity)
            .orderByAsc(EveMiningLedgerDO::getCharacterName, EveMiningLedgerDO::getTypeName);
        Page<EveMiningLedgerDO> result = ledgerMapper.selectPage(new Page<>(page, size), query);
        return new PageResp<>(result.getRecords().stream().map(EveMiningLedgerService::toResp).toList(), result
            .getTotal());
    }

    /** 在数据库内汇总当前筛选条件，避免页面为统计重复加载全部明细。 */
    public EveMiningLedgerSummaryResp summary(Long observerId,
                                              Long characterId,
                                              Integer typeId,
                                              LocalDate fromDate,
                                              LocalDate toDate,
                                              String keyword) {
        validateDateRange(fromDate, toDate);
        UserContext context = UserContextHolder.getContext();
        EveCorporationDO corporation = requireCurrentCorporation(context.getTenantId());
        Map<String, Object> summary = ledgerMapper.summarize(context.getTenantId(), corporation
            .getId(), observerId, characterId, typeId, fromDate, toDate, normalizeKeyword(keyword));
        return new EveMiningLedgerSummaryResp(number(summary.get("quantity")).longValue(), number(summary
            .get("entryCount")).longValue(), number(summary.get("observerCount")).intValue(), number(summary
                .get("characterCount")).intValue(), number(summary.get("mineralTypeCount"))
                    .intValue(), toLocalDate(summary.get("latestRecordedAt")), toLocalDateTime(summary
                        .get("latestSynchronizedAt")));
    }

    /** 同步当前军团的完整观察者清单和所有可读取账本页。 */
    @Transactional(rollbackFor = Exception.class)
    public EveMiningSyncResp syncCurrentTenant() {
        UserContext context = UserContextHolder.getContext();
        EveCorporationDO corporation = requireCurrentCorporation(context.getTenantId());
        RLock lock = redissonClient.getLock("eve:serenity:mining-sync:" + context.getTenantId() + ":" + corporation
            .getId());
        boolean locked = false;
        boolean unlockDeferred = false;
        try {
            locked = lock.tryLock(1, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("采矿账本正在同步，请稍后重试");
            }
            unlockDeferred = EveAuthorizationLifecycleService.deferUnlockUntilTransactionCompletion(lock);
            EveMiningSyncResp response = synchronize(context.getTenantId(), corporation);
            dataFreshnessService.recordSuccess(context.getTenantId(), corporation
                .getId(), EveDataFreshnessService.MINING_LEDGER, response.synchronizedAt(), response.sourceExpiresAt());
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("采矿账本正在同步，请稍后重试");
        } catch (RuntimeException e) {
            if (locked) {
                dataFreshnessService.recordFailure(context.getTenantId(), corporation
                    .getId(), EveDataFreshnessService.MINING_LEDGER, e);
            }
            throw e;
        } finally {
            if (locked && !unlockDeferred && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 由后台调度器同步指定军团的观察者和采矿账本。 */
    @Transactional(rollbackFor = Exception.class)
    public EveMiningSyncResp syncForCorporation(Long tenantId, Long corporationRefId) {
        EveCorporationDO corporation = requireSyncCorporation(tenantId, corporationRefId);
        RLock lock = redissonClient.getLock("eve:serenity:mining-sync:" + tenantId + ":" + corporationRefId);
        boolean locked = false;
        boolean unlockDeferred = false;
        try {
            locked = lock.tryLock(1, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("采矿账本正在同步，请稍后重试");
            }
            unlockDeferred = EveAuthorizationLifecycleService.deferUnlockUntilTransactionCompletion(lock);
            EveMiningSyncResp response = synchronize(tenantId, corporation);
            dataFreshnessService
                .recordSuccess(tenantId, corporationRefId, EveDataFreshnessService.MINING_LEDGER, response
                    .synchronizedAt(), response.sourceExpiresAt());
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("采矿账本正在同步，请稍后重试");
        } catch (RuntimeException e) {
            if (locked) {
                dataFreshnessService
                    .recordFailure(tenantId, corporationRefId, EveDataFreshnessService.MINING_LEDGER, e);
            }
            throw e;
        } finally {
            if (locked && !unlockDeferred && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 在发布前完整读取观察者与账本分页，任何失败都会回滚并保留既有数据。 */
    private EveMiningSyncResp synchronize(Long tenantId, EveCorporationDO corporation) {
        EveAuthorizationDO source = selectSource(tenantId);
        if (source == null) {
            throw new BusinessException("缺少可读取采矿账本的有效 Accountant 授权，请由拥有会计权限的成员重新授权并刷新游戏权限");
        }
        String accessToken = authorizationLifecycleService.ensureAccessToken(source);
        SerenityEsiPagedResponse<SerenityCorporationMiningObserverResponse> firstObserverPage = esiClient
            .getCorporationMiningObserversWithMetadata(corporation.getCorporationId(), 1, accessToken);
        ensurePageCount(firstObserverPage.pageCount(), "观察者");
        List<SerenityCorporationMiningObserverResponse> observers = new ArrayList<>(firstObserverPage.body());
        int pageCount = firstObserverPage.pageCount();
        for (int page = 2; page <= firstObserverPage.pageCount(); page++) {
            observers.addAll(esiClient.getCorporationMiningObserversWithMetadata(corporation
                .getCorporationId(), page, accessToken).body());
        }
        List<SerenityCorporationMiningObserverResponse> validObservers = observers.stream()
            .filter(item -> item != null && item.observerId() != null && item.observerId() > 0 && item
                .observerType() != null && !item.observerType().isBlank() && item.lastUpdated() != null)
            .collect(Collectors.collectingAndThen(Collectors
                .toMap(SerenityCorporationMiningObserverResponse::observerId, item -> item, (left,
                                                                                             right) -> right), values -> new ArrayList<>(values
                                                                                                 .values())));
        List<LedgerSource> ledgerSources = new ArrayList<>();
        LocalDateTime sourceExpiresAt = firstObserverPage.expiresAt();
        for (SerenityCorporationMiningObserverResponse observer : validObservers) {
            SerenityEsiPagedResponse<SerenityCorporationMiningLedgerResponse> firstLedgerPage = esiClient
                .getCorporationMiningLedgerWithMetadata(corporation.getCorporationId(), observer
                    .observerId(), 1, accessToken);
            ensurePageCount(firstLedgerPage.pageCount(), "账本");
            pageCount += firstLedgerPage.pageCount();
            sourceExpiresAt = laterOf(sourceExpiresAt, firstLedgerPage.expiresAt());
            collectLedgerEntries(ledgerSources, observer, firstLedgerPage.body(), firstLedgerPage.expiresAt());
            for (int page = 2; page <= firstLedgerPage.pageCount(); page++) {
                SerenityEsiPagedResponse<SerenityCorporationMiningLedgerResponse> ledgerPage = esiClient
                    .getCorporationMiningLedgerWithMetadata(corporation.getCorporationId(), observer
                        .observerId(), page, accessToken);
                sourceExpiresAt = laterOf(sourceExpiresAt, ledgerPage.expiresAt());
                collectLedgerEntries(ledgerSources, observer, ledgerPage.body(), ledgerPage.expiresAt());
            }
        }
        LocalDateTime synchronizedAt = LocalDateTime.now(ZoneOffset.UTC);
        publish(tenantId, corporation.getId(), source, validObservers, ledgerSources, synchronizedAt, firstObserverPage
            .expiresAt(), sourceExpiresAt, pageCount);
        return new EveMiningSyncResp(validObservers.size(), ledgerSources
            .size(), pageCount, synchronizedAt, sourceExpiresAt);
    }

    /** 将上游账本页合并为可发布记录，并限制异常响应导致的内存或写入放大。 */
    private static void collectLedgerEntries(List<LedgerSource> collected,
                                             SerenityCorporationMiningObserverResponse observer,
                                             Collection<SerenityCorporationMiningLedgerResponse> entries,
                                             LocalDateTime expiresAt) {
        for (SerenityCorporationMiningLedgerResponse entry : entries) {
            if (entry == null || entry.characterId() == null || entry.characterId() <= 0 || entry
                .recordedCorporationId() == null || entry.recordedCorporationId() <= 0 || entry
                    .typeId() == null || entry.typeId() <= 0 || entry.quantity() == null || entry
                        .quantity() < 0 || entry.lastUpdated() == null) {
                continue;
            }
            if (collected.size() >= MAX_TOTAL_ENTRIES) {
                throw new BusinessException("国服采矿账本明细数量异常，已拒绝发布本次同步结果");
            }
            collected.add(new LedgerSource(observer, entry, expiresAt));
        }
    }

    /** 将完整同步结果持久化；账本条目只按自然键覆盖，不会把同一天记录反复累加。 */
    private void publish(Long tenantId,
                         Long corporationRefId,
                         EveAuthorizationDO source,
                         List<SerenityCorporationMiningObserverResponse> observerSources,
                         List<LedgerSource> ledgerSources,
                         LocalDateTime synchronizedAt,
                         LocalDateTime observerExpiresAt,
                         LocalDateTime sourceExpiresAt,
                         int pageCount) {
        Map<Long, String> observerNames = resolveObserverNames(tenantId, corporationRefId, observerSources);
        Map<Integer, EveStaticTypeReferenceDO> types = staticReferenceService.findTypes(ledgerSources.stream()
            .map(item -> item.entry().typeId())
            .collect(Collectors.toSet()));
        Map<Long, String> universeNames = resolveUniverseNames(ledgerSources);
        Map<Long, EveMiningObserverDO> existingObservers = observerMapper
            .selectList(new LambdaQueryWrapper<EveMiningObserverDO>()
                .eq(EveMiningObserverDO::getCorporationRefId, corporationRefId)
                .eq(EveMiningObserverDO::getDeleted, 0L))
            .stream()
            .collect(Collectors.toMap(EveMiningObserverDO::getObserverId, item -> item, (left, right) -> left));
        Set<Long> activeObserverIds = new HashSet<>();
        for (SerenityCorporationMiningObserverResponse observer : observerSources) {
            activeObserverIds.add(observer.observerId());
            EveMiningObserverDO target = existingObservers.remove(observer.observerId());
            if (target == null) {
                target = new EveMiningObserverDO();
                target.setTenantId(tenantId);
                target.setCorporationRefId(corporationRefId);
                target.setObserverId(observer.observerId());
                target.setCreateUser(1L);
                target.setDeleted(0L);
            }
            target.setObserverType(observer.observerType());
            target.setObserverName(observerNames.get(observer.observerId()));
            target.setSourceLastUpdated(observer.lastUpdated());
            target.setStatus("ACTIVE");
            target.setLastSeenAt(synchronizedAt);
            target.setSourceExpiresAt(observerExpiresAt);
            if (target.getId() == null) {
                observerMapper.insert(target);
            } else {
                observerMapper.updateById(target);
            }
        }
        existingObservers.values().forEach(item -> {
            item.setStatus("MISSING");
            observerMapper.updateById(item);
        });
        Map<LedgerKey, EveMiningLedgerDO> existingLedger = ledgerMapper
            .selectList(new LambdaQueryWrapper<EveMiningLedgerDO>()
                .eq(EveMiningLedgerDO::getCorporationRefId, corporationRefId)
                .eq(EveMiningLedgerDO::getDeleted, 0L))
            .stream()
            .collect(Collectors.toMap(LedgerKey::from, item -> item, (left, right) -> left));
        for (LedgerSource sourceEntry : ledgerSources) {
            SerenityCorporationMiningLedgerResponse entry = sourceEntry.entry();
            LedgerKey key = LedgerKey.from(sourceEntry.observer().observerId(), entry);
            EveMiningLedgerDO target = existingLedger.get(key);
            if (target == null) {
                target = new EveMiningLedgerDO();
                target.setTenantId(tenantId);
                target.setCorporationRefId(corporationRefId);
                target.setObserverId(sourceEntry.observer().observerId());
                target.setCreateUser(1L);
                target.setDeleted(0L);
            }
            target.setObserverName(observerNames.get(sourceEntry.observer().observerId()));
            target.setCharacterId(entry.characterId());
            target.setCharacterName(universeNames.get(entry.characterId()));
            target.setRecordedCorporationId(entry.recordedCorporationId());
            target.setTypeId(entry.typeId());
            EveStaticTypeReferenceDO type = types.get(entry.typeId());
            target.setTypeName(type == null ? universeNames.get(entry.typeId().longValue()) : type.getTypeName());
            target.setRecordedAt(entry.lastUpdated());
            target.setQuantity(entry.quantity());
            target.setLastSeenAt(synchronizedAt);
            target.setSourceExpiresAt(sourceEntry.expiresAt());
            if (target.getId() == null) {
                ledgerMapper.insert(target);
            } else {
                ledgerMapper.updateById(target);
            }
        }
        EveMiningSyncRunDO run = new EveMiningSyncRunDO();
        run.setTenantId(tenantId);
        run.setCorporationRefId(corporationRefId);
        run.setSourceCharacterRefId(source.getCharacterRefId());
        run.setObserverCount(observerSources.size());
        run.setLedgerCount(ledgerSources.size());
        run.setPageCount(pageCount);
        run.setSourceExpiresAt(sourceExpiresAt);
        run.setSynchronizedAt(synchronizedAt);
        run.setCreateUser(1L);
        run.setDeleted(0L);
        syncRunMapper.insert(run);
    }

    /** 从同租户的玩家建筑快照优先补全观察者名称，未命中时保留可见 ID。 */
    private Map<Long, String> resolveObserverNames(Long tenantId,
                                                   Long corporationRefId,
                                                   List<SerenityCorporationMiningObserverResponse> observers) {
        Set<Long> observerIds = observers.stream()
            .map(SerenityCorporationMiningObserverResponse::observerId)
            .collect(Collectors.toSet());
        Map<Long, String> names = structureMapper.selectList(new LambdaQueryWrapper<EveCorporationStructureDO>()
            .eq(EveCorporationStructureDO::getCorporationRefId, corporationRefId)
            .in(!observerIds.isEmpty(), EveCorporationStructureDO::getStructureId, observerIds)
            .eq(EveCorporationStructureDO::getDeleted, 0L))
            .stream()
            .collect(Collectors
                .toMap(EveCorporationStructureDO::getStructureId, EveCorporationStructureDO::getStructureName, (left,
                                                                                                                right) -> left));
        List<Long> unresolvedIds = observerIds.stream()
            .filter(id -> names.get(id) == null || names.get(id).isBlank())
            .toList();
        names.putAll(resolveUniverseNames(unresolvedIds));
        return names;
    }

    /** 批量解析角色、历史军团和静态资料未覆盖的矿物名称；解析失败不影响国服账本事实发布。 */
    private Map<Long, String> resolveUniverseNames(List<LedgerSource> entries) {
        Set<Long> ids = new HashSet<>();
        entries.forEach(item -> {
            ids.add(item.entry().characterId());
            ids.add(item.entry().recordedCorporationId());
            ids.add(item.entry().typeId().longValue());
        });
        return resolveUniverseNames(ids);
    }

    /** 将批量名称接口控制在国服单次请求限制内。 */
    private Map<Long, String> resolveUniverseNames(Collection<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        List<Long> values = ids.stream().filter(Objects::nonNull).filter(id -> id > 0).distinct().toList();
        for (int start = 0; start < values.size(); start += UNIVERSE_NAME_BATCH_SIZE) {
            try {
                List<SerenityUniverseNameResponse> response = esiClient.resolveUniverseNames(values.subList(start, Math
                    .min(start + UNIVERSE_NAME_BATCH_SIZE, values.size())));
                response.stream()
                    .filter(item -> item.id() != null && item.name() != null && !item.name().isBlank())
                    .forEach(item -> names.put(item.id(), item.name()));
            } catch (SerenityEsiClientException e) {
                log.warn("EVE 采矿账本名称解析失败，failureCode={}", e.getFailureCode());
            }
        }
        return names;
    }

    /** 从同租户授权中选择已授予采矿 Scope 且游戏角色快照为 Accountant 的数据源。 */
    private EveAuthorizationDO selectSource(Long tenantId) {
        for (EveAuthorizationDO candidate : authorizationMapper.selectTenantCandidates(tenantId)) {
            if (!EveAuthorizationStatus.ACTIVE.equals(candidate.getStatus()) || candidate
                .getScopes() == null || !candidate.getScopes().contains(MINING_SCOPE)) {
                continue;
            }
            if (hasAccountantRole(tenantId, candidate)) {
                return candidate;
            }
            permissionRefreshService.reviewAuthorization(candidate);
            if (hasAccountantRole(tenantId, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** 国服采矿观察者接口明确要求 Accountant，不以本站管理员或总监身份替代游戏角色。 */
    private boolean hasAccountantRole(Long tenantId, EveAuthorizationDO authorization) {
        EveCharacterRoleSnapshotDO snapshot = roleSnapshotMapper.selectLatest(tenantId, authorization
            .getCharacterRefId());
        return snapshot != null && Objects.equals(tenantId, snapshot.getTenantId()) && Objects.equals(authorization
            .getCharacterRefId(), snapshot.getCharacterRefId()) && snapshot.getSourceExpiresAt() != null && snapshot
                .getSourceExpiresAt()
                .isAfter(LocalDateTime.now(ZoneOffset.UTC)) && snapshot.getRoles() != null && snapshot.getRoles()
                    .contains("Accountant");
    }

    /** 由当前会话反查已认领军团，浏览器不能传递可猜测的租户或军团 ID。 */
    private EveCorporationDO requireCurrentCorporation(Long tenantId) {
        EveMeContextResp context = contextService.getCurrentContext();
        if (context.corporation() == null) {
            throw new BusinessException("当前账号尚未加入已认领军团");
        }
        EveCorporationDO corporation = corporationMapper.selectByTenantAndCorporationId(tenantId, context.corporation()
            .corporationId());
        if (corporation == null || !Objects.equals(tenantId, corporation.getTenantId())) {
            throw new BusinessException("当前军团租户数据不可用");
        }
        return corporation;
    }

    /** 验证持久化任务中的军团目标仍属于有效租户。 */
    private EveCorporationDO requireSyncCorporation(Long tenantId, Long corporationRefId) {
        EveCorporationDO corporation = corporationMapper.selectActiveByTenantAndRefId(tenantId, corporationRefId);
        if (corporation == null) {
            throw new BusinessException("自动同步目标军团已不可用");
        }
        return corporation;
    }

    /** 构造租户、军团和可选筛选条件均不可省略的账本查询。 */
    private static LambdaQueryWrapper<EveMiningLedgerDO> buildQuery(Long corporationRefId,
                                                                    Long observerId,
                                                                    Long characterId,
                                                                    Integer typeId,
                                                                    LocalDate fromDate,
                                                                    LocalDate toDate,
                                                                    String keyword) {
        return new LambdaQueryWrapper<EveMiningLedgerDO>().eq(EveMiningLedgerDO::getCorporationRefId, corporationRefId)
            .eq(EveMiningLedgerDO::getDeleted, 0L)
            .eq(observerId != null, EveMiningLedgerDO::getObserverId, observerId)
            .eq(characterId != null, EveMiningLedgerDO::getCharacterId, characterId)
            .eq(typeId != null, EveMiningLedgerDO::getTypeId, typeId)
            .ge(fromDate != null, EveMiningLedgerDO::getRecordedAt, fromDate)
            .le(toDate != null, EveMiningLedgerDO::getRecordedAt, toDate)
            .and(normalizeKeyword(keyword) != null, item -> item
                .like(EveMiningLedgerDO::getObserverName, normalizeKeyword(keyword))
                .or()
                .like(EveMiningLedgerDO::getCharacterName, normalizeKeyword(keyword))
                .or()
                .like(EveMiningLedgerDO::getTypeName, normalizeKeyword(keyword)));
    }

    /** 防止前端构造反向日期范围导致页面与汇总产生难以解释的空结果。 */
    private static void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }
    }

    /** 空白检索词不进入 SQL 条件，保持明细和汇总的筛选语义一致。 */
    private static String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    /** 限制异常的 X-Pages 响应，阻止恶意或故障上游触发无边界请求。 */
    private static void ensurePageCount(int pageCount, String category) {
        if (pageCount > MAX_UPSTREAM_PAGES) {
            throw new BusinessException("国服" + category + "分页数量异常，已拒绝发布本次同步结果");
        }
    }

    /** 返回两个可空上游过期时间中较晚的一个。 */
    private static LocalDateTime laterOf(LocalDateTime left, LocalDateTime right) {
        return left == null || right != null && right.isAfter(left) ? right : left;
    }

    /** 空汇总字段按零处理，兼容 JDBC 驱动返回的不同数字实现。 */
    private static Number number(Object value) {
        return value instanceof Number number ? number : 0L;
    }

    /** 将 JDBC 聚合查询返回的日期统一转换为 Java 时间类型，兼容不同 MySQL 驱动实现。 */
    static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return null;
    }

    /** 将 JDBC 聚合查询返回的时间统一转换为 Java 时间类型，兼容不同 MySQL 驱动实现。 */
    static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    /** 转换为不含数据源令牌、站内授权或敏感内部关系的页面数据。 */
    private static EveMiningLedgerResp toResp(EveMiningLedgerDO source) {
        return new EveMiningLedgerResp(source.getId(), source.getObserverId(), source.getObserverName(), String
            .valueOf(source.getCharacterId()), source.getCharacterName(), String.valueOf(source
                .getRecordedCorporationId()), source.getTypeId(), source.getTypeName(), source.getRecordedAt(), source
                    .getQuantity(), source.getLastSeenAt(), source.getSourceExpiresAt());
    }

    /** 观察者与一条原始账本记录及其响应缓存元数据。 */
    private record LedgerSource(SerenityCorporationMiningObserverResponse observer,
                                SerenityCorporationMiningLedgerResponse entry, LocalDateTime expiresAt) {
    }

    /** 唯一定位一条国服账本聚合记录的自然键。 */
    private record LedgerKey(Long observerId, LocalDate recordedAt, Long characterId, Long recordedCorporationId,
                             Integer typeId) {
        private static LedgerKey from(Long observerId, SerenityCorporationMiningLedgerResponse source) {
            return new LedgerKey(observerId, source.lastUpdated(), source.characterId(), source
                .recordedCorporationId(), source.typeId());
        }

        private static LedgerKey from(EveMiningLedgerDO source) {
            return new LedgerKey(source.getObserverId(), source.getRecordedAt(), source.getCharacterId(), source
                .getRecordedCorporationId(), source.getTypeId());
        }
    }
}
