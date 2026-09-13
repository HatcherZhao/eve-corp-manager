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
 * EVE 顶级菜单扁平化迁移契约测试。
 *
 * @author zhaoyuqing
 */
class EveRootMenuMigrationTest {

    /** 保证总目录移除后，原功能仍由带查看权限的页面路由承载。 */
    @Test
    void shouldPromoteEveModulesWithoutDuplicatingViewPermissions() throws IOException {
        String master = resource("db/changelog/db.changelog-master.yaml");
        String migrationPath = "db/changelog/mysql/eve/038_root_eve_menus.sql";
        String migration = resource(migrationPath);

        assertThat(master.indexOf(migrationPath)).isGreaterThan(master.indexOf("037_official_news.sql"));
        assertThat(migration).contains("WHERE `parent_id` = 20000", "SET `parent_id`   = 0",
            "WHERE `id` IN (20011, 20024, 20020, 20061, 20065, 20067)", "`component` = 'Layout'",
            "WHERE `id` = 20000");
    }

    /** 读取类路径迁移资源。 */
    private static String resource(String path) throws IOException {
        try (InputStream input = EveRootMenuMigrationTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("迁移资源 %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
