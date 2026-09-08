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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 国服 OAuth state 与 PKCE 安全工具。
 *
 * @author zhaoyuqing
 */
public final class OAuthSecurityUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private OAuthSecurityUtils() {
    }

    /** 生成至少 256 bit 熵的 state。 */
    public static String generateState() {
        return randomBase64Url(32);
    }

    /** 生成符合 RFC 7636 的高熵 code verifier。 */
    public static String generateCodeVerifier() {
        return randomBase64Url(32);
    }

    /**
     * 计算 PKCE S256 code challenge。
     *
     * @param verifier PKCE verifier
     * @return Base64URL 无填充 challenge
     */
    public static String createS256Challenge(String verifier) {
        if (verifier == null || verifier.length() < 43 || verifier.length() > 128) {
            throw new IllegalArgumentException("PKCE verifier 长度无效");
        }
        return sha256Base64Url(verifier);
    }

    /**
     * 对敏感索引值执行 SHA-256 后输出 Base64URL。
     *
     * @param value 原始值
     * @return 不可逆摘要
     */
    public static String sha256Base64Url(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("摘要输入不能为空");
        }
        try {
            return BASE64_URL.encodeToString(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("运行环境不支持 SHA-256", e);
        }
    }

    /** 生成指定字节长度的 Base64URL 随机值。 */
    private static String randomBase64Url(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return BASE64_URL.encodeToString(bytes);
    }
}
