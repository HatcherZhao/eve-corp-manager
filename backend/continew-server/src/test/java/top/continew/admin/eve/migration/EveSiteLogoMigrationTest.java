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
 * 系统默认 Logo 迁移契约测试。
 *
 * @author zhaoyuqing
 */
class EveSiteLogoMigrationTest {

    /** 默认 Logo 迁移必须纳入主变更集，且只替换未自定义的旧默认值。 */
    @Test
    void shouldMigrateOnlyDefaultSiteLogoToProjectLogo() throws IOException {
        String master = resource("db/changelog/db.changelog-master.yaml");
        String migrationPath = "db/changelog/mysql/eve/024_site_logo.sql";
        String migration = resource(migrationPath);

        assertThat(master).contains(migrationPath);
        assertThat(migration)
            .contains("`code` = 'SITE_LOGO'", "`value` IS NULL", "`default_value` = '/logo.svg'", "/eve-corp-manager-logo.png");
    }

    /** 默认页签图迁移必须纳入主变更集，且只替换未自定义的旧默认值。 */
    @Test
    void shouldMigrateOnlyDefaultFaviconToProjectFavicon() throws IOException {
        String master = resource("db/changelog/db.changelog-master.yaml");
        String migrationPath = "db/changelog/mysql/eve/025_site_favicon.sql";
        String migration = resource(migrationPath);

        assertThat(master).contains(migrationPath);
        assertThat(migration)
            .contains("`code` = 'SITE_FAVICON'", "`value` IS NULL", "`default_value` = '/favicon.ico'", "/eve-corp-manager-favicon.png");
    }

    /** 读取类路径中的迁移资源。 */
    private static String resource(String path) throws IOException {
        try (InputStream input = EveSiteLogoMigrationTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("迁移资源 %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
