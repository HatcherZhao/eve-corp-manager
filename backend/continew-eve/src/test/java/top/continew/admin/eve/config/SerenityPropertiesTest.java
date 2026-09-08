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

package top.continew.admin.eve.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import top.continew.admin.eve.model.serenity.SerenityDatasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 国服契约配置测试。
 *
 * @author zhaoyuqing
 */
class SerenityPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfiguration.class);

    /** 验证默认值与国服契约快照一致。 */
    @Test
    void shouldProvideContractDefaults() {
        contextRunner.run(context -> {
            SerenityProperties properties = context.getBean(SerenityProperties.class);
            assertThat(properties.getSso().getIssuer()).isEqualTo("login.evepc.163.com");
            assertThat(properties.getSso().getAuthorizationEndpoint())
                .isEqualTo("https://login.evepc.163.com/v2/oauth/authorize");
            assertThat(properties.getSso().getTokenEndpoint()).isEqualTo("https://login.evepc.163.com/v2/oauth/token");
            assertThat(properties.getSso().getJwksEndpoint()).isEqualTo("https://login.evepc.163.com/oauth/jwks");
            assertThat(properties.getSso().getRevocationEndpoint())
                .isEqualTo("https://login.evepc.163.com/v2/oauth/revoke");
            assertThat(properties.getSso().getDeviceId()).isEqualTo("eve-corp-manager");
            assertThat(properties.getEsi().getBaseUrl()).isEqualTo("https://ali-esi.evepc.163.com/latest");
            assertThat(properties.getEsi().getDatasource()).isEqualTo(SerenityDatasource.SERENITY);
            assertThat(properties.getHttpClient().getConnectTimeout()).isEqualTo(java.time.Duration.ofSeconds(5));
            assertThat(properties.getHttpClient().getReadTimeout()).isEqualTo(java.time.Duration.ofSeconds(15));
        });
    }

    /** 验证端点与 datasource 可由外部配置覆盖。 */
    @Test
    void shouldBindOverrides() {
        contextRunner
            .withPropertyValues("eve.serenity.esi.base-url=https://example.test/latest", "eve.serenity.esi.datasource=infinity", "eve.serenity.sso.issuer=example.test")
            .run(context -> {
                SerenityProperties properties = context.getBean(SerenityProperties.class);
                assertThat(properties.getEsi().getBaseUrl()).isEqualTo("https://example.test/latest");
                assertThat(properties.getEsi().getDatasource()).isEqualTo(SerenityDatasource.INFINITY);
                assertThat(properties.getSso().getIssuer()).isEqualTo("example.test");
            });
    }

    /** 验证启用 OAuth 时缺少安全关键配置会立即失败。 */
    @Test
    void shouldFailFastWhenEnabledConfigurationIsIncomplete() {
        SerenityProperties properties = new SerenityProperties();
        properties.getSso().setEnabled(true);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("client-id");
    }

    /** 验证 Swagger 公共客户端启用时无需配置客户端密钥。 */
    @Test
    void shouldAllowPublicClientWithoutSecret() {
        SerenityProperties properties = completeEnabledProperties("https://ali-esi.evepc.163.com/ui/oauth2-redirect.html");
        properties.getSso().setClientSecret(null);

        properties.validate();

        assertThat(properties.getSso().hasClientSecret()).isFalse();
    }

    /** 验证设备标识只能使用可安全放入授权查询参数的短标识。 */
    @Test
    void shouldRejectInvalidDeviceId() {
        SerenityProperties properties = completeEnabledProperties("https://ali-esi.evepc.163.com/ui/oauth2-redirect.html");
        properties.getSso().setDeviceId("invalid device");

        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class).hasMessageContaining("设备标识");
    }

    /** 验证外部 HTTP 回调始终拒绝，本地回环 HTTP 仅在显式开启时允许。 */
    @Test
    void shouldOnlyAllowExplicitLoopbackHttpCallback() {
        SerenityProperties properties = completeEnabledProperties("http://outside.example.test/oauth/callback");
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);

        String loopback = "http://127.0.0.1:8000/api/eve/oauth/callback";
        properties = completeEnabledProperties(loopback);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
        properties.getSso().setAllowInsecureLocalhostCallback(true);
        MockEnvironment localEnvironment = new MockEnvironment();
        localEnvironment.setActiveProfiles("local");
        properties.setEnvironment(localEnvironment);
        properties.validate();
    }

    /** 创建完整的启用配置。 */
    private static SerenityProperties completeEnabledProperties(String callbackUrl) {
        SerenityProperties properties = new SerenityProperties();
        SerenityProperties.Sso sso = properties.getSso();
        sso.setEnabled(true);
        sso.setClientId("client-id");
        sso.setClientSecret("client-secret");
        sso.setCallbackUrl(callbackUrl);
        sso.setAllowedCallbackUris(java.util.Set.of(callbackUrl));
        sso.setSubjectPattern("CHARACTER:EVE:[0-9]+");
        sso.setScopeClaim("scp");
        sso.setScopeValuePattern("[a-z0-9._-]+");
        return properties;
    }

    /** 测试专用配置。 */
    @EnableConfigurationProperties(SerenityProperties.class)
    static class TestConfiguration {
    }
}
