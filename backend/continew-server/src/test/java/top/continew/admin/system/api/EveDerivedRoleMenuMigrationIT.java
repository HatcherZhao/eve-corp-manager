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

package top.continew.admin.system.api;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import liquibase.integration.spring.SpringLiquibase;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import top.continew.admin.LocalIntegrationEnvironment;
import top.continew.admin.system.mapper.MenuMapper;
import top.continew.admin.system.mapper.RoleMenuMapper;
import top.continew.admin.system.model.entity.MenuDO;
import top.continew.starter.data.handler.CompositeBaseEnumTypeHandler;
import top.continew.starter.extension.tenant.context.TenantContext;
import top.continew.starter.extension.tenant.context.TenantContextHolder;
import top.continew.starter.extension.tenant.enums.TenantIsolationLevel;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用隔离 MySQL 和真实 Mapper 验证派生角色全局策略、套餐约束及租户边界。
 *
 * @author zhaoyuqing
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EveDerivedRoleMenuMigrationIT {

    private static final String CHANGELOG = "classpath:db/changelog/mysql/eve/058_derived_role_policy.sql";
    private static final long TENANT_A = 10L;
    private static final long TENANT_B = 20L;
    private static final long TENANT_DISABLED = 30L;
    private static final long PACKAGE_ENABLED = 500L;
    private static final long PACKAGE_DISABLED = 501L;
    private static final long FEATURE_MENU = 20202L;
    private static final Set<String> TENANT_IGNORE_TABLES = Set
        .of("tenant", "tenant_package", "tenant_package_menu", "sys_menu", "eve_derived_role_menu");

    private DriverManagerDataSource dataSource;
    private JdbcTemplate server;
    private JdbcTemplate jdbc;
    private MenuMapper menuMapper;
    private RoleMenuMapper roleMenuMapper;
    private String schema;

    /** 创建测试专属数据库、执行策略迁移并装配真实租户拦截器与 XML Mapper。 */
    @BeforeAll
    void setUp() throws Exception {
        LocalIntegrationEnvironment.configureOrSkip();
        String serverUrl = "jdbc:mysql://" + property("DB_HOST") + ':' + property("DB_PORT") + '/';
        String options = "?useSSL=false&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true";
        schema = "eve_role_policy_it_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource adminDataSource = new DriverManagerDataSource(serverUrl + options, property("DB_USER"), property("DB_PWD"));
        server = new JdbcTemplate(adminDataSource);
        server.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        dataSource = new DriverManagerDataSource(serverUrl + schema + options, property("DB_USER"), property("DB_PWD"));
        jdbc = new JdbcTemplate(dataSource);
        createFixture();
        runMigration();
        configureMappers();
    }

    /** 删除本测试创建的精确随机数据库。 */
    @AfterAll
    void tearDown() {
        TenantContextHolder.clear();
        if (server != null && schema != null) {
            server.execute("DROP DATABASE IF EXISTS `" + schema + "`");
        }
    }

    /**
     * 新旧军团实时读取同一策略；模板、套餐和菜单状态变更立即生效，物化脏数据与跨租户绑定均无效。
     */
    @Test
    void shouldResolveEffectivePermissionsFromOnePolicyForEveryTenant() {
        withTenant(TENANT_A, () -> {
            assertThat(physicalMenus(102L)).hasSize(8);
            assertThat(menuMapper.selectListByRoleId(102L)).hasSize(69)
                .extracting(MenuDO::getId)
                .contains(20000L, 20010L, 20011L);
            assertThat(menuMapper.selectListByRoleId(103L)).hasSize(41)
                .extracting(MenuDO::getId)
                .doesNotContain(20041L);
            assertThat(menuMapper.selectListByRoleId(104L)).extracting(MenuDO::getId).containsExactly(19999L);
            assertThat(menuMapper.selectPermissionByUserId(1001L))
                .contains("eve:workspace:view", "eve:structures:view", "eve:extractions:view", "eve:mining:view", "eve:assets:view");
            assertThat(menuMapper.selectPermissionByUserId(1002L))
                .contains("eve:workspace:view", "eve:structures:view", "eve:extractions:view")
                .doesNotContain("eve:assets:view", "eve:mining:view");
            assertThat(menuMapper.selectListByRoleId(202L)).isEmpty();
            assertThat(roleMenuMapper.selectMenuIdByRoleIds(List.of(102L, 202L))).hasSize(69);
            assertThat(menuMapper.selectPermissionByUserId(1003L)).isEmpty();
        });
        withTenant(TENANT_B, () -> {
            assertThat(physicalMenus(202L)).isEmpty();
            assertThat(menuMapper.selectListByRoleId(202L)).hasSize(69);
            assertThat(menuMapper.selectListByRoleId(203L)).hasSize(41);
        });
        withTenant(TENANT_DISABLED, () -> {
            assertThat(menuMapper.selectListByRoleId(302L)).isEmpty();
            assertThat(menuMapper.selectPermissionByUserId(3001L)).isEmpty();
        });

        jdbc.update("INSERT INTO eve_derived_role_menu (role_code, menu_id) VALUES ('corp_admin', ?)", FEATURE_MENU);
        withTenant(TENANT_A, () -> assertThat(menuMapper.selectListByRoleId(102L)).extracting(MenuDO::getId)
            .contains(FEATURE_MENU));
        withTenant(TENANT_B, () -> assertThat(menuMapper.selectListByRoleId(202L)).extracting(MenuDO::getId)
            .contains(FEATURE_MENU));

        jdbc.update("DELETE FROM tenant_package_menu WHERE package_id = ? AND menu_id = ?", PACKAGE_ENABLED, FEATURE_MENU);
        withTenant(TENANT_A, () -> assertThat(menuMapper.selectListByRoleId(102L)).extracting(MenuDO::getId)
            .doesNotContain(FEATURE_MENU));
        jdbc.update("INSERT INTO tenant_package_menu (package_id, menu_id) VALUES (?, ?)", PACKAGE_ENABLED, FEATURE_MENU);

        jdbc.update("UPDATE tenant_package SET status = 2 WHERE id = ?", PACKAGE_ENABLED);
        withTenant(TENANT_A, () -> assertThat(menuMapper.selectListByRoleId(102L)).isEmpty());
        jdbc.update("UPDATE tenant_package SET status = 1 WHERE id = ?", PACKAGE_ENABLED);

        jdbc.update("UPDATE sys_menu SET status = 2 WHERE id = 20061");
        withTenant(TENANT_A, () -> assertThat(menuMapper.selectPermissionByUserId(1001L))
            .doesNotContain("eve:structures:view"));
        jdbc.update("UPDATE sys_menu SET status = 1 WHERE id = 20061");
        jdbc.update("UPDATE sys_menu SET deleted = id WHERE id = 20067");
        withTenant(TENANT_A, () -> assertThat(menuMapper.selectPermissionByUserId(1001L))
            .doesNotContain("eve:mining:view"));
        jdbc.update("UPDATE sys_menu SET deleted = 0 WHERE id = 20067");

        jdbc.update("DELETE FROM eve_derived_role_menu WHERE role_code = 'corp_admin' AND menu_id = ?", FEATURE_MENU);
        withTenant(TENANT_A, () -> assertThat(menuMapper.selectListByRoleId(102L)).extracting(MenuDO::getId)
            .doesNotContain(FEATURE_MENU));
    }

    /** 创建足以覆盖真实查询链的租户、套餐、角色、用户、菜单与历史物化关联。 */
    private void createFixture() {
        jdbc.execute("CREATE TABLE tenant (id BIGINT PRIMARY KEY, package_id BIGINT NOT NULL, status TINYINT NOT NULL, deleted BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE tenant_package (id BIGINT PRIMARY KEY, status TINYINT NOT NULL, deleted BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE tenant_package_menu (package_id BIGINT NOT NULL, menu_id BIGINT NOT NULL, PRIMARY KEY (package_id, menu_id))");
        jdbc.execute("""
            CREATE TABLE sys_role (
                id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, name VARCHAR(30), code VARCHAR(30) NOT NULL,
                data_scope TINYINT NOT NULL DEFAULT 1, description VARCHAR(200), sort INT NOT NULL DEFAULT 1,
                is_system BIT(1) NOT NULL DEFAULT b'0', menu_check_strictly BIT(1), dept_check_strictly BIT(1),
                create_user BIGINT, create_time DATETIME, update_user BIGINT, update_time DATETIME, deleted BIGINT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        jdbc.execute("""
            CREATE TABLE sys_menu (
                id BIGINT PRIMARY KEY, title VARCHAR(100), parent_id BIGINT, type TINYINT, path VARCHAR(255),
                name VARCHAR(100), component VARCHAR(255), redirect VARCHAR(255), icon VARCHAR(100),
                is_external BIT(1), is_cache BIT(1), is_hidden BIT(1), permission VARCHAR(100), sort INT,
                status TINYINT NOT NULL, create_user BIGINT, create_time DATETIME, update_user BIGINT,
                update_time DATETIME, deleted BIGINT NOT NULL
            )
            """);
        jdbc.execute("CREATE TABLE sys_role_menu (tenant_id BIGINT NOT NULL, role_id BIGINT NOT NULL, menu_id BIGINT NOT NULL, PRIMARY KEY (role_id, menu_id))");
        jdbc.execute("CREATE TABLE sys_user (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, status TINYINT NOT NULL, deleted BIGINT NOT NULL)");
        jdbc.execute("""
            CREATE TABLE sys_user_role (
                id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
                role_id BIGINT NOT NULL, UNIQUE KEY uk_user_role (tenant_id, user_id, role_id)
            )
            """);
        jdbc.update("INSERT INTO tenant_package VALUES (?, 1, 0), (?, 2, 0)", PACKAGE_ENABLED, PACKAGE_DISABLED);
        jdbc.update("INSERT INTO tenant VALUES (?, ?, 1, 0), (?, ?, 1, 0), (?, ?, 1, 0)", TENANT_A,
            PACKAGE_ENABLED, TENANT_B, PACKAGE_ENABLED, TENANT_DISABLED, PACKAGE_DISABLED);
        insertMenus();
        jdbc.update("INSERT INTO tenant_package_menu SELECT ?, id FROM sys_menu WHERE deleted = 0", PACKAGE_ENABLED);
        jdbc.update("INSERT INTO tenant_package_menu SELECT ?, id FROM sys_menu WHERE deleted = 0", PACKAGE_DISABLED);
        jdbc.update("""
            INSERT INTO sys_role (id, tenant_id, name, code, deleted) VALUES
            (101, 10, '军团 CEO', 'corp_owner', 0), (102, 10, '军团总监', 'corp_admin', 0),
            (103, 10, '军团成员', 'corp_member', 0), (104, 10, '资产查看员', 'eve_business_asset', 0),
            (201, 20, '军团 CEO', 'corp_owner', 0), (202, 20, '军团总监', 'corp_admin', 0),
            (203, 20, '军团成员', 'corp_member', 0), (302, 30, '军团总监', 'corp_admin', 0)
            """);
        jdbc.update("INSERT INTO sys_user VALUES (1001, 10, 1, 0), (1002, 10, 1, 0), (1003, 10, 1, 0), (2001, 20, 1, 0), (3001, 30, 1, 0)");
        jdbc.update("""
            INSERT INTO sys_user_role (tenant_id, user_id, role_id) VALUES
            (10, 1001, 102), (10, 1002, 103), (10, 1003, 202), (20, 2001, 202), (30, 3001, 302)
            """);
        jdbc.update("""
            INSERT INTO sys_role_menu VALUES
            (10, 102, 20010), (10, 102, 20011), (10, 102, 20020), (10, 102, 20022),
            (10, 102, 20023), (10, 102, 20024), (10, 102, 20200), (10, 102, 20201),
            (10, 103, 20041), (10, 104, 19999), (20, 104, 20041)
            """);
    }

    /** 插入当前 EVE 策略涉及的全部菜单，保留目录、页面和按钮类型。 */
    private void insertMenus() {
        Set<Long> pageMenus = Set
            .of(20011L, 20020L, 20024L, 20041L, 20051L, 20052L, 20061L, 20065L, 20067L, 20069L, 20072L, 20073L, 20076L, 20077L, 20078L, 20079L, 20080L, 20081L, 20084L, 20085L, 20091L, 20094L, 20098L, 20101L, 20104L, 20105L, 20106L, 20107L, 20108L, 20201L);
        Set<Long> directoryMenus = Set
            .of(20000L, 20010L, 20022L, 20023L, 20030L, 20040L, 20050L, 20060L, 20068L, 20071L, 20074L, 20075L, 20083L, 20090L, 20093L, 20100L, 20200L);
        for (long menuId = 19999; menuId <= FEATURE_MENU; menuId++) {
            int type = directoryMenus.contains(menuId) ? 1 : pageMenus.contains(menuId) ? 2 : 3;
            String permission = switch ((int)menuId) {
                case 20011 -> "eve:workspace:view";
                case 20041 -> "eve:assets:view";
                case 20061 -> "eve:structures:view";
                case 20065 -> "eve:extractions:view";
                case 20067 -> "eve:mining:view";
                default -> type == 3 ? "eve:test:" + menuId : null;
            };
            jdbc.update("""
                INSERT INTO sys_menu
                (id, title, parent_id, type, permission, sort, status, create_user, create_time, deleted)
                VALUES (?, ?, 0, ?, ?, 1, 1, 1, NOW(), 0)
                """, menuId, "菜单" + menuId, type, permission);
        }
    }

    /** 执行真实 Liquibase 策略迁移。 */
    private void runMigration() throws Exception {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(CHANGELOG);
        liquibase.afterPropertiesSet();
    }

    /** 装配项目真实 XML Mapper 与生产一致的行级租户拦截器。 */
    private void configureMappers() throws Exception {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                return new LongValue(TenantContextHolder.getTenantId());
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return TENANT_IGNORE_TABLES.contains(tableName);
            }
        }));

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setDefaultEnumTypeHandler(CompositeBaseEnumTypeHandler.class);
        configuration.addInterceptor(interceptor);
        configuration.addMapper(MenuMapper.class);
        configuration.addMapper(RoleMenuMapper.class);
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(resolver.getResources("classpath*:mapper/{MenuMapper,RoleMenuMapper}.xml"));
        SqlSessionTemplate session = new SqlSessionTemplate(factory.getObject());
        menuMapper = session.getMapper(MenuMapper.class);
        roleMenuMapper = session.getMapper(RoleMenuMapper.class);
    }

    /** 在指定租户上下文执行查询并清理线程变量。 */
    private void withTenant(Long tenantId, Runnable action) {
        TenantContext context = new TenantContext();
        context.setTenantId(tenantId);
        context.setIsolationLevel(TenantIsolationLevel.LINE);
        TenantContextHolder.setContext(context);
        try {
            action.run();
        } finally {
            TenantContextHolder.clear();
        }
    }

    /** 查询旧物化授权，仅用于证明最终权限不依赖补写。 */
    private List<Long> physicalMenus(Long roleId) {
        return jdbc.queryForList("SELECT menu_id FROM sys_role_menu WHERE role_id = ? ORDER BY menu_id", Long.class, roleId);
    }

    /** 读取集成测试 JVM 属性。 */
    private static String property(String name) {
        return System.getProperty(name);
    }
}
