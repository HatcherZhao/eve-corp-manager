package top.continew.admin.eve.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.client.SerenityEsiClient;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveCorporationAssetMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.mapper.EveCorporationMemberTrackingMapper;
import top.continew.admin.eve.mapper.EveCorporationStructureMapper;
import top.continew.admin.eve.mapper.EveMoonExtractionMapper;
import top.continew.admin.eve.mapper.EveStarMapAnnotationMapper;
import top.continew.admin.eve.mapper.EveStarMapRouteMapper;
import top.continew.admin.eve.mapper.EveStarMapRoutePointMapper;
import top.continew.admin.eve.mapper.EveStaticLocationReferenceMapper;
import top.continew.admin.eve.mapper.EveUniverseStargateMapper;
import top.continew.admin.eve.mapper.EveUniverseSyncStateMapper;
import top.continew.admin.eve.mapper.EveUniverseSystemMapper;
import top.continew.admin.eve.model.EveStarMapCoverageResp;
import top.continew.admin.eve.model.EveStarMapGraphResp;
import top.continew.admin.eve.model.EveStarMapRouteResp;
import top.continew.admin.eve.model.EveStarMapSuggestionResp;
import top.continew.admin.eve.model.EveStarMapSystemResp;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveCorporationAssetDO;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.entity.EveCorporationStructureDO;
import top.continew.admin.eve.model.entity.EveCorporationMemberTrackingDO;
import top.continew.admin.eve.model.entity.EveMoonExtractionDO;
import top.continew.admin.eve.model.entity.EveStarMapAnnotationDO;
import top.continew.admin.eve.model.entity.EveStarMapRouteDO;
import top.continew.admin.eve.model.entity.EveStarMapRoutePointDO;
import top.continew.admin.eve.model.entity.EveStaticLocationReferenceDO;
import top.continew.admin.eve.model.entity.EveUniverseStargateDO;
import top.continew.admin.eve.model.entity.EveUniverseSyncStateDO;
import top.continew.admin.eve.model.entity.EveUniverseSystemDO;
import top.continew.admin.eve.model.serenity.SerenityEsiResponse;
import top.continew.admin.eve.model.serenity.SerenityUniverseStargateResponse;
import top.continew.admin.eve.model.serenity.SerenityUniverseSystemResponse;
import top.continew.starter.core.exception.BusinessException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 提供国服公开星图、军团运营标注和经验证航线。
 *
 * <p>公开宇宙数据不属于任何租户；标注和路线始终按当前租户隔离。</p>
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveStarMapService {

    /** 单次后台同步处理有限星系，星门请求仍由统一节流器串行控制。 */
    private static final int SYSTEM_BATCH_SIZE = 4;
    /** 国服当前星系目录小于该上限，完整同步后可一次下发完整二维底图。 */
    private static final int FULL_GRAPH_SYSTEM_LIMIT = 10_000;
    private static final String SYNC_STATE_KEY = "SYSTEMS";

    private final SerenityEsiClient esiClient;
    private final EveUniverseSystemMapper universeSystemMapper;
    private final EveUniverseStargateMapper universeStargateMapper;
    private final EveUniverseSyncStateMapper universeSyncStateMapper;
    private final EveStaticLocationReferenceMapper locationReferenceMapper;
    private final EveStarMapAnnotationMapper annotationMapper;
    private final EveStarMapRouteMapper routeMapper;
    private final EveStarMapRoutePointMapper routePointMapper;
    private final EveCharacterMapper characterMapper;
    private final EveCorporationMapper corporationMapper;
    private final EveCorporationStructureMapper structureMapper;
    private final EveCorporationAssetMapper assetMapper;
    private final EveMoonExtractionMapper moonExtractionMapper;
    private final EveCorporationMemberTrackingMapper memberTrackingMapper;

    /** 将公开星系列表和一小批星系详情同步到本地，供定时任务调用。 */
    @Transactional(rollbackFor = Exception.class)
    public void synchronizeNextBatch() {
        ensureSystemIndex();
        Set<Long> pendingSystemIds = new LinkedHashSet<>(universeSystemMapper.selectPendingStructureSystemIds(SYSTEM_BATCH_SIZE));
        for (Long systemId : universeSystemMapper.selectPendingSystemIds(SYSTEM_BATCH_SIZE * 2)) {
            if (pendingSystemIds.size() >= SYSTEM_BATCH_SIZE) {
                break;
            }
            pendingSystemIds.add(systemId);
        }
        for (Long systemId : pendingSystemIds) {
            synchronizeSystem(systemId);
        }
        saveSyncSuccess();
    }

    /** 返回星图同步覆盖率；未完成同步时前端必须明确提示，而不能把空白误解为无星门。 */
    public EveStarMapCoverageResp coverage() {
        EveUniverseSyncStateDO state = universeSyncStateMapper.selectById(SYNC_STATE_KEY);
        return new EveStarMapCoverageResp(universeSystemMapper.countIndexedSystems(), universeSystemMapper
            .countSynchronizedSystems(), universeStargateMapper.selectCount(null), state == null ? null : state
                .getLastSuccessAt(), state == null ? null : state.getLastFailureAt(), state == null ? null : state
                    .getFailureCode());
    }

    /** 返回一个受节点上限保护的二维底图；指定星域时按星域筛选。 */
    public EveStarMapGraphResp graph(Long regionId) {
        UserContext context = UserContextHolder.getContext();
        Long tenantId = context.getTenantId();
        LambdaQueryWrapper<EveUniverseSystemDO> query = new LambdaQueryWrapper<EveUniverseSystemDO>()
            .isNotNull(EveUniverseSystemDO::getSynchronizedAt)
            .orderByAsc(EveUniverseSystemDO::getRegionId, EveUniverseSystemDO::getSystemId)
            .last("LIMIT " + FULL_GRAPH_SYSTEM_LIMIT);
        if (regionId != null && regionId > 0) {
            query.eq(EveUniverseSystemDO::getRegionId, regionId);
        }
        List<EveUniverseSystemDO> systems = universeSystemMapper.selectList(query);
        Map<Long, EveStaticLocationReferenceDO> names = locationsBySystemIds(systems.stream().map(EveUniverseSystemDO::getSystemId)
            .toList());
        Set<Long> systemIds = new LinkedHashSet<>(names.keySet());
        Map<Long, Boolean> structureSystems = structureSystems(context, systemIds);
        Map<Long, Boolean> assetSystems = assetSystems(context, systemIds);
        Map<Long, Boolean> moonExtractionSystems = moonExtractionSystems(context, systemIds);
        Map<Long, Integer> trackedMembers = trackedMembersBySystem(context, systemIds);
        List<EveStarMapGraphResp.Node> nodes = systems.stream().map(item -> new EveStarMapGraphResp.Node(item
            .getSystemId(), nameOf(names, item.getSystemId()), item.getRegionId(), item.getConstellationId(), item
                .getSecurityStatus(), item.getPositionX(), item.getPositionZ(), Boolean.TRUE.equals(structureSystems
                    .get(item.getSystemId())), Boolean.TRUE.equals(moonExtractionSystems.get(item.getSystemId())), Boolean.TRUE
                        .equals(assetSystems.get(item.getSystemId())), trackedMembers.getOrDefault(item.getSystemId(), 0))).toList();
        Set<String> edgeKeys = new HashSet<>();
        List<EveStarMapGraphResp.Edge> edges = systemIds.isEmpty() ? List.of() : universeStargateMapper.selectList(new LambdaQueryWrapper<EveUniverseStargateDO>()
            .in(EveUniverseStargateDO::getSystemId, systemIds)
            .in(EveUniverseStargateDO::getDestinationSystemId, systemIds)).stream().map(item -> {
                long from = Math.min(item.getSystemId(), item.getDestinationSystemId());
                long to = Math.max(item.getSystemId(), item.getDestinationSystemId());
                return new EveStarMapGraphResp.Edge(from, to);
            }).filter(edge -> edgeKeys.add(edge.fromSystemId() + ":" + edge.toSystemId())).toList();
        return new EveStarMapGraphResp(coverage(), nodes, edges, activeAnnotations(tenantId).stream().filter(item -> systemIds
            .contains(item.getSolarSystemId())).map(EveStarMapService::toGraphAnnotation).toList());
    }

    /** 以中文或英文名称搜索静态资料中的星系，最多返回二十项。 */
    public List<EveStarMapSuggestionResp> suggestSystems(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        String value = keyword.trim();
        List<EveStaticLocationReferenceDO> systems = locationReferenceMapper.selectList(new LambdaQueryWrapper<EveStaticLocationReferenceDO>()
            .eq(EveStaticLocationReferenceDO::getReferenceType, "SOLAR_SYSTEM")
            .and(item -> item.like(EveStaticLocationReferenceDO::getReferenceName, value)
                .or().like(EveStaticLocationReferenceDO::getReferenceId, value))
            .orderByAsc(EveStaticLocationReferenceDO::getReferenceName)
            .last("LIMIT 20"));
        Map<Long, EveStaticLocationReferenceDO> regionNames = locationsByTypeAndIds("REGION", systems.stream()
            .map(EveStaticLocationReferenceDO::getRegionId).filter(Objects::nonNull).toList());
        Map<Long, EveStaticLocationReferenceDO> constellationNames = locationsByTypeAndIds("CONSTELLATION", systems
            .stream().map(EveStaticLocationReferenceDO::getConstellationId).filter(Objects::nonNull).toList());
        return systems.stream().map(item -> {
            String regionName = nameOf(regionNames, item.getRegionId());
            String constellationName = nameOf(constellationNames, item.getConstellationId());
            return new EveStarMapSuggestionResp(item.getReferenceId(), item.getReferenceName(), regionName,
                constellationName, joinBreadcrumb(regionName, constellationName, item.getReferenceName()));
        }).toList();
    }

    /** 返回一个星系的邻接关系、可见军团建筑和有效标注。 */
    public EveStarMapSystemResp system(Long systemId) {
        if (systemId == null || systemId <= 0) {
            throw new BusinessException("星系参数无效");
        }
        UserContext context = UserContextHolder.getContext();
        EveStaticLocationReferenceDO location = systemLocation(systemId);
        if (location == null) {
            throw new BusinessException("未找到该星系的静态资料");
        }
        EveUniverseSystemDO universe = universeSystemMapper.selectById(systemId);
        Map<Long, EveStaticLocationReferenceDO> regionNames = locationsByTypeAndIds("REGION", java.util.Arrays.asList(location
            .getRegionId()));
        Map<Long, EveStaticLocationReferenceDO> constellationNames = locationsByTypeAndIds("CONSTELLATION", java.util.Arrays.asList(location
            .getConstellationId()));
        List<EveUniverseStargateDO> gates = universeStargateMapper.selectList(new LambdaQueryWrapper<EveUniverseStargateDO>()
            .eq(EveUniverseStargateDO::getSystemId, systemId));
        Map<Long, EveStaticLocationReferenceDO> neighborNames = locationsBySystemIds(gates.stream().map(EveUniverseStargateDO::getDestinationSystemId)
            .toList());
        List<EveStarMapSystemResp.Neighbor> neighbors = gates.stream().map(gate -> new EveStarMapSystemResp.Neighbor(gate
            .getDestinationSystemId(), nameOf(neighborNames, gate.getDestinationSystemId()))).sorted(Comparator
                .comparing(EveStarMapSystemResp.Neighbor::name, Comparator.nullsLast(String::compareTo))).toList();
        List<EveStarMapSystemResp.Structure> structures = visibleStructures(context, systemId);
        List<EveStarMapSystemResp.Annotation> annotations = activeAnnotations(context.getTenantId()).stream().filter(item -> Objects
            .equals(systemId, item.getSolarSystemId())).map(EveStarMapService::toSystemAnnotation).toList();
        return new EveStarMapSystemResp(systemId, location.getReferenceName(), location.getRegionId(), nameOf(regionNames,
            location.getRegionId()), location.getConstellationId(), nameOf(constellationNames, location
                .getConstellationId()), universe == null ? location.getSecurityStatus() : universe.getSecurityStatus(),
            neighbors, structures, annotations, universe == null ? null : universe.getSynchronizedAt());
    }

    /** 创建当前军团的人工运营标注。 */
    @Transactional(rollbackFor = Exception.class)
    public EveStarMapGraphResp.Annotation createAnnotation(Long systemId,
                                                           String category,
                                                           String title,
                                                           String note,
                                                           String colorKey,
                                                           LocalDateTime expiresAt) {
        requireSystem(systemId);
        UserContext context = UserContextHolder.getContext();
        EveStarMapAnnotationDO annotation = new EveStarMapAnnotationDO();
        annotation.setTenantId(context.getTenantId());
        annotation.setSolarSystemId(systemId);
        annotation.setCategory(category.trim());
        annotation.setTitle(title.trim());
        annotation.setNote(blankToNull(note));
        annotation.setColorKey(blankToNull(colorKey) == null ? "blue" : colorKey.trim());
        annotation.setExpiresAt(expiresAt);
        annotation.setArchived(false);
        annotation.setCreateUser(context.getId());
        annotation.setDeleted(0L);
        annotationMapper.insert(annotation);
        return toGraphAnnotation(annotation);
    }

    /** 更新当前军团拥有的人工运营标注。 */
    @Transactional(rollbackFor = Exception.class)
    public EveStarMapGraphResp.Annotation updateAnnotation(Long annotationId,
                                                           Long systemId,
                                                           String category,
                                                           String title,
                                                           String note,
                                                           String colorKey,
                                                           LocalDateTime expiresAt) {
        requireSystem(systemId);
        EveStarMapAnnotationDO annotation = requireAnnotation(annotationId);
        annotation.setSolarSystemId(systemId);
        annotation.setCategory(category.trim());
        annotation.setTitle(title.trim());
        annotation.setNote(blankToNull(note));
        annotation.setColorKey(blankToNull(colorKey) == null ? "blue" : colorKey.trim());
        annotation.setExpiresAt(expiresAt);
        annotation.setUpdateUser(UserContextHolder.getContext().getId());
        annotationMapper.updateById(annotation);
        return toGraphAnnotation(annotation);
    }

    /** 归档当前军团拥有的人工标注，保留审计记录但不再下发到地图。 */
    @Transactional(rollbackFor = Exception.class)
    public void archiveAnnotation(Long annotationId) {
        EveStarMapAnnotationDO annotation = requireAnnotation(annotationId);
        annotation.setArchived(true);
        annotation.setUpdateUser(UserContextHolder.getContext().getId());
        annotationMapper.updateById(annotation);
    }

    /** 调用国服路线接口，并串联起点、可选经由点和终点。 */
    public EveStarMapRouteResp previewRoute(Long originSystemId, Long destinationSystemId, List<Long> viaSystemIds) {
        List<Long> requested = new ArrayList<>();
        requested.add(requireSystem(originSystemId));
        if (viaSystemIds != null) {
            viaSystemIds.stream().filter(Objects::nonNull).forEach(systemId -> requested.add(requireSystem(systemId)));
        }
        requested.add(requireSystem(destinationSystemId));
        List<Long> points = new ArrayList<>();
        for (int index = 0; index < requested.size() - 1; index++) {
            appendSegment(points, esiClient.getRoute(requested.get(index), requested.get(index + 1)));
        }
        if (points.isEmpty()) {
            points.add(originSystemId);
        }
        Map<Long, EveStaticLocationReferenceDO> names = locationsBySystemIds(points);
        return toRouteResp(null, "未保存路线", null, originSystemId, destinationSystemId, points, false, null, names,
            requested);
    }

    /** 保存一条刚刚按国服路线验证的军团共享路线。 */
    @Transactional(rollbackFor = Exception.class)
    public EveStarMapRouteResp saveRoute(String title,
                                         String description,
                                         Long originSystemId,
                                         Long destinationSystemId,
                                         List<Long> viaSystemIds) {
        EveStarMapRouteResp preview = previewRoute(originSystemId, destinationSystemId, viaSystemIds);
        UserContext context = UserContextHolder.getContext();
        EveStarMapRouteDO route = new EveStarMapRouteDO();
        route.setTenantId(context.getTenantId());
        route.setTitle(title.trim());
        route.setDescription(blankToNull(description));
        route.setOriginSystemId(originSystemId);
        route.setDestinationSystemId(destinationSystemId);
        route.setJumpCount(preview.jumpCount());
        route.setValidatedAt(LocalDateTime.now(ZoneOffset.UTC));
        route.setArchived(false);
        route.setCreateUser(context.getId());
        route.setDeleted(0L);
        routeMapper.insert(route);
        saveRoutePoints(route, preview.points(), originSystemId, destinationSystemId, viaSystemIds, context);
        return getRoute(route.getId());
    }

    /** 读取当前军团已保存的有效航线。 */
    public List<EveStarMapRouteResp> routes() {
        return routeMapper.selectList(new LambdaQueryWrapper<EveStarMapRouteDO>().eq(EveStarMapRouteDO::getArchived, false)
            .eq(EveStarMapRouteDO::getDeleted, 0L).orderByDesc(EveStarMapRouteDO::getValidatedAt)).stream().map(route -> getRoute(route
                .getId())).toList();
    }

    /** 读取当前军团拥有的一条路线及其完整节点。 */
    public EveStarMapRouteResp getRoute(Long routeId) {
        EveStarMapRouteDO route = requireRoute(routeId);
        List<EveStarMapRoutePointDO> storedPoints = routePointMapper.selectList(new LambdaQueryWrapper<EveStarMapRoutePointDO>()
            .eq(EveStarMapRoutePointDO::getRouteId, route.getId()).eq(EveStarMapRoutePointDO::getDeleted, 0L)
            .orderByAsc(EveStarMapRoutePointDO::getPointOrder));
        List<Long> pointIds = storedPoints.stream().map(EveStarMapRoutePointDO::getSolarSystemId).toList();
        Map<Long, EveStaticLocationReferenceDO> names = locationsBySystemIds(pointIds);
        List<EveStarMapRouteResp.Point> points = storedPoints.stream().map(item -> new EveStarMapRouteResp.Point(item
            .getPointOrder(), item.getSolarSystemId(), nameOf(names, item.getSolarSystemId()), item.getPointKind())).toList();
        return new EveStarMapRouteResp(route.getId(), route.getTitle(), route.getDescription(), route.getOriginSystemId(),
            route.getDestinationSystemId(), route.getJumpCount(), route.getValidatedAt(), Boolean.TRUE.equals(route
                .getArchived()), points);
    }

    /** 首次同步时建立公开星系目录，单次请求之后以 INSERT IGNORE 安全续跑。 */
    private void ensureSystemIndex() {
        if (universeSystemMapper.countIndexedSystems() > 0) {
            return;
        }
        List<Long> ids = esiClient.getUniverseSystemIds().stream().filter(item -> item != null && item > 0).distinct().toList();
        for (int offset = 0; offset < ids.size(); offset += 500) {
            universeSystemMapper.insertIgnoreSystemIds(ids.subList(offset, Math.min(ids.size(), offset + 500)));
        }
        EveUniverseSyncStateDO state = new EveUniverseSyncStateDO();
        state.setSyncKey(SYNC_STATE_KEY);
        state.setExpectedCount(ids.size());
        state.setCompletedCount(0);
        if (universeSyncStateMapper.selectById(SYNC_STATE_KEY) == null) {
            universeSyncStateMapper.insert(state);
        }
    }

    /** 同步一座星系以及该响应明确声明的所有星门。 */
    private void synchronizeSystem(Long systemId) {
        SerenityEsiResponse<SerenityUniverseSystemResponse> response = esiClient.getUniverseSystemWithMetadata(systemId);
        SerenityUniverseSystemResponse source = response.body();
        if (source.systemId() == null || !Objects.equals(source.systemId(), systemId) || source.position() == null) {
            throw new BusinessException("国服返回的星系资料不完整");
        }
        EveUniverseSystemDO target = universeSystemMapper.selectById(systemId);
        if (target == null) {
            target = new EveUniverseSystemDO();
            target.setSystemId(systemId);
        }
        target.setConstellationId(source.constellationId());
        target.setRegionId(regionIdForConstellation(source.constellationId()));
        target.setSecurityStatus(source.securityStatus());
        target.setPositionX(source.position().x());
        target.setPositionY(source.position().y());
        target.setPositionZ(source.position().z());
        target.setStargateCount(source.stargates() == null ? 0 : source.stargates().size());
        target.setSourceExpiresAt(response.expiresAt());
        target.setSynchronizedAt(LocalDateTime.now(ZoneOffset.UTC));
        if (universeSystemMapper.selectById(systemId) == null) {
            universeSystemMapper.insert(target);
        } else {
            universeSystemMapper.updateById(target);
        }
        for (Long stargateId : source.stargates() == null ? List.<Long>of() : source.stargates()) {
            if (stargateId != null && stargateId > 0) {
                synchronizeStargate(stargateId);
            }
        }
    }

    /** 同步一个星门的对端星系关系。 */
    private void synchronizeStargate(Long stargateId) {
        SerenityEsiResponse<SerenityUniverseStargateResponse> response = esiClient.getUniverseStargateWithMetadata(stargateId);
        SerenityUniverseStargateResponse source = response.body();
        if (source.systemId() == null || source.destination() == null || source.destination().systemId() == null) {
            throw new BusinessException("国服返回的星门资料不完整");
        }
        EveUniverseStargateDO target = universeStargateMapper.selectById(stargateId);
        if (target == null) {
            target = new EveUniverseStargateDO();
            target.setStargateId(stargateId);
        }
        target.setSystemId(source.systemId());
        target.setDestinationStargateId(source.destination().stargateId());
        target.setDestinationSystemId(source.destination().systemId());
        target.setTypeId(source.typeId());
        if (source.position() != null) {
            target.setPositionX(source.position().x());
            target.setPositionY(source.position().y());
            target.setPositionZ(source.position().z());
        }
        target.setSourceExpiresAt(response.expiresAt());
        target.setSynchronizedAt(LocalDateTime.now(ZoneOffset.UTC));
        if (universeStargateMapper.selectById(stargateId) == null) {
            universeStargateMapper.insert(target);
        } else {
            universeStargateMapper.updateById(target);
        }
    }

    /** 记录成功覆盖率；失败状态由调度任务单独记录，避免业务读取时吞掉上游异常。 */
    private void saveSyncSuccess() {
        EveUniverseSyncStateDO state = universeSyncStateMapper.selectById(SYNC_STATE_KEY);
        if (state == null) {
            return;
        }
        state.setCompletedCount((int) universeSystemMapper.countSynchronizedSystems());
        state.setLastSuccessAt(LocalDateTime.now(ZoneOffset.UTC));
        state.setLastFailureAt(null);
        state.setFailureCode(null);
        state.setNextRetryAt(null);
        universeSyncStateMapper.updateById(state);
    }

    /** 获取当前会话可见的军团建筑，缺少模块查看权限时不读取底层数据。 */
    private List<EveStarMapSystemResp.Structure> visibleStructures(UserContext context, Long systemId) {
        if (!hasPermission(context, "eve:structures:view")) {
            return List.of();
        }
        EveCorporationDO corporation = currentCorporation(context);
        if (corporation == null) {
            return List.of();
        }
        return structureMapper.selectList(new LambdaQueryWrapper<EveCorporationStructureDO>().eq(EveCorporationStructureDO::getCorporationRefId,
            corporation.getId()).eq(EveCorporationStructureDO::getSolarSystemId, systemId).eq(EveCorporationStructureDO::getStatus,
                "ACTIVE").eq(EveCorporationStructureDO::getDeleted, 0L)).stream().map(item -> new EveStarMapSystemResp.Structure(item
                    .getStructureId(), item.getStructureName(), item.getTypeName(), item.getState(), item.getFuelExpiresAt())).toList();
    }

    /** 聚合图上有军团自有建筑的星系。 */
    private Map<Long, Boolean> structureSystems(UserContext context, Set<Long> systemIds) {
        if (!hasPermission(context, "eve:structures:view") || systemIds.isEmpty()) {
            return Map.of();
        }
        EveCorporationDO corporation = currentCorporation(context);
        if (corporation == null) {
            return Map.of();
        }
        return structureMapper.selectList(new LambdaQueryWrapper<EveCorporationStructureDO>().eq(EveCorporationStructureDO::getCorporationRefId,
            corporation.getId()).in(EveCorporationStructureDO::getSolarSystemId, systemIds).eq(EveCorporationStructureDO::getStatus,
                "ACTIVE").eq(EveCorporationStructureDO::getDeleted, 0L)).stream().collect(Collectors.toMap(EveCorporationStructureDO::getSolarSystemId,
                    item -> true, (left, right) -> true));
    }

    /** 聚合图上有当前军团资产的星系。 */
    private Map<Long, Boolean> assetSystems(UserContext context, Set<Long> systemIds) {
        if (!hasPermission(context, "eve:assets:view") || systemIds.isEmpty()) {
            return Map.of();
        }
        EveCorporationDO corporation = currentCorporation(context);
        if (corporation == null) {
            return Map.of();
        }
        return assetMapper.selectList(new LambdaQueryWrapper<EveCorporationAssetDO>().eq(EveCorporationAssetDO::getCorporationRefId,
            corporation.getId()).in(EveCorporationAssetDO::getSolarSystemId, systemIds).eq(EveCorporationAssetDO::getStatus,
                "ACTIVE").eq(EveCorporationAssetDO::getDeleted, 0L)).stream().collect(Collectors.toMap(EveCorporationAssetDO::getSolarSystemId,
                    item -> true, (left, right) -> true));
    }

    /** 聚合图上有有效月矿提取计划的星系。 */
    private Map<Long, Boolean> moonExtractionSystems(UserContext context, Set<Long> systemIds) {
        if (!hasPermission(context, "eve:extractions:view") || systemIds.isEmpty()) {
            return Map.of();
        }
        EveCorporationDO corporation = currentCorporation(context);
        if (corporation == null) {
            return Map.of();
        }
        return moonExtractionMapper.selectList(new LambdaQueryWrapper<EveMoonExtractionDO>().eq(EveMoonExtractionDO::getCorporationRefId,
            corporation.getId()).in(EveMoonExtractionDO::getSolarSystemId, systemIds).eq(EveMoonExtractionDO::getStatus,
                "ACTIVE").eq(EveMoonExtractionDO::getDeleted, 0L)).stream().collect(Collectors.toMap(EveMoonExtractionDO::getSolarSystemId,
                    item -> true, (left, right) -> true));
    }

    /** 聚合当前军团成员可公开给当前本站角色的追踪位置，不读取或下发个人原始追踪字段。 */
    private Map<Long, Integer> trackedMembersBySystem(UserContext context, Set<Long> systemIds) {
        if (!hasPermission(context, "eve:members:track:view") || systemIds.isEmpty()) {
            return Map.of();
        }
        EveCorporationDO corporation = currentCorporation(context);
        if (corporation == null) {
            return Map.of();
        }
        return memberTrackingMapper.selectActiveByCorporation(context.getTenantId(), corporation.getId()).stream()
            .map(EveCorporationMemberTrackingDO::getLocationId).filter(systemIds::contains)
            .collect(Collectors.toMap(item -> item, item -> 1, Integer::sum));
    }

    /** 保存每个官方计算节点，并标记原始起点、经由点和终点以支持路线详情展示。 */
    private void saveRoutePoints(EveStarMapRouteDO route,
                                 List<EveStarMapRouteResp.Point> points,
                                 Long originSystemId,
                                 Long destinationSystemId,
                                 List<Long> viaSystemIds,
                                 UserContext context) {
        Set<Long> vias = viaSystemIds == null ? Set.of() : new LinkedHashSet<>(viaSystemIds);
        for (EveStarMapRouteResp.Point point : points) {
            EveStarMapRoutePointDO target = new EveStarMapRoutePointDO();
            target.setTenantId(context.getTenantId());
            target.setRouteId(route.getId());
            target.setPointOrder(point.order());
            target.setSolarSystemId(point.systemId());
            target.setPointKind(Objects.equals(point.systemId(), originSystemId) ? "ORIGIN" : Objects.equals(point.systemId(),
                destinationSystemId) ? "DESTINATION" : vias.contains(point.systemId()) ? "VIA" : "COMPUTED");
            target.setCreateUser(context.getId());
            target.setDeleted(0L);
            routePointMapper.insert(target);
        }
    }

    /** 拼接两段国服路线，去除段边界的连续重复节点。 */
    private static void appendSegment(List<Long> target, List<Long> segment) {
        if (segment == null || segment.isEmpty()) {
            throw new BusinessException("国服未返回可用路线");
        }
        for (Long systemId : segment) {
            if (systemId != null && (target.isEmpty() || !Objects.equals(target.get(target.size() - 1), systemId))) {
                target.add(systemId);
            }
        }
    }

    /** 按一组星系 ID 批量读取中文位置名称。 */
    private Map<Long, EveStaticLocationReferenceDO> locationsBySystemIds(Collection<Long> systemIds) {
        if (systemIds == null || systemIds.isEmpty()) {
            return Map.of();
        }
        return locationReferenceMapper.selectList(new LambdaQueryWrapper<EveStaticLocationReferenceDO>().eq(EveStaticLocationReferenceDO::getReferenceType,
            "SOLAR_SYSTEM").in(EveStaticLocationReferenceDO::getReferenceId, systemIds)).stream().collect(Collectors.toMap(EveStaticLocationReferenceDO::getReferenceId,
                item -> item, (left, right) -> left));
    }

    /** 按位置类型和 ID 批量读取中文名称。 */
    private Map<Long, EveStaticLocationReferenceDO> locationsByTypeAndIds(String type, Collection<Long> ids) {
        List<Long> validIds = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
        if (validIds.isEmpty()) {
            return Map.of();
        }
        return locationReferenceMapper.selectList(new LambdaQueryWrapper<EveStaticLocationReferenceDO>().eq(EveStaticLocationReferenceDO::getReferenceType,
            type).in(EveStaticLocationReferenceDO::getReferenceId, validIds)).stream().collect(Collectors.toMap(EveStaticLocationReferenceDO::getReferenceId,
                item -> item, (left, right) -> left));
    }

    /** 根据静态资料查找星系，不存在时拒绝创建标注或路线。 */
    private Long requireSystem(Long systemId) {
        if (systemId == null || systemId <= 0 || systemLocation(systemId) == null) {
            throw new BusinessException("未找到指定星系");
        }
        return systemId;
    }

    /** 查询单个星系静态资料。 */
    private EveStaticLocationReferenceDO systemLocation(Long systemId) {
        return locationReferenceMapper.selectOne(new LambdaQueryWrapper<EveStaticLocationReferenceDO>().eq(EveStaticLocationReferenceDO::getReferenceType,
            "SOLAR_SYSTEM").eq(EveStaticLocationReferenceDO::getReferenceId, systemId));
    }

    /** 由静态资料关联星座所属星域，公开 ESI 星系详情本身不提供此字段。 */
    private Long regionIdForConstellation(Long constellationId) {
        EveStaticLocationReferenceDO constellation = locationsByTypeAndIds("CONSTELLATION", java.util.Arrays.asList(constellationId))
            .get(constellationId);
        return constellation == null ? null : constellation.getRegionId();
    }

    /** 只返回未归档且未到期的当前租户标注。 */
    private List<EveStarMapAnnotationDO> activeAnnotations(Long tenantId) {
        return annotationMapper.selectList(new LambdaQueryWrapper<EveStarMapAnnotationDO>().eq(EveStarMapAnnotationDO::getTenantId,
            tenantId).eq(EveStarMapAnnotationDO::getArchived, false).eq(EveStarMapAnnotationDO::getDeleted, 0L)
            .and(item -> item.isNull(EveStarMapAnnotationDO::getExpiresAt).or().gt(EveStarMapAnnotationDO::getExpiresAt,
                LocalDateTime.now(ZoneOffset.UTC))));
    }

    /** 当前租户边界内读取一个标注。 */
    private EveStarMapAnnotationDO requireAnnotation(Long annotationId) {
        EveStarMapAnnotationDO annotation = annotationMapper.selectById(annotationId);
        if (annotation == null || !Objects.equals(annotation.getTenantId(), UserContextHolder.getContext().getTenantId())) {
            throw new BusinessException("未找到当前军团的运营标注");
        }
        return annotation;
    }

    /** 当前租户边界内读取一条路线。 */
    private EveStarMapRouteDO requireRoute(Long routeId) {
        EveStarMapRouteDO route = routeMapper.selectById(routeId);
        if (route == null || !Objects.equals(route.getTenantId(), UserContextHolder.getContext().getTenantId())) {
            throw new BusinessException("未找到当前军团的航线");
        }
        return route;
    }

    /** 根据当前角色军团定位本站租户绑定。 */
    private EveCorporationDO currentCorporation(UserContext context) {
        EveCharacterDO character = characterMapper.selectActiveByUser(context.getTenantId(), context.getId()).stream().findFirst()
            .orElse(null);
        return character == null ? null : corporationMapper.selectByTenantAndCorporationId(context.getTenantId(), character
            .getCorporationId());
    }

    /** 判断当前会话是否具备站内模块数据查看权限。 */
    private static boolean hasPermission(UserContext context, String permission) {
        return context.getPermissions() != null && (context.getPermissions().contains(permission) || context.getPermissions()
            .contains("*:*:*"));
    }

    /** 将路线临时或持久化结果整理为统一响应。 */
    private static EveStarMapRouteResp toRouteResp(Long id,
                                                   String title,
                                                   String description,
                                                   Long originSystemId,
                                                   Long destinationSystemId,
                                                   List<Long> systemIds,
                                                   boolean archived,
                                                   LocalDateTime validatedAt,
                                                   Map<Long, EveStaticLocationReferenceDO> names,
                                                   List<Long> requested) {
        List<EveStarMapRouteResp.Point> points = new ArrayList<>();
        for (int index = 0; index < systemIds.size(); index++) {
            Long systemId = systemIds.get(index);
            String kind = Objects.equals(systemId, originSystemId) ? "ORIGIN" : Objects.equals(systemId, destinationSystemId)
                ? "DESTINATION" : requested.contains(systemId) ? "VIA" : "COMPUTED";
            points.add(new EveStarMapRouteResp.Point(index + 1, systemId, nameOf(names, systemId), kind));
        }
        return new EveStarMapRouteResp(id, title, description, originSystemId, destinationSystemId, Math.max(0, points
            .size() - 1), validatedAt, archived, points);
    }

    /** 转换地图标注。 */
    private static EveStarMapGraphResp.Annotation toGraphAnnotation(EveStarMapAnnotationDO item) {
        return new EveStarMapGraphResp.Annotation(item.getId(), item.getSolarSystemId(), item.getCategory(), item.getTitle(),
            item.getNote(), item.getColorKey(), item.getExpiresAt());
    }

    /** 转换详情标注。 */
    private static EveStarMapSystemResp.Annotation toSystemAnnotation(EveStarMapAnnotationDO item) {
        return new EveStarMapSystemResp.Annotation(item.getId(), item.getCategory(), item.getTitle(), item.getNote(), item
            .getColorKey(), item.getExpiresAt());
    }

    /** 从位置映射读取名称；资料未同步时返回占位文本。 */
    private static String nameOf(Map<Long, EveStaticLocationReferenceDO> locations, Long id) {
        EveStaticLocationReferenceDO location = locations.get(id);
        return location == null ? "未收录名称" : location.getReferenceName();
    }

    /** 连接非空层级名称。 */
    private static String joinBreadcrumb(String... values) {
        return java.util.Arrays.stream(values).filter(item -> item != null && !item.isBlank()).collect(Collectors.joining(" / "));
    }

    /** 统一清理可选文本字段。 */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
