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
import top.continew.admin.eve.client.SerenityTokenClientException;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterRoleSnapshotMapper;
import top.continew.admin.eve.mapper.EveCorporationMemberTrackingMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.mapper.EveCorporationRosterMemberMapper;
import top.continew.admin.eve.mapper.EveMemberTrackingAccessAuditMapper;
import top.continew.admin.eve.mapper.EveMemberSyncRunMapper;
import top.continew.admin.eve.model.EveMeContextResp;
import top.continew.admin.eve.model.EveMemberResp;
import top.continew.admin.eve.model.EveMemberOperationAuditResp;
import top.continew.admin.eve.model.EveMemberSyncResp;
import top.continew.admin.eve.model.EveMemberSyncRunResp;
import top.continew.admin.eve.model.EveMemberTrackingResp;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.entity.EveCorporationMemberTrackingDO;
import top.continew.admin.eve.model.entity.EveCorporationRosterMemberDO;
import top.continew.admin.eve.model.entity.EveMemberTrackingAccessAuditDO;
import top.continew.admin.eve.model.entity.EveMemberSyncRunDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.model.serenity.SerenityCorporationMemberTrackingResponse;
import top.continew.admin.eve.model.serenity.SerenityEsiResponse;
import top.continew.admin.eve.model.serenity.SerenityUniverseStructureResponse;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

/**
 * 管理军团完整成员名册与成员追踪快照。
 *
 * <p>数据源资格与本站展示资格严格分离：同步只使用同租户的合格授权，展示追踪字段只取决于
 * {@code eve:members:track:view}，而非查看者是否为总监。</p>
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EveCorporationMemberService {

    static final String MEMBERSHIP_SCOPE = "esi-corporations.read_corporation_membership.v1";
    static final String TRACKING_SCOPE = "esi-corporations.track_members.v1";

    private final EveContextService contextService;
    private final EveCorporationMapper corporationMapper;
    private final EveAuthorizationMapper authorizationMapper;
    private final EveCharacterRoleSnapshotMapper roleSnapshotMapper;
    private final EveCorporationRosterMemberMapper rosterMemberMapper;
    private final EveCorporationMemberTrackingMapper trackingMapper;
    private final EveMemberTrackingAccessAuditMapper trackingAccessAuditMapper;
    private final EveAuthorizationLifecycleService authorizationLifecycleService;
    private final SerenityEsiClient esiClient;
    private final RedissonClient redissonClient;
    private final EveMemberSyncRunService syncRunService;
    private final EveMemberSyncRunMapper syncRunMapper;
    private final EveMemberOperationAuditService operationAuditService;
    private final EveStaticNameReference staticNameReference;
    private final EveDataFreshnessService dataFreshnessService;

    /** 查询当前租户成员名册；追踪字段仅在当前会话具备独立权限时加载。 */
    public PageResp<EveMemberResp> page(int page, int size, String keyword, String status) {
        UserContext context = UserContextHolder.getContext();
        EveCorporationDO corporation = requireCurrentCorporation(context.getTenantId());
        LambdaQueryWrapper<EveCorporationRosterMemberDO> query = new LambdaQueryWrapper<EveCorporationRosterMemberDO>()
            .eq(EveCorporationRosterMemberDO::getCorporationRefId, corporation.getId())
            .eq(EveCorporationRosterMemberDO::getDeleted, 0L)
            .orderByDesc(EveCorporationRosterMemberDO::getStatus)
            .orderByAsc(EveCorporationRosterMemberDO::getCharacterName, EveCorporationRosterMemberDO::getCharacterId);
        if (keyword != null && !keyword.isBlank()) {
            query.and(item -> item.like(EveCorporationRosterMemberDO::getCharacterName, keyword.trim())
                .or()
                .like(EveCorporationRosterMemberDO::getCharacterId, keyword.trim()));
        }
        if (status != null && !status.isBlank()) {
            query.eq(EveCorporationRosterMemberDO::getStatus, status.trim().toUpperCase());
        }
        Page<EveCorporationRosterMemberDO> result = rosterMemberMapper.selectPage(new Page<>(page, size), query);
        boolean canViewTracking = hasTrackingPermission(context);
        Map<Long, EveCorporationMemberTrackingDO> tracking = canViewTracking
            ? trackingByMemberIds(context.getTenantId(), result.getRecords()
                .stream()
                .map(EveCorporationRosterMemberDO::getId)
                .toList())
            : Map.of();
        if (canViewTracking) {
            auditTrackingAccess(context.getTenantId(), context.getId(), null, "LIST");
        }
        return new PageResp<>(result.getRecords()
            .stream()
            .map(member -> toResp(member, tracking.get(member.getId())))
            .toList(), result.getTotal());
    }

    /** 查询当前租户单个成员详情，服务端再次裁剪追踪字段。 */
    public EveMemberResp get(Long characterId) {
        UserContext context = UserContextHolder.getContext();
        EveCorporationDO corporation = requireCurrentCorporation(context.getTenantId());
        EveCorporationRosterMemberDO member = rosterMemberMapper.selectByCharacterId(context.getTenantId(), corporation
            .getId(), characterId);
        if (member == null) {
            throw new BusinessException("成员不属于当前军团或尚未同步");
        }
        if (!hasTrackingPermission(context)) {
            return toResp(member, null);
        }
        auditTrackingAccess(context.getTenantId(), context.getId(), member.getId(), "DETAIL");
        return toResp(member, trackingMapper.selectByRosterMemberId(context.getTenantId(), member.getId()));
    }

    /** 更新当前军团成员的内部组织分组与备注，并记录不含备注正文的审计。 */
    @Transactional(rollbackFor = Exception.class)
    public EveMemberResp updateOrganization(Long characterId, String organizationGroup, String memberNote) {
        UserContext context = UserContextHolder.getContext();
        EveCorporationDO corporation = requireCurrentCorporation(context.getTenantId());
        EveCorporationRosterMemberDO member = rosterMemberMapper.selectByCharacterId(context.getTenantId(), corporation
            .getId(), characterId);
        if (member == null) {
            throw new BusinessException("成员不属于当前军团或尚未同步");
        }
        String normalizedGroup = normalizeOptionalText(organizationGroup);
        String normalizedNote = normalizeOptionalText(memberNote);
        if (!Objects.equals(member.getOrganizationGroup(), normalizedGroup) || !Objects.equals(member
            .getMemberNote(), normalizedNote)) {
            member.setOrganizationGroup(normalizedGroup);
            member.setMemberNote(normalizedNote);
            member.setUpdateUser(context.getId());
            rosterMemberMapper.updateOrganization(context.getTenantId(), member
                .getId(), normalizedGroup, normalizedNote, context.getId());
            operationAuditService.recordOrganizationUpdate(context, member.getId());
        }
        EveCorporationMemberTrackingDO tracking = hasTrackingPermission(context)
            ? trackingMapper.selectByRosterMemberId(context.getTenantId(), member.getId())
            : null;
        return toResp(member, tracking);
    }

    /** 返回当前军团最近成员名册与追踪资源的同步批次。 */
    public List<EveMemberSyncRunResp> listSyncRuns(int limit) {
        UserContext context = UserContextHolder.getContext();
        EveCorporationDO corporation = requireCurrentCorporation(context.getTenantId());
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return syncRunMapper.selectList(new LambdaQueryWrapper<EveMemberSyncRunDO>()
            .eq(EveMemberSyncRunDO::getCorporationRefId, corporation.getId())
            .eq(EveMemberSyncRunDO::getDeleted, 0L)
            .orderByDesc(EveMemberSyncRunDO::getStartedAt, EveMemberSyncRunDO::getId)
            .last("LIMIT " + safeLimit))
            .stream()
            .map(item -> new EveMemberSyncRunResp(item.getId(), item.getResource(), item.getStatus(), item
                .getRecordCount(), item.getSourceExpiresAt(), item.getFailureCode(), item.getStartedAt(), item
                    .getFinishedAt()))
            .toList();
    }

    /** 返回当前军团最近成员组织信息调整记录。 */
    public List<EveMemberOperationAuditResp> listOrganizationActivities(int limit) {
        return operationAuditService.listOrganizationActivities(limit);
    }

    /** 导出当前军团全部成员；追踪字段仍按导出者的独立权限裁剪。 */
    public List<EveMemberResp> listForExport(String keyword, String status) {
        UserContext context = UserContextHolder.getContext();
        EveCorporationDO corporation = requireCurrentCorporation(context.getTenantId());
        List<EveCorporationRosterMemberDO> members = rosterMemberMapper
            .selectList(buildRosterQuery(corporation, keyword, status));
        boolean canViewTracking = hasTrackingPermission(context);
        Map<Long, EveCorporationMemberTrackingDO> tracking = canViewTracking
            ? trackingByMemberIds(context.getTenantId(), members.stream()
                .map(EveCorporationRosterMemberDO::getId)
                .toList())
            : Map.of();
        if (canViewTracking) {
            auditTrackingAccess(context.getTenantId(), context.getId(), null, "EXPORT");
        }
        return members.stream().map(member -> toResp(member, tracking.get(member.getId()))).toList();
    }

    /**
     * 手动同步基础成员名册，并在可用时单独同步追踪快照。
     *
     * <p>追踪资源失败不会回滚已经完整读取的基础名册；名称解析失败同样不会阻止名册发布。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public EveMemberSyncResp syncCurrentTenant() {
        UserContext context = UserContextHolder.getContext();
        Long tenantId = context.getTenantId();
        EveCorporationDO corporation = requireCurrentCorporation(tenantId);
        RLock lock = redissonClient.getLock("eve:serenity:member-sync:" + tenantId + ":" + corporation.getId());
        boolean locked = false;
        boolean unlockDeferred = false;
        try {
            locked = lock.tryLock(1, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("成员数据正在同步，请稍后重试");
            }
            unlockDeferred = EveAuthorizationLifecycleService.deferUnlockUntilTransactionCompletion(lock);
            EveMemberSyncResp response = synchronizeCurrentCorporation(tenantId, corporation);
            dataFreshnessService.recordSuccess(tenantId, corporation
                .getId(), EveDataFreshnessService.MEMBER_ROSTER, response.synchronizedAt(), response
                    .rosterSourceExpiresAt());
            if (response.trackingSynchronized()) {
                dataFreshnessService.recordSuccess(tenantId, corporation
                    .getId(), EveDataFreshnessService.MEMBER_TRACKING, response.synchronizedAt(), response
                        .trackingSourceExpiresAt());
            } else {
                dataFreshnessService.recordFailure(tenantId, corporation
                    .getId(), EveDataFreshnessService.MEMBER_TRACKING, "TRACKING_UNAVAILABLE");
            }
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("成员数据正在同步，请稍后重试");
        } catch (RuntimeException e) {
            if (locked) {
                dataFreshnessService.recordFailure(tenantId, corporation
                    .getId(), EveDataFreshnessService.MEMBER_ROSTER, e);
            }
            throw e;
        } finally {
            if (locked && !unlockDeferred && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 由后台调度器同步指定军团的成员名册和追踪快照。 */
    @Transactional(rollbackFor = Exception.class)
    public EveMemberSyncResp syncForCorporation(Long tenantId, Long corporationRefId) {
        EveCorporationDO corporation = requireSyncCorporation(tenantId, corporationRefId);
        RLock lock = redissonClient.getLock("eve:serenity:member-sync:" + tenantId + ":" + corporationRefId);
        boolean locked = false;
        boolean unlockDeferred = false;
        try {
            locked = lock.tryLock(1, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("成员数据正在同步，请稍后重试");
            }
            unlockDeferred = EveAuthorizationLifecycleService.deferUnlockUntilTransactionCompletion(lock);
            EveMemberSyncResp response = synchronizeCurrentCorporation(tenantId, corporation);
            dataFreshnessService
                .recordSuccess(tenantId, corporationRefId, EveDataFreshnessService.MEMBER_ROSTER, response
                    .synchronizedAt(), response.rosterSourceExpiresAt());
            if (response.trackingSynchronized()) {
                dataFreshnessService
                    .recordSuccess(tenantId, corporationRefId, EveDataFreshnessService.MEMBER_TRACKING, response
                        .synchronizedAt(), response.trackingSourceExpiresAt());
            } else {
                dataFreshnessService
                    .recordFailure(tenantId, corporationRefId, EveDataFreshnessService.MEMBER_TRACKING, "TRACKING_UNAVAILABLE");
            }
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("成员数据正在同步，请稍后重试");
        } catch (RuntimeException e) {
            if (locked) {
                dataFreshnessService
                    .recordFailure(tenantId, corporationRefId, EveDataFreshnessService.MEMBER_ROSTER, e);
            }
            throw e;
        } finally {
            if (locked && !unlockDeferred && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 在同一军团互斥锁内同步基础名册与可选的追踪资源。 */
    private EveMemberSyncResp synchronizeCurrentCorporation(Long tenantId, EveCorporationDO corporation) {
        EveAuthorizationDO rosterSource = selectSource(tenantId, MEMBERSHIP_SCOPE, false);
        if (rosterSource == null) {
            throw new BusinessException("缺少可读取军团名册的有效国服授权，请由军团成员重新授权");
        }
        LocalDateTime synchronizedAt = LocalDateTime.now(ZoneOffset.UTC);
        SerenityEsiResponse<List<Long>> rosterResponse;
        try {
            rosterResponse = esiClient.getCorporationMembersWithMetadata(corporation
                .getCorporationId(), accessToken(rosterSource));
        } catch (SerenityEsiClientException | SerenityTokenClientException e) {
            syncRunService.record(tenantId, corporation
                .getId(), "ROSTER", "FAILED", 0, null, failureCode(e), synchronizedAt);
            throw e;
        }
        List<Long> memberIds = rosterResponse.body().stream().filter(Objects::nonNull).distinct().toList();
        if (memberIds.isEmpty()) {
            syncRunService.record(tenantId, corporation.getId(), "ROSTER", "FAILED", 0, rosterResponse
                .expiresAt(), "EMPTY_RESPONSE", synchronizedAt);
            throw new BusinessException("上游返回空成员名册，已拒绝发布本次同步结果");
        }
        Map<Long, String> names = resolveNames(memberIds);
        publishRoster(tenantId, corporation.getId(), memberIds, names, synchronizedAt);
        syncRunService.record(tenantId, corporation.getId(), "ROSTER", "SUCCEEDED", memberIds.size(), rosterResponse
            .expiresAt(), null, synchronizedAt);
        corporation.setLastSyncedAt(synchronizedAt);
        corporation.setMemberCount(memberIds.size());
        corporationMapper.updateById(corporation);

        EveAuthorizationDO trackingSource = selectSource(tenantId, TRACKING_SCOPE, true);
        if (trackingSource == null) {
            syncRunService.record(tenantId, corporation
                .getId(), "TRACKING", "SKIPPED", 0, null, "NO_QUALIFIED_SOURCE", synchronizedAt);
            return new EveMemberSyncResp(memberIds.size(), 0, false, "缺少总监追踪授权", synchronizedAt, rosterResponse
                .expiresAt(), null);
        }
        try {
            String trackingAccessToken = accessToken(trackingSource);
            SerenityEsiResponse<List<SerenityCorporationMemberTrackingResponse>> trackingResponse = esiClient
                .getCorporationMemberTrackingWithMetadata(corporation.getCorporationId(), trackingAccessToken);
            int trackingCount = publishTracking(tenantId, corporation.getId(), trackingResponse
                .body(), synchronizedAt, trackingResponse.expiresAt(), trackingAccessToken);
            syncRunService.record(tenantId, corporation
                .getId(), "TRACKING", "SUCCEEDED", trackingCount, trackingResponse.expiresAt(), null, synchronizedAt);
            return new EveMemberSyncResp(memberIds.size(), trackingCount, true, null, synchronizedAt, rosterResponse
                .expiresAt(), trackingResponse.expiresAt());
        } catch (SerenityEsiClientException | SerenityTokenClientException e) {
            log.warn("EVE 成员追踪同步失败，tenantId={}, corporationRefId={}, failureCode={}", tenantId, corporation
                .getId(), failureCode(e));
            syncRunService.record(tenantId, corporation
                .getId(), "TRACKING", "FAILED", 0, null, failureCode(e), synchronizedAt);
            return new EveMemberSyncResp(memberIds
                .size(), 0, false, "成员追踪同步失败：" + failureCode(e), synchronizedAt, rosterResponse.expiresAt(), null);
        }
    }

    /** 完整名册成功后才发布 ACTIVE/LEFT 状态，失败路径不会调用本方法。 */
    private void publishRoster(Long tenantId,
                               Long corporationRefId,
                               List<Long> characterIds,
                               Map<Long, String> names,
                               LocalDateTime observedAt) {
        Set<Long> currentIds = new HashSet<>(characterIds);
        for (EveCorporationRosterMemberDO existing : rosterMemberMapper
            .selectActiveByCorporation(tenantId, corporationRefId)) {
            if (!currentIds.contains(existing.getCharacterId())) {
                existing.setStatus("LEFT");
                existing.setLeftAt(observedAt);
                rosterMemberMapper.updateById(existing);
            }
        }
        for (Long characterId : characterIds) {
            EveCorporationRosterMemberDO member = rosterMemberMapper
                .selectByCharacterId(tenantId, corporationRefId, characterId);
            if (member == null) {
                member = new EveCorporationRosterMemberDO();
                member.setTenantId(tenantId);
                member.setCorporationRefId(corporationRefId);
                member.setCharacterId(characterId);
                member.setCharacterName(names.get(characterId));
                member.setStatus("ACTIVE");
                member.setLastSeenAt(observedAt);
                member.setCreateUser(1L);
                member.setDeleted(0L);
                rosterMemberMapper.insert(member);
                continue;
            }
            member.setStatus("ACTIVE");
            member.setLeftAt(null);
            member.setLastSeenAt(observedAt);
            if (names.containsKey(characterId)) {
                member.setCharacterName(names.get(characterId));
            }
            rosterMemberMapper.updateById(member);
        }
    }

    /** 发布追踪快照；未出现在当前名册的上游条目不会自动创建成员。 */
    private int publishTracking(Long tenantId,
                                Long corporationRefId,
                                List<SerenityCorporationMemberTrackingResponse> trackingEntries,
                                LocalDateTime observedAt,
                                LocalDateTime sourceExpiresAt,
                                String accessToken) {
        int persisted = 0;
        for (SerenityCorporationMemberTrackingResponse entry : trackingEntries) {
            if (entry == null || entry.characterId() == null) {
                continue;
            }
            EveCorporationRosterMemberDO member = rosterMemberMapper
                .selectByCharacterId(tenantId, corporationRefId, entry.characterId());
            if (member == null) {
                continue;
            }
            EveCorporationMemberTrackingDO tracking = trackingMapper.selectByRosterMemberId(tenantId, member.getId());
            if (tracking == null) {
                tracking = new EveCorporationMemberTrackingDO();
                tracking.setTenantId(tenantId);
                tracking.setRosterMemberId(member.getId());
                tracking.setCreateUser(1L);
                tracking.setDeleted(0L);
            }
            tracking.setBaseId(entry.baseId());
            tracking.setLocationId(entry.locationId());
            tracking.setShipTypeId(entry.shipTypeId());
            tracking.setLastLogonAt(entry.logonDate());
            tracking.setLastLogoffAt(entry.logoffDate());
            tracking.setSourceObservedAt(observedAt);
            tracking.setSourceExpiresAt(sourceExpiresAt);
            if (tracking.getId() == null) {
                trackingMapper.insert(tracking);
            } else {
                trackingMapper.updateById(tracking);
            }
            if (entry.startDate() != null) {
                member.setJoinedAt(entry.startDate());
                rosterMemberMapper.updateById(member);
            }
            persisted++;
        }
        resolveTrackingNames(tenantId, corporationRefId, accessToken);
        return persisted;
    }

    /** 使用公开批量名称接口补偿角色展示名；该接口只接受 32 位 ID。 */
    private Map<Long, String> resolveNames(List<Long> ids) {
        List<Long> resolvableIds = ids.stream()
            .filter(Objects::nonNull)
            .filter(id -> id > 0 && id <= Integer.MAX_VALUE)
            .distinct()
            .toList();
        Map<Long, String> names = staticNameReference.applyPublicLocationNames(resolvableIds, Map.of());
        names.putAll(staticNameReference.applyTypeNames(resolvableIds, Map.of()));
        List<Long> unresolvedIds = EveStaticNameReference.unresolvedIds(resolvableIds, names);
        for (int index = 0; index < unresolvedIds.size(); index += 1000) {
            try {
                esiClient.resolveUniverseNames(unresolvedIds.subList(index, Math.min(index + 1000, unresolvedIds
                    .size()))).forEach(item -> names.put(item.id(), item.name()));
            } catch (SerenityEsiClientException e) {
                // 名称解析不能阻止名册事实发布，后续同步会补偿。
                log.warn("EVE 成员名称解析失败，failureCode={}", e.getFailureCode());
            }
        }
        return names;
    }

    /**
     * 按 ID 类别补偿成员追踪名称。
     *
     * <p>舰船类型可通过公开批量名称接口解析；玩家建筑 ID 超过该接口的 32 位上限，
     * 必须使用已经授予结构读取 Scope 的角色单独查询。</p>
     */
    private void resolveTrackingNames(Long tenantId, Long corporationRefId, String accessToken) {
        List<EveCorporationRosterMemberDO> members = rosterMemberMapper
            .selectActiveByCorporation(tenantId, corporationRefId);
        Map<Long, EveCorporationMemberTrackingDO> trackingByMember = trackingByMemberIds(tenantId, members.stream()
            .map(EveCorporationRosterMemberDO::getId)
            .toList());
        List<Long> publicNameIds = trackingByMember.values()
            .stream()
            .flatMap(item -> java.util.stream.Stream.of(item.getLocationId(), item.getShipTypeId()))
            .filter(Objects::nonNull)
            .filter(id -> id > 0 && id <= Integer.MAX_VALUE)
            .distinct()
            .toList();
        Map<Long, String> publicNames = resolveNames(publicNameIds);
        List<Long> structureIds = trackingByMember.values()
            .stream()
            .map(EveCorporationMemberTrackingDO::getLocationId)
            .filter(Objects::nonNull)
            .filter(id -> id > Integer.MAX_VALUE)
            .distinct()
            .toList();
        Map<Long, String> structureNames = resolveStructureNames(structureIds, accessToken);
        trackingByMember.values().forEach(tracking -> {
            // 国服成员追踪没有稳定、可用的基地名称来源，保持空值且不对用户展示。
            tracking.setBaseName(null);
            tracking.setLocationName(resolveDisplayName(tracking.getLocationId(), publicNames, structureNames));
            tracking.setShipTypeName(publicNames.get(tracking.getShipTypeId()));
            trackingMapper.updateById(tracking);
        });
    }

    /** 逐项读取私有建筑名称；单个建筑不可见不应阻断其他成员的追踪数据。 */
    private Map<Long, String> resolveStructureNames(List<Long> structureIds, String accessToken) {
        Map<Long, String> names = new HashMap<>();
        for (Long structureId : structureIds) {
            EveStaticNameReference.StaticLocation reference = staticNameReference.findPublicStructure(structureId);
            if (reference != null) {
                names.put(structureId, reference.name());
                continue;
            }
            try {
                SerenityUniverseStructureResponse structure = esiClient.getUniverseStructure(structureId, accessToken);
                if (structure.name() != null && !structure.name().isBlank()) {
                    names.put(structureId, structure.name());
                }
            } catch (SerenityEsiClientException e) {
                log.warn("EVE 成员追踪建筑名称解析失败，failureCode={}", e.getFailureCode());
            }
        }
        return names;
    }

    /** 优先使用对应类别的解析结果，避免不同类别的同值 ID 相互覆盖。 */
    private static String resolveDisplayName(Long id, Map<Long, String> publicNames, Map<Long, String> structureNames) {
        return id == null ? null : id > Integer.MAX_VALUE ? structureNames.get(id) : publicNames.get(id);
    }

    /** 从同租户完整授权链选择单一数据源，不跨角色拼接 Scope、身份或令牌。 */
    private EveAuthorizationDO selectSource(Long tenantId, String requiredScope, boolean requireDirector) {
        return authorizationMapper.selectTenantCandidates(tenantId)
            .stream()
            .filter(item -> EveAuthorizationStatus.ACTIVE.equals(item.getStatus()))
            .filter(item -> item.getScopes() != null && item.getScopes().contains(requiredScope))
            .filter(item -> !requireDirector || isDirectorSource(tenantId, item))
            .findFirst()
            .orElse(null);
    }

    /** 判断数据源角色的最新快照是否仍具 CEO 或 Director 身份。 */
    private boolean isDirectorSource(Long tenantId, EveAuthorizationDO authorization) {
        EveCharacterRoleSnapshotDO snapshot = roleSnapshotMapper.selectLatest(tenantId, authorization
            .getCharacterRefId());
        return snapshot != null && Objects.equals(tenantId, snapshot.getTenantId()) && Objects.equals(authorization
            .getCharacterRefId(), snapshot.getCharacterRefId()) && snapshot.getSourceExpiresAt() != null && snapshot
                .getSourceExpiresAt()
                .isAfter(LocalDateTime.now(ZoneOffset.UTC)) && (Boolean.TRUE.equals(snapshot.getIsCeo()) || snapshot
                    .getRoles() != null && snapshot.getRoles().contains("Director"));
    }

    /** 通过既有生命周期服务获取可用令牌，令牌不会复制到同步记录或响应。 */
    private String accessToken(EveAuthorizationDO authorization) {
        return authorizationLifecycleService.ensureAccessToken(authorization);
    }

    /** 由当前会话反查军团绑定，浏览器不能指定或切换军团。 */
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

    /** 当前用户是否有本站追踪展示权限；无权限时调用方不得读取追踪表。 */
    private static boolean hasTrackingPermission(UserContext context) {
        return context.getPermissions() != null && (context.getPermissions()
            .contains("eve:members:track:view") || context.getPermissions().contains("*:*:*"));
    }

    /** 批量读取追踪数据，调用方必须已完成权限判断。 */
    private Map<Long, EveCorporationMemberTrackingDO> trackingByMemberIds(Long tenantId, List<Long> rosterMemberIds) {
        if (rosterMemberIds.isEmpty()) {
            return Map.of();
        }
        return trackingMapper.selectByRosterMemberIds(tenantId, rosterMemberIds)
            .stream()
            .collect(Collectors.toMap(EveCorporationMemberTrackingDO::getRosterMemberId, Function.identity()));
    }

    /** 写入脱敏追踪查看审计。 */
    private void auditTrackingAccess(Long tenantId, Long userId, Long rosterMemberId, String scope) {
        EveMemberTrackingAccessAuditDO audit = new EveMemberTrackingAccessAuditDO();
        audit.setTenantId(tenantId);
        audit.setUserId(userId);
        audit.setRosterMemberId(rosterMemberId);
        audit.setAccessScope(scope);
        audit.setOccurredAt(LocalDateTime.now(ZoneOffset.UTC));
        audit.setCreateUser(userId);
        audit.setDeleted(0L);
        trackingAccessAuditMapper.insert(audit);
    }

    /** 将内部快照转换为安全响应。 */
    private static EveMemberResp toResp(EveCorporationRosterMemberDO member, EveCorporationMemberTrackingDO tracking) {
        return new EveMemberResp(member.getId(), member.getCharacterId(), member.getCharacterName(), member
            .getOrganizationGroup(), member.getMemberNote(), member.getStatus(), member.getJoinedAt(), member
                .getLeftAt(), member.getLastSeenAt(), tracking == null
                    ? null
                    : new EveMemberTrackingResp(tracking.getBaseId(), tracking.getBaseName(), tracking
                        .getLocationId(), tracking.getLocationName(), tracking.getShipTypeId(), tracking
                            .getShipTypeName(), tracking.getLastLogonAt(), tracking.getLastLogoffAt(), tracking
                                .getSourceObservedAt(), tracking.getSourceExpiresAt()));
    }

    /** 空白分组与备注统一存为空值，避免同一语义出现多种空字符串。 */
    private static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 构造当前军团成员查询条件，列表与导出必须共享相同的租户、军团和筛选边界。 */
    private static LambdaQueryWrapper<EveCorporationRosterMemberDO> buildRosterQuery(EveCorporationDO corporation,
                                                                                     String keyword,
                                                                                     String status) {
        LambdaQueryWrapper<EveCorporationRosterMemberDO> query = new LambdaQueryWrapper<EveCorporationRosterMemberDO>()
            .eq(EveCorporationRosterMemberDO::getCorporationRefId, corporation.getId())
            .eq(EveCorporationRosterMemberDO::getDeleted, 0L)
            .orderByDesc(EveCorporationRosterMemberDO::getStatus)
            .orderByAsc(EveCorporationRosterMemberDO::getCharacterName, EveCorporationRosterMemberDO::getCharacterId);
        if (keyword != null && !keyword.isBlank()) {
            query.and(item -> item.like(EveCorporationRosterMemberDO::getCharacterName, keyword.trim())
                .or()
                .like(EveCorporationRosterMemberDO::getCharacterId, keyword.trim()));
        }
        if (status != null && !status.isBlank()) {
            query.eq(EveCorporationRosterMemberDO::getStatus, status.trim().toUpperCase());
        }
        return query;
    }

    /** 提取可安全记录和返回的上游失败分类。 */
    private static String failureCode(RuntimeException exception) {
        if (exception instanceof SerenityEsiClientException esiException) {
            return esiException.getFailureCode().name();
        }
        return ((SerenityTokenClientException)exception).getFailureCode().name();
    }
}
