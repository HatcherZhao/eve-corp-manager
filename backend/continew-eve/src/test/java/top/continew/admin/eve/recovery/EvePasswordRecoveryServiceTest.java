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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.continew.admin.common.api.system.UserApi;
import top.continew.admin.eve.auth.OAuthTransaction;
import top.continew.admin.eve.auth.OAuthTransactionPurpose;
import top.continew.admin.eve.auth.OAuthTransactionStore;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EVE 身份验证找回本站密码安全流程测试。
 *
 * @author zhaoyuqing
 */
class EvePasswordRecoveryServiceTest {

    private OAuthTransactionStore transactionStore;
    private SerenityTokenClient tokenClient;
    private JwtDecoder decoder;
    private EveCharacterMapper characterMapper;
    private PasswordRecoveryCredentialStore credentialStore;
    private UserApi userApi;
    private EvePasswordRecoveryService service;

    /** 初始化严格浏览器绑定的找回事务。 */
    @BeforeEach
    void setUp() {
        transactionStore = mock(OAuthTransactionStore.class);
        tokenClient = mock(SerenityTokenClient.class);
        SerenityJwtDecoderFactory decoderFactory = mock(SerenityJwtDecoderFactory.class);
        decoder = mock(JwtDecoder.class);
        characterMapper = mock(EveCharacterMapper.class);
        credentialStore = mock(PasswordRecoveryCredentialStore.class);
        userApi = mock(UserApi.class);
        SerenityCallbackUrlParser callbackParser = mock(SerenityCallbackUrlParser.class);
        SerenityProperties properties = new SerenityProperties();
        properties.getSso().setScopeClaim("scp");
        properties.getPasswordRecovery().setCredentialTtl(Duration.ofMinutes(5));
        OAuthTransaction transaction = transaction();
        when(callbackParser.parse("callback-url")).thenReturn(new SerenityCallback("code", "state", null));
        when(transactionStore.consume("state")).thenReturn(Optional.of(transaction));
        when(tokenClient.exchangeCode("code", transaction.getVerifier()))
            .thenReturn(new SerenityTokenResponse("access", "refresh", "Bearer", 1200, null));
        when(decoderFactory.create()).thenReturn(decoder);
        when(decoder.decode("access")).thenReturn(jwt(1001L));
        service = new EvePasswordRecoveryService(mock(SerenityAuthorizationStartService.class), transactionStore, callbackParser, tokenClient, decoderFactory, characterMapper, credentialStore, userApi, properties);
    }

    /** 已绑定有效角色只能签发浏览器绑定的一次性重置凭证。 */
    @Test
    void shouldIssueCredentialForExistingCharacterBinding() {
        EveCharacterDO character = boundCharacter(10L, 20L);
        when(characterMapper.selectByExternalId("serenity", 1001L)).thenReturn(character);
        when(credentialStore.issue("browser", 10L, 20L, Duration.ofMinutes(5))).thenReturn("credential");

        PasswordRecoveryCallbackResult result = service.verify("callback-url", "browser");

        assertThat(result.credential()).isEqualTo("credential");
        assertThat(result.characterName()).isEqualTo("Pilot_1001");
        assertThat(result.toString()).doesNotContain("credential-value");
    }

    /** 角色 ID 相同但 owner 已变化时不得找回历史本站账号。 */
    @Test
    void shouldRejectChangedCharacterOwner() {
        EveCharacterDO character = boundCharacter(10L, 20L);
        character.setOwnerHash("historical-owner");
        when(characterMapper.selectByExternalId("serenity", 1001L)).thenReturn(character);

        assertThatThrownBy(() -> service.verify("callback-url", "browser")).isInstanceOf(IllegalStateException.class)
            .hasMessage("该 EVE 角色未绑定可找回的本站账号，请使用账号注册或联系军团管理员");

        verify(credentialStore, never()).issue(any(), any(), any(), any());
    }

    /** 跨浏览器回调必须在换取令牌前被拒绝。 */
    @Test
    void shouldRejectCrossBrowserCallbackBeforeTokenExchange() {
        assertThatThrownBy(() -> service.verify("callback-url", "other-browser"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("EVE 密码找回授权校验失败，请重新发起");

        verify(tokenClient, never()).exchangeCode(any(), any());
    }

    /** 过期事务必须拒绝且不能查询角色绑定。 */
    @Test
    void shouldRejectExpiredTransaction() {
        when(transactionStore.consume("state")).thenReturn(Optional.of(OAuthTransaction.builder()
            .purpose(OAuthTransactionPurpose.RECOVER_PASSWORD)
            .browserBindingDigest("browser")
            .requestedScopes(Set.of("esi-characters.read_corporation_roles.v1"))
            .verifier("v".repeat(43))
            .expiresAt(Instant.now().minusSeconds(1))
            .build()));

        assertThatThrownBy(() -> service.verify("callback-url", "browser")).isInstanceOf(IllegalStateException.class)
            .hasMessage("EVE 密码找回授权校验失败，请重新发起");

        verify(characterMapper, never()).selectByExternalId(any(), any());
    }

    /** 未绑定角色不得借注册流程或任意用户 ID 重置密码。 */
    @Test
    void shouldRejectUnboundCharacter() {
        when(characterMapper.selectByExternalId("serenity", 1001L)).thenReturn(null);

        assertThatThrownBy(() -> service.verify("callback-url", "browser")).isInstanceOf(IllegalStateException.class)
            .hasMessage("该 EVE 角色未绑定可找回的本站账号，请使用账号注册或联系军团管理员");

        verify(credentialStore, never()).issue(any(), any(), any(), any());
    }

    /** 两次密码不一致不能消费重置凭证；成功后凭证只能使用一次。 */
    @Test
    void shouldKeepCredentialForMismatchAndConsumeItOnlyOnce() {
        assertThatThrownBy(() -> service.reset("credential", "browser", "new-password", "different"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("两次输入的密码不一致");
        verify(credentialStore, never()).consume(any(), any());

        when(credentialStore.consume("credential", "browser")).thenReturn(Optional
            .of(new PasswordRecoveryIdentity(10L, 20L)), Optional.empty());
        try (org.mockito.MockedStatic<TenantUtils> tenantUtils = org.mockito.Mockito.mockStatic(TenantUtils.class);
            org.mockito.MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            tenantUtils.when(() -> TenantUtils.execute(org.mockito.ArgumentMatchers
                .eq(10L), org.mockito.ArgumentMatchers.any(Runnable.class))).thenAnswer(invocation -> {
                    invocation.<Runnable>getArgument(1).run();
                    return null;
                });
            service.reset("credential", "browser", "new-password", "new-password");
            assertThatThrownBy(() -> service.reset("credential", "browser", "new-password", "new-password"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("密码重置凭证已失效，请重新验证 EVE 角色");
        }
        verify(userApi).resetPassword("new-password", 20L);
    }

    /** 密码事务提交后才注销全部会话。 */
    @Test
    void shouldLogoutAllSessionsOnlyAfterCommit() {
        when(credentialStore.consume("credential", "browser")).thenReturn(Optional
            .of(new PasswordRecoveryIdentity(10L, 20L)));
        TransactionSynchronizationManager.initSynchronization();
        try (org.mockito.MockedStatic<TenantUtils> tenantUtils = mockStatic(TenantUtils.class);
            org.mockito.MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            tenantUtils.when(() -> TenantUtils.execute(eq(10L), any(Runnable.class))).thenAnswer(invocation -> {
                invocation.<Runnable>getArgument(1).run();
                return null;
            });

            service.reset("credential", "browser", "new-password", "new-password");
            stpUtil.verify(() -> StpUtil.logout(20L), never());
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            stpUtil.verify(() -> StpUtil.logout(20L));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 密码事务回滚时不得注销现有会话。 */
    @Test
    void shouldKeepSessionsWhenPasswordResetRollsBack() {
        when(credentialStore.consume("credential", "browser")).thenReturn(Optional
            .of(new PasswordRecoveryIdentity(10L, 20L)));
        TransactionSynchronizationManager.initSynchronization();
        try (org.mockito.MockedStatic<TenantUtils> tenantUtils = mockStatic(TenantUtils.class);
            org.mockito.MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            tenantUtils.when(() -> TenantUtils.execute(eq(10L), any(Runnable.class))).thenAnswer(invocation -> {
                invocation.<Runnable>getArgument(1).run();
                return null;
            });
            org.mockito.Mockito.doThrow(new IllegalStateException("rollback"))
                .when(userApi)
                .resetPassword("new-password", 20L);

            assertThatThrownBy(() -> service.reset("credential", "browser", "new-password", "new-password"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("rollback");
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
            stpUtil.verify(() -> StpUtil.logout(20L), never());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 创建当前浏览器的未登录找回事务。 */
    private static OAuthTransaction transaction() {
        return OAuthTransaction.builder()
            .purpose(OAuthTransactionPurpose.RECOVER_PASSWORD)
            .browserBindingDigest("browser")
            .requestedScopes(Set.of("esi-characters.read_corporation_roles.v1"))
            .verifier("v".repeat(43))
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
    }

    /** 创建已绑定的有效 EVE 角色。 */
    private static EveCharacterDO boundCharacter(Long tenantId, Long userId) {
        EveCharacterDO character = new EveCharacterDO();
        character.setTenantId(tenantId);
        character.setUserId(userId);
        character.setOwnerHash("owner-hash");
        character.setName("Pilot_1001");
        character.setStatus(EveCharacterStatus.ACTIVE);
        return character;
    }

    /** 创建带所需 Scope 的已验签 JWT。 */
    private static Jwt jwt(Long characterId) {
        return Jwt.withTokenValue("access")
            .header("alg", "RS256")
            .subject("CHARACTER:EVE:" + characterId)
            .claim("owner", "owner-hash")
            .claim("scp", List.of("esi-characters.read_corporation_roles.v1"))
            .build();
    }
}
