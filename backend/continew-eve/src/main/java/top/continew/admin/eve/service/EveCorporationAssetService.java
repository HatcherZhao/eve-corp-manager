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
import top.continew.admin.eve.mapper.EveCorporationAssetMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.model.EveAssetSyncResp;
import top.continew.admin.eve.model.EveCorporationAssetResp;
import top.continew.admin.eve.model.EveCorporationAssetTreeNodeResp;
import top.continew.admin.eve.model.EveCorporationAssetTreeResp;
import top.continew.admin.eve.model.EveMeContextResp;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO;
import top.continew.admin.eve.model.entity.EveCorporationAssetDO;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.entity.EveStaticTypeReferenceDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.model.serenity.SerenityCorporationAssetNameResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationAssetResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationStructureResponse;
import top.continew.admin.eve.model.serenity.SerenityEsiPagedResponse;
import top.continew.admin.eve.model.serenity.SerenityUniverseStationResponse;
import top.continew.admin.eve.model.serenity.SerenityUniverseStructureResponse;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 管理军团资产当前快照及其基础名称解析。
 *
 * <p>资产读取只使用同租户且仍具总监身份的一条授权；浏览器始终从当前会话推导军团，不能指定
 * 军团、授权或角色。一次同步必须读取完上游声明的全部页面，成功后才发布 ACTIVE/MISSING 状态。</p>
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EveCorporationAssetService {

    static final String ASSET_SCOPE = "esi-assets.read_corporation_assets.v1";
    private static final String CORPORATION_STRUCTURE_SCOPE = "esi-corporations.read_structures.v1";
    private static final String STRUCTURE_SCOPE = "esi-universe.read_structures.v1";
    private static final int MAX_UPSTREAM_PAGES = 1000;
    private static final Pattern NPC_STATION_NAME_PATTERN = Pattern
        .compile("^.+?\\s+([0-9]+|[IVXLCDM]+)\\s+-\\s+Moon\\s+([0-9]+)\\s+-\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern INDEXED_STORAGE_NAME_PATTERN = Pattern
        .compile("^(HiSlot|MedSlot|LoSlot|RigSlot|ServiceSlot|FighterTube)([0-9]+)$");

    private final EveContextService contextService;
    private final EveCorporationMapper corporationMapper;
    private final EveCorporationAssetMapper assetMapper;
    private final EveAuthorizationMapper authorizationMapper;
    private final EveCharacterRoleSnapshotMapper roleSnapshotMapper;
    private final EveAuthorizationLifecycleService authorizationLifecycleService;
    private final SerenityEsiClient esiClient;
    private final RedissonClient redissonClient;
    private final EveStaticNameReference staticNameReference;
    private final EveStaticReferenceService staticReferenceService;

    /** 分页查询当前军团最近完整快照中仍存在的资产。 */
    public PageResp<EveCorporationAssetResp> page(int page, int size, String keyword, String locationType) {
        UserContext context = UserContextHolder.getContext();
        EveCorporationDO corporation = requireCurrentCorporation(context.getTenantId());
        LambdaQueryWrapper<EveCorporationAssetDO> query = new LambdaQueryWrapper<EveCorporationAssetDO>()
            .eq(EveCorporationAssetDO::getCorporationRefId, corporation.getId())
            .eq(EveCorporationAssetDO::getStatus, "ACTIVE")
            .eq(EveCorporationAssetDO::getDeleted, 0L)
            .orderByAsc(EveCorporationAssetDO::getLocationName, EveCorporationAssetDO::getLocationId)
            .orderByAsc(EveCorporationAssetDO::getTypeName, EveCorporationAssetDO::getTypeId)
            .orderByAsc(EveCorporationAssetDO::getItemId);
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            query.and(item -> item.like(EveCorporationAssetDO::getTypeName, value)
                .or()
                .like(EveCorporationAssetDO::getItemName, value)
                .or()
                .like(EveCorporationAssetDO::getLocationName, value)
                .or()
                .like(EveCorporationAssetDO::getItemId, value));
        }
        if (locationType != null && !locationType.isBlank()) {
            query.eq(EveCorporationAssetDO::getLocationType, locationType.trim());
        }
        Page<EveCorporationAssetDO> result = assetMapper.selectPage(new Page<>(page, size), query);
        return new PageResp<>(result.getRecords().stream().map(EveCorporationAssetService::toResp).toList(), result
            .getTotal());
    }

    /**
     * 查询当前军团完整资产树，按星系、空间站或建筑、仓库和实际物品层级返回。
     *
     * <p>物品箱不是虚拟层级：只有游戏资产之间存在父子引用时，父资产才会作为容器节点返回；
     * 已装配或装载内容的舰船会单独标识为舰船节点。</p>
     */
    public EveCorporationAssetTreeResp tree() {
        UserContext context = UserContextHolder.getContext();
        EveCorporationDO corporation = requireCurrentCorporation(context.getTenantId());
        List<EveCorporationAssetDO> assets = assetMapper.selectList(new LambdaQueryWrapper<EveCorporationAssetDO>()
            .eq(EveCorporationAssetDO::getCorporationRefId, corporation.getId())
            .eq(EveCorporationAssetDO::getStatus, "ACTIVE")
            .eq(EveCorporationAssetDO::getDeleted, 0L));
        Map<Integer, EveStaticTypeReferenceDO> typeReferences = staticReferenceService.findTypes(assets.stream()
            .map(EveCorporationAssetDO::getTypeId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet()));
        return new EveCorporationAssetTreeResp(buildTree(assets, typeReferences), assets.size());
    }

    /** 手动同步当前军团资产，并在同步完成后发布新的当前快照。 */
    @Transactional(rollbackFor = Exception.class)
    public EveAssetSyncResp syncCurrentTenant() {
        UserContext context = UserContextHolder.getContext();
        Long tenantId = context.getTenantId();
        EveCorporationDO corporation = requireCurrentCorporation(tenantId);
        RLock lock = redissonClient.getLock("eve:serenity:asset-sync:" + tenantId + ":" + corporation.getId());
        boolean locked = false;
        boolean unlockDeferred = false;
        try {
            locked = lock.tryLock(1, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("资产数据正在同步，请稍后重试");
            }
            unlockDeferred = EveAuthorizationLifecycleService.deferUnlockUntilTransactionCompletion(lock);
            return synchronizeCurrentCorporation(tenantId, corporation);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("资产数据正在同步，请稍后重试");
        } finally {
            if (locked && !unlockDeferred && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 读取所有资产页面，完成基础名称解析后再发布当前快照。 */
    private EveAssetSyncResp synchronizeCurrentCorporation(Long tenantId, EveCorporationDO corporation) {
        EveAuthorizationDO source = selectAssetSource(tenantId);
        if (source == null) {
            throw new BusinessException("缺少可读取军团资产的有效总监授权，请由总监重新授权");
        }
        String accessToken = authorizationLifecycleService.ensureAccessToken(source);
        LocalDateTime synchronizedAt = LocalDateTime.now(ZoneOffset.UTC);
        List<SerenityCorporationAssetResponse> assets = new ArrayList<>();
        SerenityEsiPagedResponse<SerenityCorporationAssetResponse> firstPage = esiClient
            .getCorporationAssetsWithMetadata(corporation.getCorporationId(), 1, accessToken);
        int pageCount = firstPage.pageCount();
        if (pageCount > MAX_UPSTREAM_PAGES) {
            throw new BusinessException("国服资产分页数量异常，已拒绝发布本次同步结果");
        }
        assets.addAll(firstPage.body());
        for (int page = 2; page <= pageCount; page++) {
            assets.addAll(esiClient.getCorporationAssetsWithMetadata(corporation.getCorporationId(), page, accessToken)
                .body());
        }
        List<SerenityCorporationAssetResponse> validAssets = assets.stream()
            .filter(asset -> asset != null && asset.itemId() != null && asset.itemId() > 0 && asset.typeId() != null)
            .collect(Collectors.collectingAndThen(Collectors
                .toMap(SerenityCorporationAssetResponse::itemId, item -> item, (left,
                                                                                right) -> right), values -> new ArrayList<>(values
                                                                                    .values())));
        if (validAssets.isEmpty()) {
            throw new BusinessException("上游返回空资产快照，已保留现有数据并拒绝发布本次同步结果");
        }
        NameResolution names = resolveNames(corporation.getCorporationId(), validAssets, accessToken, source
            .getScopes());
        publishSnapshot(tenantId, corporation.getId(), validAssets, names, synchronizedAt, firstPage.expiresAt());
        return new EveAssetSyncResp(validAssets.size(), pageCount, synchronizedAt, firstPage.expiresAt());
    }

    /** 成功读取全部页面后原子发布资产状态，失败或空响应路径不会调用本方法。 */
    private void publishSnapshot(Long tenantId,
                                 Long corporationRefId,
                                 List<SerenityCorporationAssetResponse> sourceAssets,
                                 NameResolution names,
                                 LocalDateTime observedAt,
                                 LocalDateTime sourceExpiresAt) {
        List<EveCorporationAssetDO> existingAssets = assetMapper
            .selectList(new LambdaQueryWrapper<EveCorporationAssetDO>()
                .eq(EveCorporationAssetDO::getCorporationRefId, corporationRefId)
                .eq(EveCorporationAssetDO::getDeleted, 0L));
        Map<Long, EveCorporationAssetDO> existingByItemId = existingAssets.stream()
            .collect(Collectors.toMap(EveCorporationAssetDO::getItemId, item -> item, (left, right) -> left));
        Set<Long> currentItemIds = new HashSet<>();
        for (SerenityCorporationAssetResponse source : sourceAssets) {
            currentItemIds.add(source.itemId());
            EveCorporationAssetDO asset = existingByItemId.remove(source.itemId());
            if (asset == null) {
                asset = new EveCorporationAssetDO();
                asset.setTenantId(tenantId);
                asset.setCorporationRefId(corporationRefId);
                asset.setItemId(source.itemId());
                asset.setCreateUser(1L);
                asset.setDeleted(0L);
            }
            asset.setTypeId(source.typeId());
            asset.setTypeName(names.typeNames().get(source.typeId().longValue()));
            asset.setItemName(nonBlankOr(names.corporationStructureNames().get(source.itemId()), names.itemNames()
                .get(source.itemId())));
            asset.setCorporationStructure(names.corporationStructureNames().containsKey(source.itemId()));
            asset.setLocationId(source.locationId());
            LocationResolution location = resolveLocation(source, names);
            asset.setLocationName(location.name());
            asset.setSolarSystemId(location.solarSystemId());
            asset.setSolarSystemName(location.solarSystemName());
            asset.setLocationType(source.locationType());
            asset.setLocationFlag(source.locationFlag());
            asset.setQuantity(source.quantity());
            asset.setSingleton(source.singleton());
            asset.setBlueprintCopy(source.blueprintCopy());
            asset.setStatus("ACTIVE");
            asset.setLastSeenAt(observedAt);
            asset.setSourceExpiresAt(sourceExpiresAt);
            if (asset.getId() == null) {
                assetMapper.insert(asset);
            } else {
                assetMapper.updateById(asset);
            }
        }
        for (EveCorporationAssetDO missing : existingByItemId.values()) {
            missing.setStatus("MISSING");
            assetMapper.updateById(missing);
        }
    }

    /** 解析资产类型、可命名物品和可识别位置；单项解析失败只保留 ID，不阻断资产事实发布。 */
    private NameResolution resolveNames(Long corporationId,
                                        List<SerenityCorporationAssetResponse> assets,
                                        String accessToken,
                                        List<String> scopes) {
        List<Long> typeIds = assets.stream()
            .map(SerenityCorporationAssetResponse::typeId)
            .filter(Objects::nonNull)
            .map(Integer::longValue)
            .distinct()
            .toList();
        Map<Long, String> typeNames = resolveUniverseNames(typeIds, "类型", staticNameReference
            .applyTypeNames(typeIds, Map.of()));
        List<Long> publicLocationIds = assets.stream()
            .filter(asset -> "station".equals(asset.locationType()) || "solar_system".equals(asset.locationType()))
            .map(SerenityCorporationAssetResponse::locationId)
            .filter(this::isPublicUniverseId)
            .distinct()
            .toList();
        Map<Long, String> publicLocationNames = resolveUniverseNames(publicLocationIds, "位置", staticNameReference
            .applyPublicLocationNames(publicLocationIds, Map.of()));
        Map<Long, String> itemNames = resolveAssetNames(corporationId, assets.stream()
            .map(SerenityCorporationAssetResponse::itemId)
            .distinct()
            .toList(), accessToken);
        Map<Long, String> corporationStructureNames = scopes != null && scopes.contains(CORPORATION_STRUCTURE_SCOPE)
            ? resolveCorporationStructureNames(corporationId, accessToken)
            : Map.of();
        Map<Long, LocationResolution> stationLocations = resolveStationLocations(assets.stream()
            .filter(asset -> "station".equals(asset.locationType()))
            .map(SerenityCorporationAssetResponse::locationId)
            .filter(this::isPublicUniverseId)
            .distinct()
            .toList(), publicLocationNames);
        Map<Long, LocationResolution> structureLocations = scopes != null && scopes.contains(STRUCTURE_SCOPE)
            ? resolveStructureLocations(assets.stream()
                .filter(asset -> "station".equals(asset.locationType()))
                .map(SerenityCorporationAssetResponse::locationId)
                .filter(id -> id != null && id > Integer.MAX_VALUE)
                .distinct()
                .toList(), accessToken)
            : Map.of();
        Map<Long, LocationResolution> locations = new HashMap<>(stationLocations);
        locations.putAll(structureLocations);
        Set<Long> solarSystemIds = new HashSet<>();
        assets.stream()
            .filter(asset -> "solar_system".equals(asset.locationType()))
            .map(SerenityCorporationAssetResponse::locationId)
            .filter(this::isPublicUniverseId)
            .forEach(solarSystemIds::add);
        locations.values()
            .stream()
            .map(LocationResolution::solarSystemId)
            .filter(Objects::nonNull)
            .forEach(solarSystemIds::add);
        List<Long> solarSystemIdList = new ArrayList<>(solarSystemIds);
        Map<Long, String> solarSystemNames = resolveUniverseNames(solarSystemIdList, "星系", staticNameReference
            .applySolarSystemNames(solarSystemIdList, Map.of()));
        return new NameResolution(typeNames, publicLocationNames, itemNames, corporationStructureNames, locations, solarSystemNames);
    }

    /** 读取本军团建筑清单，以资产 item ID 为键保存建筑自定义名称。 */
    private Map<Long, String> resolveCorporationStructureNames(Long corporationId, String accessToken) {
        Map<Long, String> names = new HashMap<>();
        try {
            SerenityEsiPagedResponse<SerenityCorporationStructureResponse> firstPage = esiClient
                .getCorporationStructuresWithMetadata(corporationId, 1, accessToken);
            firstPage.body().forEach(item -> names.put(item.structureId(), item.name()));
            for (int page = 2; page <= firstPage.pageCount(); page++) {
                esiClient.getCorporationStructuresWithMetadata(corporationId, page, accessToken)
                    .body()
                    .forEach(item -> names.put(item.structureId(), item.name()));
            }
        } catch (SerenityEsiClientException e) {
            log.warn("EVE 军团自有建筑解析失败，failureCode={}", e.getFailureCode());
        }
        return names;
    }

    /** 通过公开批量名称接口按每批 1000 个 ID 解析物品类型或公共位置名称。 */
    private Map<Long, String> resolveUniverseNames(List<Long> ids, String category, Map<Long, String> referenceNames) {
        Map<Long, String> names = new HashMap<>(referenceNames);
        List<Long> unresolvedIds = EveStaticNameReference.unresolvedIds(ids, names);
        for (int index = 0; index < unresolvedIds.size(); index += 1000) {
            try {
                esiClient.resolveUniverseNames(unresolvedIds.subList(index, Math.min(index + 1000, unresolvedIds
                    .size()))).forEach(item -> names.put(item.id(), item.name()));
            } catch (SerenityEsiClientException e) {
                log.warn("EVE 资产{}名称解析失败，failureCode={}", category, e.getFailureCode());
            }
        }
        return names;
    }

    /** 读取游戏中已有自定义名称的物品；不支持命名或单批失败时按 ID 回退。 */
    private Map<Long, String> resolveAssetNames(Long corporationId, List<Long> itemIds, String accessToken) {
        Map<Long, String> names = new HashMap<>();
        for (int index = 0; index < itemIds.size(); index += 1000) {
            try {
                List<SerenityCorporationAssetNameResponse> response = esiClient
                    .getCorporationAssetNames(corporationId, itemIds.subList(index, Math.min(index + 1000, itemIds
                        .size())), accessToken);
                response.stream()
                    .filter(item -> item.itemId() != null && item.name() != null && !item.name().isBlank())
                    .forEach(item -> names.put(item.itemId(), item.name()));
            } catch (SerenityEsiClientException e) {
                log.warn("EVE 资产自定义名称解析失败，failureCode={}", e.getFailureCode());
            }
        }
        return names;
    }

    /** 逐项解析公开空间站及其所属星系；单项失败时仍保留批量名称解析结果。 */
    private Map<Long, LocationResolution> resolveStationLocations(List<Long> stationIds,
                                                                  Map<Long, String> publicLocationNames) {
        Map<Long, LocationResolution> locations = new HashMap<>();
        for (Long stationId : stationIds) {
            EveStaticNameReference.StaticLocation reference = staticNameReference.findNpcStation(stationId);
            if (reference != null) {
                locations.put(stationId, new LocationResolution(reference.name(), reference.solarSystemId(), null));
                continue;
            }
            try {
                SerenityUniverseStationResponse station = esiClient.getUniverseStation(stationId);
                locations.put(stationId, new LocationResolution(nonBlankOr(station.name(), publicLocationNames
                    .get(stationId)), station.solarSystemId(), null));
            } catch (SerenityEsiClientException e) {
                log.warn("EVE 资产空间站位置解析失败，failureCode={}", e.getFailureCode());
                locations.put(stationId, new LocationResolution(publicLocationNames.get(stationId), null, null));
            }
        }
        return locations;
    }

    /** 逐项解析玩家建筑位置及其所属星系；没有结构读取 Scope 或单项不可见时保留原始 ID。 */
    private Map<Long, LocationResolution> resolveStructureLocations(List<Long> structureIds, String accessToken) {
        Map<Long, LocationResolution> locations = new HashMap<>();
        for (Long structureId : structureIds) {
            EveStaticNameReference.StaticLocation reference = staticNameReference.findPublicStructure(structureId);
            if (reference != null) {
                locations.put(structureId, new LocationResolution(reference.name(), reference.solarSystemId(), null));
                continue;
            }
            try {
                SerenityUniverseStructureResponse structure = esiClient.getUniverseStructure(structureId, accessToken);
                locations.put(structureId, new LocationResolution(structure.name(), structure.solarSystemId(), null));
            } catch (SerenityEsiClientException e) {
                log.warn("EVE 资产建筑位置解析失败，failureCode={}", e.getFailureCode());
            }
        }
        return locations;
    }

    /** 为不同位置类别选择对应的解析来源，防止物品 ID 被误当为公共宇宙 ID。 */
    private static LocationResolution resolveLocation(SerenityCorporationAssetResponse asset, NameResolution names) {
        if (asset.locationId() == null) {
            return LocationResolution.EMPTY;
        }
        if ("item".equals(asset.locationType())) {
            return new LocationResolution(names.itemNames().get(asset.locationId()), null, null);
        }
        if ("solar_system".equals(asset.locationType())) {
            String solarSystemName = names.solarSystemNames().get(asset.locationId());
            return new LocationResolution(nonBlankOr(solarSystemName, names.publicLocationNames()
                .get(asset.locationId())), asset.locationId(), solarSystemName);
        }
        LocationResolution location = names.locations().get(asset.locationId());
        if (location == null) {
            return new LocationResolution(names.publicLocationNames().get(asset.locationId()), null, null);
        }
        return new LocationResolution(location.name(), location.solarSystemId(), names.solarSystemNames()
            .get(location.solarSystemId()));
    }

    /** 从同租户完整授权链选择有资产 Scope 且角色快照仍有效的总监数据源。 */
    private EveAuthorizationDO selectAssetSource(Long tenantId) {
        return authorizationMapper.selectTenantCandidates(tenantId)
            .stream()
            .filter(item -> EveAuthorizationStatus.ACTIVE.equals(item.getStatus()))
            .filter(item -> item.getScopes() != null && item.getScopes().contains(ASSET_SCOPE))
            .filter(item -> isDirectorSource(tenantId, item))
            .findFirst()
            .orElse(null);
    }

    /** 判断数据源角色的最新上游快照是否仍是 CEO 或总监。 */
    private boolean isDirectorSource(Long tenantId, EveAuthorizationDO authorization) {
        EveCharacterRoleSnapshotDO snapshot = roleSnapshotMapper.selectLatest(tenantId, authorization
            .getCharacterRefId());
        return snapshot != null && Objects.equals(tenantId, snapshot.getTenantId()) && Objects.equals(authorization
            .getCharacterRefId(), snapshot.getCharacterRefId()) && snapshot.getSourceExpiresAt() != null && snapshot
                .getSourceExpiresAt()
                .isAfter(LocalDateTime.now(ZoneOffset.UTC)) && (Boolean.TRUE.equals(snapshot.getIsCeo()) || snapshot
                    .getRoles() != null && snapshot.getRoles().contains("Director"));
    }

    /** 从当前会话反查军团绑定，客户端不能指定军团或租户。 */
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

    /** 公开批量名称接口仅接受 32 位有效宇宙 ID。 */
    private boolean isPublicUniverseId(Long id) {
        return id != null && id > 0 && id <= Integer.MAX_VALUE;
    }

    /** 转换资产快照响应，绝不返回服务端数据源或授权信息。 */
    private static EveCorporationAssetResp toResp(EveCorporationAssetDO asset) {
        return new EveCorporationAssetResp(asset.getItemId(), asset.getTypeId(), asset.getTypeName(), asset
            .getItemName(), asset.getLocationId(), asset.getLocationName(), asset.getLocationType(), asset
                .getLocationFlag(), asset.getQuantity(), asset.getSingleton(), asset.getBlueprintCopy(), asset
                    .getLastSeenAt(), asset.getSourceExpiresAt());
    }

    /** 将完整快照拼成安全的物理位置与容器树，循环和缺失父项统一降级到未归类位置。 */
    static List<EveCorporationAssetTreeNodeResp> buildTree(List<EveCorporationAssetDO> assets) {
        return buildTree(assets, Map.of());
    }

    /** 根据静态类型分类构建树，舰船识别不依赖中文名称或当前资产快照。 */
    static List<EveCorporationAssetTreeNodeResp> buildTree(List<EveCorporationAssetDO> assets,
                                                           Map<Integer, EveStaticTypeReferenceDO> typeReferences) {
        Map<Long, EveCorporationAssetDO> assetsByItemId = assets.stream()
            .filter(asset -> asset.getItemId() != null)
            .collect(Collectors.toMap(EveCorporationAssetDO::getItemId, item -> item, (left, right) -> left));
        Map<Long, Long> parentByItemId = new HashMap<>();
        for (EveCorporationAssetDO asset : assetsByItemId.values()) {
            if ("item".equals(asset.getLocationType()) && asset.getLocationId() != null && !Objects.equals(asset
                .getItemId(), asset.getLocationId()) && assetsByItemId.containsKey(asset.getLocationId())) {
                parentByItemId.put(asset.getItemId(), asset.getLocationId());
            }
        }
        Set<Long> cyclicItems = parentByItemId.keySet()
            .stream()
            .filter(itemId -> hasParentCycle(itemId, parentByItemId))
            .collect(Collectors.toSet());
        cyclicItems.forEach(parentByItemId::remove);

        Map<Long, MutableTreeNode> assetNodes = new HashMap<>();
        assetsByItemId.values()
            .forEach(asset -> assetNodes.put(asset.getItemId(), MutableTreeNode.asset(asset, typeReferences.get(asset
                .getTypeId()))));
        parentByItemId.forEach((itemId, parentId) -> assetNodes.get(parentId).children.add(assetNodes.get(itemId)));
        groupCorporationStructureChildren(assetNodes.values());
        groupCorporationOfficeChildren(assetNodes.values());
        groupAssetSafetyPackageChildren(assetNodes.values());
        groupShipChildren(assetNodes.values());

        Map<String, MutableTreeNode> systems = new LinkedHashMap<>();
        for (EveCorporationAssetDO asset : assetsByItemId.values()) {
            if (parentByItemId.containsKey(asset.getItemId())) {
                continue;
            }
            LocationPath path = resolveLocationPath(asset, assetsByItemId, cyclicItems);
            MutableTreeNode system = systems.computeIfAbsent(path.solarSystemKey(), ignored -> MutableTreeNode
                .virtual(path.solarSystemKey(), path.solarSystemName(), "solar_system"));
            if (Boolean.TRUE.equals(asset.getCorporationStructure())) {
                system.children.add(assetNodes.get(asset.getItemId()));
                continue;
            }
            if ("solar_system".equals(asset.getLocationType())) {
                MutableTreeNode spaceAsset = assetNodes.get(asset.getItemId());
                spaceAsset.kind = spaceAsset.isNpcStructure() ? "npc_structure" : "space_asset";
                system.children.add(spaceAsset);
                continue;
            }
            MutableTreeNode location = system.children.stream()
                .filter(node -> node.key.equals(path.locationKey()))
                .findFirst()
                .orElseGet(() -> {
                    MutableTreeNode created = MutableTreeNode.virtual(path.locationKey(), path.locationName(), path
                        .locationKind());
                    system.children.add(created);
                    return created;
                });
            String warehouseKey = path.locationKey() + ":warehouse:" + nonBlankOr(asset
                .getLocationFlag(), "unclassified");
            MutableTreeNode warehouse = location.children.stream()
                .filter(node -> node.key.equals(warehouseKey))
                .findFirst()
                .orElseGet(() -> {
                    MutableTreeNode created = MutableTreeNode.virtual(warehouseKey, localizeWarehouseName(asset
                        .getLocationFlag()), "warehouse");
                    location.children.add(created);
                    return created;
                });
            warehouse.children.add(assetNodes.get(asset.getItemId()));
        }
        return systems.values()
            .stream()
            .map(MutableTreeNode::toResp)
            .sorted(Comparator.comparing(EveCorporationAssetTreeNodeResp::title, Comparator
                .nullsLast(String::compareTo)))
            .toList();
    }

    /**
     * 将军团自有建筑内的直属资产按游戏建筑分区分组。
     *
     * <p>建筑自身的 {@code AutoFit} 表示它部署在太空中，不能被展示为建筑内部仓位；真正的仓位
     * 以子资产的 {@code location_flag} 为准。</p>
     */
    private static void groupCorporationStructureChildren(Iterable<MutableTreeNode> assetNodes) {
        for (MutableTreeNode structure : assetNodes) {
            if (!Boolean.TRUE.equals(structure.asset.getCorporationStructure()) || structure.children.isEmpty()) {
                continue;
            }
            List<MutableTreeNode> directChildren = new ArrayList<>(structure.children);
            structure.children.clear();
            MutableTreeNode compartmentGroup = MutableTreeNode
                .virtual(structure.key + ":compartments", "建筑分区", "structure_compartment_group");
            Map<String, MutableTreeNode> compartments = new LinkedHashMap<>();
            for (MutableTreeNode child : directChildren) {
                String locationFlag = nonBlankOr(child.asset.getLocationFlag(), "unclassified");
                MutableTreeNode compartment = compartments.computeIfAbsent(locationFlag, ignored -> MutableTreeNode
                    .virtual(structure.key + ":compartment:" + locationFlag, localizeWarehouseName(child.asset
                        .getLocationFlag()), "structure_compartment"));
                compartment.children.add(child);
            }
            compartmentGroup.children.addAll(compartments.values());
            structure.children.add(compartmentGroup);
        }
    }

    /** 将军团办公室内的资产按实际军团机库归位，办公室不是物品箱。 */
    private static void groupCorporationOfficeChildren(Iterable<MutableTreeNode> assetNodes) {
        for (MutableTreeNode office : assetNodes) {
            if (!office.isCorporationOffice() || office.children.isEmpty()) {
                continue;
            }
            List<MutableTreeNode> directChildren = new ArrayList<>(office.children);
            office.children.clear();
            office.kind = "corporation_office";
            Map<String, MutableTreeNode> hangars = new LinkedHashMap<>();
            for (MutableTreeNode child : directChildren) {
                String locationFlag = nonBlankOr(child.asset.getLocationFlag(), "unclassified");
                MutableTreeNode hangar = hangars.computeIfAbsent(locationFlag, ignored -> MutableTreeNode
                    .virtual(office.key + ":hangar:" + locationFlag, localizeWarehouseName(child.asset
                        .getLocationFlag()), "corporation_hangar"));
                hangar.children.add(child);
            }
            office.children.addAll(hangars.values());
        }
    }

    /** 将资产安全包裹中的资产按其上游保留的原军团机库标记分层。 */
    private static void groupAssetSafetyPackageChildren(Iterable<MutableTreeNode> assetNodes) {
        for (MutableTreeNode packageNode : assetNodes) {
            if (!packageNode.isAssetSafetyPackage() || packageNode.children.isEmpty()) {
                continue;
            }
            List<MutableTreeNode> directChildren = new ArrayList<>(packageNode.children);
            packageNode.children.clear();
            MutableTreeNode originGroup = MutableTreeNode
                .virtual(packageNode.key + ":original-location", "原存放位置", "asset_safety_origin");
            Map<String, MutableTreeNode> originalHangars = new LinkedHashMap<>();
            for (MutableTreeNode child : directChildren) {
                String locationFlag = nonBlankOr(child.asset.getLocationFlag(), "unclassified");
                MutableTreeNode hangar = originalHangars.computeIfAbsent(locationFlag, ignored -> MutableTreeNode
                    .virtual(packageNode.key + ":original-hangar:" + locationFlag, localizeWarehouseName(child.asset
                        .getLocationFlag()), "corporation_hangar"));
                hangar.children.add(child);
            }
            originGroup.children.addAll(originalHangars.values());
            packageNode.children.add(originGroup);
        }
    }

    /**
     * 将舰船直属资产按实际舰船槽位或舱位分组，避免将装配模块与货仓物品混在同一层。
     *
     * <p>国服资产快照通过子资产的 {@code location_flag} 区分高、中、低、改装件槽以及货仓等位置，
     * 该字段是展示舰船装配结构的唯一可靠依据。</p>
     */
    private static void groupShipChildren(Iterable<MutableTreeNode> assetNodes) {
        for (MutableTreeNode ship : assetNodes) {
            if (!ship.isShip()) {
                continue;
            }
            ship.kind = "ship";
            if (ship.children.isEmpty()) {
                continue;
            }
            List<MutableTreeNode> directChildren = new ArrayList<>(ship.children);
            ship.children.clear();
            MutableTreeNode compartmentGroup = MutableTreeNode
                .virtual(ship.key + ":compartments", "舰船分区", "ship_compartment_group");
            Map<String, MutableTreeNode> compartments = new LinkedHashMap<>();
            for (MutableTreeNode child : directChildren) {
                String locationFlag = nonBlankOr(child.asset.getLocationFlag(), "unclassified");
                MutableTreeNode compartment = compartments.computeIfAbsent(locationFlag, ignored -> MutableTreeNode
                    .virtual(ship.key + ":compartment:" + locationFlag, localizeWarehouseName(child.asset
                        .getLocationFlag()), "ship_compartment"));
                compartment.children.add(child);
            }
            compartmentGroup.children.addAll(compartments.values());
            ship.children.add(compartmentGroup);
        }
    }

    /** 判断给定父项链是否形成循环，避免异常上游数据导致树接口无限递归。 */
    private static boolean hasParentCycle(Long itemId, Map<Long, Long> parentByItemId) {
        Set<Long> visited = new HashSet<>();
        Long current = itemId;
        while (current != null && parentByItemId.containsKey(current)) {
            if (!visited.add(current)) {
                return true;
            }
            current = parentByItemId.get(current);
        }
        return false;
    }

    /** 追溯根资产的物理位置；父项缺失、循环或未知位置均归入可见的未解析分组。 */
    private static LocationPath resolveLocationPath(EveCorporationAssetDO asset,
                                                    Map<Long, EveCorporationAssetDO> assetsByItemId,
                                                    Set<Long> cyclicItems) {
        EveCorporationAssetDO current = asset;
        Set<Long> visited = new HashSet<>();
        while (current != null && "item".equals(current.getLocationType())) {
            if (!visited.add(current.getItemId()) || cyclicItems.contains(current.getItemId())) {
                return LocationPath.UNRESOLVED;
            }
            current = assetsByItemId.get(current.getLocationId());
        }
        if (current == null) {
            return LocationPath.UNRESOLVED;
        }
        String solarSystemKey = current.getSolarSystemId() == null
            ? "solar:unresolved"
            : "solar:" + current.getSolarSystemId();
        String solarSystemName = nonBlankOr(current.getSolarSystemName(), "未解析星系");
        if ("station".equals(current.getLocationType())) {
            String stationId = current.getLocationId() == null ? "unresolved" : current.getLocationId().toString();
            String locationKind = current.getLocationId() != null && current.getLocationId() > Integer.MAX_VALUE
                ? "player_structure"
                : "npc_station";
            return new LocationPath(solarSystemKey, solarSystemName, "station:" + stationId, localizeStationName(current
                .getLocationName(), solarSystemName), locationKind);
        }
        if ("solar_system".equals(current.getLocationType())) {
            String directSolarKey = current.getLocationId() == null ? "unresolved" : current.getLocationId().toString();
            return new LocationPath(solarSystemKey, solarSystemName, "solar-location:" + directSolarKey, "太空资产", "space_assets");
        }
        return LocationPath.UNRESOLVED;
    }

    /** 返回第一个非空文本，避免向客户端暴露无意义的空树节点标题。 */
    private static String nonBlankOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** 将已验证可识别的 NPC 空间站格式本地化，玩家建筑和未知专名保持上游原文。 */
    private static String localizeStationName(String locationName, String solarSystemName) {
        String resolvedName = nonBlankOr(locationName, "未解析空间站 / 建筑");
        Matcher matcher = NPC_STATION_NAME_PATTERN.matcher(resolvedName);
        if (!matcher.matches() || solarSystemName == null || solarSystemName.isBlank()) {
            return resolvedName;
        }
        String facilityName = localizeNpcFacilityName(matcher.group(3));
        return solarSystemName + " " + matcher.group(1) + " - 卫星 " + matcher.group(2) + " - " + facilityName;
    }

    /** 仅翻译已由国服资料验证的 NPC 设施名，避免猜测玩家命名或未确认的游戏专名。 */
    private static String localizeNpcFacilityName(String facilityName) {
        if ("Caldari Steel Warehouse".equalsIgnoreCase(facilityName)) {
            return "加达里钢铁仓库";
        }
        return facilityName;
    }

    /** 将游戏固定仓位标记显示为中文；未收录的标记保留原文以防产生错误含义。 */
    private static String localizeWarehouseName(String locationFlag) {
        if (locationFlag == null || locationFlag.isBlank()) {
            return "未分类仓位";
        }
        if (locationFlag.matches("CorpSAG[1-7]")) {
            return "军团机库 " + locationFlag.substring(locationFlag.length() - 1);
        }
        return switch (locationFlag) {
            case "Hangar" -> "机库";
            case "CorpDeliveries" -> "军团交付机库";
            case "AssetSafety" -> "资产安全机库";
            case "OfficeFolder" -> "办公室";
            case "StructureFuel" -> "建筑燃料仓";
            case "QuantumCoreRoom" -> "量子核心仓";
            case "Cargo" -> "货仓";
            case "DroneBay" -> "无人机舱";
            case "FighterBay" -> "铁骑无人机舱";
            case "AutoFit" -> "自动装配仓位";
            default -> localizeIndexedStorageName(locationFlag);
        };
    }

    /** 将带序号的舰船或建筑槽位显示为面向玩家的一位起始中文编号。 */
    private static String localizeIndexedStorageName(String locationFlag) {
        Matcher matcher = INDEXED_STORAGE_NAME_PATTERN.matcher(locationFlag);
        if (!matcher.matches()) {
            return locationFlag;
        }
        int displayIndex = Integer.parseInt(matcher.group(2)) + 1;
        String prefix = switch (matcher.group(1)) {
            case "HiSlot" -> "高能量槽";
            case "MedSlot" -> "中能量槽";
            case "LoSlot" -> "低能量槽";
            case "RigSlot" -> "改装件槽";
            case "ServiceSlot" -> "服务槽";
            case "FighterTube" -> "铁骑发射管";
            default -> locationFlag;
        };
        return prefix + " " + displayIndex;
    }

    /** 汇总本次同步期间可用的名称解析结果。 */
    private record NameResolution(Map<Long, String> typeNames, Map<Long, String> publicLocationNames,
                                  Map<Long, String> itemNames, Map<Long, String> corporationStructureNames,
                                  Map<Long, LocationResolution> locations, Map<Long, String> solarSystemNames) {
    }

    /** 位置名称与所属星系的解析结果。 */
    private record LocationResolution(String name, Long solarSystemId, String solarSystemName) {
        private static final LocationResolution EMPTY = new LocationResolution(null, null, null);
    }

    /** 根资产在物理位置树中的归属。 */
    private record LocationPath(String solarSystemKey, String solarSystemName, String locationKey, String locationName,
                                String locationKind) {
        private static final LocationPath UNRESOLVED = new LocationPath("solar:unresolved", "未解析星系", "station:unresolved", "未归类资产", "unresolved");
    }

    /** 构树期间使用的可变节点，响应生成后转为不可变记录。 */
    private static final class MutableTreeNode {

        private static final int CORPORATION_OFFICE_TYPE_ID = 27;
        private static final int ASSET_SAFETY_PACKAGE_TYPE_ID = 60;
        private static final int CUSTOMS_OFFICE_TYPE_ID = 2233;

        private final String key;
        private final String title;
        private String kind;
        private final EveCorporationAssetDO asset;
        private final String marketCategoryL1;
        private final List<MutableTreeNode> children = new ArrayList<>();

        private MutableTreeNode(String key,
                                String title,
                                String kind,
                                EveCorporationAssetDO asset,
                                String marketCategoryL1) {
            this.key = key;
            this.title = title;
            this.kind = kind;
            this.asset = asset;
            this.marketCategoryL1 = marketCategoryL1;
        }

        /** 创建由游戏资产驱动的节点。 */
        private static MutableTreeNode asset(EveCorporationAssetDO asset, EveStaticTypeReferenceDO typeReference) {
            String name = nonBlankOr(asset.getItemName(), nonBlankOr(asset.getTypeName(), "类型 #" + asset.getTypeId()));
            String marketCategoryL1 = typeReference == null ? null : typeReference.getMarketCategoryL1();
            return new MutableTreeNode("item:" + asset.getItemId(), name, "asset", asset, marketCategoryL1);
        }

        /** 创建星系、空间站或仓库等虚拟分组节点。 */
        private static MutableTreeNode virtual(String key, String title, String kind) {
            return new MutableTreeNode(key, title, kind, null, null);
        }

        /** 判断带有下级资产的物品是否为舰船，而非普通物品箱。 */
        private boolean isShip() {
            if ("ship".equals(kind)) {
                return true;
            }
            if (asset == null || Boolean.TRUE.equals(asset.getCorporationStructure())) {
                return false;
            }
            if ("舰船".equals(marketCategoryL1)) {
                return true;
            }
            return children.stream().map(node -> node.asset).anyMatch(MutableTreeNode::isShipFitting);
        }

        /** 判断资产是否为固定类型的军团办公室。 */
        private boolean isCorporationOffice() {
            return asset != null && Objects.equals(asset.getTypeId(), CORPORATION_OFFICE_TYPE_ID);
        }

        /** 判断资产是否为资产安全包裹。 */
        private boolean isAssetSafetyPackage() {
            return asset != null && Objects.equals(asset.getTypeId(), ASSET_SAFETY_PACKAGE_TYPE_ID);
        }

        /** 判断资产是否为系统公共建筑，当前固定类型为海关办公室。 */
        private boolean isNpcStructure() {
            return asset != null && Objects.equals(asset.getTypeId(), CUSTOMS_OFFICE_TYPE_ID);
        }

        /** 舰船已装配的模块会位于固定舰船槽位中，可作为类型名称缺失时的兜底识别依据。 */
        private static boolean isShipFitting(EveCorporationAssetDO asset) {
            if (asset == null || asset.getLocationFlag() == null) {
                return false;
            }
            return asset.getLocationFlag().matches("(?:HiSlot|MedSlot|LoSlot|RigSlot|FighterTube)\\d+") || "DroneBay"
                .equals(asset.getLocationFlag()) || "FighterBay".equals(asset.getLocationFlag());
        }

        /** 按稳定标题排序并转换为 API 响应节点。 */
        private EveCorporationAssetTreeNodeResp toResp() {
            children.sort(Comparator.comparing(node -> node.title, Comparator.nullsLast(String::compareTo)));
            if (asset != null && Boolean.TRUE.equals(asset.getCorporationStructure())) {
                kind = "corporation_structure";
            } else if (isCorporationOffice()) {
                kind = "corporation_office";
            } else if (isAssetSafetyPackage()) {
                kind = "asset_safety_package";
            } else if (isShip()) {
                kind = "ship";
            } else if (asset != null && !children.isEmpty()) {
                kind = "container";
            }
            List<EveCorporationAssetTreeNodeResp> responseChildren = children.stream()
                .map(MutableTreeNode::toResp)
                .toList();
            return asset == null
                ? new EveCorporationAssetTreeNodeResp(key, title, kind, null, null, null, null, null, null, null, null, null, null, responseChildren)
                : new EveCorporationAssetTreeNodeResp(key, title, kind, asset.getItemId(), asset.getTypeId(), asset
                    .getTypeName(), asset.getItemName(), asset.getQuantity(), asset.getLocationFlag(), asset
                        .getSingleton(), asset.getBlueprintCopy(), asset.getLastSeenAt(), asset
                            .getSourceExpiresAt(), responseChildren);
        }
    }
}
