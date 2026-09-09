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

package top.continew.admin.eve.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.auth.OAuthTransaction;
import top.continew.admin.eve.auth.OAuthTransactionPurpose;
import top.continew.admin.eve.auth.OAuthTransactionStore;
import top.continew.admin.eve.auth.SerenityAuthorizationStartService;
import top.continew.admin.eve.auth.SerenityAuthorizationStart;
import top.continew.admin.eve.client.SerenityTokenClient;
import top.continew.admin.eve.client.SerenityTokenResponse;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.mapper.EveAuthAuditMapper;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.model.EvePermissionRefreshResp;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.model.enums.EvePermissionRefreshStatus;
import top.continew.admin.eve.security.SerenityCallback;
import top.continew.admin.eve.security.SerenityCallbackUrlParser;
import top.continew.admin.eve.security.SerenityJwtDecoderFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 严格重新授权流程测试。
 *
 * @author zhaoyuqing
 */
class EvePermissionReauthorizationServiceTest {

    private EveAuthorizationTokenService tokenService;
    private EvePermissionRefreshService refreshService;
    private SerenityTokenClient tokenClient;
    private JwtDecoder jwtDecoder;
    private SerenityAuthorizationStartService authorizationStartService;
    private EvePermissionReauthorizationService service;

    /** 初始化原角色、原授权和一次性 OAuth 事务。 */
    @BeforeEach
    void setUp() throws InterruptedException {
        EveCharacterMapper characterMapper = mock(EveCharacterMapper.class);
        EveAuthorizationMapper authorizationMapper = mock(EveAuthorizationMapper.class);
        OAuthTransactionStore transactionStore = mock(OAuthTransactionStore.class);
        SerenityCallbackUrlParser callbackParser = mock(SerenityCallbackUrlParser.class);
        authorizationStartService = mock(SerenityAuthorizationStartService.class);
        tokenClient = mock(SerenityTokenClient.class);
        SerenityJwtDecoderFactory decoderFactory = mock(SerenityJwtDecoderFactory.class);
        jwtDecoder = mock(JwtDecoder.class);
        tokenService = mock(EveAuthorizationTokenService.class);
        refreshService = mock(EvePermissionRefreshService.class);
        SerenityProperties properties = new SerenityProperties();
        properties.getSso().setScopeClaim("scp");

        EveCharacterDO character = new EveCharacterDO();
        character.setId(100L);
        character.setTenantId(10L);
        character.setUserId(20L);
        character.setServer("serenity");
        character.setCharacterId(8001L);
        character.setOwnerHash("owner-hash");
        when(characterMapper.selectActiveByUser(10L, 20L)).thenReturn(List.of(character));
        EveAuthorizationDO authorization = new EveAuthorizationDO();
        authorization.setId(400L);
        authorization.setTenantId(10L);
        authorization.setUserId(20L);
        authorization.setCharacterRefId(100L);
        authorization.setStatus(EveAuthorizationStatus.ACTIVE);
        authorization.setScopes(List.of("esi-characters.read_corporation_roles.v1"));
        when(authorizationMapper.selectByUserCharacter(10L, 20L, 100L)).thenReturn(List.of(authorization));
        when(authorizationMapper.selectOwnedById(10L, 20L, 400L)).thenReturn(authorization);

        OAuthTransaction transaction = OAuthTransaction.builder()
            .purpose(OAuthTransactionPurpose.EXPAND_SCOPES)
            .boundTenantId(10L)
            .boundUserId(20L)
            .boundServer("serenity")
            .boundCharacterId(8001L)
            .browserBindingDigest("browser-digest")
            .requestedScopes(Set.of("esi-characters.read_corporation_roles.v1"))
            .verifier("verifier-value-with-at-least-forty-three-characters-123")
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
        when(callbackParser.parse("callback-url")).thenReturn(new SerenityCallback("code", "state", null));
        when(transactionStore.consume("state")).thenReturn(Optional.of(transaction));
        when(decoderFactory.create()).thenReturn(jwtDecoder);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock userLock = mock(RLock.class);
        RLock authorizationLock = mock(RLock.class);
        when(redissonClient.getLock("eve:serenity:permission-refresh:10:20")).thenReturn(userLock);
        when(redissonClient.getLock("eve:serenity:authorization-refresh:400")).thenReturn(authorizationLock);
        when(userLock.tryLock(anyLong(), eq(java.util.concurrent.TimeUnit.MILLISECONDS))).thenReturn(true);
        when(authorizationLock.tryLock(anyLong(), eq(java.util.concurrent.TimeUnit.MILLISECONDS))).thenReturn(true);
        when(userLock.isHeldByCurrentThread()).thenReturn(true);
        when(authorizationLock.isHeldByCurrentThread()).thenReturn(true);
        service = new EvePermissionReauthorizationService(characterMapper, authorizationMapper, mock(EveAuthAuditMapper.class), authorizationStartService, transactionStore, callbackParser, tokenClient, decoderFactory, tokenService, refreshService, properties, redissonClient, new EveAuthorizationScopePolicy(properties));
    }

    /** 重新授权必须一次性索取已确认军团运营功能的完整 Scope 包。 */
    @Test
    void shouldRequestAllPlannedScopes() {
        SerenityAuthorizationStart expected = new SerenityAuthorizationStart("https://login.example.test");
        when(authorizationStartService
            .start(eq(OAuthTransactionPurpose.EXPAND_SCOPES), eq(10L), eq(20L), eq("serenity"), eq(8001L), eq("browser-digest"), any()))
            .thenReturn(expected);

        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(context());
            assertThat(service.startCurrent("browser-digest")).isSameAs(expected);
        }

        @SuppressWarnings("unchecked") org.mockito.ArgumentCaptor<Set<String>> scopes = org.mockito.ArgumentCaptor
            .forClass(Set.class);
        verify(authorizationStartService)
            .start(eq(OAuthTransactionPurpose.EXPAND_SCOPES), eq(10L), eq(20L), eq("serenity"), eq(8001L), eq("browser-digest"), scopes
                .capture());
        assertThat(scopes.getValue())
            .contains("esi-characters.read_corporation_roles.v1", "esi-assets.read_corporation_assets.v1", "esi-corporations.read_divisions.v1", "esi-corporations.track_members.v1", "esi-universe.read_structures.v1", "esi-mail.send_mail.v1");
    }

    /** 同一角色重新授权成功后原子替换令牌并立即刷新权限，不创建新账号。 */
    @Test
    void shouldReplaceScopesAndRefreshExistingAccount() {
        SerenityTokenResponse token = new SerenityTokenResponse("access", "refresh", "Bearer", 1200, null);
        when(tokenClient.exchangeCode(any(), any())).thenReturn(token);
        when(jwtDecoder.decode("access")).thenReturn(jwt(8001L));
        EvePermissionRefreshResp expected = new EvePermissionRefreshResp(EvePermissionRefreshStatus.REFRESHED, LocalDateTime
            .now(), null, null, false, null, null, List.of(), null, List.of(), List.of(), null, List.of());
        when(refreshService.refresh(10L, 20L, true)).thenReturn(expected);
        UserContext context = context();

        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(context);
            assertThat(service.handleCurrent("callback-url", "browser-digest")).isSameAs(expected);
        }

        verify(tokenService)
            .replaceTokenResponse(10L, 20L, 400L, new SerenityTokenResponse("access", "refresh", "Bearer", 1200, "esi-characters.read_corporation_roles.v1"), EveAuthorizationStatus.ACTIVE);
        verify(refreshService).refresh(10L, 20L, true);
    }

    /** 回调角色与原绑定不一致时必须拒绝且不得覆盖原授权。 */
    @Test
    void shouldRejectDifferentCharacterWithoutChangingAuthorization() {
        when(tokenClient.exchangeCode(any(), any()))
            .thenReturn(new SerenityTokenResponse("access", "refresh", "Bearer", 1200, null));
        when(jwtDecoder.decode("access")).thenReturn(jwt(8002L));

        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(context());
            assertThatThrownBy(() -> service.handleCurrent("callback-url", "browser-digest"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("重新授权角色或 Scope 与原事务不一致");
        }

        verify(tokenService, never()).replaceTokenResponse(any(), any(), any(), any(), any());
        verify(refreshService, never()).refresh(any(), any(), anyBoolean());
    }

    /** 回调角色 ID 相同但 owner 已变化时不得覆盖原授权。 */
    @Test
    void shouldRejectChangedOwnerWithoutChangingAuthorization() {
        when(tokenClient.exchangeCode(any(), any()))
            .thenReturn(new SerenityTokenResponse("access", "refresh", "Bearer", 1200, null));
        when(jwtDecoder.decode("access")).thenReturn(jwt(8001L, "changed-owner"));

        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(context());
            assertThatThrownBy(() -> service.handleCurrent("callback-url", "browser-digest"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("重新授权角色或 Scope 与原事务不一致");
        }

        verify(tokenService, never()).replaceTokenResponse(any(), any(), any(), any(), any());
        verify(refreshService, never()).refresh(any(), any(), anyBoolean());
    }

    /** 创建当前站内会话上下文。 */
    private static UserContext context() {
        UserContext context = new UserContext();
        context.setTenantId(10L);
        context.setId(20L);
        return context;
    }

    /** 创建含角色 subject 与 Scope 的已验证 JWT。 */
    private static Jwt jwt(Long characterId) {
        return jwt(characterId, "owner-hash");
    }

    /** 创建可指定 owner 的已验证 JWT。 */
    private static Jwt jwt(Long characterId, String ownerHash) {
        return Jwt.withTokenValue("access")
            .header("alg", "RS256")
            .subject("CHARACTER:EVE:" + characterId)
            .claim("owner", ownerHash)
            .claim("scp", List.of("esi-characters.read_corporation_roles.v1"))
            .build();
    }
}
