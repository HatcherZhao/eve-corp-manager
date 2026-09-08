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

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.client.OAuthFailureCode;
import top.continew.admin.eve.client.SerenityTokenClient;
import top.continew.admin.eve.client.SerenityTokenClientException;
import top.continew.admin.eve.mapper.EveAuthAuditMapper;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.model.EveAuthorizationRevocationResp;
import top.continew.admin.eve.model.entity.EveAuthAuditDO;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

/**
 * 主动撤销授权归属与闭环测试。
 *
 * @author zhaoyuqing
 */
class EveAuthorizationRevocationServiceTest {

    /** 上游暂时不可用时仍必须清除本地令牌并记录脱敏审计。 */
    @Test
    void shouldRevokeLocallyWhenUpstreamIsUnavailable() throws InterruptedException {
        EveAuthorizationMapper mapper = mock(EveAuthorizationMapper.class);
        EveAuthorizationTokenService tokenService = mock(EveAuthorizationTokenService.class);
        SerenityTokenClient tokenClient = mock(SerenityTokenClient.class);
        EveAuthAuditMapper auditMapper = mock(EveAuthAuditMapper.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("eve:serenity:authorization-refresh:9")).thenReturn(lock);
        when(lock.tryLock(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        EveAuthorizationRevocationService service = new EveAuthorizationRevocationService(mapper, tokenService, tokenClient, auditMapper, redissonClient);
        EveAuthorizationDO authorization = authorization();
        when(mapper.selectOwnedById(10L, 20L, 9L)).thenReturn(authorization);
        doThrow(new SerenityTokenClientException(OAuthFailureCode.TRANSIENT)).when(tokenClient)
            .revoke("refresh", "refresh_token");

        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(context());
            EveAuthorizationRevocationResp result = service.revokeCurrent(9L);

            assertThat(result.status())
                .isEqualTo(EveAuthorizationRevocationResp.Status.LOCAL_REVOKED_UPSTREAM_UNCONFIRMED);
            assertThat(result.failureCode()).isEqualTo(OAuthFailureCode.TRANSIENT);
        }

        verify(tokenService).clearRevokedTokens(10L, 20L, 9L);
        verify(auditMapper).insert(any(EveAuthAuditDO.class));
    }

    /** 上游两类令牌均撤销成功时返回已确认结果且不暴露失败码。 */
    @Test
    void shouldReportConfirmedUpstreamRevocation() throws InterruptedException {
        EveAuthorizationMapper mapper = mock(EveAuthorizationMapper.class);
        EveAuthorizationTokenService tokenService = mock(EveAuthorizationTokenService.class);
        SerenityTokenClient tokenClient = mock(SerenityTokenClient.class);
        EveAuthAuditMapper auditMapper = mock(EveAuthAuditMapper.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("eve:serenity:authorization-refresh:9")).thenReturn(lock);
        when(lock.tryLock(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(mapper.selectOwnedById(10L, 20L, 9L)).thenReturn(authorization());
        EveAuthorizationRevocationService service = new EveAuthorizationRevocationService(mapper, tokenService, tokenClient, auditMapper, redissonClient);

        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(context());
            EveAuthorizationRevocationResp result = service.revokeCurrent(9L);

            assertThat(result.status()).isEqualTo(EveAuthorizationRevocationResp.Status.UPSTREAM_REVOKED);
            assertThat(result.failureCode()).isNull();
        }

        verify(tokenClient).revoke("refresh", "refresh_token");
        verify(tokenClient).revoke("access", "access_token");
        verify(tokenService).clearRevokedTokens(10L, 20L, 9L);
    }

    /** 不属于当前会话的授权 ID 必须在调用上游前拒绝。 */
    @Test
    void shouldRejectAuthorizationNotOwnedByCurrentUser() throws InterruptedException {
        EveAuthorizationMapper mapper = mock(EveAuthorizationMapper.class);
        EveAuthorizationTokenService tokenService = mock(EveAuthorizationTokenService.class);
        SerenityTokenClient tokenClient = mock(SerenityTokenClient.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("eve:serenity:authorization-refresh:9")).thenReturn(lock);
        when(lock.tryLock(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        EveAuthorizationRevocationService service = new EveAuthorizationRevocationService(mapper, tokenService, tokenClient, mock(EveAuthAuditMapper.class), redissonClient);
        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(context());
            assertThatThrownBy(() -> service.revokeCurrent(9L)).isInstanceOf(IllegalStateException.class)
                .hasMessage("当前用户无权撤销该 EVE 授权");
        }
        verify(tokenClient, never()).revoke(any(), any());
        verify(tokenService, never()).clearRevokedTokens(anyLong(), anyLong(), anyLong());
    }

    /** 跨租户授权 ID 必须按当前租户查询并在调用上游前拒绝。 */
    @Test
    void shouldRejectAuthorizationFromAnotherTenant() throws InterruptedException {
        EveAuthorizationMapper mapper = mock(EveAuthorizationMapper.class);
        EveAuthorizationTokenService tokenService = mock(EveAuthorizationTokenService.class);
        SerenityTokenClient tokenClient = mock(SerenityTokenClient.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("eve:serenity:authorization-refresh:9")).thenReturn(lock);
        when(lock.tryLock(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        EveAuthorizationDO crossTenantAuthorization = authorization();
        crossTenantAuthorization.setTenantId(99L);
        when(mapper.selectOwnedById(10L, 20L, 9L)).thenReturn(crossTenantAuthorization);
        EveAuthorizationRevocationService service = new EveAuthorizationRevocationService(mapper, tokenService, tokenClient, mock(EveAuthAuditMapper.class), redissonClient);

        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(context());
            assertThatThrownBy(() -> service.revokeCurrent(9L)).isInstanceOf(IllegalStateException.class)
                .hasMessage("当前用户无权撤销该 EVE 授权");
        }

        verify(mapper).selectOwnedById(10L, 20L, 9L);
        verify(tokenClient, never()).revoke(any(), any());
        verify(tokenService, never()).clearRevokedTokens(anyLong(), anyLong(), anyLong());
    }

    /** 创建当前会话。 */
    private static UserContext context() {
        UserContext context = new UserContext();
        context.setTenantId(10L);
        context.setId(20L);
        return context;
    }

    /** 创建包含两类令牌的授权。 */
    private static EveAuthorizationDO authorization() {
        EveAuthorizationDO authorization = new EveAuthorizationDO();
        authorization.setId(9L);
        authorization.setTenantId(10L);
        authorization.setUserId(20L);
        authorization.setCharacterRefId(100L);
        authorization.setAccessToken("access");
        authorization.setRefreshToken("refresh");
        return authorization;
    }
}
