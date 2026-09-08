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

package top.continew.admin.eve.auth;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.util.UriComponentsBuilder;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.security.OAuthSecurityUtils;
import top.continew.starter.core.exception.BusinessException;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

/**
 * 国服授权启动服务测试。
 *
 * @author zhaoyuqing
 */
class SerenityAuthorizationStartServiceTest {

    /** 国服 OAuth 未配置时，所有授权用途均返回可展示的统一业务提示。 */
    @Test
    void shouldRejectEveryAuthorizationPurposeWhenSsoIsDisabled() {
        SerenityProperties properties = new SerenityProperties();
        OAuthTransactionStore store = mock(OAuthTransactionStore.class);
        SerenityAuthorizationStartService service = new SerenityAuthorizationStartService(properties, store);

        for (OAuthTransactionPurpose purpose : OAuthTransactionPurpose.values()) {
            assertThatThrownBy(() -> service.start(purpose, 10L, 20L, "serenity", 8001L, "browser-digest", Set
                .of("esi-characters.read_corporation_roles.v1"))).isInstanceOf(BusinessException.class)
                .hasMessage("国服授权通道暂未启用，请联系管理员");
        }
        verifyNoInteractions(store);
    }

    /** 绑定信息或请求参数不满足契约时，不应泄露授权配置。 */
    @Test
    void shouldReturnSafeBusinessMessageForInvalidAuthorizationRequest() {
        SerenityProperties properties = new SerenityProperties();
        properties.getSso().setEnabled(true);
        OAuthTransactionStore store = mock(OAuthTransactionStore.class);
        SerenityAuthorizationStartService service = new SerenityAuthorizationStartService(properties, store);

        assertThatThrownBy(() -> service.start(OAuthTransactionPurpose.REGISTER, null, "", Set
            .of("esi-characters.read_corporation_roles.v1"))).isInstanceOf(BusinessException.class)
            .hasMessage("国服授权请求无效，请重新发起");
        verifyNoInteractions(store);
    }

    /** 验证生成 code flow、PKCE S256 并保存只含站内相对结果路径的事务。 */
    @Test
    void shouldCreatePkceAuthorizationTransaction() {
        SerenityProperties properties = new SerenityProperties();
        SerenityProperties.Sso sso = properties.getSso();
        sso.setEnabled(true);
        sso.setClientId("client-id");
        sso.setCallbackUrl("https://app.example.test/api/eve/oauth/callback");
        sso.setAuthorizationEndpoint("https://login.example.test/oauth/authorize");
        sso.setTransactionTtl(Duration.ofMinutes(10));
        Set<String> scopes = Set.of("esi-characters.read_corporation_roles.v1");
        OAuthTransactionStore store = mock(OAuthTransactionStore.class);
        SerenityAuthorizationStartService service = new SerenityAuthorizationStartService(properties, store);

        SerenityAuthorizationStart result = service
            .start(OAuthTransactionPurpose.REGISTER, null, "browser-digest", scopes);

        ArgumentCaptor<String> stateCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<OAuthTransaction> transactionCaptor = ArgumentCaptor.forClass(OAuthTransaction.class);
        verify(store).save(stateCaptor.capture(), transactionCaptor.capture(), org.mockito.ArgumentMatchers.eq(Duration
            .ofMinutes(10)));
        OAuthTransaction transaction = transactionCaptor.getValue();
        var query = UriComponentsBuilder.fromUriString(result.authorizationUri()).build().getQueryParams();
        assertThat(query.getFirst("response_type")).isEqualTo("code");
        assertThat(query.getFirst("state")).isEqualTo(stateCaptor.getValue());
        assertThat(query.getFirst("device_id")).isEqualTo("eve-corp-manager");
        assertThat(query.getFirst("relogin")).isEqualTo("1");
        assertThat(query.getFirst("code_challenge_method")).isEqualTo("S256");
        assertThat(query.getFirst("code_challenge")).isEqualTo(OAuthSecurityUtils.createS256Challenge(transaction
            .getVerifier()));
        assertThat(transaction.getRedirectUri()).isEqualTo("/login/eve/result").startsWith("/");
        assertThat(transaction.toString()).doesNotContain(transaction.getVerifier(), "browser-digest");
        assertThat(result.toString()).doesNotContain("https://", stateCaptor.getValue());
    }

    /** 重新授权事务必须固化租户、用户、服务器和原角色四重绑定。 */
    @Test
    void shouldBindReauthorizationToExistingIdentity() {
        SerenityProperties properties = new SerenityProperties();
        SerenityProperties.Sso sso = properties.getSso();
        sso.setEnabled(true);
        sso.setClientId("client-id");
        sso.setCallbackUrl("https://app.example.test/api/eve/oauth/callback");
        sso.setAuthorizationEndpoint("https://login.example.test/oauth/authorize");
        OAuthTransactionStore store = mock(OAuthTransactionStore.class);
        SerenityAuthorizationStartService service = new SerenityAuthorizationStartService(properties, store);

        SerenityAuthorizationStart result = service
            .start(OAuthTransactionPurpose.EXPAND_SCOPES, 10L, 20L, "serenity", 8001L, "browser-digest", sso
                .getRequiredScopes());

        ArgumentCaptor<OAuthTransaction> transactionCaptor = ArgumentCaptor.forClass(OAuthTransaction.class);
        verify(store).save(org.mockito.ArgumentMatchers.anyString(), transactionCaptor
            .capture(), org.mockito.ArgumentMatchers.eq(sso.getTransactionTtl()));
        OAuthTransaction transaction = transactionCaptor.getValue();
        assertThat(transaction.getBoundTenantId()).isEqualTo(10L);
        assertThat(transaction.getBoundUserId()).isEqualTo(20L);
        assertThat(transaction.getBoundServer()).isEqualTo("serenity");
        assertThat(transaction.getBoundCharacterId()).isEqualTo(8001L);
        assertThat(UriComponentsBuilder.fromUriString(result.authorizationUri()).build().getQueryParams())
            .doesNotContainKey("relogin");
    }
}
