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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** 工业制造迁移的结构和菜单契约测试。 */
class EveIndustryMigrationTest {

    @Test
    void shouldRegisterIndustryTablesMenusAndPermissions() throws IOException {
        String master = resource("db/changelog/db.changelog-master.yaml");
        String migration = resource("db/changelog/mysql/eve/047_industry.sql");
        String validationMigration = resource("db/changelog/mysql/eve/048_industry_validation.sql");
        String menuMigration = resource("db/changelog/mysql/eve/049_industry_menu_structure.sql");
        String snapshotMigration = resource("db/changelog/mysql/eve/050_industry_snapshots.sql");
        String planMigration = resource("db/changelog/mysql/eve/051_industry_plans.sql");
        assertThat(master).contains("db/changelog/mysql/eve/047_industry.sql");
        assertThat(master).contains("db/changelog/mysql/eve/048_industry_validation.sql");
        assertThat(master).contains("db/changelog/mysql/eve/049_industry_menu_structure.sql");
        assertThat(master)
            .contains("db/changelog/mysql/eve/050_industry_snapshots.sql", "db/changelog/mysql/eve/051_industry_plans.sql");
        assertThat(migration)
            .contains("CREATE TABLE `eve_industry_blueprint`", "CREATE TABLE `eve_industry_config`", "CREATE TABLE `eve_industry_facility_profile`", "CREATE TABLE `eve_industry_opportunity_snapshot`", "(20100, '工业制造'", "(20101, '生产机会'", "eve:industry:view", "eve:industry:data:manage");
        assertThat(validationMigration)
            .contains("CREATE TABLE `eve_industry_system_cost_index`", "CREATE TABLE `eve_industry_golden_case`", "ADD COLUMN `versioned_surcharge`");
        assertThat(menuMigration)
            .contains("'/eve/industry/opportunities'", "'机会分析'", "'蓝图计算'", "'生产计划'", "'库存与材料'", "'工业配置'", "'数据与校验'", "20104", "20105", "20106", "20107", "20108");
        assertThat(snapshotMigration)
            .contains("eve_industry_market_input_snapshot", "eve_industry_market_order_slice", "eve_industry_system_snapshot", "input_snapshot_json");
        assertThat(planMigration)
            .contains("eve_industry_plan", "eve_industry_plan_step", "eve:industry:plan:view", "eve:industry:plan:manage");
    }

    private static String resource(String path) throws IOException {
        try (InputStream input = EveIndustryMigrationTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("迁移资源 %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
