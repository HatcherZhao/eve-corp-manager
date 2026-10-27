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

package top.continew.admin.eve.agent.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import top.continew.admin.eve.agent.EveAgentPrincipal;

/**
 * 外部 Agent 查询服务安全边界与序列化回归测试。
 *
 * @author zhaoyuqing
 */
class EveAgentQueryServiceTest {

    private NamedParameterJdbcTemplate jdbcTemplate;
    private EveAgentQueryService service;
    private EveAgentPrincipal principal;

    /**
     * 初始化固定军团身份和 JDBC 模拟。
     */
    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        service = new EveAgentQueryService(jdbcTemplate);
        principal = new EveAgentPrincipal(9L, 100L, 200L, 300L, "QQ 机器人");
    }

    /**
     * 验证目录不暴露令牌字段，并声明邮件正文只读能力。
     */
    @Test
    void shouldExposeSafeCatalog() {
        Map<String, Object> catalog = service.catalog(principal);

        assertThat(catalog.toString()).contains("mail", "documentReadable")
            .doesNotContain("accessToken", "refreshToken", "ownerHash", "lastFailureMessage");
    }

    /** 返回服务端查询时刻供机器人判断缓存年龄，且不能将查询时刻写回或冒充同步时间。 */
    @Test
    void shouldReturnServerQueryTimeSeparatelyFromSnapshotTime() {
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of());
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);

        Map<String, Object> result = service.query(principal, Map.of("dataset", "market"));

        OffsetDateTime queriedAt = OffsetDateTime.parse((String)result.get("queriedAt"));
        assertThat(queriedAt.getOffset()).isEqualTo(ZoneOffset.ofHours(8));
        assertThat(queriedAt.toInstant()).isBetween(before.toInstant(), OffsetDateTime.now(ZoneOffset.UTC).toInstant());
        assertThat(result).containsEntry("cacheOnly", true)
            .doesNotContainKeys("sourceUpdatedAt", "quoteSynchronizedAt");
    }

    /**
     * 验证查询使用真实 SQL 分页并强制注入租户与军团条件。
     */
    @Test
    void shouldQueryWithProjectionFiltersAndCorporationScope() {
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of(Map
            .of("itemId", 9007199254740991L, "typeName", "三钛合金", "lastSeenAt", Timestamp
                .valueOf("2026-10-25 12:00:00"))));

        Map<String, Object> result = service.query(principal, Map.of("dataset", "assets", "fields", List
            .of("itemId", "typeName", "lastSeenAt"), "filters", List.of(Map
                .of("field", "typeName", "operator", "contains", "value", "三钛")), "sort", List.of(Map
                    .of("field", "lastSeenAt", "direction", "DESC")), "page", 2, "size", 10));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), paramsCaptor.capture());
        assertThat(sqlCaptor.getValue())
            .contains("a.tenant_id=:tenantId", "a.corporation_ref_id=:corporationRefId", "a.type_name LIKE :filter0", "LIMIT :limit OFFSET :offset")
            .doesNotContain("三钛");
        MapSqlParameterSource params = (MapSqlParameterSource)paramsCaptor.getValue();
        assertThat(params.getValue("tenantId")).isEqualTo(100L);
        assertThat(params.getValue("corporationRefId")).isEqualTo(200L);
        assertThat(params.getValue("offset")).isEqualTo(10L);
        @SuppressWarnings("unchecked") List<Map<String, Object>> items = (List<Map<String, Object>>)result.get("items");
        assertThat(items.get(0)).containsEntry("itemId", "9007199254740991")
            .containsEntry("lastSeenAt", "2026-10-25T20:00:00+08:00");
    }

    /** Agent 权限摘要与用户鉴权共用有效授权，并始终限定当前军团租户。 */
    @Test
    void shouldReadEffectivePermissionsInAgentSummary() {
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of());
        service.query(principal, Map.of("dataset", "permission_summary"));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> parameters = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).queryForList(sql.capture(), parameters.capture());
        assertThat(sql.getValue())
            .contains("eve_effective_role_menu", "rm.tenant_id=r.tenant_id", "r.tenant_id=:tenantId");
        assertThat(parameters.getValue().getValue("tenantId")).isEqualTo(100L);
    }

    /**
     * 验证所有可形成 SQL 结构的输入都必须命中白名单。
     */
    @Test
    void shouldRejectSqlStructureInjection() {
        assertThatThrownBy(() -> service.query(principal, Map.of("dataset", "assets; DROP TABLE eve_character")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不支持的数据集");
        assertThatThrownBy(() -> service.query(principal, Map.of("dataset", "assets", "fields", List
            .of("itemId, deleted")))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不支持字段");
        assertThatThrownBy(() -> service.query(principal, Map.of("dataset", "assets", "sort", List.of(Map
            .of("field", "itemId", "direction", "DESC; DROP TABLE x"))))).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("排序方向");
        assertThatThrownBy(() -> service.query(principal, Map.of("dataset", "assets", "filters", List.of(Map
            .of("field", "itemId", "operator", "eq OR 1=1", "value", 1))))).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("筛选操作符");
        verify(jdbcTemplate, never()).queryForList(anyString(), any(SqlParameterSource.class));
    }

    /**
     * 验证整数筛选不会静默截断小数或溢出的超大数值。
     */
    @Test
    void shouldRejectNonIntegralOrOverflowedIntegerFilters() {
        assertThatThrownBy(() -> service.query(principal, Map.of("dataset", "assets", "filters", List.of(Map
            .of("field", "itemId", "operator", "eq", "value", "1.5"))))).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("筛选值类型不正确");
        assertThatThrownBy(() -> service.query(principal, Map.of("dataset", "assets", "filters", List.of(Map
            .of("field", "itemId", "operator", "eq", "value", "9223372036854775808")))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("筛选值类型不正确");

        verify(jdbcTemplate, never()).queryForList(anyString(), any(SqlParameterSource.class));
    }

    /**
     * 验证聚合先应用军团过滤，并拒绝恶意聚合别名。
     */
    @Test
    void shouldAggregateInsideCorporationScope() {
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of(Map
            .of("typeName", "凡晶石", "totalQuantity", 12345L)));

        Map<String, Object> result = service.aggregate(principal, Map.of("dataset", "mining_ledger", "groupBy", List
            .of("typeName"), "metrics", List.of(Map
                .of("function", "sum", "field", "quantity", "alias", "totalQuantity"))));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), any(SqlParameterSource.class));
        assertThat(sqlCaptor.getValue())
            .contains("l.tenant_id=:tenantId", "l.corporation_ref_id=:corporationRefId", "SUM(l.quantity)", "GROUP BY l.type_name");
        assertThat(result.get("total")).isEqualTo(1L);

        assertThatThrownBy(() -> service.aggregate(principal, Map.of("dataset", "mining_ledger", "metrics", List.of(Map
            .of("function", "count", "alias", "x` FROM eve_character --")))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("聚合别名");
    }

    /** 验证双字段分组默认排序和指标同值排序都有完整稳定顺序。 */
    @Test
    void shouldKeepAggregateGroupPaginationStable() {
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(2L);
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of());
        Map<String, Object> request = Map.of("dataset", "mining_ledger", "groupBy", List
            .of("characterName", "typeName"), "metrics", List.of(Map.of("function", "count", "alias", "rowCount")));
        service.aggregate(principal, request);
        var sortedRequest = new java.util.HashMap<>(request);
        sortedRequest.put("sort", List.of(Map.of("field", "rowCount", "direction", "DESC")));
        service.aggregate(principal, sortedRequest);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).queryForList(sql.capture(), any(SqlParameterSource.class));
        assertThat(sql.getAllValues().get(0)).contains("ORDER BY l.character_name ASC, l.type_name ASC LIMIT");
        assertThat(sql.getAllValues().get(1))
            .contains("ORDER BY `rowCount` DESC, l.character_name ASC, l.type_name ASC LIMIT");
    }

    /**
     * 验证无分组聚合固定只有一行，并拒绝重复或覆盖分组字段的别名。
     */
    @Test
    void shouldKeepSingleAggregateRowAndRejectAliasConflicts() {
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of(Map
            .of("rowCount", 0L)));

        Map<String, Object> firstPage = service.aggregate(principal, Map.of("dataset", "mining_ledger", "metrics", List
            .of(Map.of("function", "count", "alias", "rowCount")), "page", 1, "size", 1));
        Map<String, Object> secondPage = service.aggregate(principal, Map.of("dataset", "mining_ledger", "metrics", List
            .of(Map.of("function", "count", "alias", "rowCount")), "page", 2, "size", 1));

        assertThat(firstPage).containsEntry("total", 1L).containsEntry("hasMore", false);
        assertThat(secondPage).containsEntry("total", 1L).containsEntry("hasMore", false);
        verify(jdbcTemplate, never()).queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class));

        assertThatThrownBy(() -> service.aggregate(principal, Map.of("dataset", "mining_ledger", "metrics", List.of(Map
            .of("function", "count", "alias", "duplicate"), Map
                .of("function", "sum", "field", "quantity", "alias", "duplicate")))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("重复或与分组字段冲突");
        assertThatThrownBy(() -> service.aggregate(principal, Map.of("dataset", "mining_ledger", "groupBy", List
            .of("typeName"), "metrics", List.of(Map.of("function", "count", "alias", "typeName")))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("重复或与分组字段冲突");
        assertThatThrownBy(() -> service.aggregate(principal, Map.of("dataset", "static_types", "metrics", List.of(Map
            .of("function", "max", "field", "description", "alias", "descriptionMax")))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("字段不允许聚合");
    }

    /**
     * 验证北京时间存储字段执行最值聚合后不会再次增加八小时。
     */
    @Test
    void shouldPreserveTimeBasisForTimeAggregate() {
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of(Map
            .of("latestUpdate", LocalDateTime.of(2026, 10, 25, 12, 0))));

        Map<String, Object> result = service.aggregate(principal, Map
            .of("dataset", "moon_structure_notes", "metrics", List.of(Map
                .of("function", "max", "field", "updateTime", "alias", "latestUpdate"))));

        @SuppressWarnings("unchecked") Map<String, Object> item = ((List<Map<String, Object>>)result.get("items"))
            .get(0);
        assertThat(item).containsEntry("latestUpdate", "2026-10-25T12:00:00+08:00");
    }

    /**
     * 验证邮件按当前军团有效角色及有效授权读取本地正文，且 SQL 只有 SELECT。
     */
    @Test
    void shouldReadCachedMailWithoutUpstreamOrWrite() {
        when(jdbcTemplate.queryForMap(anyString(), any(SqlParameterSource.class))).thenReturn(Map
            .of("content", "2345", "totalLength", 10, "returnedLength", 4));

        Map<String, Object> result = service.readDocument(principal, Map
            .of("dataset", "mail", "id", "456", "characterId", "991", "offset", 2, "limit", 4));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).queryForMap(sqlCaptor.capture(), paramsCaptor.capture());
        String sql = sqlCaptor.getValue();
        assertThat(sql).startsWith("SELECT SUBSTRING(m.body")
            .contains("JOIN eve_character c", "c.status='ACTIVE'", "JOIN eve_authorization a", "a.status='ACTIVE'", "JSON_CONTAINS(a.scopes", "c.corporation_id=:corporationId", "m.character_id=:characterId", "m.mail_id=:documentId")
            .doesNotContainIgnoringCase("UPDATE ", "INSERT ", "DELETE ");
        MapSqlParameterSource params = (MapSqlParameterSource)paramsCaptor.getValue();
        assertThat(params.getValue("corporationId")).isEqualTo(300L);
        assertThat(params.getValue("characterId")).isEqualTo(991L);
        assertThat(result).containsEntry("content", "2345")
            .containsEntry("returnedLength", 4)
            .containsEntry("nextOffset", 6)
            .containsEntry("hasMore", true)
            .containsEntry("id", "456");
    }

    /**
     * 验证精确金额以字符串返回，避免外部 Agent 的 JSON 浮点精度损失。
     */
    @Test
    void shouldSerializeDecimalAsString() {
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of(Map
            .of("typeId", 34, "highestBuyPrice", new BigDecimal("6.120000"), "sourceUpdatedAt", LocalDateTime
                .of(2026, 10, 25, 12, 0))));

        Map<String, Object> result = service.query(principal, Map.of("dataset", "market", "fields", List
            .of("typeId", "highestBuyPrice", "sourceUpdatedAt")));

        @SuppressWarnings("unchecked") Map<String, Object> item = ((List<Map<String, Object>>)result.get("items"))
            .get(0);
        assertThat(item).containsEntry("typeId", "34")
            .containsEntry("highestBuyPrice", "6.120000")
            .containsEntry("sourceUpdatedAt", "2026-10-25T12:00:00+08:00");
    }

    /** 验证本站北京时间字段查询和筛选都不重复增加八小时，覆盖实际市场快照时间。 */
    @ParameterizedTest
    @CsvSource({"market,sourceUpdatedAt", "market,quoteSynchronizedAt", "market,detailSynchronizedAt",
        "corporation,lastSyncedAt", "characters,joinedAt", "character_role_snapshots,capturedAt",
        "industry_opportunities,calculatedAt", "industry_plans,confirmedAt", "industry_market_snapshots,capturedAt",
        "industry_market_snapshots,sourceUpdatedAt", "industry_system_snapshots,capturedAt",
        "industry_system_cost_index,fetchedAt", "static_types,sourceUpdatedAt", "static_locations,sourceUpdatedAt",
        "news,publishedAt"})
    void shouldPreserveBeijingStorageTime(String dataset, String field) {
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of(Map
            .of(field, Timestamp.valueOf("2026-10-25 22:07:18"))));

        Map<String, Object> result = service.query(principal, Map.of("dataset", dataset, "fields", List
            .of(field), "filters", List.of(Map
                .of("field", field, "operator", "gte", "value", "2026-10-25T14:07:18Z"))));

        assertThat(((List<Map<String, Object>>)result.get("items")).get(0))
            .containsEntry(field, "2026-10-25T22:07:18+08:00");
        ArgumentCaptor<SqlParameterSource> parameters = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).queryForList(anyString(), parameters.capture());
        assertThat(parameters.getValue().getValue("filter0")).isEqualTo(LocalDateTime.of(2026, 10, 25, 22, 7, 18));
    }

    /** 验证官方事件时间和显式 UTC 同步时间仍按 UTC 解释，不能全表统一改为北京时间。 */
    @ParameterizedTest
    @CsvSource({"mail,sentAt", "mail,bodySynchronizedAt", "mail,lastSeenAt", "notifications,sentAt",
        "notifications,lastSeenAt", "data_freshness,lastSuccessfulAt", "data_freshness,sourceExpiresAt",
        "data_freshness,lastFailureAt", "assets,lastSeenAt", "structures,lastSeenAt", "members,lastSeenAt",
        "moon_extractions,chunkArrivalAt", "moon_ore_profiles,sourceSentAt", "universe_systems,synchronizedAt",
        "industry_system_cost_index,expiresAt", "starmap_routes,validatedAt"})
    void shouldPreserveUtcStorageTime(String dataset, String field) {
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of(Map
            .of(field, LocalDateTime.of(2026, 10, 25, 14, 7, 18))));

        Map<String, Object> result = service.query(principal, Map.of("dataset", dataset, "fields", List.of(field)));

        assertThat(((List<Map<String, Object>>)result.get("items")).get(0))
            .containsEntry(field, "2026-10-25T22:07:18+08:00");
    }

    /** 时间聚合也必须继承原字段的时区，不能通过别名丢失北京时间存储基准。 */
    @Test
    void shouldPreserveBeijingTimeInAggregateAlias() {
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of(Map
            .of("latestCalculation", Timestamp.valueOf("2026-10-25 22:07:18"))));

        Map<String, Object> result = service.aggregate(principal, Map
            .of("dataset", "industry_opportunities", "metrics", List.of(Map
                .of("function", "max", "field", "calculatedAt", "alias", "latestCalculation"))));

        assertThat(((List<Map<String, Object>>)result.get("items")).get(0))
            .containsEntry("latestCalculation", "2026-10-25T22:07:18+08:00");
    }

    /**
     * 验证不同时区表达的同一瞬时会转换为相同的 UTC 数据库存储值。
     */
    @Test
    void shouldNormalizeEquivalentTimeFiltersToUtc() {
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of());

        service.query(principal, Map.of("dataset", "mail", "filters", List.of(Map
            .of("field", "sentAt", "operator", "gte", "value", "2026-10-25T12:00:00+08:00"))));
        service.query(principal, Map.of("dataset", "mail", "filters", List.of(Map
            .of("field", "sentAt", "operator", "gte", "value", "2026-10-25T04:00:00Z"))));

        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).queryForList(anyString(), paramsCaptor.capture());
        List<SqlParameterSource> values = paramsCaptor.getAllValues();
        assertThat(values.get(0).getValue("filter0")).isEqualTo(LocalDateTime.of(2026, 10, 25, 4, 0));
        assertThat(values.get(1).getValue("filter0")).isEqualTo(LocalDateTime.of(2026, 10, 25, 4, 0));
    }

    /**
     * 验证无权限记录与尚未缓存正文会返回不同缺失原因，并避免切片边界溢出。
     */
    @Test
    void shouldClassifyMissingDocumentsAndAvoidOffsetOverflow() {
        when(jdbcTemplate.queryForMap(anyString(), any(SqlParameterSource.class)))
            .thenThrow(new EmptyResultDataAccessException(1));

        Map<String, Object> missing = service.readDocument(principal, Map
            .of("dataset", "notifications", "id", 1, "characterId", 99, "offset", Integer.MAX_VALUE, "limit", 20000));

        assertThat(missing).containsEntry("content", "")
            .containsEntry("contentAvailable", false)
            .containsEntry("missingReason", "document_not_found_or_not_authorized")
            .containsEntry("offset", 0)
            .containsEntry("hasMore", false);
    }
}
