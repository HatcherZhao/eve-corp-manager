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
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import top.continew.admin.LocalIntegrationEnvironment;
import top.continew.admin.eve.agent.EveAgentPrincipal;
import top.continew.admin.eve.service.EveGameNotificationPresentationService;

/**
 * 外部 Agent 查询层真实 MySQL 集成测试，使用单连接同名临时表隔离全部合成数据。
 *
 * @author zhaoyuqing
 */
class EveAgentQueryPersistenceIT {

    private static final long TENANT_ID = 992001L;
    private static final long OTHER_TENANT_ID = 992002L;
    private static final long CORPORATION_REF_ID = 992101L;
    private static final long OTHER_CORPORATION_REF_ID = 992102L;
    private static final long CORPORATION_ID = 992201L;
    private static final long OTHER_CORPORATION_ID = 992202L;
    private static final Pattern TABLE_PATTERN = Pattern.compile("\\b(?:eve|sys)_[a-z0-9_]+\\b");
    private static final Map<String, String> MIGRATION_TABLES = Map
        .of("eve_industry_market_input_snapshot", "050_industry_snapshots.sql", "eve_industry_market_order_slice", "050_industry_snapshots.sql", "eve_industry_system_snapshot", "050_industry_snapshots.sql", "eve_industry_plan", "051_industry_plans.sql", "eve_industry_plan_step", "051_industry_plans.sql", "eve_moon_ore_profile", "053_moon_ore_profile.sql", "eve_agent_audit", "054_agent_access.sql");

    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;
    private EveAgentQueryService service;
    private EveAgentPrincipal principal;
    private String databaseName;

    /**
     * 建立单连接并以同名临时表屏蔽真实表，测试期间不写入任何真实业务表。
     */
    @BeforeEach
    void setUp() throws Exception {
        LocalIntegrationEnvironment.configureOrSkip();
        databaseName = property("DB_NAME");
        String url = "jdbc:mysql://" + property("DB_HOST") + ":" + property("DB_PORT") + "/" + databaseName + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        Connection connection = DriverManager.getConnection(url, property("DB_USER"), property("DB_PWD"));
        dataSource = new SingleConnectionDataSource(connection, true);
        jdbc = new JdbcTemplate(dataSource);
        createTemporaryTables();
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        namedJdbc.getJdbcTemplate().setQueryTimeout(10);
        service = new EveAgentQueryService(namedJdbc, mock(EveGameNotificationPresentationService.class));
        principal = new EveAgentPrincipal(992301L, TENANT_ID, CORPORATION_REF_ID, CORPORATION_ID, "集成测试 Agent");
    }

    /**
     * 关闭单连接，全部临时表和合成数据随连接自动销毁。
     */
    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    /**
     * 所有已注册数据集必须能在真实 MySQL 上执行默认投影查询与聚合 SQL。
     */
    @Test
    void shouldExecuteEveryRegisteredDatasetAgainstRealMysql() {
        List<String> executed = new ArrayList<>();
        EveAgentDatasetRegistry.all().forEach((key, dataset) -> {
            Map<String, Object> page = service.query(principal, Map.of("dataset", key, "size", 1));
            assertThat(page).containsEntry("dataset", key).containsEntry("total", 0L);
            Map<String, Object> aggregate = service.aggregate(principal, Map.of("dataset", key, "metrics", List.of(Map
                .of("function", "count", "alias", "rowCount")), "size", 1));
            assertThat(aggregate).containsEntry("dataset", key);
            executed.add(key);
        });

        assertThat(executed).containsExactlyInAnyOrderElementsOf(EveAgentDatasetRegistry.all().keySet());
        assertThat(executed).hasSizeGreaterThanOrEqualTo(35);
    }

    /**
     * 邮件与通知必须同时满足租户、军团、有效角色、有效授权和精确 OAuth Scope。
     */
    @Test
    void shouldIsolateCommunicationByCorporationAuthorizationAndScope() {
        insertCharacter(1L, TENANT_ID, 11L, 101L, CORPORATION_ID, "有效角色", "ACTIVE");
        insertCharacter(2L, TENANT_ID, 12L, 102L, OTHER_CORPORATION_ID, "同租户其他军团", "ACTIVE");
        insertCharacter(3L, OTHER_TENANT_ID, 13L, 103L, CORPORATION_ID, "其他租户", "ACTIVE");
        insertCharacter(4L, TENANT_ID, 14L, 104L, CORPORATION_ID, "缺少邮件权限", "ACTIVE");
        insertCharacter(5L, TENANT_ID, 15L, 105L, CORPORATION_ID, "已撤销授权", "ACTIVE");
        insertAuthorization(21L, TENANT_ID, 1L, 11L, "ACTIVE", "[\"esi-mail.read_mail.v1\",\"esi-characters.read_notifications.v1\"]");
        insertAuthorization(22L, TENANT_ID, 2L, 12L, "ACTIVE", "[\"esi-mail.read_mail.v1\"]");
        insertAuthorization(23L, OTHER_TENANT_ID, 3L, 13L, "ACTIVE", "[\"esi-mail.read_mail.v1\"]");
        insertAuthorization(24L, TENANT_ID, 4L, 14L, "ACTIVE", "[\"esi-characters.read_notifications.v1\"]");
        insertAuthorization(25L, TENANT_ID, 5L, 15L, "REVOKED", "[\"esi-mail.read_mail.v1\"]");
        insertMail(31L, TENANT_ID, 11L, 1L, 101L, 5001L, "有效邮件", "正文", false);
        insertMail(32L, TENANT_ID, 12L, 2L, 102L, 5001L, "其他军团邮件", "越权正文", false);
        insertMail(33L, OTHER_TENANT_ID, 13L, 3L, 103L, 5001L, "其他租户邮件", "越权正文", false);
        insertMail(34L, TENANT_ID, 14L, 4L, 104L, 5002L, "无邮件Scope", "越权正文", false);
        insertMail(35L, TENANT_ID, 15L, 5L, 105L, 5003L, "已撤销邮件", "越权正文", false);
        insertMail(36L, TENANT_ID, 11L, 1L, 101L, 5004L, "正文未缓存", null, false);
        insertMail(37L, TENANT_ID, 11L, 1L, 101L, 5005L, "长正文邮件", "前".repeat(520) + "深层关键字", false);
        insertNotification(41L, TENANT_ID, 11L, 1L, 101L, 6001L, "有效通知");
        insertNotification(42L, TENANT_ID, 12L, 2L, 102L, 6002L, "其他军团通知");

        Map<String, Object> mailPage = service.query(principal, Map.of("dataset", "mail", "size", 100));
        assertThat(subjects(mailPage)).containsExactlyInAnyOrder("有效邮件", "正文未缓存", "长正文邮件");
        Map<String, Object> deepBodyMatch = service.query(principal, Map.of("dataset", "mail", "fields", List
            .of("subject", "bodyPreview"), "keyword", "深层关键字", "size", 10, "textLimit", 2000));
        assertThat(subjects(deepBodyMatch)).containsExactly("长正文邮件");
        assertThat(fieldValues(deepBodyMatch, "bodyPreview").get(0).toString()).hasSize(500).doesNotContain("深层关键字");
        Map<String, Object> notificationPage = service.query(principal, Map
            .of("dataset", "notifications", "size", 100));
        assertThat(fieldValues(notificationPage, "contentPreview")).containsExactly("有效通知");

        Map<String, Object> body = service.readDocument(principal, Map
            .of("dataset", "mail", "id", 5001L, "characterId", 101L));
        assertThat(body).containsEntry("content", "正文").containsEntry("contentAvailable", true);
        Map<String, Object> otherCorporationBody = service.readDocument(principal, Map
            .of("dataset", "mail", "id", 5001L, "characterId", 102L));
        assertThat(otherCorporationBody).containsEntry("contentAvailable", false)
            .containsEntry("missingReason", "document_not_found_or_not_authorized");
        Map<String, Object> uncached = service.readDocument(principal, Map
            .of("dataset", "mail", "id", 5004L, "characterId", 101L));
        assertThat(uncached).containsEntry("contentAvailable", false)
            .containsEntry("missingReason", "content_not_cached");
        assertThat(jdbc.queryForObject("SELECT is_read FROM eve_game_mail WHERE id=31", Boolean.class)).isFalse();
    }

    /**
     * 军团字段数据必须隔离聚合；同租户多军团合成数据不能混入 principal 的军团结果。
     */
    @Test
    void shouldAggregateOnlyCurrentCorporationRows() {
        insertMiningLedger(51L, TENANT_ID, CORPORATION_REF_ID, 100L);
        insertMiningLedger(52L, TENANT_ID, CORPORATION_REF_ID, 200L);
        insertMiningLedger(53L, TENANT_ID, OTHER_CORPORATION_REF_ID, 900L);
        insertMiningLedger(54L, OTHER_TENANT_ID, CORPORATION_REF_ID, 800L);

        Map<String, Object> result = service.aggregate(principal, Map.of("dataset", "mining_ledger", "metrics", List
            .of(Map.of("function", "sum", "field", "quantity", "alias", "totalQuantity"))));

        assertThat(fieldValues(result, "totalQuantity").get(0).toString()).isEqualTo("300");
    }

    /**
     * 无分组聚合无论源数据为空还是存在多行，分页总数都固定为一条聚合结果。
     */
    @Test
    void shouldPageUngroupedAggregateAsSingleResultRow() {
        Map<String, Object> empty = service.aggregate(principal, Map.of("dataset", "mining_ledger", "metrics", List
            .of(Map.of("function", "count", "alias", "rowCount")), "page", 1, "size", 1));
        assertThat(empty).containsEntry("total", 1L).containsEntry("hasMore", false);
        assertThat(fieldValues(empty, "rowCount").get(0).toString()).isEqualTo("0");

        insertMiningLedger(55L, TENANT_ID, CORPORATION_REF_ID, 100L);
        insertMiningLedger(56L, TENANT_ID, CORPORATION_REF_ID, 200L);
        insertMiningLedger(57L, TENANT_ID, CORPORATION_REF_ID, 300L);
        Map<String, Object> firstPage = service.aggregate(principal, Map.of("dataset", "mining_ledger", "metrics", List
            .of(Map.of("function", "count", "alias", "rowCount")), "page", 1, "size", 1));
        Map<String, Object> secondPage = service.aggregate(principal, Map.of("dataset", "mining_ledger", "metrics", List
            .of(Map.of("function", "count", "alias", "rowCount")), "page", 2, "size", 1));

        assertThat(firstPage).containsEntry("total", 1L).containsEntry("hasMore", false);
        assertThat(fieldValues(firstPage, "rowCount").get(0).toString()).isEqualTo("3");
        assertThat(secondPage).containsEntry("total", 1L).containsEntry("hasMore", false);
        assertThat(fieldValues(secondPage, "rowCount")).isEmpty();
    }

    /**
     * 带 Z 与 +08:00 的同一瞬时必须命中相同 UTC 数据，输出统一为北京时间且不写回已读。
     */
    @Test
    void shouldTreatEquivalentOffsetsAsSameInstantWithoutMutation() {
        insertCharacter(61L, TENANT_ID, 16L, 106L, CORPORATION_ID, "时区角色", "ACTIVE");
        insertAuthorization(62L, TENANT_ID, 61L, 16L, "ACTIVE", "[\"esi-mail.read_mail.v1\"]");
        insertMail(63L, TENANT_ID, 16L, 61L, 106L, 7001L, "时区邮件", "时间正文", false);

        Map<String, Object> plusEight = service.query(principal, Map.of("dataset", "mail", "filters", List.of(Map
            .of("field", "sentAt", "operator", "eq", "value", "2026-10-25T12:00:00+08:00"))));
        Map<String, Object> utc = service.query(principal, Map.of("dataset", "mail", "filters", List.of(Map
            .of("field", "sentAt", "operator", "eq", "value", "2026-10-25T04:00:00Z"))));

        assertThat(subjects(plusEight)).containsExactly("时区邮件");
        assertThat(subjects(utc)).containsExactly("时区邮件");
        assertThat(fieldValues(utc, "sentAt")).containsExactly("2026-10-25T12:00:00+08:00");
        assertThat(jdbc.queryForObject("SELECT is_read FROM eve_game_mail WHERE id=63", Boolean.class)).isFalse();
    }

    private void createTemporaryTables() throws Exception {
        Set<String> tables = new LinkedHashSet<>();
        EveAgentDatasetRegistry.all().values().forEach(dataset -> {
            Matcher matcher = TABLE_PATTERN.matcher(dataset.from());
            while (matcher.find()) {
                tables.add(matcher.group());
            }
        });
        for (String table : tables) {
            if (realTableExists(table)) {
                Map<String, Object> createRow = jdbc
                    .queryForMap("SHOW CREATE TABLE `" + databaseName + "`.`" + table + "`");
                String createSql = createRow.values()
                    .stream()
                    .map(Object::toString)
                    .filter(value -> value.startsWith("CREATE TABLE"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("无法读取真实表结构: " + table));
                jdbc.execute(createSql.replaceFirst("CREATE TABLE", "CREATE TEMPORARY TABLE"));
            } else {
                String migration = MIGRATION_TABLES.get(table);
                assertThat(migration).as("缺少临时表迁移映射: %s", table).isNotNull();
                jdbc.execute(temporaryTableDdl(migration, table));
            }
        }
    }

    private boolean realTableExists(String table) {
        Integer count = jdbc
            .queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name=?", Integer.class, databaseName, table);
        return count != null && count > 0;
    }

    private static String temporaryTableDdl(String resourceName, String tableName) throws Exception {
        String sql = resource(resourceName);
        String marker = "CREATE TABLE `" + tableName + "`";
        int start = sql.indexOf(marker);
        assertThat(start).as("迁移中缺少表 %s", tableName).isGreaterThanOrEqualTo(0);
        int end = sql.indexOf(';', start);
        assertThat(end).isGreaterThan(start);
        return sql.substring(start, end).replaceFirst("CREATE TABLE", "CREATE TEMPORARY TABLE");
    }

    private void insertCharacter(long id,
                                 long tenantId,
                                 long userId,
                                 long characterId,
                                 long corporationId,
                                 String name,
                                 String status) {
        jdbc.update("""
            INSERT INTO eve_character
                (id, tenant_id, user_id, server, character_id, corporation_id, owner_hash, name, status,
                 is_primary, create_user, create_time, deleted)
            VALUES (?, ?, ?, 'serenity', ?, ?, ?, ?, ?, b'0', ?, '2026-10-25 04:00:00', 0)
            """, id, tenantId, userId, characterId, corporationId, "owner-" + id, name, status, userId);
    }

    private void insertAuthorization(long id,
                                     long tenantId,
                                     long characterRefId,
                                     long userId,
                                     String status,
                                     String scopes) {
        jdbc.update("""
            INSERT INTO eve_authorization
                (id, tenant_id, character_ref_id, user_id, server, scopes, expires_at, status,
                 last_verification_status, create_user, create_time, deleted)
            VALUES (?, ?, ?, ?, 'serenity', CAST(? AS JSON), '2026-11-25 04:00:00', ?, 'VERIFIED', ?,
                    '2026-10-25 04:00:00', 0)
            """, id, tenantId, characterRefId, userId, scopes, status, userId);
    }

    private void insertMail(long id,
                            long tenantId,
                            long userId,
                            long characterRefId,
                            long characterId,
                            long mailId,
                            String subject,
                            String body,
                            boolean read) {
        jdbc.update("""
            INSERT INTO eve_game_mail
                (id, tenant_id, user_id, character_ref_id, character_id, mail_id, subject, sent_at, is_read,
                 body, last_seen_at, create_user, create_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, '2026-10-25 04:00:00', ?, ?, '2026-10-25 04:01:00', ?,
                    '2026-10-25 04:01:00', 0)
            """, id, tenantId, userId, characterRefId, characterId, mailId, subject, read, body, userId);
    }

    private void insertNotification(long id,
                                    long tenantId,
                                    long userId,
                                    long characterRefId,
                                    long characterId,
                                    long notificationId,
                                    String content) {
        jdbc.update("""
            INSERT INTO eve_game_notification
                (id, tenant_id, user_id, character_ref_id, character_id, notification_id, is_read,
                 notification_type, category, content, sent_at, last_seen_at, create_user, create_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, 0, 'TestNotification', 'OTHER', ?, '2026-10-25 04:00:00',
                    '2026-10-25 04:01:00', ?, '2026-10-25 04:01:00', 0)
            """, id, tenantId, userId, characterRefId, characterId, notificationId, content, userId);
    }

    private void insertMiningLedger(long id, long tenantId, long corporationRefId, long quantity) {
        jdbc.update("""
            INSERT INTO eve_mining_ledger
                (id, tenant_id, corporation_ref_id, observer_id, character_id, recorded_corporation_id,
                 type_id, recorded_at, quantity, last_seen_at, create_user, create_time, deleted)
            VALUES (?, ?, ?, ?, 2, 3, 45492, '2026-10-25', ?, '2026-10-25 04:00:00', 1,
                    '2026-10-25 04:00:00', 0)
            """, id, tenantId, corporationRefId, id, quantity);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> fieldValues(Map<String, Object> page, String field) {
        return ((List<Map<String, Object>>)page.get("items")).stream().map(item -> item.get(field)).toList();
    }

    private static List<Object> subjects(Map<String, Object> page) {
        return fieldValues(page, "subject");
    }

    private static String resource(String name) throws Exception {
        try (var input = EveAgentQueryPersistenceIT.class.getClassLoader()
            .getResourceAsStream("db/changelog/mysql/eve/" + name)) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String property(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少集成测试配置: " + key);
        }
        return value;
    }
}
