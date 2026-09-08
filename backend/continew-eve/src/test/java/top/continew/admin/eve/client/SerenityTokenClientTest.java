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

package top.continew.admin.eve.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import top.continew.admin.eve.config.SerenityProperties;

import java.nio.charset.StandardCharsets;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 国服 Token 客户端测试。
 *
 * @author zhaoyuqing
 */
class SerenityTokenClientTest {

    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

    private SerenityProperties properties;
    private MockRestServiceServer server;
    private SerenityTokenClient client;

    /** 初始化绑定到 MockRestServiceServer 的 RestClient。 */
    @BeforeEach
    void setUp() {
        properties = new SerenityProperties();
        properties.getSso().setClientId("client-id");
        properties.getSso().setClientSecret("client-secret");
        properties.getSso().setCallbackUrl("https://app.example.test/api/eve/oauth/callback");
        properties.getSso().setTokenEndpoint("https://login.example.test/oauth/token");
        properties.getSso().setRevocationEndpoint("https://login.example.test/oauth/revoke");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SerenityTokenClient(builder.build(), properties);
    }

    /** 验证授权码交换只向固定端点发送 Basic 认证表单。 */
    @Test
    void shouldExchangeCodeUsingFixedFormPostAndBasicAuth() {
        String authorization = "Basic " + HttpHeaders
            .encodeBasicAuth("client-id", "client-secret", StandardCharsets.UTF_8);
        server.expect(once(), requestTo("https://login.example.test/oauth/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, authorization))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().string(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers
                .containsString("grant_type=authorization_code"), org.hamcrest.Matchers
                    .containsString("code=authorization-code"), org.hamcrest.Matchers
                        .containsString("code_verifier=" + VERIFIER))))
            .andRespond(withSuccess("""
                {"access_token":"access-secret","refresh_token":"refresh-secret","token_type":"Bearer","expires_in":1200,"scope":"scope-a scope-b"}
                """, MediaType.APPLICATION_JSON));

        SerenityTokenResponse response = client.exchangeCode("authorization-code", VERIFIER);

        assertThat(response.accessToken()).isEqualTo("access-secret");
        assertThat(response.toString()).doesNotContain("access-secret", "refresh-secret", "scope-a");
        server.verify();
    }

    /** 验证 Swagger 公共客户端通过表单 client_id 换取令牌且不发送 Basic 认证。 */
    @Test
    void shouldExchangeCodeAsPublicClientWithoutBasicAuth() {
        properties.getSso().setClientSecret(null);
        server.expect(once(), requestTo("https://login.example.test/oauth/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
            .andExpect(content().string(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers
                .containsString("client_id=client-id"), org.hamcrest.Matchers
                    .containsString("grant_type=authorization_code"), org.hamcrest.Matchers
                        .containsString("code_verifier=" + VERIFIER))))
            .andRespond(withSuccess("""
                {"access_token":"access-secret","refresh_token":"refresh-secret","token_type":"Bearer","expires_in":1200,"scope":"scope-a"}
                """, MediaType.APPLICATION_JSON));

        SerenityTokenResponse response = client.exchangeCode("authorization-code", VERIFIER);

        assertThat(response.accessToken()).isEqualTo("access-secret");
        server.verify();
    }

    /** 验证 Swagger 公共客户端刷新令牌时继续通过表单提交 client_id。 */
    @Test
    void shouldRefreshAsPublicClientWithoutBasicAuth() {
        properties.getSso().setClientSecret("");
        server.expect(once(), requestTo("https://login.example.test/oauth/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
            .andExpect(content().string(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers
                .containsString("client_id=client-id"), org.hamcrest.Matchers
                    .containsString("grant_type=refresh_token"), org.hamcrest.Matchers
                        .containsString("refresh_token=refresh-secret"))))
            .andRespond(withSuccess("""
                {"access_token":"new-access-secret","refresh_token":"new-refresh-secret","token_type":"Bearer","expires_in":1200,"scope":"scope-a"}
                """, MediaType.APPLICATION_JSON));

        SerenityTokenResponse response = client.refresh("refresh-secret");

        assertThat(response.accessToken()).isEqualTo("new-access-secret");
        server.verify();
    }

    /** 验证客户端异常不会回显授权码、verifier 或固定完整 URL。 */
    @Test
    void shouldReturnSanitizedFailure() {
        server.expect(requestTo("https://login.example.test/oauth/token")).andRespond(withServerError());
        assertThatThrownBy(() -> client.exchangeCode("sensitive-code", VERIFIER))
            .isInstanceOf(SerenityTokenClientException.class)
            .hasMessage("国服 OAuth Token 操作失败")
            .hasMessageNotContaining("sensitive-code")
            .hasMessageNotContaining(VERIFIER)
            .hasMessageNotContaining("https://");
    }

    /** 授权码交换缺少刷新令牌时必须拒绝建立不可续期授权。 */
    @Test
    void shouldRejectCodeExchangeWithoutRefreshToken() {
        server.expect(requestTo("https://login.example.test/oauth/token")).andRespond(withSuccess("""
            {"access_token":"access-secret","token_type":"Bearer","expires_in":1200,"scope":"scope-a"}
            """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchangeCode("authorization-code", VERIFIER))
            .isInstanceOf(SerenityTokenClientException.class);
        server.verify();
    }

    /** 本地 OAuth 配置错误应原样暴露，不能被伪装成上游故障。 */
    @Test
    void shouldNotMaskLocalConfigurationFailure() {
        properties.getSso().setClientId(null);

        assertThatThrownBy(() -> client.exchangeCode("authorization-code", VERIFIER))
            .isInstanceOf(IllegalArgumentException.class)
            .isNotInstanceOf(SerenityTokenClientException.class);
    }

    /** 读取超时必须归类为可重试的上游临时故障。 */
    @Test
    void shouldClassifyReadTimeoutAsTransient() {
        SerenityTokenClientException exception = SerenityTokenClient
            .classify(new ResourceAccessException("timeout", new SocketTimeoutException()));

        assertThat(exception.getFailureCode()).isEqualTo(OAuthFailureCode.TRANSIENT);
    }

    /** Token 端点超时和限流状态必须保持授权可重试，不能清空有效令牌。 */
    @Test
    void shouldClassifyTimeoutAndRateLimitsAsTransient() {
        for (int status : new int[] {408, 420, 429}) {
            SerenityTokenClientException exception = SerenityTokenClient
                .classify(new HttpClientErrorException(HttpStatusCode.valueOf(status)));

            assertThat(exception.getFailureCode()).as("HTTP %s", status).isEqualTo(OAuthFailureCode.TRANSIENT);
        }
    }
}
