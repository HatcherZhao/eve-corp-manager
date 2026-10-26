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

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import top.continew.admin.LocalIntegrationEnvironment;
import top.continew.admin.eve.agent.EveAgentPrincipal;
import top.continew.admin.eve.agent.auth.EveAgentApplicationService;
import top.continew.admin.eve.mapper.EveAgentApplicationMapper;
import top.continew.admin.eve.mapper.EveAgentAuditMapper;
import top.continew.admin.eve.mapper.EveAgentGrantMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.service.EveContextService;
import top.continew.starter.core.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 已上线单军团 Agent 应用升级到多军团授权模型的真实 MySQL 回归测试。
 *
 * <p>测试只在随机新建 schema 中构造 057 迁移所需旧表，结束后精确删除。</p>
 *
 * @author zhaoyuqing
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EveAgentMultiCorporationUpgradeIT {

    private static final String CHANGELOG = "classpath:db/changelog/mysql/eve/057_agent_multi_corporation_grants.sql";
    private static final String ACTIVE_TOKEN = "eve_agent_" + "A".repeat(43);
    private static final String REVOKED_TOKEN = "eve_agent_" + "R".repeat(43);
    private static final String EXPIRED_TOKEN = "eve_agent_" + "E".repeat(43);
    private static final String DELETED_TOKEN = "eve_agent_" + "D".repeat(43);

    private String host;
    private String port;
    private String username;
    private String password;
    private String schema;
    private boolean schemaCreated;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    /** 创建隔离 schema 并写入 057 之前各生命周期的旧应用。 */
    @BeforeAll
    void setUp() throws Exception {
        LocalIntegrationEnvironment.configureOrSkip();
        host = property("DB_HOST");
        port = property("DB_PORT");
        username = property("DB_USER");
        password = property("DB_PWD");
        schema = "eve_agent_upgrade_it_" + ProcessHandle.current().pid() + "_" + UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 8);
        assertThat(schemaExists()).isFalse();
        createSchema();
        try {
            dataSource = new DriverManagerDataSource(databaseUrl(), username, password);
            jdbc = new JdbcTemplate(dataSource);
            createLegacyApplicationTable();
            createLegacyCorporation();
            insertLegacyApplications();
        } catch (Exception e) {
            dropSchema();
            throw e;
        }
    }

    /** 删除本测试创建的随机 schema。 */
    @AfterAll
    void tearDown() throws Exception {
        dropSchema();
    }

    /** 057 必须保留旧密钥并只为有效应用迁移原军团授权。 */
    @Test
    void shouldUpgradeLegacyApplicationsWithoutRotatingTokenOrExpandingCorporationScope() throws Exception {
        String activeHash = hash(ACTIVE_TOKEN);
        String revokedHash = hash(REVOKED_TOKEN);

        runMigration();

        Map<String, Object> active = jdbc.queryForMap("""
            SELECT token_hash, invitation_code FROM eve_agent_application WHERE id = 1001
            """);
        Map<String, Object> revoked = jdbc.queryForMap("""
            SELECT token_hash, invitation_code FROM eve_agent_application WHERE id = 1002
            """);
        assertThat(active.get("token_hash")).isEqualTo(activeHash);
        assertThat(revoked.get("token_hash")).isEqualTo(revokedHash);
        assertThat(active.get("invitation_code").toString()).matches("[0-9a-f]{32}")
            .doesNotContain(ACTIVE_TOKEN)
            .isNotEqualTo(activeHash);
        assertThat(revoked.get("invitation_code").toString()).matches("[0-9a-f]{32}")
            .doesNotContain(REVOKED_TOKEN)
            .isNotEqualTo(revokedHash);

        assertThat(jdbc.queryForList("""
            SELECT application_id, tenant_id, corporation_ref_id, corporation_id, status
            FROM eve_agent_grant ORDER BY application_id
            """)).singleElement()
            .satisfies(grant -> assertThat(grant).containsEntry("application_id", 1001L)
                .containsEntry("tenant_id", 2001L)
                .containsEntry("corporation_ref_id", 3001L)
                .containsEntry("corporation_id", 4001L)
                .containsEntry("status", "ACTIVE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eve_agent_grant WHERE corporation_id <> 4001", Long.class))
            .isZero();

        EveAgentApplicationService service = applicationService();
        EveAgentPrincipal identity = service.authenticate(ACTIVE_TOKEN);
        assertThat(identity.applicationId()).isEqualTo(1001L);
        EveAgentPrincipal scope = service.resolveCorporation(identity, null);
        assertThat(scope.tenantId()).isEqualTo(2001L);
        assertThat(scope.corporationRefId()).isEqualTo(3001L);
        assertThat(scope.corporationId()).isEqualTo(4001L);
        for (String invalidToken : new String[] {REVOKED_TOKEN, EXPIRED_TOKEN, DELETED_TOKEN}) {
            assertThatThrownBy(() -> service.authenticate(invalidToken)).isInstanceOf(BusinessException.class)
                .hasMessage("外部 Agent 凭据无效或已失效");
        }
    }

    /** 创建与 054 上线版本一致且仅包含 057 所需字段的旧应用表。 */
    private void createLegacyApplicationTable() {
        jdbc.execute("""
            CREATE TABLE eve_agent_application
            (
                id bigint NOT NULL,
                tenant_id bigint NOT NULL,
                corporation_ref_id bigint NOT NULL,
                corporation_id bigint NOT NULL,
                name varchar(80) NOT NULL,
                token_hash char(64) NOT NULL,
                status varchar(16) NOT NULL,
                expires_at datetime NOT NULL,
                create_user bigint DEFAULT NULL,
                create_time datetime NOT NULL,
                deleted bigint NOT NULL DEFAULT 0,
                PRIMARY KEY (id),
                UNIQUE KEY uk_legacy_agent_token (token_hash)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
    }

    /** 构造旧应用所属军团，使升级后认证与范围解析使用真实 Mapper。 */
    private void createLegacyCorporation() {
        jdbc.execute("""
            CREATE TABLE eve_corporation
            (
                id bigint NOT NULL PRIMARY KEY,
                tenant_id bigint NOT NULL,
                corporation_id bigint NOT NULL,
                name varchar(80) NOT NULL,
                ticker varchar(16) NOT NULL,
                status varchar(16) NOT NULL,
                deleted bigint NOT NULL DEFAULT 0
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        jdbc.update("""
            INSERT INTO eve_corporation (id, tenant_id, corporation_id, name, ticker, status)
            VALUES (3001, 2001, 4001, '合成升级测试军团', 'TEST', 'ACTIVE')
            """);
    }

    /** 写入有效、已撤销、已过期和已删除旧应用，验证迁移不会复活失效凭据。 */
    private void insertLegacyApplications() {
        jdbc.update("""
            INSERT INTO eve_agent_application
                (id, tenant_id, corporation_ref_id, corporation_id, name, token_hash, status,
                 expires_at, create_user, create_time, deleted)
            VALUES
                (1001, 2001, 3001, 4001, '旧版有效应用', ?, 'ACTIVE',
                 DATE_ADD(UTC_TIMESTAMP(), INTERVAL 30 DAY), 5001, NOW(), 0),
                (1002, 2002, 3002, 4002, '旧版已撤销应用', ?, 'REVOKED',
                 DATE_ADD(UTC_TIMESTAMP(), INTERVAL 30 DAY), 5002, NOW(), 0),
                (1003, 2003, 3003, 4003, '旧版已过期应用', ?, 'ACTIVE',
                 DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY), 5003, NOW(), 0),
                (1004, 2004, 3004, 4004, '旧版已删除应用', ?, 'ACTIVE',
                 DATE_ADD(UTC_TIMESTAMP(), INTERVAL 30 DAY), 5004, NOW(), 1004)
            """, hash(ACTIVE_TOKEN), hash(REVOKED_TOKEN), hash(EXPIRED_TOKEN), hash(DELETED_TOKEN));
    }

    /** 装配真实应用、授权和军团 Mapper，仅替换本场景不使用的管理与审计依赖。 */
    private EveAgentApplicationService applicationService() throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(EveAgentApplicationMapper.class);
        configuration.addMapper(EveAgentGrantMapper.class);
        configuration.addMapper(EveCorporationMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        SqlSessionTemplate session = new SqlSessionTemplate(factory.getObject());
        return new EveAgentApplicationService(session
            .getMapper(EveAgentApplicationMapper.class), mock(EveAgentAuditMapper.class), session
                .getMapper(EveAgentGrantMapper.class), session
                    .getMapper(EveCorporationMapper.class), mock(EveContextService.class));
    }

    /** 仅执行待验证的 057 增量迁移。 */
    private void runMigration() throws Exception {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(CHANGELOG);
        liquibase.afterPropertiesSet();
    }

    /** 计算与生产应用一致的 SHA-256 token 摘要。 */
    private static String hash(String token) {
        try {
            return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 创建测试专属 schema。 */
    private void createSchema() throws SQLException {
        try (Connection connection = DriverManager.getConnection(serverUrl(), username, password);
            Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            schemaCreated = true;
        }
    }

    /** 精确删除测试专属 schema。 */
    private void dropSchema() throws SQLException {
        if (!schemaCreated) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(serverUrl(), username, password);
            Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
            schemaCreated = false;
        }
    }

    /** 确认随机 schema 尚不存在。 */
    private boolean schemaExists() throws SQLException {
        try (Connection connection = DriverManager.getConnection(serverUrl(), username, password);
            Statement statement = connection.createStatement(); ResultSet result = statement
                .executeQuery("SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '" + schema + "'")) {
            return result.next();
        }
    }

    /** 构造不绑定业务 schema 的管理连接。 */
    private String serverUrl() {
        return "jdbc:mysql://" + host + ':' + port + "/?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    }

    /** 构造仅连接随机测试 schema 的数据源地址。 */
    private String databaseUrl() {
        return "jdbc:mysql://" + host + ':' + port + '/' + schema + "?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true";
    }

    /** 读取集成测试环境配置。 */
    private static String property(String key) {
        return System.getProperty(key);
    }
}
