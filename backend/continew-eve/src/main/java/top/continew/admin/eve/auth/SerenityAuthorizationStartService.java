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

package top.continew.admin.eve.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.security.OAuthSecurityUtils;
import top.continew.starter.core.exception.BusinessException;

import java.time.Instant;
import java.util.Set;

/**
 * 创建 Authorization Code + PKCE S256 事务，不建立任何本站或 Sa-Token 会话。
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class SerenityAuthorizationStartService {

    private final SerenityProperties properties;
    private final OAuthTransactionStore transactionStore;

    /**
     * 创建并保存一次性授权事务。
     *
     * @param purpose              事务用途
     * @param boundUserId          已登录用户 ID，注册场景为空
     * @param browserBindingDigest 浏览器绑定摘要
     * @param requestedScopes      请求 Scope
     * @return 脱敏授权启动结果
     */
    public SerenityAuthorizationStart start(OAuthTransactionPurpose purpose,
                                            Long boundUserId,
                                            String browserBindingDigest,
                                            Set<String> requestedScopes) {
        return start(purpose, null, boundUserId, null, null, browserBindingDigest, requestedScopes);
    }

    /**
     * 创建严格绑定租户、用户、服务器和角色的重新授权事务。
     *
     * @param purpose              事务用途
     * @param boundTenantId        绑定租户 ID
     * @param boundUserId          绑定用户 ID
     * @param boundServer          绑定服务器代码
     * @param boundCharacterId     绑定游戏角色 ID
     * @param browserBindingDigest 浏览器绑定摘要
     * @param requestedScopes      请求 Scope
     * @return 脱敏授权启动结果
     */
    public SerenityAuthorizationStart start(OAuthTransactionPurpose purpose,
                                            Long boundTenantId,
                                            Long boundUserId,
                                            String boundServer,
                                            Long boundCharacterId,
                                            String browserBindingDigest,
                                            Set<String> requestedScopes) {
        SerenityProperties.Sso sso = properties.getSso();
        if (!sso.isEnabled()) {
            throw new BusinessException("国服授权通道暂未启用，请联系管理员");
        }
        boolean reauthorizationBindingValid = purpose != OAuthTransactionPurpose.EXPAND_SCOPES || boundTenantId != null && boundServer != null && !boundServer
            .isBlank() && boundCharacterId != null;
        boolean accountBindingValid = !requiresBoundUser(purpose) || boundTenantId != null;
        if (purpose == null || browserBindingDigest == null || browserBindingDigest
            .isBlank() || requestedScopes == null || requestedScopes.isEmpty() || !requestedScopes.containsAll(sso
                .getRequiredScopes()) || (requiresBoundUser(purpose) && boundUserId == null) || !accountBindingValid || !reauthorizationBindingValid) {
            throw new BusinessException("国服授权请求无效，请重新发起");
        }
        String state = OAuthSecurityUtils.generateState();
        String verifier = OAuthSecurityUtils.generateCodeVerifier();
        String challenge = OAuthSecurityUtils.createS256Challenge(verifier);
        OAuthTransaction transaction = OAuthTransaction.builder()
            .browserBindingDigest(browserBindingDigest)
            .purpose(purpose)
            .boundUserId(boundUserId)
            .boundTenantId(boundTenantId)
            .boundServer(boundServer)
            .boundCharacterId(boundCharacterId)
            .redirectUri(resultPath(purpose))
            .requestedScopes(Set.copyOf(requestedScopes))
            .verifier(verifier)
            .expiresAt(Instant.now().plus(sso.getTransactionTtl()))
            .build();
        transactionStore.save(state, transaction, sso.getTransactionTtl());
        UriComponentsBuilder authorizationBuilder = UriComponentsBuilder.fromUriString(sso.getAuthorizationEndpoint())
            .queryParam("response_type", "code")
            .queryParam("client_id", sso.getClientId())
            .queryParam("redirect_uri", sso.getCallbackUrl())
            .queryParam("device_id", sso.getDeviceId())
            .queryParam("scope", String.join(" ", requestedScopes))
            .queryParam("state", state)
            .queryParam("code_challenge", challenge)
            .queryParam("code_challenge_method", "S256");
        if (purpose == OAuthTransactionPurpose.REGISTER) {
            // 新用户注册必须要求网易重新认证，避免沿用浏览器中其他人的网易登录态。
            authorizationBuilder.queryParam("relogin", "1");
        }
        String authorizationUri = authorizationBuilder.build().encode().toUriString();
        return new SerenityAuthorizationStart(authorizationUri);
    }

    /** 返回用途对应的站内相对结果页。 */
    private static String resultPath(OAuthTransactionPurpose purpose) {
        return switch (purpose) {
            case REGISTER -> "/login/eve/result";
            case RECOVER_PASSWORD -> "/login/eve/password-recovery";
            case BIND_CHARACTER, EXPAND_SCOPES -> "/profile/eve/result";
        };
    }

    /** 判断该授权用途是否必须由已有本站会话发起。 */
    private static boolean requiresBoundUser(OAuthTransactionPurpose purpose) {
        return purpose == OAuthTransactionPurpose.BIND_CHARACTER || purpose == OAuthTransactionPurpose.EXPAND_SCOPES;
    }
}
