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
 * EVE 派生角色权限分离与主角色唯一约束迁移契约测试。
 *
 * @author zhaoyuqing
 */
class EveRoleBindingHardeningMigrationTest {

    private static final String MIGRATION_PATH = "db/changelog/mysql/eve/005_role_binding_hardening.sql";

    /** 纠正变更集必须在能力权限初始化后执行，并保留套餐菜单关系。 */
    @Test
    void shouldRegisterHardeningMigrationAfterCapabilityPermissions() throws IOException {
        String master = resource("db/changelog/db.changelog-master.yaml");
        String migration = resource(MIGRATION_PATH);

        assertThat(master.indexOf("db/changelog/mysql/eve/004_eve_capability_permissions.sql")).isLessThan(master
            .indexOf(MIGRATION_PATH));
        assertThat(migration)
            .contains("onFail:HALT", "HAVING COUNT(*) > 1", "GENERATED ALWAYS AS", "ADD UNIQUE INDEX `uk_eve_character_tenant_primary_user`");
        assertThat(migration)
            .contains("'corp_owner', 'corp_admin', 'corp_member'", "20020, 20021, 20022, 20023, 20024");
        assertThat(migration).doesNotContain("DELETE FROM `tenant_package_menu`", "DELETE `tenant_package_menu`");
    }

    /** 读取类路径迁移资源。 */
    private static String resource(String path) throws IOException {
        try (InputStream input = EveRoleBindingHardeningMigrationTest.class.getClassLoader()
            .getResourceAsStream(path)) {
            assertThat(input).as("迁移资源 %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
