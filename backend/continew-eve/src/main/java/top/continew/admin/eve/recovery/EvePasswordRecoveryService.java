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

package top.continew.admin.eve.recovery;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.continew.admin.common.api.system.UserApi;
import top.continew.admin.eve.auth.OAuthTransaction;
import top.continew.admin.eve.auth.OAuthTransactionPurpose;
import top.continew.admin.eve.auth.OAuthTransactionStore;
import top.continew.admin.eve.auth.SerenityAuthorizationStart;
import top.continew.admin.eve.auth.SerenityAuthorizationStartService;
import top.continew.admin.eve.client.SerenityTokenClient;
import top.continew.admin.eve.client.SerenityTokenResponse;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.enums.EveCharacterStatus;
import top.continew.admin.eve.security.SerenityCallback;
import top.continew.admin.eve.security.SerenityCallbackUrlParser;
import top.continew.admin.eve.security.SerenityJwtDecoderFactory;
import top.continew.starter.extension.tenant.util.TenantUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 使用已绑定 EVE 角色验证身份并重置本站密码，不创建用户或角色绑定。
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EvePasswordRecoveryService {

    private static final Pattern CHARACTER_ID_SUFFIX = Pattern.compile("(?:^|:)([1-9][0-9]*)$");

    private final SerenityAuthorizationStartService authorizationStartService;
    private final OAuthTransactionStore transactionStore;
    private final SerenityCallbackUrlParser callbackParser;
    private final SerenityTokenClient tokenClient;
    private final SerenityJwtDecoderFactory jwtDecoderFactory;
    private final EveCharacterMapper characterMapper;
    private final PasswordRecoveryCredentialStore credentialStore;
    private final UserApi userApi;
    private final SerenityProperties properties;

    /** 发起未登录用户的 EVE 身份验证找回密码事务。 */
    public SerenityAuthorizationStart start(String browserBindingDigest) {
        return authorizationStartService
            .start(OAuthTransactionPurpose.RECOVER_PASSWORD, null, browserBindingDigest, properties.getSso()
                .getRequiredScopes());
    }

    /**
     * 验证 EVE 回调与历史唯一角色绑定，成功后签发短期一次性密码重置凭证。
     *
     * @param callbackUrl          固定回调完整地址
     * @param browserBindingDigest 当前浏览器绑定摘要
     * @return 不包含角色、用户、令牌或回调地址的凭证
     */
    public PasswordRecoveryCallbackResult verify(String callbackUrl, String browserBindingDigest) {
        SerenityCallback callback = callbackParser.parse(callbackUrl);
        OAuthTransaction transaction = transactionStore.consume(callback.state())
            .orElseThrow(() -> new IllegalStateException("EVE 密码找回授权已失效，请重新发起"));
        validateTransaction(transaction, browserBindingDigest, callback);
        SerenityTokenResponse token = tokenClient.exchangeCode(callback.code(), transaction.getVerifier());
        Jwt jwt = jwtDecoderFactory.create().decode(token.accessToken());
        Long characterId = parseCharacterId(jwt.getSubject());
        if (!readScopes(jwt.getClaim(properties.getSso().getScopeClaim())).containsAll(transaction
            .getRequestedScopes())) {
            throw new IllegalStateException("国服授权未授予所需权限，请重新授权并勾选全部权限");
        }
        EveCharacterDO character = characterMapper.selectByExternalId(properties.getEsi()
            .getDatasource()
            .getValue(), characterId);
        if (character == null || !EveCharacterStatus.ACTIVE.equals(character.getStatus()) || character
            .getTenantId() == null || character.getUserId() == null || !sameOwner(character.getOwnerHash(), jwt
                .getClaimAsString("owner"))) {
            throw new IllegalStateException("该 EVE 角色未绑定可找回的本站账号，请使用账号注册或联系军团管理员");
        }
        String credential = credentialStore.issue(browserBindingDigest, character.getTenantId(), character
            .getUserId(), properties.getPasswordRecovery().getCredentialTtl());
        return new PasswordRecoveryCallbackResult(credential, character.getName());
    }

    /**
     * 原子消费重置凭证并沿用本站用户 API 的密码编码和重置逻辑。
     *
     * @param credential           一次性重置凭证
     * @param browserBindingDigest 当前浏览器绑定摘要
     * @param password             已经 RSA 解密的新密码
     * @param confirmPassword      已经 RSA 解密的确认密码
     */
    @Transactional(rollbackFor = Exception.class)
    public void reset(String credential, String browserBindingDigest, String password, String confirmPassword) {
        if (password == null || password.isBlank() || confirmPassword == null || confirmPassword.isBlank()) {
            throw new IllegalArgumentException("新密码不能为空");
        }
        if (!MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8), confirmPassword
            .getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("两次输入的密码不一致");
        }
        PasswordRecoveryIdentity identity = credentialStore.consume(credential, browserBindingDigest)
            .orElseThrow(() -> new IllegalStateException("密码重置凭证已失效，请重新验证 EVE 角色"));
        TenantUtils.execute(identity.tenantId(), () -> userApi.resetPassword(password, identity.userId()));
        logoutAfterCommit(identity.userId());
    }

    /** 在密码事务提交后注销该用户全部本站会话，回滚时不产生会话副作用。 */
    private static void logoutAfterCommit(Long userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            StpUtil.logout(userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /** 仅在事务成功提交后注销全部会话。 */
            @Override
            public void afterCommit() {
                StpUtil.logout(userId);
            }
        });
    }

    /** 使用恒定时间比较已验签 owner 与历史角色所有者标识。 */
    private static boolean sameOwner(String expectedOwner, String actualOwner) {
        return expectedOwner != null && !expectedOwner.isBlank() && actualOwner != null && !actualOwner
            .isBlank() && MessageDigest.isEqual(expectedOwner.getBytes(StandardCharsets.UTF_8), actualOwner
                .getBytes(StandardCharsets.UTF_8));
    }

    /** 校验一次性事务用途、时效、浏览器绑定及上游错误。 */
    private static void validateTransaction(OAuthTransaction transaction,
                                            String browserBindingDigest,
                                            SerenityCallback callback) {
        boolean sameBrowser = browserBindingDigest != null && transaction
            .getBrowserBindingDigest() != null && MessageDigest.isEqual(transaction.getBrowserBindingDigest()
                .getBytes(StandardCharsets.US_ASCII), browserBindingDigest.getBytes(StandardCharsets.US_ASCII));
        if (transaction.getPurpose() != OAuthTransactionPurpose.RECOVER_PASSWORD || transaction
            .getBoundUserId() != null || transaction.getBoundTenantId() != null || transaction
                .getExpiresAt() == null || transaction.getExpiresAt().isBefore(Instant.now()) || callback
                    .hasError() || !sameBrowser) {
            throw new IllegalStateException("EVE 密码找回授权校验失败，请重新发起");
        }
    }

    /** 从已验签 subject 提取角色 ID。 */
    private static Long parseCharacterId(String subject) {
        Matcher matcher = CHARACTER_ID_SUFFIX.matcher(subject == null ? "" : subject);
        if (!matcher.find()) {
            throw new IllegalStateException("国服角色标识无效");
        }
        return Long.valueOf(matcher.group(1));
    }

    /** 读取字符串或字符串集合形式的 Scope。 */
    private static List<String> readScopes(Object claim) {
        if (claim instanceof String value) {
            return value.isBlank() ? List.of() : List.of(value.trim().split("\\s+"));
        }
        if (claim instanceof Collection<?> values && values.stream().allMatch(String.class::isInstance)) {
            return values.stream().map(String.class::cast).distinct().toList();
        }
        throw new IllegalStateException("国服授权 Scope 无效");
    }
}
