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
import top.continew.admin.eve.model.serenity.SerenityCorporationRolesResponse;
import top.continew.admin.eve.model.serenity.SerenityEsiResponse;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 国服 ESI 客户端响应元数据测试。
 *
 * @author zhaoyuqing
 */
class SerenityEsiClientTest {

    private MockRestServiceServer server;
    private SerenityEsiClient client;

    /** 初始化固定测试基址与可验证的 RestClient。 */
    @BeforeEach
    void setUp() {
        SerenityProperties properties = new SerenityProperties();
        properties.getEsi().setBaseUrl("https://esi.example.test/latest");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SerenityEsiClient(builder.build(), properties);
    }

    /** 角色查询应保留上游 Expires 和 ETag，不再伪造接口数据时间。 */
    @Test
    void shouldPreserveRoleCacheMetadata() {
        server.expect(once(), requestTo("https://esi.example.test/latest/characters/8001/roles/?datasource=serenity"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
            .andRespond(withSuccess("""
                {"roles":["Director"],"roles_at_hq":[],"roles_at_base":[],"roles_at_other":[]}
                """, MediaType.APPLICATION_JSON).header(HttpHeaders.EXPIRES, "Wed, 09 Sep 2026 12:00:00 GMT")
                .header(HttpHeaders.ETAG, "\"roles-v1\""));

        SerenityEsiResponse<SerenityCorporationRolesResponse> response = client
            .getCorporationRolesWithMetadata(8001L, "access-token");

        assertThat(response.body().roles()).containsExactly("Director");
        assertThat(response.expiresAt()).isEqualTo(LocalDateTime.ofInstant(Instant.parse("2026-09-09T12:00:00Z"), ZoneId
            .systemDefault()));
        assertThat(response.etag()).isEqualTo("\"roles-v1\"");
        server.verify();
    }

    /** 本地配置错误应原样暴露，不能被归类为国服临时故障。 */
    @Test
    void shouldNotMaskLocalConfigurationFailure() {
        SerenityProperties invalidProperties = new SerenityProperties();
        invalidProperties.getEsi().setBaseUrl(null);
        SerenityEsiClient invalidClient = new SerenityEsiClient(RestClient.create(), invalidProperties);

        assertThatThrownBy(() -> invalidClient.getCharacter(8001L)).isInstanceOf(NullPointerException.class);
    }

    /** 读取超时必须归类为可重试的上游临时故障。 */
    @Test
    void shouldClassifyReadTimeoutAsTransient() {
        SerenityEsiClientException exception = SerenityEsiClient
            .classify(new ResourceAccessException("timeout", new SocketTimeoutException()));

        assertThat(exception.getFailureCode()).isEqualTo(OAuthFailureCode.TRANSIENT);
    }

    /** ESI 超时和限流状态必须保持授权可重试，不能误判为永久失效。 */
    @Test
    void shouldClassifyTimeoutAndRateLimitsAsTransient() {
        for (int status : new int[] {408, 420, 429}) {
            SerenityEsiClientException exception = SerenityEsiClient
                .classify(new HttpClientErrorException(HttpStatusCode.valueOf(status)));

            assertThat(exception.getFailureCode()).as("HTTP %s", status).isEqualTo(OAuthFailureCode.TRANSIENT);
        }
    }
}
