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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import top.continew.admin.eve.client.SerenityStaticDataClient;
import top.continew.admin.eve.model.EveOfficialTypeSyncStatusResp;
import top.continew.admin.eve.model.EveStaticTypeReferenceResp;
import top.continew.admin.eve.model.serenity.SerenityMarketGroupResponse;
import top.continew.admin.eve.model.serenity.SerenityUniverseNameResponse;
import top.continew.admin.eve.model.serenity.SerenityUniverseTypeResponse;
import top.continew.starter.core.exception.BusinessException;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 以可续跑的分阶段任务构建晨曦官方物品目录。
 *
 * <p>类型 ID 与中文名称覆盖官方目录中的全部类型；市场分类只适用于可交易物品，未进入市场组的类型
 * 保留为空分类。单个物品说明在实际查看时补齐，避免对五万余种类型逐项请求。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EveOfficialTypeReferenceService {

    private static final int NAME_BATCH_SIZE = 1000;
    private static final int CATEGORY_DEPTH = 6;

    private final SerenityStaticDataClient client;
    private final EveStaticReferenceService staticReferenceService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final RedissonClient redissonClient;

    /** 物品名称与说明变化很少，成功取得后在该窗口内直接复用。 */
    @Value("${eve.reference.official-type-detail-ttl:P30D}")
    private Duration officialTypeDetailTtl = Duration.ofDays(30);

    /** 使用有限请求预算推进一段同步，进度持久化后可在重启后继续。 */
    public EveOfficialTypeSyncStatusResp advance(int requestBudget) {
        int remaining = Math.max(1, Math.min(requestBudget, 50));
        try {
            while (remaining > 0) {
                String phase = stateString("phase");
                switch (phase) {
                    case "TYPE_IDS" -> remaining -= advanceTypeIds();
                    case "MARKET_GROUPS" -> remaining -= advanceMarketGroups(remaining);
                    case "NAMES" -> remaining -= advanceNames();
                    case "PUBLISH" -> {
                        publish();
                        remaining = 0;
                    }
                    case "READY" -> remaining = 0;
                    default -> throw new BusinessException("未知的官方物品目录同步阶段");
                }
            }
            return status();
        } catch (RuntimeException e) {
            jdbcTemplate.update("UPDATE eve_static_type_sync_state SET last_error = ?, update_time = NOW() WHERE id = 1", concise(e));
            throw e;
        }
    }

    /** 清空未发布的暂存进度并从官方目录第一页重新开始。 */
    public EveOfficialTypeSyncStatusResp restart() {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("DELETE FROM eve_static_type_stage");
            jdbcTemplate.update("DELETE FROM eve_static_market_group_stage");
            jdbcTemplate.update("UPDATE eve_static_type_sync_state SET phase = 'TYPE_IDS', current_page = 1, total_pages = 0, started_at = NOW(), completed_at = NULL, last_error = NULL, update_time = NOW() WHERE id = 1");
        });
        return status();
    }

    /** 返回可展示和监控的持久同步进度。 */
    public EveOfficialTypeSyncStatusResp status() {
        Map<String, Object> state = jdbcTemplate.queryForMap("SELECT * FROM eve_static_type_sync_state WHERE id = 1");
        return new EveOfficialTypeSyncStatusResp(String.valueOf(state.get("phase")), number(state
            .get("current_page")), number(state.get("total_pages")), count("eve_static_type_stage"), countWhere("eve_static_type_stage", "type_name IS NOT NULL"), count("eve_static_market_group_stage"), countWhere("eve_static_market_group_stage", "loaded = 1"), dateTime(state
                .get("started_at")), dateTime(state.get("completed_at")), state.get("last_error") == null
                    ? null
                    : String.valueOf(state.get("last_error")));
    }

    /** 按需补齐官方类型详情；有效缓存直接复用，同一类型的跨实例请求只允许一个访问上游。 */
    public EveStaticTypeReferenceResp refreshTypeDetail(int typeId) {
        TypeDetailState current = findTypeDetailState(typeId);
        if (current == null) {
            throw new BusinessException("该类型尚未进入官方物品目录快照");
        }
        if (isOfficialDetailFresh(current)) {
            return requireType(typeId);
        }
        RLock lock = redissonClient.getLock("eve:reference:official-type-detail:" + typeId);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(30, TimeUnit.SECONDS);
            if (!acquired) {
                throw new BusinessException("官方物品详情正在刷新，请稍后重试");
            }
            // 等锁期间其他实例可能已经完成刷新，锁内必须再次确认。
            TypeDetailState lockedState = findTypeDetailState(typeId);
            if (lockedState == null) {
                throw new BusinessException("该类型尚未进入官方物品目录快照");
            }
            if (!isOfficialDetailFresh(lockedState)) {
                SerenityUniverseTypeResponse official = client.getType(typeId);
                jdbcTemplate.update("UPDATE eve_static_type_reference SET type_name = ?, type_description = ?, source_updated_at = NOW(), official_detail_updated_at = NOW() WHERE type_id = ?", official
                    .name(), official.description(), typeId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("官方物品详情刷新被中断");
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return requireType(typeId);
    }

    /** 读取详情缓存状态；时间字段能区分 Excel 说明和已验证的官方说明。 */
    private TypeDetailState findTypeDetailState(int typeId) {
        List<TypeDetailState> states = jdbcTemplate.query("SELECT type_description, official_detail_updated_at FROM eve_static_type_reference WHERE type_id = ?", (row, index) -> {
            java.sql.Timestamp updatedAt = row.getTimestamp("official_detail_updated_at");
            return new TypeDetailState(row.getString("type_description"), updatedAt == null
                ? null
                : updatedAt.toLocalDateTime());
        }, typeId);
        return states.isEmpty() ? null : states.get(0);
    }

    /** 官方详情只有在成功时间仍处于复用窗口内时才可直接返回。 */
    private boolean isOfficialDetailFresh(TypeDetailState state) {
        return state.officialDetailUpdatedAt() != null && !state.officialDetailUpdatedAt()
            .isBefore(LocalDateTime.now().minus(officialTypeDetailTtl));
    }

    /** 返回已经入库的类型资料。 */
    private EveStaticTypeReferenceResp requireType(int typeId) {
        var type = staticReferenceService.findTypes(Set.of(typeId)).get(typeId);
        if (type == null) {
            throw new BusinessException("该类型尚未进入官方物品目录快照");
        }
        return new EveStaticTypeReferenceResp(type.getTypeId(), type.getTypeName(), type.getTypeDescription(), type
            .getMarketCategoryL1(), type.getMarketCategoryL2(), type.getMarketCategoryL3(), type
                .getMarketCategoryL4(), type.getMarketCategoryL5(), type.getMarketCategoryL6(), type
                    .getSourceUpdatedAt());
    }

    /** 已保存说明及其官方成功更新时间。 */
    record TypeDetailState(String description, LocalDateTime officialDetailUpdatedAt) {
    }

    /** 读取一页类型 ID 并保存；官方分页完整后进入市场组阶段。 */
    private int advanceTypeIds() {
        int page = stateInt("current_page");
        var response = client.getTypeIds(page);
        List<Integer> ids = response.body().stream().filter(id -> id != null && id > 0).distinct().toList();
        batchInsertTypeIds(ids);
        int nextPage = page + 1;
        int totalPages = response.pageCount();
        jdbcTemplate.update("UPDATE eve_static_type_sync_state SET phase = ?, current_page = ?, total_pages = ?, started_at = COALESCE(started_at, NOW()), last_error = NULL, update_time = NOW() WHERE id = 1", nextPage > totalPages
            ? "MARKET_GROUPS"
            : "TYPE_IDS", nextPage, totalPages);
        return 1;
    }

    /** 首次登记全部市场组，随后按预算逐组读取详情。 */
    private int advanceMarketGroups(int budget) {
        Long registered = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM eve_static_market_group_stage", Long.class);
        int used = 0;
        if (registered == null || registered == 0) {
            batchInsertMarketGroupIds(client.getMarketGroupIds());
            used++;
        }
        int available = Math.max(0, budget - used);
        if (available > 0) {
            List<Integer> groupIds = jdbcTemplate.queryForList("SELECT market_group_id FROM eve_static_market_group_stage WHERE loaded = 0 ORDER BY market_group_id LIMIT " + available, Integer.class);
            for (Integer groupId : groupIds) {
                saveMarketGroup(client.getMarketGroup(groupId));
                used++;
            }
        }
        Long missing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM eve_static_market_group_stage WHERE loaded = 0", Long.class);
        if (missing != null && missing == 0) {
            jdbcTemplate.update("UPDATE eve_static_type_sync_state SET phase = 'NAMES', last_error = NULL, update_time = NOW() WHERE id = 1");
        }
        return Math.max(used, 1);
    }

    /** 批量解析一千种类型的官方中文名，全部完成后进入发布阶段。 */
    private int advanceNames() {
        List<Long> ids = jdbcTemplate.queryForList("SELECT type_id FROM eve_static_type_stage WHERE type_name IS NULL AND name_attempts < 3 ORDER BY type_id LIMIT " + NAME_BATCH_SIZE, Long.class);
        if (ids.isEmpty()) {
            long unresolved = countWhere("eve_static_type_stage", "type_name IS NULL");
            if (unresolved > 0) {
                // 三次连续响应仍缺少名称时保持正式快照不动；重置计数让后续调度可在官方恢复后继续。
                jdbcTemplate.update("UPDATE eve_static_type_stage SET name_attempts = 0 WHERE type_name IS NULL");
                throw new BusinessException("官方名称接口仍有 " + unresolved + " 个类型未返回，未发布本轮目录");
            }
            jdbcTemplate.update("UPDATE eve_static_type_sync_state SET phase = 'PUBLISH', last_error = NULL, update_time = NOW() WHERE id = 1");
            return 0;
        }
        Map<Long, String> names = new HashMap<>();
        for (SerenityUniverseNameResponse item : client.getTypeNames(ids)) {
            if ("inventory_type".equals(item.category()) && item.id() != null && item.name() != null && !item.name()
                .isBlank()) {
                names.put(item.id(), item.name().trim());
            }
        }
        jdbcTemplate.batchUpdate("UPDATE eve_static_type_stage SET name_attempts = name_attempts + 1 WHERE type_id = ?", ids
            .stream()
            .map(id -> new Object[] {id})
            .toList());
        jdbcTemplate.batchUpdate("UPDATE eve_static_type_stage SET type_name = ? WHERE type_id = ?", names
            .entrySet()
            .stream()
            .map(entry -> new Object[] {entry.getValue(), entry.getKey()})
            .toList());
        return 1;
    }

    /** 构造市场分类路径并在单事务中合并正式类型快照。 */
    private void publish() {
        long resolvedNames = countWhere("eve_static_type_stage", "type_name IS NOT NULL");
        if (resolvedNames == 0) {
            throw new BusinessException("官方物品目录没有可发布的中文名称");
        }
        Map<Integer, GroupStage> groups = loadGroups();
        Map<Integer, List<String>> categories = buildCategories(groups);
        batchUpdateCategories(categories);
        transactionTemplate.executeWithoutResult(status -> {
            // 未被本轮官方目录确认的旧资料仅退出目录，不删除，避免资产、工业等历史引用失效。
            jdbcTemplate.update("UPDATE eve_static_type_reference SET official_directory = 0");
            jdbcTemplate.update("""
                INSERT INTO eve_static_type_reference
                    (type_id, type_name, type_description, market_category_l1, market_category_l2,
                     market_category_l3, market_category_l4, market_category_l5, market_category_l6,
                     source_updated_at, official_directory)
                SELECT type_id, type_name, type_description, market_category_l1, market_category_l2,
                       market_category_l3, market_category_l4, market_category_l5, market_category_l6, NOW(), 1
                FROM eve_static_type_stage WHERE type_name IS NOT NULL
                ON DUPLICATE KEY UPDATE
                    type_name = VALUES(type_name), market_category_l1 = VALUES(market_category_l1),
                    market_category_l2 = VALUES(market_category_l2), market_category_l3 = VALUES(market_category_l3),
                    market_category_l4 = VALUES(market_category_l4), market_category_l5 = VALUES(market_category_l5),
                    market_category_l6 = VALUES(market_category_l6), source_updated_at = VALUES(source_updated_at),
                    official_directory = 1
                """);
            jdbcTemplate.update("""
                INSERT INTO eve_static_reference_import
                    (reference_name, source_file_name, source_sha256, source_updated_at, imported_at, record_count)
                VALUES ('SERENITY_ESI_TYPES', '晨曦官方 ESI', REPEAT('0', 64), NOW(), NOW(), ?)
                ON DUPLICATE KEY UPDATE source_file_name = VALUES(source_file_name), source_updated_at = VALUES(source_updated_at),
                    imported_at = VALUES(imported_at), record_count = VALUES(record_count)
                """, resolvedNames);
            jdbcTemplate.update("UPDATE eve_static_type_sync_state SET phase = 'READY', completed_at = NOW(), last_error = NULL, update_time = NOW() WHERE id = 1");
        });
        staticReferenceService.afterOfficialTypeSnapshotPublished();
    }

    private Map<Integer, GroupStage> loadGroups() {
        Map<Integer, GroupStage> result = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT market_group_id, parent_group_id, group_name, type_ids_json FROM eve_static_market_group_stage WHERE loaded = 1", row -> {
            try {
                List<Integer> typeIds = objectMapper.readValue(row.getString("type_ids_json"), new TypeReference<>() {
                });
                result.put(row.getInt("market_group_id"), new GroupStage(row.getInt("market_group_id"), integerOrNull(row
                    .getObject("parent_group_id")), row.getString("group_name"), typeIds));
            } catch (JsonProcessingException e) {
                throw new BusinessException("无法解析官方市场分类暂存数据");
            }
        });
        return result;
    }

    /** 将叶子市场组映射为最多六层的官方分类路径。 */
    static Map<Integer, List<String>> buildCategories(Map<Integer, GroupStage> groups) {
        Map<Integer, List<String>> result = new HashMap<>();
        for (GroupStage group : groups.values()) {
            List<String> path = new ArrayList<>();
            Set<Integer> visited = new LinkedHashSet<>();
            GroupStage current = group;
            while (current != null && visited.add(current.id())) {
                path.add(0, current.name());
                current = current.parentId() == null ? null : groups.get(current.parentId());
            }
            if (path.size() > CATEGORY_DEPTH) {
                path = path.subList(path.size() - CATEGORY_DEPTH, path.size());
            }
            List<String> finalPath = List.copyOf(path);
            group.typeIds().forEach(typeId -> result.put(typeId, finalPath));
        }
        return result;
    }

    private void batchInsertTypeIds(List<Integer> ids) {
        jdbcTemplate.batchUpdate("INSERT IGNORE INTO eve_static_type_stage (type_id) VALUES (?)", new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                statement.setInt(1, ids.get(index));
            }

            @Override
            public int getBatchSize() {
                return ids.size();
            }
        });
    }

    private void batchInsertMarketGroupIds(List<Integer> ids) {
        jdbcTemplate.batchUpdate("INSERT IGNORE INTO eve_static_market_group_stage (market_group_id) VALUES (?)", ids
            .stream()
            .filter(id -> id != null && id > 0)
            .map(id -> new Object[] {id})
            .toList());
    }

    private void saveMarketGroup(SerenityMarketGroupResponse group) {
        try {
            jdbcTemplate.update("UPDATE eve_static_market_group_stage SET parent_group_id = ?, group_name = ?, type_ids_json = ?, loaded = 1 WHERE market_group_id = ?", group
                .parentGroupId(), group.name(), objectMapper.writeValueAsString(group.types() == null
                    ? List.of()
                    : group.types()), group.marketGroupId());
        } catch (JsonProcessingException e) {
            throw new BusinessException("无法保存官方市场分类");
        }
    }

    private void batchUpdateCategories(Map<Integer, List<String>> categories) {
        jdbcTemplate.batchUpdate("UPDATE eve_static_type_stage SET market_category_l1 = ?, market_category_l2 = ?, market_category_l3 = ?, market_category_l4 = ?, market_category_l5 = ?, market_category_l6 = ? WHERE type_id = ?", categories
            .entrySet()
            .stream()
            .map(entry -> {
                List<String> path = entry.getValue();
                return new Object[] {valueAt(path, 0), valueAt(path, 1), valueAt(path, 2), valueAt(path, 3), valueAt(path, 4), valueAt(path, 5), entry
                    .getKey()};
            })
            .toList());
    }

    private String stateString(String column) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM eve_static_type_sync_state WHERE id = 1", String.class);
    }

    private int stateInt(String column) {
        Integer value = jdbcTemplate.queryForObject("SELECT " + column + " FROM eve_static_type_sync_state WHERE id = 1", Integer.class);
        return value == null ? 0 : value;
    }

    private long count(String table) {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return value == null ? 0 : value;
    }

    private long countWhere(String table, String condition) {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + condition, Long.class);
        return value == null ? 0 : value;
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    /** 兼容 Connector/J 将 INT UNSIGNED 返回为 Long，并保留数据库空值。 */
    static Integer integerOrNull(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static LocalDateTime dateTime(Object value) {
        return value instanceof LocalDateTime dateTime ? dateTime : null;
    }

    private static String concise(Throwable error) {
        String value = error.getMessage();
        if (value == null || value.isBlank()) {
            value = error.getClass().getSimpleName();
        }
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    private static String valueAt(List<String> path, int index) {
        return index < path.size() ? path.get(index) : "";
    }

    /** 构建分类路径所需的暂存市场组。 */
    record GroupStage(int id, Integer parentId, String name, List<Integer> typeIds) {
    }
}
