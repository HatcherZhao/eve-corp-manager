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

/**
 * 月矿页面菜单与接口查看权限的迁移契约测试。
 *
 * @author zhaoyuqing
 */
class EveMoonExtractionPermissionMigrationTest {

    /** 路由菜单必须保留独立的查看权限，并授予套餐和三个军团派生身份。 */
    @Test
    void shouldRestoreMoonExtractionViewPermissionAfterRouteMigration() throws IOException {
        String master = resource("db/changelog/db.changelog-master.yaml");
        String migrationPath = "db/changelog/mysql/eve/020_moon_extraction_view_permission.sql";
        String migration = resource(migrationPath);

        assertThat(master).contains(migrationPath);
        assertThat(migration)
            .contains("(20065, '查看月矿计划', 20022, 3", "'eve:extractions:view'", "(20001, 20065)", "'corp_owner', 'corp_admin', 'corp_member'");
    }

    /** 月矿模块必须收敛为情报与备注，不再暴露排产、负责人或协同任务语义。 */
    @Test
    void shouldMigrateMoonPlanningToMoonIntelligence() throws IOException {
        String master = resource("db/changelog/db.changelog-master.yaml");
        String migrationPath = "db/changelog/mysql/eve/026_moon_intelligence.sql";
        String migration = resource(migrationPath);

        assertThat(master).contains(migrationPath);
        assertThat(migration)
            .contains("`note` varchar(500)", "'月矿情报'", "'查看月矿情报'", "'维护月矿备注'", "`path` = '/eve/extractions'", "`name` = 'EveExtractions'");
    }

    /** 读取类路径中的迁移资源。 */
    private static String resource(String path) throws IOException {
        try (InputStream input = EveMoonExtractionPermissionMigrationTest.class.getClassLoader()
            .getResourceAsStream(path)) {
            assertThat(input).as("迁移资源 %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
