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
 * 军团级外部 Agent 授权迁移契约测试。
 *
 * @author zhaoyuqing
 */
class EveAgentAccessMigrationTest {

    /** 授权表只持久化摘要，并仅向军团 CEO 和总监授予管理入口。 */
    @Test
    void shouldRegisterHashedCredentialAuditAndManagerPermission() throws IOException {
        String master = resource("db/changelog/db.changelog-master.yaml");
        String migrationPath = "db/changelog/mysql/eve/054_agent_access.sql";
        String migration = resource(migrationPath);

        assertThat(master.indexOf(migrationPath)).isGreaterThan(master.indexOf("053_moon_ore_profile.sql"));
        assertThat(migration)
            .contains("CREATE TABLE `eve_agent_application`", "`token_hash`", "char(64)", "UNIQUE KEY `uk_eve_agent_application_token`", "CREATE TABLE `eve_agent_audit`", "'eve:agents:manage'", "'corp_owner', 'corp_admin'")
            .doesNotContain("`token` varchar", "'corp_member'");
    }

    /** 布局修复使用后续迁移，不改写已上线的 054，并按顺序挂载到军团布局。 */
    @Test
    void shouldRegisterAgentMenuLayoutFixAfterCaptchaMigration() throws IOException {
        String master = resource("db/changelog/db.changelog-master.yaml");
        String path = "db/changelog/mysql/eve/056_agent_menu_layout.sql";
        assertThat(master.indexOf(path)).isGreaterThan(master.indexOf("055_disable_login_captcha.sql"));
        assertThat(resource(path))
            .contains("`component` = 'Layout'", "`id` = 20200", "20201, '外部 Agent', 20200", "'eve:agents:manage'", "'corp_owner', 'corp_admin'");
    }

    /** 读取类路径迁移资源。 */
    private static String resource(String path) throws IOException {
        try (InputStream input = EveAgentAccessMigrationTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("迁移资源 %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
