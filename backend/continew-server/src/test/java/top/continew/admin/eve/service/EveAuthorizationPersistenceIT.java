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

package top.continew.admin.eve.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import top.continew.admin.ContiNewAdminApplication;
import top.continew.admin.LocalIntegrationEnvironment;
import top.continew.admin.eve.client.SerenityTokenResponse;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EVE 授权持久化真实数据库集成测试。
 *
 * @author zhaoyuqing
 */
class EveAuthorizationPersistenceIT {

    private static final Long TENANT_ID = 990001L;
    private static final Long USER_ID = 990002L;
    private static final Long AUTHORIZATION_ID = -990003L;

    /** Token 必须密文落库，Scope 必须通过 JSON TypeHandler 正确往返。 */
    @Test
    void shouldEncryptTokensAndPersistScopesAsJson() {
        LocalIntegrationEnvironment.configureOrSkip();
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(ContiNewAdminApplication.class)
            .run("--server.port=0", "--continew-starter.tenant.enabled=false")) {
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            EveAuthorizationMapper authorizationMapper = context.getBean(EveAuthorizationMapper.class);
            EveAuthorizationTokenService tokenService = context.getBean(EveAuthorizationTokenService.class);
            jdbcTemplate.update("DELETE FROM eve_authorization WHERE id = ?", AUTHORIZATION_ID);
            try {
                jdbcTemplate.update("""
                    INSERT INTO eve_authorization
                        (id, tenant_id, character_ref_id, user_id, server, scopes, expires_at,
                         status, failure_count, last_verification_status, create_user, create_time, deleted)
                    VALUES (?, ?, ?, ?, 'serenity', JSON_ARRAY(), ?, 'ACTIVE', 0, 'PENDING', ?, ?, 0)
                    """, AUTHORIZATION_ID, TENANT_ID, 990004L, USER_ID, LocalDateTime.now()
                    .plusMinutes(1), USER_ID, LocalDateTime.now());

                tokenService
                    .saveTokenResponse(TENANT_ID, USER_ID, AUTHORIZATION_ID, new SerenityTokenResponse("plain-access", "plain-refresh", "Bearer", 1200, "scope-a scope-b"));

                Map<String, Object> raw = jdbcTemplate
                    .queryForMap("SELECT access_token, refresh_token, scopes FROM eve_authorization WHERE id = ?", AUTHORIZATION_ID);
                assertThat(raw.get("access_token")).isNotEqualTo("plain-access");
                assertThat(raw.get("refresh_token")).isNotEqualTo("plain-refresh");
                assertThat(String.valueOf(raw.get("scopes"))).contains("scope-a", "scope-b");

                EveAuthorizationDO authorization = authorizationMapper
                    .selectOwnedById(TENANT_ID, USER_ID, AUTHORIZATION_ID);
                assertThat(authorization.getAccessToken()).isEqualTo("plain-access");
                assertThat(authorization.getRefreshToken()).isEqualTo("plain-refresh");
                assertThat(authorization.getScopes()).containsExactly("scope-a", "scope-b");
            } finally {
                jdbcTemplate.update("DELETE FROM eve_authorization WHERE id = ?", AUTHORIZATION_ID);
            }
        }
    }
}
