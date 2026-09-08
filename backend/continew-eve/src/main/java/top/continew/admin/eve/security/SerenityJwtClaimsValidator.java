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

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import top.continew.admin.eve.config.SerenityProperties;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 国服 JWT audience、subject 与 Scope 严格校验器。
 *
 * @author zhaoyuqing
 */
public class SerenityJwtClaimsValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID = new OAuth2Error("invalid_token", "国服身份令牌声明无效", null);

    private final String clientId;
    private final String scopeClaim;
    private final Pattern subjectPattern;
    private final Pattern scopeValuePattern;
    private final Set<String> requiredScopes;

    /** 使用已通过 fail-fast 校验的配置创建声明校验器。 */
    public SerenityJwtClaimsValidator(SerenityProperties.Sso properties) {
        this.clientId = properties.getClientId();
        this.scopeClaim = properties.getScopeClaim();
        this.subjectPattern = Pattern.compile(properties.getSubjectPattern());
        this.scopeValuePattern = Pattern.compile(properties.getScopeValuePattern());
        this.requiredScopes = Set.copyOf(properties.getRequiredScopes());
    }

    /** {@inheritDoc} */
    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> audience = token.getAudience();
        if (audience != null && !audience.isEmpty() && !audience.contains(clientId) || token
            .getSubject() == null || !subjectPattern.matcher(token.getSubject()).matches()) {
            return OAuth2TokenValidatorResult.failure(INVALID);
        }
        List<String> scopes = readScopes(token.getClaim(scopeClaim));
        if (scopes.isEmpty() || !scopes.containsAll(requiredScopes) || scopes.stream()
            .anyMatch(scope -> !scopeValuePattern.matcher(scope).matches())) {
            return OAuth2TokenValidatorResult.failure(INVALID);
        }
        return OAuth2TokenValidatorResult.success();
    }

    /** 严格读取字符串或字符串集合形式的 Scope。 */
    private static List<String> readScopes(Object claim) {
        if (claim instanceof String value) {
            return value.isBlank() ? List.of() : List.of(value.split(" "));
        }
        if (claim instanceof Collection<?> values && values.stream().allMatch(String.class::isInstance)) {
            return values.stream().map(String.class::cast).toList();
        }
        return List.of();
    }
}
