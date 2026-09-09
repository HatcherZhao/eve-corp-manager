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

    /** 分页查询静态物品类型资料，供基础信息菜单展示。 */
    public PageResp<EveStaticTypeReferenceResp> pageTypes(int page, int size, String keyword, String marketCategoryL1) {
        Page<EveStaticTypeReferenceDO> result = typeReferenceMapper
            .selectPage(new Page<>(page, size), buildTypeQuery(keyword, marketCategoryL1));
        return new PageResp<>(result.getRecords().stream().map(EveStaticReferenceService::toTypeResp).toList(), result
            .getTotal());
    }

    /** 导出筛选后的完整静态物品类型资料。 */
    public List<EveStaticTypeReferenceResp> listTypesForExport(String keyword, String marketCategoryL1) {
        return typeReferenceMapper.selectList(buildTypeQuery(keyword, marketCategoryL1))
            .stream()
            .map(EveStaticReferenceService::toTypeResp)
            .toList();
    }

    /** 分页查询星域、星座、星系与建筑位置资料。 */
    public PageResp<EveStaticLocationReferenceResp> pageLocations(int page,
                                                                  int size,
                                                                  String keyword,
                                                                  String referenceType) {
        Page<EveStaticLocationReferenceDO> result = locationReferenceMapper
            .selectPage(new Page<>(page, size), buildLocationQuery(keyword, referenceType));
        return new PageResp<>(result.getRecords()
            .stream()
            .map(EveStaticReferenceService::toLocationResp)
            .toList(), result.getTotal());
    }

    /** 导出筛选后的完整静态位置资料。 */
    public List<EveStaticLocationReferenceResp> listLocationsForExport(String keyword, String referenceType) {
        return locationReferenceMapper.selectList(buildLocationQuery(keyword, referenceType))
            .stream()
            .map(EveStaticReferenceService::toLocationResp)
            .toList();
    }

    /** 生成物品类型查询条件，关键字覆盖 ID、名称、说明与六级市场分类。 */
    private static LambdaQueryWrapper<EveStaticTypeReferenceDO> buildTypeQuery(String keyword,
                                                                               String marketCategoryL1) {
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
        if (marketCategoryL1 != null && !marketCategoryL1.isBlank()) {
            query.eq(EveStaticTypeReferenceDO::getMarketCategoryL1, marketCategoryL1.trim());
        }
        return query;
    }

    /** 生成位置查询条件，关键字覆盖位置 ID、名称与所属层级 ID。 */
    private static LambdaQueryWrapper<EveStaticLocationReferenceDO> buildLocationQuery(String keyword,
                                                                                       String referenceType) {
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
        return query;
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
