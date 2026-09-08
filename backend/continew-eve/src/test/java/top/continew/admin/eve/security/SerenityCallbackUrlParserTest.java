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

package top.continew.admin.eve.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import top.continew.admin.eve.config.SerenityProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 国服 OAuth 回调 URL 攻击面测试。
 *
 * @author zhaoyuqing
 */
class SerenityCallbackUrlParserTest {

    private SerenityCallbackUrlParser parser;

    /** 初始化固定回调地址。 */
    @BeforeEach
    void setUp() {
        SerenityProperties properties = new SerenityProperties();
        properties.getSso().setCallbackUrl("https://app.example.test/api/eve/oauth/callback");
        parser = new SerenityCallbackUrlParser(properties);
    }

    /** 验证合法授权码回调。 */
    @Test
    void shouldParseValidCodeCallback() {
        SerenityCallback callback = parser
            .parse("https://app.example.test/api/eve/oauth/callback?code=abc-123&state=state-123");
        assertThat(callback.code()).isEqualTo("abc-123");
        assertThat(callback.state()).isEqualTo("state-123");
        assertThat(callback.hasError()).isFalse();
        assertThat(callback.toString()).doesNotContain("abc-123", "state-123");
    }

    /** 验证合法错误回调仍必须携带 state。 */
    @Test
    void shouldParseValidErrorCallback() {
        SerenityCallback callback = parser
            .parse("https://app.example.test/api/eve/oauth/callback?error=access_denied&state=state-123");
        assertThat(callback.error()).isEqualTo("access_denied");
        assertThat(callback.hasError()).isTrue();
    }

    /** 验证重复参数、端点混淆、混合结果和片段令牌均被拒绝。 */
    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example/api/eve/oauth/callback?code=a&state=b",
        "http://app.example.test/api/eve/oauth/callback?code=a&state=b",
        "https://app.example.test:444/api/eve/oauth/callback?code=a&state=b",
        "https://app.example.test/api/eve/oauth/other?code=a&state=b",
        "https://app.example.test/api/eve/oauth/callback?code=a&code=b&state=c",
        "https://app.example.test/api/eve/oauth/callback?code=a&state=b&state=c",
        "https://app.example.test/api/eve/oauth/callback?code=a&error=denied&state=b",
        "https://app.example.test/api/eve/oauth/callback?code=a&state=b#access_token=secret",
        "https://app.example.test/api/eve/oauth/callback?code=a", "https://app.example.test/api/eve/oauth/callback"})
    void shouldRejectMaliciousCallbacks(String url) {
        assertThatThrownBy(() -> parser.parse(url)).isInstanceOf(SerenityCallbackException.class)
            .hasMessage("国服 OAuth 回调校验失败")
            .hasMessageNotContaining(url);
    }
}
