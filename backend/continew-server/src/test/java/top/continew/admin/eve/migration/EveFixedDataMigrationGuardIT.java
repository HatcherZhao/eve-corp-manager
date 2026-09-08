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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 使用隔离临时库执行 EVE 固定数据迁移保护，验证空库、匹配数据和冲突数据三态。
 *
 * @author zhaoyuqing
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EveFixedDataMigrationGuardIT {

    private static final String PACKAGE_GUARD = "classpath:db/changelog/mysql/eve/002a_eve_tenant_package_guard.sql";
    private static final String PERMISSION_GUARD = "classpath:db/changelog/mysql/eve/002b_eve_capability_permission_guard.sql";

    private String host;
    private String port;
    private String username;
    private String password;
    private String schemaPrefix;

    /** 加载显式集成测试配置，并为本次进程生成唯一临时库前缀。 */
    @BeforeAll
    void setUp() {
        LocalIntegrationEnvironment.configureOrSkip();
        host = System.getProperty("DB_HOST");
        port = System.getProperty("DB_PORT");
        username = System.getProperty("DB_USER");
        password = System.getProperty("DB_PWD");
        schemaPrefix = "eve_guard_it_" + ProcessHandle.current().pid() + "_" + UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 8);
    }

    /** 两个保护变更集在目标表为空时都必须成功执行。 */
    @Test
    void shouldAllowEmptyTables() throws Exception {
        withSchema("empty", schema -> assertThatCode(() -> runGuards(schema)).doesNotThrowAnyException());
    }

    /** 固定 ID 和业务键对应内容完全一致时必须允许已有安装继续升级。 */
    @Test
    void shouldAllowExactlyMatchingRows() throws Exception {
        withSchema("matching", schema -> {
            insertMatchingPackage(schema);
            insertMatchingPermissions(schema);
            assertThatCode(() -> runGuards(schema)).doesNotThrowAnyException();
        });
    }

    /** 固定 ID 被其他业务数据占用时，两个保护变更集都必须 HALT。 */
    @Test
    void shouldHaltOnConflictingRows() throws Exception {
        withSchema("package_conflict", schema -> {
            execute(schema, """
                INSERT INTO tenant_package
                    (id, name, sort, menu_check_strictly, description, status, create_user, update_user, update_time, deleted)
                VALUES (20001, '冲突套餐', 1, b'1', 'EVE军团认领流程专用套餐', 1, 1, NULL, NULL, 0)
                """);
            assertThatThrownBy(() -> runGuard(schema, PACKAGE_GUARD)).isInstanceOf(UnexpectedLiquibaseException.class)
                .hasRootCauseInstanceOf(PreconditionFailedException.class);
        });
        withSchema("permission_conflict", schema -> {
            execute(schema, """
                INSERT INTO sys_menu
                    (id, title, parent_id, type, path, name, component, redirect, icon, is_external, is_cache,
                     is_hidden, permission, sort, status, create_user, update_user, update_time, deleted)
                VALUES
                    (20020, '冲突权限', 20010, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0',
                     'eve:assets:view', 20, 1, 1, NULL, NULL, 0)
                """);
            assertThatThrownBy(() -> runGuard(schema, PERMISSION_GUARD))
                .isInstanceOf(UnexpectedLiquibaseException.class)
                .hasRootCauseInstanceOf(PreconditionFailedException.class);
        });
    }

    /** 在独立临时库中执行场景，并在结束时精确删除该库。 */
    private void withSchema(String suffix, CheckedSchemaAction action) throws Exception {
        String schema = schemaPrefix + '_' + suffix;
        createSchema(schema);
        try {
            createGuardTables(schema);
            action.run(schema);
        } finally {
            dropSchema(schema);
        }
    }

    /** 创建仅包含保护 SQL 所需字段的临时表。 */
    private void createGuardTables(String schema) throws SQLException {
        execute(schema, """
            CREATE TABLE tenant_package (
                id BIGINT PRIMARY KEY,
                name VARCHAR(30) NOT NULL,
                sort INT NOT NULL,
                menu_check_strictly BIT(1),
                description VARCHAR(200),
                status TINYINT UNSIGNED NOT NULL,
                create_user BIGINT NOT NULL,
                update_user BIGINT,
                update_time DATETIME,
                deleted BIGINT NOT NULL
            )
            """);
        execute(schema, """
            CREATE TABLE sys_menu (
                id BIGINT PRIMARY KEY,
                title VARCHAR(30) NOT NULL,
                parent_id BIGINT NOT NULL,
                type TINYINT UNSIGNED NOT NULL,
                path VARCHAR(255),
                name VARCHAR(50),
                component VARCHAR(255),
                redirect VARCHAR(255),
                icon VARCHAR(50),
                is_external BIT(1) NOT NULL,
                is_cache BIT(1) NOT NULL,
                is_hidden BIT(1) NOT NULL,
                permission VARCHAR(100),
                sort INT NOT NULL,
                status TINYINT UNSIGNED NOT NULL,
                create_user BIGINT NOT NULL,
                update_user BIGINT,
                update_time DATETIME,
                deleted BIGINT NOT NULL
            )
            """);
    }

    /** 写入与 003 变更集完全一致的套餐固定数据。 */
    private void insertMatchingPackage(String schema) throws SQLException {
        execute(schema, """
            INSERT INTO tenant_package
                (id, name, sort, menu_check_strictly, description, status, create_user, update_user, update_time, deleted)
            VALUES (20001, 'EVE军团套餐', 1, b'1', 'EVE军团认领流程专用套餐', 1, 1, NULL, NULL, 0)
            """);
    }

    /** 写入与 004 变更集完全一致的五条权限固定数据。 */
    private void insertMatchingPermissions(String schema) throws SQLException {
        execute(schema, """
            INSERT INTO sys_menu
                (id, title, parent_id, type, path, name, component, redirect, icon, is_external, is_cache,
                 is_hidden, permission, sort, status, create_user, update_user, update_time, deleted)
            VALUES
                (20020, '查看军团资产', 20010, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0',
                 'eve:assets:view', 20, 1, 1, NULL, NULL, 0),
                (20021, '查看建筑设施', 20010, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0',
                 'eve:structures:view', 21, 1, 1, NULL, NULL, 0),
                (20022, '查看月矿计划', 20010, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0',
                 'eve:extractions:view', 22, 1, 1, NULL, NULL, 0),
                (20023, '查看采矿账本', 20010, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0',
                 'eve:mining:view', 23, 1, 1, NULL, NULL, 0),
                (20024, '查看军团人员', 20010, 3, NULL, NULL, NULL, NULL, NULL, b'0', b'0', b'0',
                 'eve:members:view', 24, 1, 1, NULL, NULL, 0)
            """);
    }

    /** 依次执行现有 002a 和 002b 保护变更集。 */
    private void runGuards(String schema) throws LiquibaseException {
        runGuard(schema, PACKAGE_GUARD);
        runGuard(schema, PERMISSION_GUARD);
    }

    /** 通过 SpringLiquibase 执行单个格式化 SQL 变更集。 */
    private void runGuard(String schema, String changeLog) throws LiquibaseException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(databaseUrl(schema), username, password);
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(changeLog);
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

    /** 在指定临时库执行固定测试 SQL。 */
    private void execute(String schema, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl(schema), username, password);
            Statement statement = connection.createStatement()) {
            statement.execute(sql);
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

    /** 允许场景动作向测试框架传播数据库或 Liquibase 异常。 */
    @FunctionalInterface
    private interface CheckedSchemaAction {
        void run(String schema) throws Exception;
    }
}
