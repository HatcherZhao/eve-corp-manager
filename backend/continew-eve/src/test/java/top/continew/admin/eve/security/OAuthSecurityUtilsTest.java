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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OAuth state 与 PKCE 工具测试。
 *
 * @author zhaoyuqing
 */
class OAuthSecurityUtilsTest {

    /** 验证 RFC 7636 附录 B 的 S256 示例向量。 */
    @Test
    void shouldMatchRfc7636S256Vector() {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        assertThat(OAuthSecurityUtils.createS256Challenge(verifier))
            .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    /** 验证生成值具有足够长度、URL 安全且不会重复。 */
    @Test
    void shouldGenerateHighEntropyUrlSafeValues() {
        String state = OAuthSecurityUtils.generateState();
        String verifier = OAuthSecurityUtils.generateCodeVerifier();
        assertThat(state).hasSizeGreaterThanOrEqualTo(43).matches("[A-Za-z0-9_-]+");
        assertThat(verifier).hasSizeBetween(43, 128).matches("[A-Za-z0-9_-]+").isNotEqualTo(state);
    }
}
