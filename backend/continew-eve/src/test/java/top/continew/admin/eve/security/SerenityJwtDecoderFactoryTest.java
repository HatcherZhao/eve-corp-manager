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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import top.continew.admin.eve.config.SerenityProperties;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 国服 JWKS JWT 验签与声明校验测试。
 *
 * @author zhaoyuqing
 */
class SerenityJwtDecoderFactoryTest {

    private static final String ISSUER = "login.evepc.163.com";
    private static final String CLIENT_ID = "client-id";
    private static final String REQUIRED_SCOPE = "esi-characters.read_corporation_roles.v1";
    private static RSAKey rsaKey;
    private static ECKey ecKey;

    /** 生成测试签名密钥。 */
    @BeforeAll
    static void generateKeys() throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("rsa-key").generate();
        ecKey = new ECKeyGenerator(Curve.P_256).keyID("ec-key").generate();
    }

    /** 验证 RS256 身份令牌。 */
    @Test
    void shouldValidateRs256Token() throws Exception {
        String token = sign(rsaKey, JWSAlgorithm.RS256, claims(ISSUER, CLIENT_ID, REQUIRED_SCOPE, Instant.now()
            .plusSeconds(300)));
        Jwt jwt = decode(token, rsaKey.toPublicJWK());
        assertThat(jwt.getSubject()).isEqualTo("CHARACTER:EVE:123456");
    }

    /** 验证 ES256 身份令牌。 */
    @Test
    void shouldValidateEs256Token() throws Exception {
        String token = sign(ecKey, JWSAlgorithm.ES256, claims(ISSUER, CLIENT_ID, REQUIRED_SCOPE, Instant.now()
            .plusSeconds(300)));
        assertThat(decode(token, ecKey.toPublicJWK()).getAudience()).contains(CLIENT_ID);
    }

    /** 国服实际访问令牌可能不返回 audience，此时仍依赖签名、issuer、subject、Scope 与 OAuth 事务完成校验。 */
    @Test
    void shouldValidateTokenWithoutAudience() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder(claims(ISSUER, CLIENT_ID, REQUIRED_SCOPE, Instant.now()
            .plusSeconds(300))).audience(List.of()).build();
        Jwt jwt = decode(sign(rsaKey, JWSAlgorithm.RS256, claims), rsaKey.toPublicJWK());
        assertThat(jwt.getAudience()).isNull();
        assertThat(jwt.getSubject()).isEqualTo("CHARACTER:EVE:123456");
    }

    /** 验证错误 issuer、audience、subject、Scope 与过期时间均拒绝。 */
    @Test
    void shouldRejectInvalidClaims() throws Exception {
        assertRejected(sign(rsaKey, JWSAlgorithm.RS256, claims("wrong-issuer", CLIENT_ID, REQUIRED_SCOPE, Instant.now()
            .plusSeconds(300))), rsaKey.toPublicJWK());
        assertRejected(sign(rsaKey, JWSAlgorithm.RS256, claims(ISSUER, "wrong-client", REQUIRED_SCOPE, Instant.now()
            .plusSeconds(300))), rsaKey.toPublicJWK());
        assertRejected(sign(rsaKey, JWSAlgorithm.RS256, claims(ISSUER, CLIENT_ID, "wrong-scope", Instant.now()
            .plusSeconds(300))), rsaKey.toPublicJWK());
        assertRejected(sign(rsaKey, JWSAlgorithm.RS256, claims(ISSUER, CLIENT_ID, REQUIRED_SCOPE, Instant.now()
            .minusSeconds(300))), rsaKey.toPublicJWK());

        JWTClaimsSet invalidSubject = new JWTClaimsSet.Builder(claims(ISSUER, CLIENT_ID, REQUIRED_SCOPE, Instant.now()
            .plusSeconds(300))).subject("unexpected-subject").build();
        assertRejected(sign(rsaKey, JWSAlgorithm.RS256, invalidSubject), rsaKey.toPublicJWK());
    }

    /** 验证白名单外的 HS256 算法不会被接受。 */
    @Test
    void shouldRejectNonWhitelistedAlgorithm() throws Exception {
        byte[] secret = new byte[32];
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims(ISSUER, CLIENT_ID, REQUIRED_SCOPE, Instant
            .now()
            .plusSeconds(300)));
        jwt.sign(new MACSigner(new SecretKeySpec(secret, "HmacSHA256")));
        assertRejected(jwt.serialize(), rsaKey.toPublicJWK());
    }

    /** 创建标准测试声明。 */
    private static JWTClaimsSet claims(String issuer, String audience, String scope, Instant expiresAt) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder().issuer(issuer)
            .audience(audience)
            .subject("CHARACTER:EVE:123456")
            .claim("scp", List.of(scope))
            .issueTime(Date.from(now.minusSeconds(5)))
            .notBeforeTime(Date.from(now.minusSeconds(5)))
            .expirationTime(Date.from(expiresAt))
            .build();
    }

    /** 使用指定 JWK 和算法签名。 */
    private static String sign(JWK key, JWSAlgorithm algorithm, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(algorithm).keyID(key.getKeyID()).build(), claims);
        if (key instanceof RSAKey rsa) {
            jwt.sign(new RSASSASigner(rsa));
        } else if (key instanceof ECKey ec) {
            jwt.sign(new ECDSASigner(ec));
        }
        return jwt.serialize();
    }

    /** 使用 MockRestServiceServer 提供固定 JWKS 并解码。 */
    private static Jwt decode(String token, JWK publicKey) {
        SerenityProperties properties = jwtProperties();
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(properties.getSso().getJwksEndpoint()))
            .andRespond(withSuccess(new JWKSet(publicKey).toString(), MediaType.APPLICATION_JSON));
        Jwt result = new SerenityJwtDecoderFactory(properties).create(restTemplate).decode(token);
        server.verify();
        return result;
    }

    /** 断言令牌被严格拒绝。 */
    private static void assertRejected(String token, JWK publicKey) {
        assertThatThrownBy(() -> decode(token, publicKey)).isInstanceOf(JwtException.class);
    }

    /** 创建已知格式的严格 JWT 配置。 */
    private static SerenityProperties jwtProperties() {
        SerenityProperties properties = new SerenityProperties();
        SerenityProperties.Sso sso = properties.getSso();
        sso.setIssuer(ISSUER);
        sso.setClientId(CLIENT_ID);
        sso.setJwksEndpoint("https://login.example.test/oauth/jwks");
        sso.setSubjectPattern("CHARACTER:EVE:[0-9]+");
        sso.setScopeClaim("scp");
        sso.setScopeValuePattern("[a-z0-9._-]+");
        sso.setRequiredScopes(Set.of(REQUIRED_SCOPE));
        return properties;
    }
}
