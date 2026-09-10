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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.client.SerenityEsiClient;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterRoleSnapshotMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.mapper.EveCorporationStructureMapper;
import top.continew.admin.eve.model.EveCorporationStructureResp;
import top.continew.admin.eve.model.EveMeContextResp;
import top.continew.admin.eve.model.EveStructureSyncResp;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.entity.EveCorporationStructureDO;
import top.continew.admin.eve.model.entity.EveStaticLocationReferenceDO;
import top.continew.admin.eve.model.entity.EveStaticTypeReferenceDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.model.serenity.SerenityCorporationStructureResponse;
import top.continew.admin.eve.model.serenity.SerenityEsiPagedResponse;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 管理军团自有玩家建筑的完整快照、服务与燃料状态。
 *
 * <p>页面只能读取已持久化快照；同步必须由同军团、有效且具空间站管理员资格的授权执行。
 * 因此，国服分页失败或空响应不会覆盖原有建筑数据。</p>
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveCorporationStructureService {

    private static final String STRUCTURE_SCOPE = "esi-corporations.read_structures.v1";
    private static final int MAX_UPSTREAM_PAGES = 1000;

    private final EveContextService contextService;
    private final EveCorporationMapper corporationMapper;
    private final EveCorporationStructureMapper structureMapper;
    private final EveAuthorizationMapper authorizationMapper;
    private final EveCharacterRoleSnapshotMapper roleSnapshotMapper;
    private final EveAuthorizationLifecycleService authorizationLifecycleService;
    private final EvePermissionRefreshService permissionRefreshService;
    private final EveStaticReferenceService staticReferenceService;
    private final SerenityEsiClient esiClient;
    private final RedissonClient redissonClient;
    private final EveDataFreshnessService dataFreshnessService;

    /** 分页查询当前军团仍有效的建筑快照。 */
    public PageResp<EveCorporationStructureResp> page(int page, int size, String keyword, String state) {
        EveCorporationDO corporation = requireCurrentCorporation(UserContextHolder.getContext().getTenantId());
        LambdaQueryWrapper<EveCorporationStructureDO> query = new LambdaQueryWrapper<EveCorporationStructureDO>()
            .eq(EveCorporationStructureDO::getCorporationRefId, corporation.getId())
            .eq(EveCorporationStructureDO::getStatus, "ACTIVE")
            .eq(EveCorporationStructureDO::getDeleted, 0L)
            .orderByAsc(EveCorporationStructureDO::getFuelExpiresAt)
            .orderByAsc(EveCorporationStructureDO::getStructureName);
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            query.and(item -> item.like(EveCorporationStructureDO::getStructureName, value)
                .or()
                .like(EveCorporationStructureDO::getTypeName, value)
                .or()
                .like(EveCorporationStructureDO::getSolarSystemName, value)
                .or()
                .like(EveCorporationStructureDO::getStructureId, value));
        }
        if (state != null && !state.isBlank()) {
            query.eq(EveCorporationStructureDO::getState, state.trim());
        }
        Page<EveCorporationStructureDO> result = structureMapper.selectPage(new Page<>(page, size), query);
        return new PageResp<>(result.getRecords().stream().map(EveCorporationStructureService::toResp).toList(), result
            .getTotal());
    }

    /** 同步当前军团的全部建筑页面，并原子发布成功快照。 */
    @Transactional(rollbackFor = Exception.class)
    public EveStructureSyncResp syncCurrentTenant() {
        UserContext context = UserContextHolder.getContext();
        EveCorporationDO corporation = requireCurrentCorporation(context.getTenantId());
        RLock lock = redissonClient.getLock("eve:serenity:structure-sync:" + context.getTenantId() + ":" + corporation
            .getId());
        boolean locked = false;
        boolean unlockDeferred = false;
        try {
            locked = lock.tryLock(1, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("建筑数据正在同步，请稍后重试");
            }
            unlockDeferred = EveAuthorizationLifecycleService.deferUnlockUntilTransactionCompletion(lock);
            EveStructureSyncResp response = synchronize(context.getTenantId(), corporation);
            dataFreshnessService.recordSuccess(context.getTenantId(), corporation
                .getId(), EveDataFreshnessService.STRUCTURES, response.synchronizedAt(), response.sourceExpiresAt());
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("建筑数据正在同步，请稍后重试");
        } catch (RuntimeException e) {
            if (locked) {
                dataFreshnessService.recordFailure(context.getTenantId(), corporation
                    .getId(), EveDataFreshnessService.STRUCTURES, e);
            }
            throw e;
        } finally {
            if (locked && !unlockDeferred && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 由后台调度器同步指定军团建筑，不依赖登录会话。 */
    @Transactional(rollbackFor = Exception.class)
    public EveStructureSyncResp syncForCorporation(Long tenantId, Long corporationRefId) {
        EveCorporationDO corporation = requireSyncCorporation(tenantId, corporationRefId);
        RLock lock = redissonClient.getLock("eve:serenity:structure-sync:" + tenantId + ":" + corporationRefId);
        boolean locked = false;
        boolean unlockDeferred = false;
        try {
            locked = lock.tryLock(1, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("建筑数据正在同步，请稍后重试");
            }
            unlockDeferred = EveAuthorizationLifecycleService.deferUnlockUntilTransactionCompletion(lock);
            EveStructureSyncResp response = synchronize(tenantId, corporation);
            dataFreshnessService.recordSuccess(tenantId, corporationRefId, EveDataFreshnessService.STRUCTURES, response
                .synchronizedAt(), response.sourceExpiresAt());
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("建筑数据正在同步，请稍后重试");
        } catch (RuntimeException e) {
            if (locked) {
                dataFreshnessService.recordFailure(tenantId, corporationRefId, EveDataFreshnessService.STRUCTURES, e);
            }
            throw e;
        } finally {
            if (locked && !unlockDeferred && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 完整读取上游响应后才写入当前快照。 */
    private EveStructureSyncResp synchronize(Long tenantId, EveCorporationDO corporation) {
        EveAuthorizationDO source = selectSource(tenantId);
        if (source == null) {
            throw new BusinessException("缺少可读取军团建筑的有效空间站管理员或总监授权，请重新授权并刷新权限");
        }
        String accessToken = authorizationLifecycleService.ensureAccessToken(source);
        SerenityEsiPagedResponse<SerenityCorporationStructureResponse> firstPage = esiClient
            .getCorporationStructuresWithMetadata(corporation.getCorporationId(), 1, accessToken);
        if (firstPage.pageCount() > MAX_UPSTREAM_PAGES) {
            throw new BusinessException("国服建筑分页数量异常，已拒绝发布本次同步结果");
        }
        List<SerenityCorporationStructureResponse> sourceStructures = new ArrayList<>(firstPage.body());
        for (int page = 2; page <= firstPage.pageCount(); page++) {
            sourceStructures.addAll(esiClient.getCorporationStructuresWithMetadata(corporation
                .getCorporationId(), page, accessToken).body());
        }
        List<SerenityCorporationStructureResponse> validStructures = sourceStructures.stream()
            .filter(item -> item != null && item.structureId() != null && item.structureId() > 0 && item
                .typeId() != null && item.solarSystemId() != null)
            .collect(Collectors.collectingAndThen(Collectors
                .toMap(SerenityCorporationStructureResponse::structureId, item -> item, (left,
                                                                                         right) -> right), values -> new ArrayList<>(values
                                                                                             .values())));
        LocalDateTime observedAt = LocalDateTime.now(ZoneOffset.UTC);
        // 军团没有自有建筑是合法完整快照，必须发布 MISSING 状态而不是把旧建筑误报为仍存在。
        publish(tenantId, corporation.getId(), validStructures, observedAt, firstPage.expiresAt());
        return new EveStructureSyncResp(validStructures.size(), firstPage.pageCount(), observedAt, firstPage
            .expiresAt());
    }

    /** 将本次完整数据替换为当前快照，失败路径不会调用该方法。 */
    private void publish(Long tenantId,
                         Long corporationRefId,
                         List<SerenityCorporationStructureResponse> sources,
                         LocalDateTime observedAt,
                         LocalDateTime sourceExpiresAt) {
        Map<Long, EveCorporationStructureDO> existing = structureMapper
            .selectList(new LambdaQueryWrapper<EveCorporationStructureDO>()
                .eq(EveCorporationStructureDO::getCorporationRefId, corporationRefId)
                .eq(EveCorporationStructureDO::getDeleted, 0L))
            .stream()
            .collect(Collectors.toMap(EveCorporationStructureDO::getStructureId, item -> item, (left, right) -> left));
        Map<Integer, EveStaticTypeReferenceDO> types = staticReferenceService.findTypes(sources.stream()
            .map(SerenityCorporationStructureResponse::typeId)
            .collect(Collectors.toSet()));
        Map<Long, EveStaticLocationReferenceDO> systems = staticReferenceService.findLocations("SOLAR_SYSTEM", sources
            .stream()
            .map(SerenityCorporationStructureResponse::solarSystemId)
            .collect(Collectors.toSet()));
        Set<Long> seen = new HashSet<>();
        for (SerenityCorporationStructureResponse source : sources) {
            seen.add(source.structureId());
            EveCorporationStructureDO target = existing.remove(source.structureId());
            if (target == null) {
                target = new EveCorporationStructureDO();
                target.setTenantId(tenantId);
                target.setCorporationRefId(corporationRefId);
                target.setStructureId(source.structureId());
                target.setCreateUser(1L);
                target.setDeleted(0L);
            }
            EveStaticTypeReferenceDO type = types.get(source.typeId());
            EveStaticLocationReferenceDO system = systems.get(source.solarSystemId());
            target.setTypeId(source.typeId());
            target.setTypeName(type == null ? null : type.getTypeName());
            target.setStructureName(source.name());
            target.setSolarSystemId(source.solarSystemId());
            target.setSolarSystemName(system == null ? null : system.getReferenceName());
            target.setState(source.state());
            target.setFuelExpiresAt(source.fuelExpiresAt());
            target.setStateTimerStartAt(source.stateTimerStartAt());
            target.setStateTimerEndAt(source.stateTimerEndAt());
            target.setUnanchorsAt(source.unanchorsAt());
            target.setServices(source.services() == null
                ? List.of()
                : source.services()
                    .stream()
                    .map(item -> new EveCorporationStructureDO.StructureService(item.name(), item.state()))
                    .toList());
            target.setStatus("ACTIVE");
            target.setLastSeenAt(observedAt);
            target.setSourceExpiresAt(sourceExpiresAt);
            if (target.getId() == null) {
                structureMapper.insert(target);
            } else {
                structureMapper.updateById(target);
            }
        }
        existing.values().forEach(item -> {
            item.setStatus("MISSING");
            structureMapper.updateById(item);
        });
    }

    /** 从同租户授权中选择同时满足 Scope 与游戏角色资格的同步数据源。 */
    private EveAuthorizationDO selectSource(Long tenantId) {
        List<EveAuthorizationDO> candidates = authorizationMapper.selectTenantCandidates(tenantId)
            .stream()
            .filter(item -> EveAuthorizationStatus.ACTIVE.equals(item.getStatus()))
            .filter(item -> item.getScopes() != null && item.getScopes().contains(STRUCTURE_SCOPE))
            .toList();
        for (EveAuthorizationDO candidate : candidates) {
            if (hasStructureRole(tenantId, candidate)) {
                return candidate;
            }
            // 角色快照到期时先从国服复核，避免有效的总监或空间站管理员被陈旧缓存误判为无权同步。
            permissionRefreshService.reviewAuthorization(candidate);
            if (hasStructureRole(tenantId, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** CEO、总监或空间站管理员均可作为建筑同步来源。 */
    private boolean hasStructureRole(Long tenantId, EveAuthorizationDO authorization) {
        EveCharacterRoleSnapshotDO snapshot = roleSnapshotMapper.selectLatest(tenantId, authorization
            .getCharacterRefId());
        return snapshot != null && Objects.equals(tenantId, snapshot.getTenantId()) && Objects.equals(authorization
            .getCharacterRefId(), snapshot.getCharacterRefId()) && snapshot.getSourceExpiresAt() != null && snapshot
                .getSourceExpiresAt()
                .isAfter(LocalDateTime.now(ZoneOffset.UTC)) && (Boolean.TRUE.equals(snapshot.getIsCeo()) || snapshot
                    .getRoles() != null && (snapshot.getRoles().contains("Director") || snapshot.getRoles()
                        .contains("Station_Manager")));
    }

    /** 由当前会话反查军团，客户端不能提供或猜测军团标识。 */
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

    /** 转换为不含授权和数据源信息的页面响应。 */
    private static EveCorporationStructureResp toResp(EveCorporationStructureDO source) {
        List<EveCorporationStructureResp.Service> services = source.getServices() == null
            ? List.of()
            : source.getServices()
                .stream()
                .map(item -> new EveCorporationStructureResp.Service(item.name(), item.state()))
                .toList();
        return new EveCorporationStructureResp(source.getStructureId(), source.getTypeId(), source.getTypeName(), source
            .getStructureName(), source.getSolarSystemId(), source.getSolarSystemName(), source.getState(), source
                .getFuelExpiresAt(), source.getStateTimerStartAt(), source.getStateTimerEndAt(), source
                    .getUnanchorsAt(), services, source.getStatus(), source.getLastSeenAt(), source
                        .getSourceExpiresAt());
    }
}
