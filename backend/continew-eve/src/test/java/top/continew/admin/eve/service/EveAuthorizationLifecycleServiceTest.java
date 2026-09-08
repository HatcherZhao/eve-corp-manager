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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import top.continew.admin.eve.client.OAuthFailureCode;
import top.continew.admin.eve.client.SerenityTokenClient;
import top.continew.admin.eve.client.SerenityTokenClientException;
import top.continew.admin.eve.client.SerenityTokenResponse;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveAuthAuditMapper;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.security.SerenityJwtDecoderFactory;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * 授权令牌生命周期协调测试。
 *
 * @author zhaoyuqing
 */
class EveAuthorizationLifecycleServiceTest {

    private EveAuthorizationMapper authorizationMapper;
    private EveAuthorizationTokenService tokenService;
    private SerenityTokenClient tokenClient;
    private EveCharacterMapper characterMapper;
    private EveCharacterDO character;
    private JwtDecoder jwtDecoder;
    private RLock lock;
    private EveAuthorizationLifecycleService service;

    /** 初始化授权级分布式锁与服务替身。 */
    @BeforeEach
    void setUp() throws InterruptedException {
        authorizationMapper = mock(EveAuthorizationMapper.class);
        tokenService = mock(EveAuthorizationTokenService.class);
        tokenClient = mock(SerenityTokenClient.class);
        characterMapper = mock(EveCharacterMapper.class);
        jwtDecoder = mock(JwtDecoder.class);
        SerenityJwtDecoderFactory jwtDecoderFactory = mock(SerenityJwtDecoderFactory.class);
        when(jwtDecoderFactory.create()).thenReturn(jwtDecoder);
        RedissonClient redissonClient = mock(RedissonClient.class);
        lock = mock(RLock.class);
        when(redissonClient.getLock("eve:serenity:authorization-refresh:9")).thenReturn(lock);
        when(lock.tryLock(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        character = new EveCharacterDO();
        character.setId(100L);
        character.setTenantId(10L);
        character.setUserId(20L);
        character.setServer("serenity");
        character.setCharacterId(8001L);
        character.setOwnerHash("owner-hash");
        when(characterMapper.selectById(100L)).thenReturn(character);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("CHARACTER:EVE:8001");
        when(jwt.getClaimAsString("owner")).thenReturn("owner-hash");
        when(jwt.getClaim("scp")).thenReturn("scope-a");
        when(jwtDecoder.decode("new-access")).thenReturn(jwt);
        SerenityProperties properties = new SerenityProperties();
        properties.getSso().setScopeClaim("scp");
        service = new EveAuthorizationLifecycleService(authorizationMapper, characterMapper, mock(EveAuthAuditMapper.class), tokenService, tokenClient, jwtDecoderFactory, redissonClient, properties);
    }

    /** 刷新令牌轮换时必须在锁内原子保存新 refresh token。 */
    @Test
    void shouldRotateRefreshTokenUnderAuthorizationLock() {
        EveAuthorizationDO authorization = expiringAuthorization("old-refresh");
        when(authorizationMapper.selectOwnedById(10L, 20L, 9L)).thenReturn(authorization);
        SerenityTokenResponse response = new SerenityTokenResponse("new-access", "new-refresh", "Bearer", 1200, "scope-a");
        when(tokenClient.refresh("old-refresh")).thenReturn(response);

        assertThat(service.ensureAccessToken(authorization)).isEqualTo("new-access");
        verify(tokenService).saveTokenResponse(10L, 20L, 9L, response);
        verify(lock).unlock();
    }

    /** UTC 写入的未过期访问令牌在中国时区运行时也不得被提前刷新。 */
    @Test
    void shouldKeepFreshUtcAccessTokenWithoutRefreshing() {
        EveAuthorizationDO authorization = expiringAuthorization("old-refresh");
        authorization.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(20));

        assertThat(service.ensureAccessToken(authorization)).isEqualTo("old-access");

        verify(tokenClient, never()).refresh("old-refresh");
        verify(authorizationMapper, never()).selectOwnedById(10L, 20L, 9L);
    }

    /** 上游未轮换 refresh token 时保留原值，避免把有效凭证覆盖为空。 */
    @Test
    void shouldKeepExistingRefreshTokenWhenResponseOmitsRotation() {
        EveAuthorizationDO authorization = expiringAuthorization("old-refresh");
        when(authorizationMapper.selectOwnedById(10L, 20L, 9L)).thenReturn(authorization);
        when(tokenClient.refresh("old-refresh"))
            .thenReturn(new SerenityTokenResponse("new-access", null, "Bearer", 1200, "scope-a"));

        service.ensureAccessToken(authorization);

        verify(tokenService)
            .saveTokenResponse(10L, 20L, 9L, new SerenityTokenResponse("new-access", "old-refresh", "Bearer", 1200, "scope-a"));
    }

    /** 永久刷新失败必须记录脱敏分类并要求重新授权。 */
    @Test
    void shouldDisableAuthorizationAfterPermanentRefreshFailure() {
        EveAuthorizationDO authorization = expiringAuthorization("old-refresh");
        when(authorizationMapper.selectOwnedById(10L, 20L, 9L)).thenReturn(authorization);
        when(tokenClient.refresh("old-refresh"))
            .thenThrow(new SerenityTokenClientException(OAuthFailureCode.INVALID_GRANT));

        assertThatThrownBy(() -> service.ensureAccessToken(authorization))
            .isInstanceOf(SerenityTokenClientException.class);
        verify(tokenService).recordFailure(10L, 20L, 9L, OAuthFailureCode.INVALID_GRANT);
    }

    /** Token 上游临时失败应记录可重试分类。 */
    @Test
    void shouldRecordRetryableTokenFailure() {
        EveAuthorizationDO authorization = expiringAuthorization("old-refresh");
        when(authorizationMapper.selectOwnedById(10L, 20L, 9L)).thenReturn(authorization);
        when(tokenClient.refresh("old-refresh"))
            .thenThrow(new SerenityTokenClientException(OAuthFailureCode.TRANSIENT));

        assertThatThrownBy(() -> service.ensureAccessToken(authorization))
            .isInstanceOf(SerenityTokenClientException.class);
        verify(tokenService).recordFailure(10L, 20L, 9L, OAuthFailureCode.TRANSIENT);
    }

    /** 刷新响应即使结构合法，只要角色身份不一致就必须永久失效且不得持久化。 */
    @Test
    void shouldRejectRefreshedTokenForDifferentCharacter() {
        EveAuthorizationDO authorization = expiringAuthorization("old-refresh");
        when(authorizationMapper.selectOwnedById(10L, 20L, 9L)).thenReturn(authorization);
        when(tokenClient.refresh("old-refresh"))
            .thenReturn(new SerenityTokenResponse("new-access", "new-refresh", "Bearer", 1200, "scope-a"));
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("CHARACTER:EVE:9999");
        when(jwt.getClaimAsString("owner")).thenReturn("owner-hash");
        when(jwt.getClaim("scp")).thenReturn("scope-a");
        when(jwtDecoder.decode("new-access")).thenReturn(jwt);

        assertThatThrownBy(() -> service.ensureAccessToken(authorization))
            .isInstanceOf(SerenityTokenClientException.class);

        verify(tokenService).recordFailure(10L, 20L, 9L, OAuthFailureCode.PERMANENT);
        verify(tokenService, never()).saveTokenResponse(eq(10L), eq(20L), eq(9L), org.mockito.ArgumentMatchers.any());
    }

    /** 刷新 JWT 的 owner_hash 与原绑定不一致时必须永久拒绝。 */
    @Test
    void shouldRejectRefreshedTokenForDifferentOwner() {
        EveAuthorizationDO authorization = expiringAuthorization("old-refresh");
        when(authorizationMapper.selectOwnedById(10L, 20L, 9L)).thenReturn(authorization);
        when(tokenClient.refresh("old-refresh"))
            .thenReturn(new SerenityTokenResponse("new-access", "new-refresh", "Bearer", 1200, "scope-a"));
        Jwt jwt = jwt("CHARACTER:EVE:8001", "different-owner", "scope-a");
        when(jwtDecoder.decode("new-access")).thenReturn(jwt);

        assertPermanentIdentityRejection(authorization);
    }

    /** 授权服务器或角色服务器与当前数据源不一致时必须永久拒绝。 */
    @Test
    void shouldRejectRefreshedTokenForDifferentServer() {
        EveAuthorizationDO authorization = expiringAuthorization("old-refresh");
        character.setServer("tranquility");
        when(authorizationMapper.selectOwnedById(10L, 20L, 9L)).thenReturn(authorization);
        when(tokenClient.refresh("old-refresh"))
            .thenReturn(new SerenityTokenResponse("new-access", "new-refresh", "Bearer", 1200, "scope-a"));

        assertPermanentIdentityRejection(authorization);
    }

    /** JWT Scope 与 Token 响应 Scope 不一致时不得持久化任一结果。 */
    @Test
    void shouldRejectRefreshedTokenWhenJwtAndResponseScopesDiffer() {
        EveAuthorizationDO authorization = expiringAuthorization("old-refresh");
        when(authorizationMapper.selectOwnedById(10L, 20L, 9L)).thenReturn(authorization);
        when(tokenClient.refresh("old-refresh"))
            .thenReturn(new SerenityTokenResponse("new-access", "new-refresh", "Bearer", 1200, "scope-a"));
        Jwt mismatchedScopeJwt = jwt("CHARACTER:EVE:8001", "owner-hash", "scope-b");
        when(jwtDecoder.decode("new-access")).thenReturn(mismatchedScopeJwt);

        assertPermanentIdentityRejection(authorization);
    }

    /** 刷新响应省略 Scope 时必须保留原授权 Scope。 */
    @Test
    void shouldKeepExistingScopesWhenRefreshResponseOmitsScope() {
        EveAuthorizationDO authorization = expiringAuthorization("old-refresh");
        when(authorizationMapper.selectOwnedById(10L, 20L, 9L)).thenReturn(authorization);
        when(tokenClient.refresh("old-refresh"))
            .thenReturn(new SerenityTokenResponse("new-access", "new-refresh", "Bearer", 1200, null));

        service.ensureAccessToken(authorization);

        verify(tokenService)
            .saveTokenResponse(10L, 20L, 9L, new SerenityTokenResponse("new-access", "new-refresh", "Bearer", 1200, "scope-a"));
    }

    /** 统一断言身份不一致会永久停用且不持久化新令牌。 */
    private void assertPermanentIdentityRejection(EveAuthorizationDO authorization) {
        assertThatThrownBy(() -> service.ensureAccessToken(authorization))
            .isInstanceOf(SerenityTokenClientException.class);
        verify(tokenService).recordFailure(10L, 20L, 9L, OAuthFailureCode.PERMANENT);
        verify(tokenService, never()).saveTokenResponse(eq(10L), eq(20L), eq(9L), org.mockito.ArgumentMatchers.any());
    }

    /** 创建指定 subject、owner_hash 和 Scope 的已验签 JWT 替身。 */
    private static Jwt jwt(String subject, String ownerHash, String scopes) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        when(jwt.getClaimAsString("owner")).thenReturn(ownerHash);
        when(jwt.getClaim("scp")).thenReturn(scopes);
        return jwt;
    }

    /** 创建临近过期的有效授权。 */
    private static EveAuthorizationDO expiringAuthorization(String refreshToken) {
        EveAuthorizationDO authorization = new EveAuthorizationDO();
        authorization.setId(9L);
        authorization.setTenantId(10L);
        authorization.setUserId(20L);
        authorization.setCharacterRefId(100L);
        authorization.setServer("serenity");
        authorization.setScopes(java.util.List.of("scope-a"));
        authorization.setStatus(EveAuthorizationStatus.ACTIVE);
        authorization.setAccessToken("old-access");
        authorization.setRefreshToken(refreshToken);
        authorization.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(10));
        return authorization;
    }
}
