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

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import top.continew.admin.LocalIntegrationEnvironment;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 在全新数据库执行完整迁移，约束未来新增功能必须进入统一授权并保留完整路由父节点。
 *
 * @author zhaoyuqing
 */
class EveDerivedRolePolicyCoverageIT {

    /** 每个有效套餐菜单都应定义默认身份，每个策略节点都应具有同身份可访问的父目录。 */
    @Test
    void shouldCoverAllEvePackageMenusAndRouteParentsAfterFullMigration() throws Exception {
        LocalIntegrationEnvironment.configureOrSkip();
        String rootUrl = "jdbc:mysql://" + System.getProperty("DB_HOST") + ':' + System.getProperty("DB_PORT") + '/';
        String options = "?useSSL=false&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true";
        String schema = "eve_policy_coverage_" + UUID.randomUUID().toString().replace("-", "");
        String user = System.getProperty("DB_USER");
        String password = System.getProperty("DB_PWD");
        JdbcTemplate server = new JdbcTemplate(new DriverManagerDataSource(rootUrl + options, user, password));
        server.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        try {
            DriverManagerDataSource dataSource = new DriverManagerDataSource(rootUrl + schema + options, user, password);
            SpringLiquibase liquibase = new SpringLiquibase();
            liquibase.setDataSource(dataSource);
            liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
            liquibase.afterPropertiesSet();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            assertThat(jdbc.queryForList("""
                SELECT menu.id FROM tenant_package_menu package_menu
                JOIN sys_menu menu ON menu.id = package_menu.menu_id AND menu.deleted = 0 AND menu.status = 1
                LEFT JOIN eve_derived_role_menu policy ON policy.menu_id = menu.id
                WHERE package_menu.package_id = 20001 AND policy.menu_id IS NULL
                """, Long.class)).as("新增功能必须声明全局角色范围，不能仅补写已有军团角色").isEmpty();
            assertThat(jdbc.queryForList("""
                SELECT policy.menu_id FROM eve_derived_role_menu policy
                JOIN sys_menu menu ON menu.id = policy.menu_id AND menu.deleted = 0 AND menu.status = 1
                JOIN sys_menu parent ON parent.id = menu.parent_id AND parent.deleted = 0 AND parent.status = 1
                LEFT JOIN eve_derived_role_menu parent_policy ON parent_policy.menu_id = parent.id
                    AND parent_policy.role_code = policy.role_code
                WHERE menu.parent_id <> 0 AND parent_policy.menu_id IS NULL
                """, Long.class)).as("授权页面必须具有同身份的完整父目录").isEmpty();
            Long policies = jdbc.queryForObject("SELECT COUNT(*) FROM eve_derived_role_menu", Long.class);
            assertThat(policies).isPositive();
            // 模拟 MySQL DDL 已提交、Liquibase 尚未记录成功，重启时必须能安全继续。
            jdbc.update("DELETE FROM DATABASECHANGELOG WHERE ID = '058-derived-role-policy'");
            liquibase.afterPropertiesSet();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eve_derived_role_menu", Long.class))
                .isEqualTo(policies);
        } finally {
            server.execute("DROP DATABASE `" + schema + "`");
        }
    }
}
