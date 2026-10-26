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

package top.continew.admin.eve.agent;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import top.continew.admin.LocalIntegrationEnvironment;
import top.continew.admin.common.config.mybatis.MyBatisPlusMetaObjectHandler;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.agent.auth.EveAgentApplicationService;
import top.continew.admin.eve.agent.model.EveAgentCredentialResp;
import top.continew.admin.eve.mapper.EveAgentApplicationMapper;
import top.continew.admin.eve.mapper.EveAgentAuditMapper;
import top.continew.admin.eve.mapper.EveAgentGrantMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.model.EveMeContextResp;
import top.continew.admin.eve.service.EveContextService;
import top.continew.starter.core.exception.BusinessException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 外部 Agent 授权真实 MySQL 持久化集成测试。
 *
 * <p>测试仅在随机新建 schema 中执行完整 Liquibase，结束后精确删除，不连接或修改业务 schema。</p>
 *
 * @author zhaoyuqing
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EveAgentAuthorizationPersistenceIT {

    private static final String CHANGELOG = "classpath:db/changelog/db.changelog-master.yaml";
    private static final long TENANT_ID = 992001L;
    private static final long CORPORATION_ID = 992002L;
    private static final long USER_ID = 992003L;
    private static final long SECOND_TENANT_ID = 992011L;
    private static final long SECOND_CORPORATION_ID = 992012L;

    private String host;
    private String port;
    private String username;
    private String password;
    private String schema;
    private boolean schemaCreated;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private EveAgentApplicationService service;

    /** 创建随机空 schema、执行完整迁移并装配真实 MyBatis Mapper。 */
    @BeforeAll
    void setUp() throws Exception {
        LocalIntegrationEnvironment.configureOrSkip();
        host = property("DB_HOST");
        port = property("DB_PORT");
        username = property("DB_USER");
        password = property("DB_PWD");
        schema = "eve_agent_auth_it_" + ProcessHandle.current().pid() + "_" + UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 8);
        assertThat(schemaExists()).as("随机测试 schema 必须尚不存在").isFalse();
        createSchema();
        try {
            dataSource = new DriverManagerDataSource(databaseUrl(), username, password);
            runLiquibase();
            jdbc = new JdbcTemplate(dataSource);
            insertCorporation();
            insertSecondCorporation();
            service = applicationService();
            transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        } catch (Exception e) {
            dropSchema();
            throw e;
        }
    }

    /** 清理线程身份并精确删除本测试创建的 schema。 */
    @AfterAll
    void tearDown() throws Exception {
        UserContextHolder.clearContext();
        dropSchema();
    }

    /**
     * 验证凭据只以摘要落库，且无网页登录上下文时仍可认证、审计、轮换和撤销。
     */
    @Test
    void shouldPersistHashedCredentialAuditRotationAndRevocationWithoutLoginTenant() {
        EveAgentCredentialResp created = asManager(() -> inTransaction(() -> service
            .createApplication("真实 MySQL Agent", 30)));
        Long applicationId = created.application().id();
        String originalToken = created.token();

        Map<String, Object> stored = jdbc.queryForMap("""
            SELECT token_hash, token_prefix, status
            FROM eve_agent_application
            WHERE id = ? AND tenant_id = ? AND corporation_ref_id = ?
            """, applicationId, TENANT_ID, corporationRefId());
        assertThat(originalToken).startsWith("eve_agent_").hasSize(53);
        assertThat(stored.get("token_hash").toString()).matches("[0-9a-f]{64}").isNotEqualTo(originalToken);
        assertThat(stored.get("token_prefix").toString()).startsWith("eve_agent_").doesNotContain(originalToken);
        assertThat(stored.get("status")).isEqualTo("ACTIVE");
        assertThat(created.application().invitationCode()).matches("[A-Za-z0-9_-]{32}");
        assertThat(jdbc
            .queryForObject("SELECT COUNT(*) FROM eve_agent_grant WHERE application_id = ? " + "AND corporation_id = ? AND status = 'ACTIVE'", Long.class, applicationId, CORPORATION_ID))
            .isEqualTo(1L);
        assertThat(columnCount("eve_agent_application", "token")).isZero();
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM eve_agent_audit
            WHERE application_id = ? AND tool_name = 'system.application_created'
              AND application_name NOT LIKE ?
            """, Long.class, applicationId, "%" + originalToken + "%")).isEqualTo(1L);

        Long secondGrantId = asManager(SECOND_TENANT_ID, () -> inTransaction(() -> service.approveInvitation(created
            .application()
            .invitationCode(), true))).id();

        UserContextHolder.clearContext();
        EveAgentPrincipal unscoped = service.authenticate(originalToken);
        assertThat(unscoped.tenantId()).isNull();
        assertThat(service.listAuthorizedCorporations(unscoped)).extracting("corporationId")
            .containsExactly(CORPORATION_ID, SECOND_CORPORATION_ID);
        assertThatThrownBy(() -> service.resolveCorporation(unscoped, null))
            .hasMessage("该应用已授权多个军团，请明确提供 corporationId");
        EveAgentPrincipal principal = service.resolveCorporation(unscoped, CORPORATION_ID + "");
        assertThat(principal.tenantId()).isEqualTo(TENANT_ID);
        assertThat(principal.corporationId()).isEqualTo(CORPORATION_ID);

        inTransaction(() -> {
            service.recordCall(principal, "eve_query", true, 17L, 3);
            return null;
        });
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM eve_agent_audit
            WHERE tenant_id = ? AND application_id = ? AND actor_type = 'AGENT'
              AND actor_user_id IS NULL AND tool_name = 'eve_query'
              AND success = 1 AND duration_ms = 17 AND result_count = 3
            """, Long.class, TENANT_ID, applicationId)).isEqualTo(1L);
        assertThat(jdbc
            .queryForObject("SELECT last_used_at FROM eve_agent_application WHERE id = ?", LocalDateTime.class, applicationId))
            .isNotNull();

        asManager(SECOND_TENANT_ID, () -> inTransaction(() -> {
            service.revokeGrant(secondGrantId);
            return null;
        }));
        UserContextHolder.clearContext();
        assertThat(service.listAuthorizedCorporations(unscoped)).extracting("corporationId")
            .containsExactly(CORPORATION_ID);

        EveAgentCredentialResp rotated = asManager(() -> inTransaction(() -> service.rotateCredential(applicationId)));
        UserContextHolder.clearContext();
        assertThat(rotated.token()).startsWith("eve_agent_").isNotEqualTo(originalToken);
        assertThatThrownBy(() -> service.authenticate(originalToken)).isInstanceOf(BusinessException.class)
            .hasMessage("外部 Agent 凭据无效或已失效");
        assertThat(service.authenticate(rotated.token()).applicationId()).isEqualTo(applicationId);
        assertThat(jdbc
            .queryForObject("SELECT token_hash FROM eve_agent_application WHERE id = ?", String.class, applicationId))
            .isNotEqualTo(originalToken);

        asManager(() -> inTransaction(() -> {
            service.revoke(applicationId);
            return null;
        }));
        UserContextHolder.clearContext();
        assertThatThrownBy(() -> service.authenticate(rotated.token())).isInstanceOf(BusinessException.class)
            .hasMessage("外部 Agent 凭据无效或已失效");
        assertThat(jdbc
            .queryForObject("SELECT status FROM eve_agent_application WHERE id = ?", String.class, applicationId))
            .isEqualTo("REVOKED");
    }

    /** 完整迁移关闭登录图形验证码，并将重置默认值同步为关闭。 */
    @Test
    void shouldDisableLoginCaptchaAfterFullMigration() {
        assertThat(jdbc.queryForMap("""
            SELECT value, default_value FROM sys_option WHERE code = 'LOGIN_CAPTCHA_ENABLED'
            """)).containsEntry("value", "0").containsEntry("default_value", "0");
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID = '055-disable-login-captcha'
            """, Long.class)).isEqualTo(1L);
    }

    /** Agent 菜单必须位于已有 Layout 下，直达与侧栏进入时都保留全站导航。 */
    @Test
    void shouldPlaceAgentMenuInsideCorporationLayout() {
        assertThat(jdbc.queryForMap("""
            SELECT child.parent_id, parent.component FROM sys_menu child
            JOIN sys_menu parent ON child.parent_id = parent.id
            WHERE child.id = 20201 AND child.deleted = 0 AND parent.deleted = 0
            """)).containsEntry("parent_id", 20200L).containsEntry("component", "Layout");
        assertThat(jdbc.queryForObject("SELECT permission FROM sys_menu WHERE id = 20201", String.class))
            .isEqualTo("eve:agents:manage");
    }

    /** 使用真实 Mapper 与生产元对象处理器构造授权服务。 */
    private EveAgentApplicationService applicationService() throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        GlobalConfig globalConfig = GlobalConfigUtils.defaults();
        globalConfig.setMetaObjectHandler(new MyBatisPlusMetaObjectHandler());
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        configuration.addMapper(EveAgentApplicationMapper.class);
        configuration.addMapper(EveAgentAuditMapper.class);
        configuration.addMapper(EveAgentGrantMapper.class);
        configuration.addMapper(EveCorporationMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setGlobalConfig(globalConfig);
        SqlSessionTemplate session = new SqlSessionTemplate(factory.getObject());
        EveContextService contexts = mock(EveContextService.class);
        when(contexts.getCurrentContext()).thenAnswer(invocation -> {
            UserContext current = UserContextHolder.getContext();
            boolean second = current != null && SECOND_TENANT_ID == current.getTenantId();
            return new EveMeContextResp(second
                ? SECOND_TENANT_ID
                : TENANT_ID, null, new EveMeContextResp.CorporationInfo(second
                    ? SECOND_CORPORATION_ID
                    : CORPORATION_ID, second ? "第二测试军团" : "真实测试军团", second
                        ? "IT2"
                        : "IT"), "corp_admin", null, null, null, null);
        });
        return new EveAgentApplicationService(session.getMapper(EveAgentApplicationMapper.class), session
            .getMapper(EveAgentAuditMapper.class), session.getMapper(EveAgentGrantMapper.class), session
                .getMapper(EveCorporationMapper.class), contexts);
    }

    /** 仅在当前线程设置合成管理身份，避免创建 Sa-Token 登录会话。 */
    private <T> T asManager(CheckedSupplier<T> action) {
        return asManager(TENANT_ID, action);
    }

    /** 以指定测试军团管理者身份执行动作。 */
    private <T> T asManager(long tenantId, CheckedSupplier<T> action) {
        UserContext context = new UserContext();
        context.setId(USER_ID);
        context.setTenantId(tenantId);
        context.setRoleCodes(Set.of("corp_admin"));
        context.setPermissions(Set.of("eve:agents:manage"));
        UserContextHolder.setContext(context, false);
        try {
            return action.get();
        } finally {
            UserContextHolder.clearContext();
        }
    }

    /** 在 Spring JDBC 事务中执行单个授权状态变化。 */
    private <T> T inTransaction(CheckedSupplier<T> action) {
        return transactions.execute(status -> action.get());
    }

    /** 写入测试军团绑定，并返回其数据库主键。 */
    private void insertCorporation() {
        jdbc.update("""
            INSERT INTO eve_corporation
                (tenant_id, server, corporation_id, name, ticker, status, create_user, create_time, deleted)
            VALUES (?, 'serenity', ?, '真实测试军团', 'IT', 'ACTIVE', ?, NOW(), 0)
            """, TENANT_ID, CORPORATION_ID, USER_ID);
    }

    /** 写入第二个测试军团，用于验证同一密钥的跨军团授权与独立撤销。 */
    private void insertSecondCorporation() {
        jdbc.update("""
            INSERT INTO eve_corporation
                (tenant_id, server, corporation_id, name, ticker, status, create_user, create_time, deleted)
            VALUES (?, 'serenity', ?, '第二测试军团', 'IT2', 'ACTIVE', ?, NOW(), 0)
            """, SECOND_TENANT_ID, SECOND_CORPORATION_ID, USER_ID);
    }

    /** 查询测试军团绑定主键。 */
    private Long corporationRefId() {
        return jdbc
            .queryForObject("SELECT id FROM eve_corporation WHERE tenant_id = ? AND corporation_id = ?", Long.class, TENANT_ID, CORPORATION_ID);
    }

    /** 查询指定表中精确列名数量。 */
    private long columnCount(String table, String column) {
        return jdbc.queryForObject("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
            """, Long.class, table, column);
    }

    /** 在随机新 schema 上执行完整项目迁移。 */
    private void runLiquibase() throws Exception {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(CHANGELOG);
        liquibase.afterPropertiesSet();
    }

    /** 创建专属测试 schema。 */
    private void createSchema() throws SQLException {
        try (Connection connection = DriverManager.getConnection(serverUrl(), username, password);
            Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            schemaCreated = true;
        }
    }

    /** 精确删除专属测试 schema。 */
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

    /** 确认随机 schema 名未被已有数据库占用。 */
    private boolean schemaExists() throws SQLException {
        try (Connection connection = DriverManager.getConnection(serverUrl(), username, password);
            Statement statement = connection.createStatement(); ResultSet result = statement
                .executeQuery("SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '" + schema + "'")) {
            return result.next();
        }
    }

    /** MySQL 管理连接不绑定任何业务 schema。 */
    private String serverUrl() {
        return "jdbc:mysql://" + host + ':' + port + "/?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    }

    /** 仅连接当前随机测试 schema。 */
    private String databaseUrl() {
        return "jdbc:mysql://" + host + ':' + port + '/' + schema + "?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true";
    }

    /** 读取集成测试加载后的系统属性。 */
    private static String property(String key) {
        return System.getProperty(key);
    }

    /** 允许测试动作传播运行时异常。 */
    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get();
    }
}
