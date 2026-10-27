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

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import top.continew.admin.LocalIntegrationEnvironment;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用隔离 MySQL 验证历史游戏通知分类迁移不会改变通知正文和归属。
 *
 * @author zhaoyuqing
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EveNotificationCategoryMigrationIT {

    private static final String CHANGELOG = "classpath:db/changelog/mysql/eve/059_notification_categories.sql";

    private JdbcTemplate server;
    private JdbcTemplate jdbc;
    private DriverManagerDataSource dataSource;
    private String schema;

    /** 创建测试专属数据库和足以执行分类迁移的通知表。 */
    @BeforeAll
    void setUp() {
        LocalIntegrationEnvironment.configureOrSkip();
        String serverUrl = "jdbc:mysql://" + property("DB_HOST") + ':' + property("DB_PORT") + '/';
        String options = "?useSSL=false&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true";
        schema = "eve_notification_category_it_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource adminDataSource = new DriverManagerDataSource(serverUrl + options, property("DB_USER"), property("DB_PWD"));
        server = new JdbcTemplate(adminDataSource);
        server.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        dataSource = new DriverManagerDataSource(serverUrl + schema + options, property("DB_USER"), property("DB_PWD"));
        jdbc = new JdbcTemplate(dataSource);
        createFixture();
    }

    /** 删除本测试创建的精确随机数据库。 */
    @AfterAll
    void tearDown() {
        if (server != null && schema != null) {
            server.execute("DROP DATABASE IF EXISTS `" + schema + "`");
        }
    }

    /** 迁移应修正组织通知分类，重复执行安全，并完整保留归属、正文、个人类和已删除记录。 */
    @Test
    void shouldReclassifyOnlyActiveOrganizationNotifications() throws Exception {
        List<NotificationSnapshot> before = snapshots();

        runMigration();
        runMigration();

        assertThat(category(1L)).isEqualTo("CORPORATION_MEMBER");
        assertThat(category(2L)).isEqualTo("CORPORATION_MEMBER");
        assertThat(category(3L)).isEqualTo("CORPORATION_MEMBER");
        assertThat(category(4L)).isEqualTo("STRUCTURE_ASSET_SAFETY");
        assertThat(category(5L)).isEqualTo("STRUCTURE_ASSET_SAFETY");
        assertThat(category(6L)).isEqualTo("STRUCTURE_ASSET_SAFETY");
        assertThat(category(7L)).isEqualTo("WAR_SOVEREIGNTY");
        assertThat(category(8L)).isEqualTo("WAR_SOVEREIGNTY");
        assertThat(category(9L)).isEqualTo("OTHER");
        assertThat(category(10L)).isEqualTo("OTHER");
        assertThat(category(11L)).isEqualTo("OTHER");

        List<NotificationSnapshot> after = snapshots();
        assertThat(after).usingRecursiveFieldByFieldElementComparatorIgnoringFields("category")
            .containsExactlyElementsOf(before);
    }

    /** 建立覆盖全部修正类型、个人类型、未知类型和逻辑删除记录的测试数据。 */
    private void createFixture() {
        jdbc.execute("""
            CREATE TABLE eve_game_notification (
                id BIGINT PRIMARY KEY,
                tenant_id BIGINT NOT NULL,
                user_id BIGINT NOT NULL,
                character_ref_id BIGINT NOT NULL,
                character_id BIGINT NOT NULL,
                notification_id BIGINT NOT NULL,
                notification_type VARCHAR(128),
                category VARCHAR(40) NOT NULL,
                content MEDIUMTEXT,
                deleted TINYINT NOT NULL DEFAULT 0
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        insert(1L, " CharAppAcceptMsg ", 0L);
        insert(2L, "charappwithdrawmsg", 0L);
        insert(3L, "CharTerminationMsg", 0L);
        insert(4L, "OwnershipTransferred", 0L);
        insert(5L, "unknown notification type (6040)", 0L);
        insert(6L, "UNKNOWN NOTIFICATION TYPE (6041)", 0L);
        insert(7L, "EntosisCaptureStarted", 0L);
        insert(8L, "AcceptedSurrender", 0L);
        insert(9L, "NPCStandingsLost", 0L);
        insert(10L, "StructureItemsDelivered", 0L);
        insert(11L, "CharAppAcceptMsg", 11L);
    }

    /** 插入带固定归属和正文的通知，便于证明迁移仅触达分类列。 */
    private void insert(Long id, String type, Long deleted) {
        jdbc.update("""
            INSERT INTO eve_game_notification
            (id, tenant_id, user_id, character_ref_id, character_id, notification_id,
             notification_type, category, content, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'OTHER', ?, ?)
            """, id, 100L + id, 200L + id, 300L + id, 400L + id, 500L + id, type, "正文-" + id, deleted);
    }

    /** 通过真实 Liquibase 执行待验证的格式化 SQL。 */
    private void runMigration() throws Exception {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(CHANGELOG);
        liquibase.afterPropertiesSet();
    }

    /** 读取当前分类。 */
    private String category(Long id) {
        return jdbc.queryForObject("SELECT category FROM eve_game_notification WHERE id = ?", String.class, id);
    }

    /** 读取所有需要保持不变的通知字段及分类。 */
    private List<NotificationSnapshot> snapshots() {
        return jdbc.query("""
            SELECT id, tenant_id, user_id, character_ref_id, character_id, notification_id,
                   notification_type, category, content, deleted
            FROM eve_game_notification ORDER BY id
            """, (resultSet, rowNumber) -> new NotificationSnapshot(resultSet.getLong("id"), resultSet
            .getLong("tenant_id"), resultSet.getLong("user_id"), resultSet.getLong("character_ref_id"), resultSet
                .getLong("character_id"), resultSet.getLong("notification_id"), resultSet
                    .getString("notification_type"), resultSet.getString("category"), resultSet
                        .getString("content"), resultSet.getLong("deleted")));
    }

    /** 读取集成测试 JVM 属性。 */
    private static String property(String name) {
        return System.getProperty(name);
    }

    /** 通知迁移前后快照。 */
    private record NotificationSnapshot(Long id, Long tenantId, Long userId, Long characterRefId, Long characterId,
                                        Long notificationId, String notificationType, String category, String content,
                                        Long deleted) {
    }
}
