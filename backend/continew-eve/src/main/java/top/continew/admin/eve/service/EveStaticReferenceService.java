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
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.eve.mapper.EveStaticLocationReferenceMapper;
import top.continew.admin.eve.mapper.EveStaticReferenceImportMapper;
import top.continew.admin.eve.mapper.EveStaticTypeReferenceMapper;
import top.continew.admin.eve.model.EveStaticReferenceImportResp;
import top.continew.admin.eve.model.EveStaticLocationReferenceResp;
import top.continew.admin.eve.model.EveStaticLocationTreeNodeResp;
import top.continew.admin.eve.model.EveStaticTypeCategoryNodeResp;
import top.continew.admin.eve.model.EveStaticTypeReferenceResp;
import top.continew.admin.eve.model.entity.EveStaticLocationReferenceDO;
import top.continew.admin.eve.model.entity.EveStaticReferenceImportDO;
import top.continew.admin.eve.model.entity.EveStaticTypeReferenceDO;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.resp.PageResp;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 将 evedata.xlsx 解析并持久化为可查询、可更新的 EVE 静态资料。
 *
 * <p>资料导入先在内存完整校验，再在同一事务中替换数据库快照。资产树按市场分类识别舰船，
 * 不再根据中文名称后缀或当前测试账号的资产种类进行推断。</p>
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EveStaticReferenceService {

    private static final String REFERENCE_NAME = "EVEDATA";
    private static final int BATCH_SIZE = 500;
    private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newFactory();

    private final EveStaticTypeReferenceMapper typeReferenceMapper;
    private final EveStaticLocationReferenceMapper locationReferenceMapper;
    private final EveStaticReferenceImportMapper importMapper;
    private final JdbcTemplate jdbcTemplate;

    /** 分类树只会在资料导入后发生变化，缓存避免每次打开资料页都读取全部类型。 */
    private volatile List<EveStaticTypeCategoryNodeResp> typeCategoryTreeCache;
    /** 星域导航树只会在资料导入后发生变化，缓存避免逐页重复聚合位置资料。 */
    private volatile List<EveStaticLocationTreeNodeResp> locationTreeCache;

    @Value("${eve.reference.workbook-path:docs/evedata.xlsx}")
    private String workbookPath;

    /** 应用就绪后仅在数据库尚无资料时导入配置文件，避免覆盖管理员后来上传的更新版本。 */
    @EventListener(ApplicationReadyEvent.class)
    public void importConfiguredWorkbook() {
        Path source = resolveConfiguredWorkbook();
        if (typeReferenceMapper.selectCount(null) > 0) {
            return;
        }
        if (!Files.isRegularFile(source)) {
            log.warn("未找到 EVE 静态资料文件，保留现有数据库资料，path={}", source.toAbsolutePath());
            return;
        }
        try {
            importWorkbook(source, source.getFileName().toString(), false);
        } catch (RuntimeException e) {
            log.error("EVE 静态资料自动导入失败，保留原有资料", e);
        }
    }

    /**
     * 解析随项目交付的默认资料文件。
     *
     * <p>本地启动脚本从 {@code backend/} 作为工作目录运行，而配置默认值相对项目根目录，
     * 因此在当前目录不存在时回退到上一级项目根目录。</p>
     *
     * @return 可用于读取或记录日志的规范化路径
     */
    private Path resolveConfiguredWorkbook() {
        Path configured = Path.of(workbookPath);
        if (configured.isAbsolute()) {
            return configured.normalize();
        }
        Path currentDirectory = Path.of("").toAbsolutePath();
        Path currentCandidate = currentDirectory.resolve(configured).normalize();
        if (Files.isRegularFile(currentCandidate)) {
            return currentCandidate;
        }
        Path projectRoot = currentDirectory.getParent();
        return projectRoot == null ? currentCandidate : projectRoot.resolve(configured).normalize();
    }

    /** 管理员上传新资料后强制替换数据库中的整套静态参考资料。 */
    @Transactional(rollbackFor = Exception.class)
    public EveStaticReferenceImportResp importWorkbook(Path source, String sourceFileName, boolean force) {
        ParsedWorkbook workbook = parseWorkbook(source, sourceFileName);
        EveStaticReferenceImportDO previous = importMapper.selectById(REFERENCE_NAME);
        if (!force && previous != null && workbook.sha256().equals(previous.getSourceSha256())) {
            return new EveStaticReferenceImportResp(workbook.sourceFileName(), workbook.sourceUpdatedAt(), workbook
                .types()
                .size(), workbook.locations().size(), false);
        }
        // 资料快照在完整解析后才替换；JdbcTemplate 避开通用 Mapper 的全表删除保护，仍受本事务回滚保护。
        jdbcTemplate.update("DELETE FROM eve_static_type_reference");
        jdbcTemplate.update("DELETE FROM eve_static_location_reference");
        batchInsertTypes(workbook.types());
        batchInsertLocations(workbook.locations());
        EveStaticReferenceImportDO imported = new EveStaticReferenceImportDO();
        imported.setReferenceName(REFERENCE_NAME);
        imported.setSourceFileName(workbook.sourceFileName());
        imported.setSourceSha256(workbook.sha256());
        imported.setSourceUpdatedAt(workbook.sourceUpdatedAt());
        imported.setImportedAt(LocalDateTime.now());
        imported.setRecordCount(workbook.types().size() + workbook.locations().size());
        if (previous == null) {
            importMapper.insert(imported);
        } else {
            importMapper.updateById(imported);
        }
        typeCategoryTreeCache = null;
        locationTreeCache = null;
        return new EveStaticReferenceImportResp(workbook.sourceFileName(), workbook.sourceUpdatedAt(), workbook.types()
            .size(), workbook.locations().size(), true);
    }

    /** 批量查询类型资料，调用方据此识别舰船、建筑等游戏对象。 */
    public Map<Integer, EveStaticTypeReferenceDO> findTypes(Collection<Integer> typeIds) {
        if (typeIds == null || typeIds.isEmpty()) {
            return Map.of();
        }
        List<EveStaticTypeReferenceDO> references = typeReferenceMapper
            .selectList(new LambdaQueryWrapper<EveStaticTypeReferenceDO>()
                .in(EveStaticTypeReferenceDO::getTypeId, typeIds));
        Map<Integer, EveStaticTypeReferenceDO> result = new HashMap<>();
        references.forEach(item -> result.put(item.getTypeId(), item));
        return result;
    }

    /** 查询位置资料，类型和 ID 共同构成唯一键。 */
    public EveStaticLocationReferenceDO findLocation(String referenceType, Long referenceId) {
        if (referenceType == null || referenceId == null) {
            return null;
        }
        return locationReferenceMapper.selectOne(new LambdaQueryWrapper<EveStaticLocationReferenceDO>()
            .eq(EveStaticLocationReferenceDO::getReferenceType, referenceType)
            .eq(EveStaticLocationReferenceDO::getReferenceId, referenceId));
    }

    /** 按资料类型批量查询位置，避免资产同步逐个查询静态位置表。 */
    public Map<Long, EveStaticLocationReferenceDO> findLocations(String referenceType, Collection<Long> referenceIds) {
        if (referenceType == null || referenceIds == null || referenceIds.isEmpty()) {
            return Map.of();
        }
        List<EveStaticLocationReferenceDO> references = locationReferenceMapper
            .selectList(new LambdaQueryWrapper<EveStaticLocationReferenceDO>()
                .eq(EveStaticLocationReferenceDO::getReferenceType, referenceType)
                .in(EveStaticLocationReferenceDO::getReferenceId, referenceIds));
        Map<Long, EveStaticLocationReferenceDO> result = new HashMap<>();
        references.forEach(item -> result.put(item.getReferenceId(), item));
        return result;
    }

    /** 分页查询静态物品类型资料，支持按市场分类树节点过滤。 */
    public PageResp<EveStaticTypeReferenceResp> pageTypes(int page,
                                                          int size,
                                                          String keyword,
                                                          List<String> marketCategoryPath,
                                                          boolean unclassified) {
        Page<EveStaticTypeReferenceDO> result = typeReferenceMapper
            .selectPage(new Page<>(page, size), buildTypeQuery(keyword, marketCategoryPath, unclassified));
        return new PageResp<>(result.getRecords().stream().map(EveStaticReferenceService::toTypeResp).toList(), result
            .getTotal());
    }

    /** 导出当前关键词与分类树节点筛选后的完整静态物品类型资料。 */
    public List<EveStaticTypeReferenceResp> listTypesForExport(String keyword,
                                                               List<String> marketCategoryPath,
                                                               boolean unclassified) {
        return typeReferenceMapper.selectList(buildTypeQuery(keyword, marketCategoryPath, unclassified))
            .stream()
            .map(EveStaticReferenceService::toTypeResp)
            .toList();
    }

    /**
     * 返回游戏市场使用的完整物品分类树。
     *
     * <p>分类节点只携带分类名称、路径和数量；实际物品继续按右侧分页列表加载，避免将两万余个物品
     * 作为树叶节点下发至浏览器。</p>
     *
     * @return 六级市场分类树及资料源未分类节点
     */
    public List<EveStaticTypeCategoryNodeResp> listTypeCategories() {
        List<EveStaticTypeCategoryNodeResp> cached = typeCategoryTreeCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (typeCategoryTreeCache == null) {
                typeCategoryTreeCache = buildTypeCategoryTree();
            }
            return typeCategoryTreeCache;
        }
    }

    /** 分页查询星域、星座、星系与建筑位置资料。 */
    public PageResp<EveStaticLocationReferenceResp> pageLocations(int page,
                                                                  int size,
                                                                  String keyword,
                                                                  String referenceType,
                                                                  String hierarchyType,
                                                                  Long hierarchyId) {
        Page<EveStaticLocationReferenceDO> result = locationReferenceMapper
            .selectPage(new Page<>(page, size), buildLocationQuery(keyword, referenceType, hierarchyType, hierarchyId));
        return new PageResp<>(result.getRecords()
            .stream()
            .map(EveStaticReferenceService::toLocationResp)
            .toList(), result.getTotal());
    }

    /** 导出筛选后的完整静态位置资料。 */
    public List<EveStaticLocationReferenceResp> listLocationsForExport(String keyword,
                                                                       String referenceType,
                                                                       String hierarchyType,
                                                                       Long hierarchyId) {
        return locationReferenceMapper
            .selectList(buildLocationQuery(keyword, referenceType, hierarchyType, hierarchyId))
            .stream()
            .map(EveStaticReferenceService::toLocationResp)
            .toList();
    }

    /** 返回按星域、星座、星系组织的位置导航树，空间站和公开建筑在右侧列表按需展示。 */
    public List<EveStaticLocationTreeNodeResp> listLocationTree() {
        List<EveStaticLocationTreeNodeResp> cached = locationTreeCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (locationTreeCache == null) {
                locationTreeCache = buildLocationTree();
            }
            return locationTreeCache;
        }
    }

    /** 生成物品类型查询条件，关键字覆盖 ID、名称、说明与六级市场分类。 */
    private static LambdaQueryWrapper<EveStaticTypeReferenceDO> buildTypeQuery(String keyword,
                                                                               List<String> marketCategoryPath,
                                                                               boolean unclassified) {
        LambdaQueryWrapper<EveStaticTypeReferenceDO> query = new LambdaQueryWrapper<EveStaticTypeReferenceDO>()
            .orderByAsc(EveStaticTypeReferenceDO::getMarketCategoryL1, EveStaticTypeReferenceDO::getMarketCategoryL2, EveStaticTypeReferenceDO::getTypeName, EveStaticTypeReferenceDO::getTypeId);
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            query.and(item -> item.like(EveStaticTypeReferenceDO::getTypeId, value)
                .or()
                .like(EveStaticTypeReferenceDO::getTypeName, value)
                .or()
                .like(EveStaticTypeReferenceDO::getTypeDescription, value)
                .or()
                .like(EveStaticTypeReferenceDO::getMarketCategoryL1, value)
                .or()
                .like(EveStaticTypeReferenceDO::getMarketCategoryL2, value)
                .or()
                .like(EveStaticTypeReferenceDO::getMarketCategoryL3, value)
                .or()
                .like(EveStaticTypeReferenceDO::getMarketCategoryL4, value)
                .or()
                .like(EveStaticTypeReferenceDO::getMarketCategoryL5, value)
                .or()
                .like(EveStaticTypeReferenceDO::getMarketCategoryL6, value));
        }
        if (unclassified) {
            query.eq(EveStaticTypeReferenceDO::getMarketCategoryL1, "");
            return query;
        }
        List<String> path = marketCategoryPath == null ? List.of() : marketCategoryPath;
        if (path.size() > 0 && !path.get(0).isBlank()) {
            query.eq(EveStaticTypeReferenceDO::getMarketCategoryL1, path.get(0).trim());
        }
        if (path.size() > 1 && !path.get(1).isBlank()) {
            query.eq(EveStaticTypeReferenceDO::getMarketCategoryL2, path.get(1).trim());
        }
        if (path.size() > 2 && !path.get(2).isBlank()) {
            query.eq(EveStaticTypeReferenceDO::getMarketCategoryL3, path.get(2).trim());
        }
        if (path.size() > 3 && !path.get(3).isBlank()) {
            query.eq(EveStaticTypeReferenceDO::getMarketCategoryL4, path.get(3).trim());
        }
        if (path.size() > 4 && !path.get(4).isBlank()) {
            query.eq(EveStaticTypeReferenceDO::getMarketCategoryL5, path.get(4).trim());
        }
        if (path.size() > 5 && !path.get(5).isBlank()) {
            query.eq(EveStaticTypeReferenceDO::getMarketCategoryL6, path.get(5).trim());
        }
        return query;
    }

    /** 读取全部分类列并构造轻量分类树，物品本身仍由分页接口按需返回。 */
    private List<EveStaticTypeCategoryNodeResp> buildTypeCategoryTree() {
        QueryWrapper<EveStaticTypeReferenceDO> query = new QueryWrapper<EveStaticTypeReferenceDO>()
            .select("market_category_l1", "market_category_l2", "market_category_l3", "market_category_l4", "market_category_l5", "market_category_l6")
            .orderByAsc("market_category_l1", "market_category_l2", "market_category_l3", "market_category_l4", "market_category_l5", "market_category_l6");
        List<EveStaticTypeReferenceDO> references = typeReferenceMapper.selectList(query);
        Map<String, MutableTypeCategoryNode> roots = new LinkedHashMap<>();
        MutableTypeCategoryNode unclassified = new MutableTypeCategoryNode("未分类", List.of(), true);
        for (EveStaticTypeReferenceDO reference : references) {
            List<String> path = marketCategoryPath(reference);
            if (path.isEmpty()) {
                unclassified.directTypeCount++;
                unclassified.typeCount++;
                continue;
            }
            Map<String, MutableTypeCategoryNode> siblings = roots;
            List<String> traversedPath = new ArrayList<>();
            MutableTypeCategoryNode current = null;
            for (String segment : path) {
                traversedPath.add(segment);
                current = siblings.computeIfAbsent(segment, name -> new MutableTypeCategoryNode(name, List
                    .copyOf(traversedPath), false));
                current.typeCount++;
                siblings = current.children;
            }
            current.directTypeCount++;
        }
        List<EveStaticTypeCategoryNodeResp> result = new ArrayList<>();
        roots.values().forEach(node -> result.add(node.toResponse()));
        if (unclassified.typeCount > 0) {
            result.add(unclassified.toResponse());
        }
        return List.copyOf(result);
    }

    /** 将类型资料的连续非空分类列转换为树节点路径。 */
    private static List<String> marketCategoryPath(EveStaticTypeReferenceDO reference) {
        List<String> path = new ArrayList<>(6);
        String[] categories = {reference.getMarketCategoryL1(), reference.getMarketCategoryL2(), reference
            .getMarketCategoryL3(), reference.getMarketCategoryL4(), reference.getMarketCategoryL5(), reference
                .getMarketCategoryL6()};
        for (String category : categories) {
            if (category == null || category.isBlank()) {
                break;
            }
            path.add(category);
        }
        return path;
    }

    /** 构建星域、星座、星系三级导航树，并将空间站与公开建筑计入所属星系数量。 */
    private List<EveStaticLocationTreeNodeResp> buildLocationTree() {
        QueryWrapper<EveStaticLocationReferenceDO> query = new QueryWrapper<EveStaticLocationReferenceDO>()
            .select("reference_type", "reference_id", "reference_name", "solar_system_id", "constellation_id", "region_id")
            .orderByAsc("reference_type", "reference_name", "reference_id");
        List<EveStaticLocationReferenceDO> references = locationReferenceMapper.selectList(query);
        Map<Long, MutableLocationTreeNode> regions = new LinkedHashMap<>();
        Map<Long, MutableLocationTreeNode> constellations = new LinkedHashMap<>();
        Map<Long, MutableLocationTreeNode> solarSystems = new LinkedHashMap<>();
        for (EveStaticLocationReferenceDO reference : references) {
            if ("REGION".equals(reference.getReferenceType())) {
                regions.put(reference.getReferenceId(), MutableLocationTreeNode.from(reference));
            }
        }
        for (EveStaticLocationReferenceDO reference : references) {
            if (!"CONSTELLATION".equals(reference.getReferenceType())) {
                continue;
            }
            MutableLocationTreeNode constellation = MutableLocationTreeNode.from(reference);
            constellations.put(reference.getReferenceId(), constellation);
            MutableLocationTreeNode region = regions.get(reference.getRegionId());
            if (region != null) {
                region.children.add(constellation);
            }
        }
        for (EveStaticLocationReferenceDO reference : references) {
            if (!"SOLAR_SYSTEM".equals(reference.getReferenceType())) {
                continue;
            }
            MutableLocationTreeNode solarSystem = MutableLocationTreeNode.from(reference);
            solarSystems.put(reference.getReferenceId(), solarSystem);
            MutableLocationTreeNode constellation = constellations.get(reference.getConstellationId());
            if (constellation != null) {
                constellation.children.add(solarSystem);
            }
        }
        for (EveStaticLocationReferenceDO reference : references) {
            MutableLocationTreeNode region = regions.get("REGION".equals(reference.getReferenceType())
                ? reference.getReferenceId()
                : reference.getRegionId());
            MutableLocationTreeNode constellation = constellations.get("CONSTELLATION".equals(reference
                .getReferenceType()) ? reference.getReferenceId() : reference.getConstellationId());
            MutableLocationTreeNode solarSystem = solarSystems.get("SOLAR_SYSTEM".equals(reference.getReferenceType())
                ? reference.getReferenceId()
                : reference.getSolarSystemId());
            if (region != null) {
                region.locationCount++;
            }
            if (constellation != null) {
                constellation.locationCount++;
            }
            if (solarSystem != null) {
                solarSystem.locationCount++;
            }
        }
        return regions.values().stream().map(MutableLocationTreeNode::toResponse).toList();
    }

    /** 生成位置查询条件，关键字覆盖位置 ID、名称与所属层级 ID。 */
    private static LambdaQueryWrapper<EveStaticLocationReferenceDO> buildLocationQuery(String keyword,
                                                                                       String referenceType,
                                                                                       String hierarchyType,
                                                                                       Long hierarchyId) {
        LambdaQueryWrapper<EveStaticLocationReferenceDO> query = new LambdaQueryWrapper<EveStaticLocationReferenceDO>()
            .orderByAsc(EveStaticLocationReferenceDO::getReferenceType, EveStaticLocationReferenceDO::getReferenceName, EveStaticLocationReferenceDO::getReferenceId);
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            query.and(item -> item.like(EveStaticLocationReferenceDO::getReferenceId, value)
                .or()
                .like(EveStaticLocationReferenceDO::getReferenceName, value)
                .or()
                .like(EveStaticLocationReferenceDO::getSolarSystemId, value)
                .or()
                .like(EveStaticLocationReferenceDO::getConstellationId, value)
                .or()
                .like(EveStaticLocationReferenceDO::getRegionId, value));
        }
        if (referenceType != null && !referenceType.isBlank()) {
            query.eq(EveStaticLocationReferenceDO::getReferenceType, referenceType.trim());
        }
        applyLocationHierarchyFilter(query, hierarchyType, hierarchyId);
        return query;
    }

    /** 依据左侧导航选择的星域、星座或星系筛选右侧所有下级位置。 */
    private static void applyLocationHierarchyFilter(LambdaQueryWrapper<EveStaticLocationReferenceDO> query,
                                                     String hierarchyType,
                                                     Long hierarchyId) {
        if (hierarchyType == null || hierarchyType.isBlank() || hierarchyId == null) {
            return;
        }
        switch (hierarchyType) {
            case "REGION" -> query.and(item -> item.eq(EveStaticLocationReferenceDO::getRegionId, hierarchyId)
                .or()
                .eq(EveStaticLocationReferenceDO::getReferenceType, "REGION")
                .eq(EveStaticLocationReferenceDO::getReferenceId, hierarchyId));
            case "CONSTELLATION" -> query.and(item -> item
                .eq(EveStaticLocationReferenceDO::getConstellationId, hierarchyId)
                .or()
                .eq(EveStaticLocationReferenceDO::getReferenceType, "CONSTELLATION")
                .eq(EveStaticLocationReferenceDO::getReferenceId, hierarchyId));
            case "SOLAR_SYSTEM" -> query.and(item -> item
                .eq(EveStaticLocationReferenceDO::getSolarSystemId, hierarchyId)
                .or()
                .eq(EveStaticLocationReferenceDO::getReferenceType, "SOLAR_SYSTEM")
                .eq(EveStaticLocationReferenceDO::getReferenceId, hierarchyId));
            default -> throw new BusinessException("位置树节点类型无效");
        }
    }

    /** 将物品资料实体转换为不会暴露数据库主键的列表响应。 */
    private static EveStaticTypeReferenceResp toTypeResp(EveStaticTypeReferenceDO source) {
        return new EveStaticTypeReferenceResp(source.getTypeId(), source.getTypeName(), source
            .getTypeDescription(), source.getMarketCategoryL1(), source.getMarketCategoryL2(), source
                .getMarketCategoryL3(), source.getMarketCategoryL4(), source.getMarketCategoryL5(), source
                    .getMarketCategoryL6(), source.getSourceUpdatedAt());
    }

    /** 将位置资料实体转换为不会暴露数据库主键的列表响应。 */
    private static EveStaticLocationReferenceResp toLocationResp(EveStaticLocationReferenceDO source) {
        return new EveStaticLocationReferenceResp(source.getReferenceType(), source.getReferenceId(), source
            .getReferenceName(), source.getSolarSystemId(), source.getConstellationId(), source.getRegionId(), source
                .getSecurityStatus(), source.getSourceUpdatedAt());
    }

    /** 写入类型资料，避免逐行 SQL 导致导入大表耗时过长。 */
    private void batchInsertTypes(List<EveStaticTypeReferenceDO> references) {
        forBatches(references, batch -> jdbcTemplate
            .batchUpdate("INSERT INTO eve_static_type_reference (type_id, type_name, type_description, market_category_l1, market_category_l2, market_category_l3, market_category_l4, market_category_l5, market_category_l6, source_updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement statement, int index) throws SQLException {
                    EveStaticTypeReferenceDO item = batch.get(index);
                    statement.setInt(1, item.getTypeId());
                    statement.setString(2, item.getTypeName());
                    statement.setString(3, item.getTypeDescription());
                    statement.setString(4, item.getMarketCategoryL1());
                    statement.setString(5, item.getMarketCategoryL2());
                    statement.setString(6, item.getMarketCategoryL3());
                    statement.setString(7, item.getMarketCategoryL4());
                    statement.setString(8, item.getMarketCategoryL5());
                    statement.setString(9, item.getMarketCategoryL6());
                    statement.setObject(10, item.getSourceUpdatedAt());
                }

                @Override
                public int getBatchSize() {
                    return batch.size();
                }
            }));
    }

    /** 批量写入星系与位置资料。 */
    private void batchInsertLocations(List<EveStaticLocationReferenceDO> references) {
        forBatches(references, batch -> jdbcTemplate
            .batchUpdate("INSERT INTO eve_static_location_reference (reference_type, reference_id, reference_name, solar_system_id, constellation_id, region_id, security_status, source_updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement statement, int index) throws SQLException {
                    EveStaticLocationReferenceDO item = batch.get(index);
                    statement.setString(1, item.getReferenceType());
                    statement.setLong(2, item.getReferenceId());
                    statement.setString(3, item.getReferenceName());
                    statement.setObject(4, item.getSolarSystemId());
                    statement.setObject(5, item.getConstellationId());
                    statement.setObject(6, item.getRegionId());
                    statement.setBigDecimal(7, item.getSecurityStatus());
                    statement.setObject(8, item.getSourceUpdatedAt());
                }

                @Override
                public int getBatchSize() {
                    return batch.size();
                }
            }));
    }

    /** 将大表拆为固定批次提交，控制 JDBC 参数和内存占用。 */
    private static <T> void forBatches(List<T> values, BatchConsumer<T> consumer) {
        for (int start = 0; start < values.size(); start += BATCH_SIZE) {
            consumer.accept(values.subList(start, Math.min(start + BATCH_SIZE, values.size())));
        }
    }

    /** 完整读取工作簿中资产树需要的所有资料页，并在写库前校验表头和必填数据。 */
    private static ParsedWorkbook parseWorkbook(Path source, String sourceFileName) {
        try (ZipFile archive = new ZipFile(source.toFile(), StandardCharsets.UTF_8)) {
            List<String> sharedStrings = readSharedStrings(archive);
            LocalDateTime sourceUpdatedAt = toLocalDateTime(Files.getLastModifiedTime(source));
            List<EveStaticTypeReferenceDO> types = parseTypes(readSheet(archive, "xl/worksheets/sheet1.xml", sharedStrings), sourceUpdatedAt);
            List<EveStaticLocationReferenceDO> locations = new ArrayList<>();
            locations
                .addAll(parseRegions(readSheet(archive, "xl/worksheets/sheet2.xml", sharedStrings), sourceUpdatedAt));
            locations
                .addAll(parseConstellations(readSheet(archive, "xl/worksheets/sheet3.xml", sharedStrings), sourceUpdatedAt));
            locations
                .addAll(parseSolarSystems(readSheet(archive, "xl/worksheets/sheet4.xml", sharedStrings), sourceUpdatedAt));
            locations
                .addAll(parseNpcStations(readSheet(archive, "xl/worksheets/sheet5.xml", sharedStrings), sourceUpdatedAt));
            locations
                .addAll(parsePublicStructures(readSheet(archive, "xl/worksheets/sheet6.xml", sharedStrings), sourceUpdatedAt));
            if (types.isEmpty() || locations.isEmpty()) {
                throw new BusinessException("EVE 静态资料缺少类型或位置数据");
            }
            return new ParsedWorkbook(sourceFileName, sha256(source), sourceUpdatedAt, types, locations);
        } catch (IOException | XMLStreamException e) {
            throw new BusinessException("无法读取 evedata.xlsx：" + e.getMessage());
        }
    }

    /** 读取共享字符串表，保留富文本中的所有文本片段。 */
    private static List<String> readSharedStrings(ZipFile archive) throws IOException, XMLStreamException {
        ZipEntry entry = requireEntry(archive, "xl/sharedStrings.xml");
        List<String> values = new ArrayList<>();
        try (InputStream input = new BufferedInputStream(archive.getInputStream(entry))) {
            XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(input);
            StringBuilder value = null;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && "si".equals(reader.getLocalName())) {
                    value = new StringBuilder();
                } else if (event == XMLStreamConstants.CHARACTERS && value != null) {
                    value.append(reader.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT && "si".equals(reader.getLocalName())) {
                    values.add(value.toString());
                    value = null;
                }
            }
            reader.close();
        }
        return values;
    }

    /** 读取一个工作表的单元格文本，按列索引构造成行数组。 */
    private static List<String[]> readSheet(ZipFile archive,
                                            String entryName,
                                            List<String> sharedStrings) throws IOException, XMLStreamException {
        ZipEntry entry = requireEntry(archive, entryName);
        List<String[]> rows = new ArrayList<>();
        try (InputStream input = new BufferedInputStream(archive.getInputStream(entry))) {
            XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(input);
            String[] row = null;
            int column = -1;
            String cellType = null;
            StringBuilder value = null;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if ("row".equals(reader.getLocalName())) {
                        row = new String[10];
                    } else if ("c".equals(reader.getLocalName())) {
                        column = columnIndex(reader.getAttributeValue(null, "r"));
                        cellType = reader.getAttributeValue(null, "t");
                        value = new StringBuilder();
                    } else if (("v".equals(reader.getLocalName()) || "t".equals(reader
                        .getLocalName())) && value != null) {
                        value.setLength(0);
                    }
                } else if (event == XMLStreamConstants.CHARACTERS && value != null) {
                    value.append(reader.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if ("c".equals(reader.getLocalName()) && row != null && column >= 0 && column < row.length) {
                        row[column] = "s".equals(cellType)
                            ? sharedStrings.get(Integer.parseInt(value.toString()))
                            : value.toString();
                    } else if ("row".equals(reader.getLocalName()) && row != null) {
                        rows.add(row);
                        row = null;
                    }
                }
            }
            reader.close();
        }
        return rows;
    }

    /** 从物品列表读取名称、说明和全部市场分类。 */
    private static List<EveStaticTypeReferenceDO> parseTypes(List<String[]> rows, LocalDateTime sourceUpdatedAt) {
        requireHeader(rows, "typeID", "物品列表");
        List<EveStaticTypeReferenceDO> result = new ArrayList<>();
        for (int index = 1; index < rows.size(); index++) {
            String[] row = rows.get(index);
            if (isBlank(row[0]) || isBlank(row[1])) {
                continue;
            }
            EveStaticTypeReferenceDO item = new EveStaticTypeReferenceDO();
            item.setTypeId(toInt(row[0], "物品类型 ID"));
            item.setTypeName(row[1].trim());
            item.setTypeDescription(emptyToNull(row[2]));
            item.setMarketCategoryL1(valueAt(row, 3));
            item.setMarketCategoryL2(valueAt(row, 4));
            item.setMarketCategoryL3(valueAt(row, 5));
            item.setMarketCategoryL4(valueAt(row, 6));
            item.setMarketCategoryL5(valueAt(row, 7));
            item.setMarketCategoryL6(valueAt(row, 8));
            item.setSourceUpdatedAt(sourceUpdatedAt);
            result.add(item);
        }
        return result;
    }

    /** 解析星域资料。 */
    private static List<EveStaticLocationReferenceDO> parseRegions(List<String[]> rows, LocalDateTime sourceUpdatedAt) {
        requireHeader(rows, "星域ID", "星域列表");
        return parseLocations(rows, "REGION", sourceUpdatedAt, row -> location(row, "REGION", 0, 1, -1, -1, -1, -1, sourceUpdatedAt));
    }

    /** 解析星座资料。 */
    private static List<EveStaticLocationReferenceDO> parseConstellations(List<String[]> rows,
                                                                          LocalDateTime sourceUpdatedAt) {
        requireHeader(rows, "星座ID", "星座列表");
        return parseLocations(rows, "CONSTELLATION", sourceUpdatedAt, row -> location(row, "CONSTELLATION", 0, 1, -1, -1, 2, -1, sourceUpdatedAt));
    }

    /** 解析星系资料。 */
    private static List<EveStaticLocationReferenceDO> parseSolarSystems(List<String[]> rows,
                                                                        LocalDateTime sourceUpdatedAt) {
        requireHeader(rows, "星系ID", "星系列表");
        return parseLocations(rows, "SOLAR_SYSTEM", sourceUpdatedAt, row -> location(row, "SOLAR_SYSTEM", 0, 1, 0, 2, 4, 6, sourceUpdatedAt));
    }

    /** 解析 NPC 空间站资料。 */
    private static List<EveStaticLocationReferenceDO> parseNpcStations(List<String[]> rows,
                                                                       LocalDateTime sourceUpdatedAt) {
        requireHeader(rows, "空间站ID", "NPC空间站");
        return parseLocations(rows, "NPC_STATION", sourceUpdatedAt, row -> location(row, "NPC_STATION", 0, 1, 2, 4, 6, 8, sourceUpdatedAt));
    }

    /** 解析公开玩家建筑资料。 */
    private static List<EveStaticLocationReferenceDO> parsePublicStructures(List<String[]> rows,
                                                                            LocalDateTime sourceUpdatedAt) {
        requireHeader(rows, "建筑物ID", "玩家公开建筑");
        return parseLocations(rows, "PUBLIC_STRUCTURE", sourceUpdatedAt, row -> location(row, "PUBLIC_STRUCTURE", 0, 1, 3, 5, 7, 9, sourceUpdatedAt));
    }

    /** 将位置表的有效数据行转为标准实体。 */
    private static List<EveStaticLocationReferenceDO> parseLocations(List<String[]> rows,
                                                                     String referenceType,
                                                                     LocalDateTime sourceUpdatedAt,
                                                                     LocationRowMapper mapper) {
        List<EveStaticLocationReferenceDO> result = new ArrayList<>();
        for (int index = 1; index < rows.size(); index++) {
            String[] row = rows.get(index);
            if (isBlank(row[0]) || isBlank(row[1])) {
                continue;
            }
            result.add(mapper.map(row));
        }
        return result;
    }

    /** 将一行原始位置资料转换为标准列。 */
    private static EveStaticLocationReferenceDO location(String[] row,
                                                         String referenceType,
                                                         int idColumn,
                                                         int nameColumn,
                                                         int solarSystemColumn,
                                                         int constellationColumn,
                                                         int regionColumn,
                                                         int securityColumn,
                                                         LocalDateTime sourceUpdatedAt) {
        EveStaticLocationReferenceDO item = new EveStaticLocationReferenceDO();
        item.setReferenceType(referenceType);
        item.setReferenceId(toLong(valueAt(row, idColumn), "位置 ID"));
        item.setReferenceName(valueAt(row, nameColumn));
        item.setSolarSystemId(numberAt(row, solarSystemColumn));
        item.setConstellationId(numberAt(row, constellationColumn));
        item.setRegionId(numberAt(row, regionColumn));
        item.setSecurityStatus(decimalAt(row, securityColumn));
        item.setSourceUpdatedAt(sourceUpdatedAt);
        return item;
    }

    /** 检查工作簿的首列表头，避免表结构变化后导入错列。 */
    private static void requireHeader(List<String[]> rows, String expected, String sheetName) {
        if (rows.isEmpty() || !expected.equals(valueAt(rows.get(0), 0))) {
            throw new BusinessException(sheetName + " 表头不符合 evedata.xlsx 格式");
        }
    }

    /** 从单元格地址中计算零基列索引。 */
    private static int columnIndex(String address) {
        if (address == null) {
            return -1;
        }
        int index = 0;
        for (int position = 0; position < address.length(); position++) {
            char character = address.charAt(position);
            if (!Character.isLetter(character)) {
                break;
            }
            index = index * 26 + Character.toUpperCase(character) - 'A' + 1;
        }
        return index - 1;
    }

    /** 读取工作簿压缩包中的指定条目。 */
    private static ZipEntry requireEntry(ZipFile archive, String name) {
        ZipEntry entry = archive.getEntry(name);
        if (entry == null) {
            throw new BusinessException("evedata.xlsx 缺少 " + name);
        }
        return entry;
    }

    /** 计算文件 SHA-256，用于识别资料是否已更新。 */
    private static String sha256(Path source) {
        try (InputStream input = Files.newInputStream(source)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int length; (length = input.read(buffer)) >= 0;) {
                digest.update(buffer, 0, length);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new BusinessException("无法计算 evedata.xlsx 校验值");
        }
    }

    /** 将文件时间转为本地无时区时间。 */
    private static LocalDateTime toLocalDateTime(FileTime fileTime) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(fileTime.toMillis()), ZoneId.systemDefault());
    }

    private static String valueAt(String[] row, int index) {
        return index < 0 || index >= row.length || row[index] == null ? "" : row[index].trim();
    }

    private static Long numberAt(String[] row, int index) {
        String value = valueAt(row, index);
        return value.isBlank() ? null : toLong(value, "位置关联 ID");
    }

    private static BigDecimal decimalAt(String[] row, int index) {
        String value = valueAt(row, index);
        return value.isBlank() ? null : new BigDecimal(value);
    }

    private static Integer toInt(String value, String fieldName) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(fieldName + "不是有效整数");
        }
    }

    private static Long toLong(String value, String fieldName) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(fieldName + "不是有效整数");
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 构建分类树期间使用的可变节点，完成后转换为不可变接口响应。 */
    private static final class MutableTypeCategoryNode {

        private final String name;
        private final List<String> path;
        private final boolean unclassified;
        private final Map<String, MutableTypeCategoryNode> children = new LinkedHashMap<>();
        private long directTypeCount;
        private long typeCount;

        /** 创建分类树节点。 */
        private MutableTypeCategoryNode(String name, List<String> path, boolean unclassified) {
            this.name = name;
            this.path = path;
            this.unclassified = unclassified;
        }

        /** 递归转换为供接口返回的不可变树节点。 */
        private EveStaticTypeCategoryNodeResp toResponse() {
            return new EveStaticTypeCategoryNodeResp(name, path, directTypeCount, typeCount, unclassified, children
                .values()
                .stream()
                .map(MutableTypeCategoryNode::toResponse)
                .toList());
        }
    }

    /** 构建位置导航树期间使用的可变节点，完成后转换为不可变接口响应。 */
    private static final class MutableLocationTreeNode {

        private final String referenceType;
        private final Long referenceId;
        private final String referenceName;
        private final List<MutableLocationTreeNode> children = new ArrayList<>();
        private long locationCount;

        /** 创建位置导航树节点。 */
        private MutableLocationTreeNode(String referenceType, Long referenceId, String referenceName) {
            this.referenceType = referenceType;
            this.referenceId = referenceId;
            this.referenceName = referenceName;
        }

        /** 用一条星域、星座或星系资料构建对应的导航节点。 */
        private static MutableLocationTreeNode from(EveStaticLocationReferenceDO source) {
            return new MutableLocationTreeNode(source.getReferenceType(), source.getReferenceId(), source
                .getReferenceName());
        }

        /** 递归转换为供接口返回的不可变树节点。 */
        private EveStaticLocationTreeNodeResp toResponse() {
            return new EveStaticLocationTreeNodeResp(referenceType, referenceId, referenceName, locationCount, children
                .stream()
                .map(MutableLocationTreeNode::toResponse)
                .toList());
        }
    }

    /** 解析完成但尚未落库的工作簿快照。 */
    private record ParsedWorkbook(String sourceFileName, String sha256, LocalDateTime sourceUpdatedAt,
                                  List<EveStaticTypeReferenceDO> types, List<EveStaticLocationReferenceDO> locations) {
    }

    /** 指定位置表的行转换规则。 */
    @FunctionalInterface
    private interface LocationRowMapper {
        EveStaticLocationReferenceDO map(String[] row);
    }

    /** 单批资料写入器。 */
    @FunctionalInterface
    private interface BatchConsumer<T> {
        void accept(List<T> batch);
    }
}
