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

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;
import top.continew.admin.eve.config.SerenityProperties;

/**
 * 仅允许 RS256 与 ES256 的国服 JWKS JWT 解码器工厂。
 *
 * @author zhaoyuqing
 */
@Component
public class SerenityJwtDecoderFactory {

    private final SerenityProperties properties;

    /** 创建解码器工厂。 */
    public SerenityJwtDecoderFactory(SerenityProperties properties) {
        this.properties = properties;
    }

    /** 创建生产用 JWKS 解码器。 */
    public JwtDecoder create() {
        return create(null);
    }

    /**
     * 创建可注入固定 HTTP 实现的 JWKS 解码器。
     *
     * @param restOperations JWKS HTTP 实现，生产环境传 {@code null}
     * @return 严格 JWT 解码器
     */
    public JwtDecoder create(RestOperations restOperations) {
        SerenityProperties.Sso sso = properties.getSso();
        NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder builder = NimbusJwtDecoder.withJwkSetUri(sso.getJwksEndpoint())
            .jwsAlgorithms(algorithms -> {
                algorithms.clear();
                algorithms.add(SignatureAlgorithm.RS256);
                algorithms.add(SignatureAlgorithm.ES256);
            });
        if (restOperations != null) {
            builder.restOperations(restOperations);
        }
        NimbusJwtDecoder decoder = builder.build();
        DelegatingOAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(JwtValidators
            .createDefaultWithIssuer(sso.getIssuer()), new SerenityJwtClaimsValidator(sso));
        decoder.setJwtValidator(validator);
        return decoder;
    }
}
