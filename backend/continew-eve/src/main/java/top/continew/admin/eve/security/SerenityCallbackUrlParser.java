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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import top.continew.admin.eve.config.SerenityProperties;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 不发起网络访问的国服 OAuth 回调 URL 严格解析器。
 *
 * @author zhaoyuqing
 */
@Component
@RequiredArgsConstructor
public class SerenityCallbackUrlParser {

    private static final int MAX_PARAMETER_LENGTH = 1024;

    private final SerenityProperties properties;

    /**
     * 校验并解析完整回调 URL。
     *
     * @param callbackUrl 浏览器返回的完整 URL
     * @return 已校验参数
     */
    public SerenityCallback parse(String callbackUrl) {
        SerenityProperties.Sso config = properties.getSso();
        if (callbackUrl == null || callbackUrl.isBlank() || callbackUrl.length() > config.getMaxCallbackUrlLength()) {
            throw invalid();
        }
        URI actual = parseUri(callbackUrl);
        URI expected = parseUri(config.getCallbackUrl());
        if (actual.getFragment() != null || actual.getUserInfo() != null || !sameEndpoint(actual, expected)) {
            throw invalid();
        }
        Map<String, String> params = parseQuery(actual.getRawQuery());
        String code = params.get("code");
        String state = params.get("state");
        String error = params.get("error");
        if (state == null || (code == null) == (error == null)) {
            throw invalid();
        }
        return new SerenityCallback(code, state, error);
    }

    /** 安全解析 URI，不将原始值写入异常。 */
    private static URI parseUri(String value) {
        try {
            return URI.create(value);
        } catch (RuntimeException e) {
            throw invalid();
        }
    }

    /** 精确比较 scheme、host、port 与 path。 */
    private static boolean sameEndpoint(URI actual, URI expected) {
        return equalsIgnoreCase(actual.getScheme(), expected.getScheme()) && equalsIgnoreCase(actual.getHost(), expected
            .getHost()) && effectivePort(actual) == effectivePort(expected) && safeEquals(actual.getRawPath(), expected
                .getRawPath());
    }

    /** 解析查询参数并拒绝重复安全参数。 */
    private static Map<String, String> parseQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            throw invalid();
        }
        Map<String, String> params = new HashMap<>();
        for (String pair : rawQuery.split("&", -1)) {
            int separator = pair.indexOf('=');
            String rawName = separator < 0 ? pair : pair.substring(0, separator);
            String rawValue = separator < 0 ? "" : pair.substring(separator + 1);
            String name = decode(rawName);
            if (!"code".equals(name) && !"state".equals(name) && !"error".equals(name)) {
                continue;
            }
            String value = decode(rawValue);
            if (value.isBlank() || value.length() > MAX_PARAMETER_LENGTH || containsControl(value) || params
                .putIfAbsent(name, value) != null) {
                throw invalid();
            }
        }
        return params;
    }

    /** 严格解码查询组件。 */
    private static String decode(String value) {
        try {
            return UriUtils.decode(value, StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            throw invalid();
        }
    }

    /** 返回 URI 的有效端口。 */
    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /** 判断是否包含控制字符。 */
    private static boolean containsControl(String value) {
        return value.chars().anyMatch(character -> Character.isISOControl(character));
    }

    /** 忽略大小写比较。 */
    private static boolean equalsIgnoreCase(String first, String second) {
        return first != null && second != null && first.equalsIgnoreCase(second);
    }

    /** 空值安全的精确比较。 */
    private static boolean safeEquals(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    /** 创建不包含输入内容的固定异常。 */
    private static SerenityCallbackException invalid() {
        return new SerenityCallbackException("国服 OAuth 回调校验失败");
    }
}
