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
import top.continew.admin.common.model.dto.EveLoginIdentityReviewDTO;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.model.EvePermissionRefreshResp;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.enums.EvePermissionRefreshStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 账号登录前 EVE 身份复核测试。
 *
 * @author zhaoyuqing
 */
class EveLoginIdentityReviewApiImplTest {

    private EveCharacterMapper characterMapper;
    private EveAuthorizationMapper authorizationMapper;
    private EvePermissionRefreshService permissionRefreshService;
    private EveDerivedIdentityService derivedIdentityService;
    private EveLoginIdentityReviewApiImpl service;

    /** 初始化复核依赖。 */
    @BeforeEach
    void setUp() {
        characterMapper = mock(EveCharacterMapper.class);
        authorizationMapper = mock(EveAuthorizationMapper.class);
        permissionRefreshService = mock(EvePermissionRefreshService.class);
        derivedIdentityService = mock(EveDerivedIdentityService.class);
        service = new EveLoginIdentityReviewApiImpl(characterMapper, authorizationMapper, permissionRefreshService, derivedIdentityService);
    }

    /** 未绑定 EVE 的本地账号不应触发上游复核。 */
    @Test
    void shouldIgnoreUnboundLocalUser() {
        when(characterMapper.selectByUser(10L, 20L)).thenReturn(List.of());

        EveLoginIdentityReviewDTO result = service.review(10L, 20L);

        assertThat(result.status()).isEqualTo(EveLoginIdentityReviewDTO.Status.NOT_BOUND);
        verify(permissionRefreshService, never()).reviewAuthorization(org.mockito.ArgumentMatchers.any());
    }

    /** 已绑定用户刷新成功后应允许按最新角色创建会话。 */
    @Test
    void shouldAllowLoginAfterRefreshingDerivedIdentity() {
        stubActiveBinding();
        when(permissionRefreshService.reviewAuthorization(org.mockito.ArgumentMatchers.any()))
            .thenReturn(response(EvePermissionRefreshStatus.REFRESHED));

        EveLoginIdentityReviewDTO result = service.review(10L, 20L);

        assertThat(result.status()).isEqualTo(EveLoginIdentityReviewDTO.Status.VERIFIED);
        verify(permissionRefreshService).reviewAuthorization(org.mockito.ArgumentMatchers.any());
    }

    /** 多角色用户必须逐条复核，避免次要角色残留 CEO 或总监权限。 */
    @Test
    void shouldReviewEveryActiveCharacterBeforeLogin() {
        EveCharacterDO primary = new EveCharacterDO();
        primary.setId(100L);
        EveCharacterDO secondary = new EveCharacterDO();
        secondary.setId(101L);
        when(characterMapper.selectByUser(10L, 20L)).thenReturn(List.of(primary, secondary));
        when(characterMapper.selectActiveByUser(10L, 20L)).thenReturn(List.of(primary, secondary));
        when(authorizationMapper.selectByUserCharacter(10L, 20L, 100L)).thenReturn(List.of(authorization(400L)));
        when(authorizationMapper.selectByUserCharacter(10L, 20L, 101L)).thenReturn(List.of(authorization(401L)));
        when(permissionRefreshService.reviewAuthorization(org.mockito.ArgumentMatchers.any()))
            .thenReturn(response(EvePermissionRefreshStatus.REFRESHED));

        EveLoginIdentityReviewDTO result = service.review(10L, 20L);

        assertThat(result.status()).isEqualTo(EveLoginIdentityReviewDTO.Status.VERIFIED);
        verify(permissionRefreshService, times(2)).reviewAuthorization(org.mockito.ArgumentMatchers.any());
    }

    /** 已失活绑定必须先收敛派生身份并拒绝登录当前租户。 */
    @Test
    void shouldRejectInactiveMembershipAndConvergeRoles() {
        EveCharacterDO character = new EveCharacterDO();
        when(characterMapper.selectByUser(10L, 20L)).thenReturn(List.of(character));
        when(characterMapper.selectActiveByUser(10L, 20L)).thenReturn(List.of());

        EveLoginIdentityReviewDTO result = service.review(10L, 20L);

        assertThat(result.status()).isEqualTo(EveLoginIdentityReviewDTO.Status.MEMBERSHIP_INVALID);
        verify(derivedIdentityService).synchronize(10L, 20L);
    }

    /** 上游确认离团后必须拒绝创建新会话。 */
    @Test
    void shouldRejectMembershipInvalidResult() {
        stubActiveBinding();
        when(permissionRefreshService.reviewAuthorization(org.mockito.ArgumentMatchers.any()))
            .thenReturn(response(EvePermissionRefreshStatus.MEMBERSHIP_INVALID));

        EveLoginIdentityReviewDTO result = service.review(10L, 20L);

        assertThat(result.status()).isEqualTo(EveLoginIdentityReviewDTO.Status.MEMBERSHIP_INVALID);
    }

    /** 永久授权失败必须要求重新授权且不得建立旧权限会话。 */
    @Test
    void shouldRejectPermanentAuthorizationFailure() {
        stubActiveBinding();
        when(permissionRefreshService.reviewAuthorization(org.mockito.ArgumentMatchers.any()))
            .thenReturn(response(EvePermissionRefreshStatus.REAUTHORIZATION_REQUIRED));

        EveLoginIdentityReviewDTO result = service.review(10L, 20L);

        assertThat(result.status()).isEqualTo(EveLoginIdentityReviewDTO.Status.REAUTHORIZATION_REQUIRED);
    }

    /** 临时上游故障必须保守拒绝本次登录。 */
    @Test
    void shouldFailClosedWhenUpstreamIsTemporarilyUnavailable() {
        stubActiveBinding();
        when(permissionRefreshService.reviewAuthorization(org.mockito.ArgumentMatchers.any()))
            .thenReturn(response(EvePermissionRefreshStatus.UPSTREAM_UNAVAILABLE));

        EveLoginIdentityReviewDTO result = service.review(10L, 20L);

        assertThat(result.status()).isEqualTo(EveLoginIdentityReviewDTO.Status.UPSTREAM_UNAVAILABLE);
    }

    /** 本地编程或数据库异常不得伪装成上游暂时不可用。 */
    @Test
    void shouldPropagateUnexpectedLocalFailure() {
        stubActiveBinding();
        when(permissionRefreshService.reviewAuthorization(org.mockito.ArgumentMatchers.any()))
            .thenThrow(new IllegalStateException("local failure"));

        assertThatThrownBy(() -> service.review(10L, 20L)).isInstanceOf(IllegalStateException.class)
            .hasMessage("local failure");
    }

    /** 建立有效 EVE 角色绑定。 */
    private void stubActiveBinding() {
        EveCharacterDO character = new EveCharacterDO();
        character.setId(100L);
        when(characterMapper.selectByUser(10L, 20L)).thenReturn(List.of(character));
        when(characterMapper.selectActiveByUser(10L, 20L)).thenReturn(List.of(character));
        when(authorizationMapper.selectByUserCharacter(10L, 20L, 100L)).thenReturn(List.of(authorization(400L)));
    }

    /** 创建授权测试对象。 */
    private static EveAuthorizationDO authorization(Long id) {
        EveAuthorizationDO authorization = new EveAuthorizationDO();
        authorization.setId(id);
        return authorization;
    }

    /** 创建指定状态的权限刷新结果。 */
    private static EvePermissionRefreshResp response(EvePermissionRefreshStatus status) {
        return new EvePermissionRefreshResp(status, LocalDateTime.now(), null, null, false, null, null, List
            .of(), null, List.of(), List.of(), null, List.of());
    }
}
