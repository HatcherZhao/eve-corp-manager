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

package top.continew.admin.eve.migration;

import liquibase.exception.LiquibaseException;
import liquibase.exception.PreconditionFailedException;
import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import top.continew.admin.LocalIntegrationEnvironment;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 使用真实 MySQL 验证派生角色权限清理和用户主角色唯一约束。
 *
 * @author zhaoyuqing
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EveRoleBindingHardeningMigrationIT {

    private static final String CHANGELOG = "classpath:db/changelog/mysql/eve/005_role_binding_hardening.sql";

    private String host;
    private String port;
    private String username;
    private String password;
    private String schemaPrefix;

    /** 加载显式集成测试配置，并生成仅供本测试使用的临时库前缀。 */
    @BeforeAll
    void setUp() {
        LocalIntegrationEnvironment.configureOrSkip();
        host = System.getProperty("DB_HOST");
        port = System.getProperty("DB_PORT");
        username = System.getProperty("DB_USER");
        password = System.getProperty("DB_PWD");
        schemaPrefix = "eve_binding_it_" + ProcessHandle.current().pid() + "_" + UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 8);
    }

    /** 空表首次迁移必须成功建立生成列和唯一索引。 */
    @Test
    void shouldMigrateEmptyTables() throws Exception {
        withSchema("empty", schema -> {
            assertThatCode(() -> runMigration(schema)).doesNotThrowAnyException();
            assertThat(queryLong(schema, """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'eve_character'
                  AND column_name = 'active_primary_user_id'
                """)).isEqualTo(1);
            assertThat(queryLong(schema, """
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'eve_character'
                  AND index_name = 'uk_eve_character_tenant_primary_user'
                  AND non_unique = 0
                """)).isEqualTo(2);
        });
    }

    /** 无重复主角色时应清理派生角色业务权限，保留独立功能角色和套餐菜单。 */
    @Test
    void shouldCleanDerivedRolePermissionsAndEnforceUniquePrimary() throws Exception {
        withSchema("matching", schema -> {
            insertMatchingData(schema);
            assertThatCode(() -> runMigration(schema)).doesNotThrowAnyException();

            assertThat(queryLong(schema, """
                SELECT COUNT(*) FROM sys_role_menu
                WHERE role_id IN (1, 2, 3) AND menu_id BETWEEN 20020 AND 20024
                """)).isZero();
            assertThat(queryLong(schema, "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 4 AND menu_id = 20020"))
                .isEqualTo(1);
            assertThat(queryLong(schema, "SELECT COUNT(*) FROM tenant_package_menu WHERE package_id = 20001"))
                .isEqualTo(5);
            assertThatThrownBy(() -> execute(schema, """
                INSERT INTO eve_character (tenant_id, user_id, is_primary, deleted)
                VALUES (10, 20, b'1', 0)
                """)).isInstanceOf(SQLException.class).hasMessageContaining("Duplicate entry");
            assertThatCode(() -> execute(schema, """
                INSERT INTO eve_character (tenant_id, user_id, is_primary, deleted)
                VALUES (10, 20, b'0', 0), (10, 20, b'1', 99)
                """)).doesNotThrowAnyException();
        });
    }

    /** 存量存在同租户同用户多个未删除主角色时必须在 DDL 前 HALT。 */
    @Test
    void shouldHaltWhenExistingPrimaryCharactersConflict() throws Exception {
        withSchema("conflict", schema -> {
            execute(schema, """
                INSERT INTO eve_character (tenant_id, user_id, is_primary, deleted)
                VALUES (10, 20, b'1', 0), (10, 20, b'1', 0)
                """);

            assertThatThrownBy(() -> runMigration(schema)).isInstanceOf(UnexpectedLiquibaseException.class)
                .hasRootCauseInstanceOf(PreconditionFailedException.class);
            assertThat(queryLong(schema, """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'eve_character'
                  AND column_name = 'active_primary_user_id'
                """)).isZero();
        });
    }

    /** 创建迁移所需的最小真实 MySQL 表结构。 */
    private void createTables(String schema) throws SQLException {
        execute(schema, """
            CREATE TABLE sys_role (
                id BIGINT PRIMARY KEY,
                tenant_id BIGINT NOT NULL,
                code VARCHAR(30) NOT NULL,
                deleted BIGINT NOT NULL DEFAULT 0
            ) ENGINE=InnoDB
            """);
        execute(schema, """
            CREATE TABLE sys_role_menu (
                role_id BIGINT NOT NULL,
                menu_id BIGINT NOT NULL,
                tenant_id BIGINT NOT NULL,
                PRIMARY KEY (role_id, menu_id)
            ) ENGINE=InnoDB
            """);
        execute(schema, """
            CREATE TABLE tenant_package_menu (
                package_id BIGINT NOT NULL,
                menu_id BIGINT NOT NULL,
                PRIMARY KEY (package_id, menu_id)
            ) ENGINE=InnoDB
            """);
        execute(schema, """
            CREATE TABLE eve_character (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                tenant_id BIGINT NOT NULL,
                user_id BIGINT NOT NULL,
                is_primary BIT(1) NOT NULL DEFAULT b'0',
                deleted BIGINT NOT NULL DEFAULT 0
            ) ENGINE=InnoDB
            """);
    }

    /** 写入可安全迁移的派生角色、独立功能角色、套餐菜单和角色绑定数据。 */
    private void insertMatchingData(String schema) throws SQLException {
        execute(schema, """
            INSERT INTO sys_role (id, tenant_id, code, deleted) VALUES
                (1, 10, 'corp_owner', 0),
                (2, 10, 'corp_admin', 0),
                (3, 10, 'corp_member', 0),
                (4, 10, 'asset_viewer', 0)
            """);
        execute(schema, """
            INSERT INTO sys_role_menu (role_id, menu_id, tenant_id) VALUES
                (1, 20020, 10), (1, 20021, 10),
                (2, 20022, 10), (2, 20023, 10),
                (3, 20024, 10), (3, 20011, 10),
                (4, 20020, 10)
            """);
        execute(schema, """
            INSERT INTO tenant_package_menu (package_id, menu_id) VALUES
                (20001, 20020), (20001, 20021), (20001, 20022), (20001, 20023), (20001, 20024)
            """);
        execute(schema, """
            INSERT INTO eve_character (tenant_id, user_id, is_primary, deleted) VALUES
                (10, 20, b'1', 0),
                (10, 20, b'0', 0),
                (10, 20, b'1', 100),
                (10, 21, b'1', 0),
                (11, 20, b'1', 0)
            """);
    }

    /** 在独立临时库中执行场景，并在结束后精确删除该库。 */
    private void withSchema(String suffix, CheckedSchemaAction action) throws Exception {
        String schema = schemaPrefix + '_' + suffix;
        createSchema(schema);
        try {
            createTables(schema);
            action.run(schema);
        } finally {
            dropSchema(schema);
        }
    }

    /** 通过 SpringLiquibase 执行纠正变更集。 */
    private void runMigration(String schema) throws LiquibaseException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(databaseUrl(schema), username, password);
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(CHANGELOG);
        liquibase.afterPropertiesSet();
    }

    /** 创建名称随机且受控的临时数据库。 */
    private void createSchema(String schema) throws SQLException {
        try (Connection connection = DriverManager.getConnection(serverUrl(), username, password);
            Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    /** 精确删除当前场景创建的临时数据库。 */
    private void dropSchema(String schema) throws SQLException {
        try (Connection connection = DriverManager.getConnection(serverUrl(), username, password);
            Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
        }
    }

    /** 在指定临时库执行 SQL。 */
    private void execute(String schema, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl(schema), username, password);
            Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    /** 查询单个计数值。 */
    private long queryLong(String schema, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl(schema), username, password);
            Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    /** 构建不绑定业务库的 MySQL 管理连接。 */
    private String serverUrl() {
        return "jdbc:mysql://" + host + ':' + port + "/?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    }

    /** 构建仅指向当前随机临时库的 MySQL 连接。 */
    private String databaseUrl(String schema) {
        return "jdbc:mysql://" + host + ':' + port + '/' + schema + "?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true";
    }

    /** 允许场景动作传播数据库或 Liquibase 异常。 */
    @FunctionalInterface
    private interface CheckedSchemaAction {
        void run(String schema) throws Exception;
    }
}
