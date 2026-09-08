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
 * EVE 固定数据迁移冲突保护契约测试。
 *
 * @author zhaoyuqing
 */
class EveFixedDataMigrationGuardTest {

    /** 套餐固定 ID 与业务名称必须在原写入变更集之前执行 HALT 校验。 */
    @Test
    void shouldGuardTenantPackageBeforeInsertChangeset() throws IOException {
        String master = resource("db/changelog/db.changelog-master.yaml");
        String guardPath = "db/changelog/mysql/eve/002a_eve_tenant_package_guard.sql";
        String insertPath = "db/changelog/mysql/eve/003_eve_tenant_package.sql";
        String guard = resource(guardPath);

        assertThat(master.indexOf(guardPath)).isGreaterThanOrEqualTo(0).isLessThan(master.indexOf(insertPath));
        assertThat(guard).contains("onFail:HALT", "WHERE `id` = 20001", "WHERE `name` = 'EVE军团套餐'");
    }

    /** 能力权限固定 ID 与权限编码必须在原写入变更集之前执行 HALT 校验。 */
    @Test
    void shouldGuardCapabilityPermissionsBeforeInsertChangeset() throws IOException {
        String master = resource("db/changelog/db.changelog-master.yaml");
        String guardPath = "db/changelog/mysql/eve/002b_eve_capability_permission_guard.sql";
        String insertPath = "db/changelog/mysql/eve/004_eve_capability_permissions.sql";
        String guard = resource(guardPath);

        assertThat(master.indexOf(guardPath)).isGreaterThanOrEqualTo(0).isLessThan(master.indexOf(insertPath));
        assertThat(guard).contains("onFail:HALT", "`id` = 20020", "`permission` = 'eve:assets:view'", "`id` <> 20020");
    }

    /** 读取类路径迁移资源。 */
    private static String resource(String path) throws IOException {
        try (InputStream input = EveFixedDataMigrationGuardTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("迁移资源 %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
