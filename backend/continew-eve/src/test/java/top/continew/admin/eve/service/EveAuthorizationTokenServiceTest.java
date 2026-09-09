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

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.Test;
import top.continew.admin.eve.client.OAuthFailureCode;
import top.continew.admin.eve.client.SerenityTokenResponse;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EVE 授权令牌持久化服务测试。
 *
 * @author zhaoyuqing
 */
class EveAuthorizationTokenServiceTest {

    /** 验证有效 Token 响应写入加密实体字段并规范化 Scope。 */
    @Test
    void shouldPersistValidatedTokenResponse() {
        EveAuthorizationMapper mapper = mock(EveAuthorizationMapper.class);
        when(mapper.update(any(), any())).thenReturn(1);
        EveAuthorizationTokenService service = service(mapper);
        service
            .saveTokenResponse(10L, 20L, 9L, new SerenityTokenResponse("access", "refresh", "Bearer", 1200, "scope-a   scope-b\tscope-a"));

        org.mockito.ArgumentCaptor<EveAuthorizationDO> entityCaptor = org.mockito.ArgumentCaptor
            .forClass(EveAuthorizationDO.class);
        org.mockito.ArgumentCaptor<UpdateWrapper> wrapperCaptor = org.mockito.ArgumentCaptor
            .forClass(UpdateWrapper.class);
        verify(mapper).update(entityCaptor.capture(), wrapperCaptor.capture());
        assertThat(entityCaptor.getValue().getAccessToken()).isEqualTo("access");
        assertThat(entityCaptor.getValue().getRefreshToken()).isEqualTo("refresh");
        assertThat(entityCaptor.getValue().getScopes()).containsExactly("scope-a", "scope-b");
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EveAuthorizationStatus.ACTIVE);
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("tenant_id", "user_id", "id", "deleted");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values()).contains(EveAuthorizationStatus.ACTIVE);
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values()).doesNotContain("access", "refresh");
    }

    /** 验证无效 Token 响应在调用 Mapper 前被拒绝。 */
    @Test
    void shouldRejectInvalidTokenResponseBeforePersistence() {
        EveAuthorizationMapper mapper = mock(EveAuthorizationMapper.class);
        EveAuthorizationTokenService service = service(mapper);
        assertThatThrownBy(() -> service
            .saveTokenResponse(10L, 20L, 9L, new SerenityTokenResponse("access", "refresh", "MAC", 1200, "scope-a")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("国服 Token 响应无效");
        org.mockito.Mockito.verifyNoInteractions(mapper);
    }

    /** 缺少刷新令牌时不得持久化 ACTIVE 授权。 */
    @Test
    void shouldRejectTokenResponseWithoutRefreshToken() {
        EveAuthorizationMapper mapper = mock(EveAuthorizationMapper.class);
        EveAuthorizationTokenService service = service(mapper);

        assertThatThrownBy(() -> service
            .saveTokenResponse(10L, 20L, 9L, new SerenityTokenResponse("access", null, "Bearer", 1200, "scope-a")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("国服 Token 响应无效");
        org.mockito.Mockito.verifyNoInteractions(mapper);
    }

    /** 撤销先完成时刷新结果必须因 ACTIVE 状态 CAS 未命中而被拒绝。 */
    @Test
    void shouldNotRestoreRevokedAuthorizationWithStaleRefreshResult() {
        EveAuthorizationMapper mapper = mock(EveAuthorizationMapper.class);
        when(mapper.update(any(), any())).thenReturn(0);
        EveAuthorizationTokenService service = service(mapper);

        assertThatThrownBy(() -> service
            .saveTokenResponse(10L, 20L, 9L, new SerenityTokenResponse("access", "refresh", "Bearer", 1200, "scope-a")))
            .isInstanceOf(IllegalStateException.class);

        org.mockito.ArgumentCaptor<UpdateWrapper> captor = org.mockito.ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper).update(any(), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("status");
    }

    /** 验证撤销使用显式 SQL SET NULL 清除两类密文。 */
    @Test
    @SuppressWarnings("rawtypes")
    void shouldExplicitlyClearTokensAfterRevocation() {
        EveAuthorizationMapper mapper = mock(EveAuthorizationMapper.class);
        when(mapper.update(any(), any())).thenReturn(1);
        EveAuthorizationTokenService service = service(mapper);
        service.clearRevokedTokens(10L, 20L, 9L);

        org.mockito.ArgumentCaptor<UpdateWrapper> wrapperCaptor = org.mockito.ArgumentCaptor
            .forClass(UpdateWrapper.class);
        verify(mapper).update(isNull(), wrapperCaptor.capture());
        String sqlSet = wrapperCaptor.getValue().getSqlSet();
        assertThat(sqlSet).contains("access_token", "refresh_token", "status", "revoked_at");
    }

    /** 验证上游失败仅保存归一化代码。 */
    @Test
    void shouldPersistOnlyNormalizedFailureCode() {
        EveAuthorizationMapper mapper = mock(EveAuthorizationMapper.class);
        EveAuthorizationDO current = new EveAuthorizationDO();
        current.setFailureCount(2);
        when(mapper.selectOwnedById(10L, 20L, 9L)).thenReturn(current);
        when(mapper.update(any(), any())).thenReturn(1);
        EveAuthorizationTokenService service = service(mapper);
        service.recordFailure(10L, 20L, 9L, OAuthFailureCode.INVALID_GRANT);

        verify(mapper).update(isNull(), any(Wrapper.class));
    }

    /** 永久失败必须原子清除令牌，不得影响本站登录会话。 */
    @Test
    @SuppressWarnings("rawtypes")
    void shouldRetainSiteSessionAfterPermanentFailure() {
        EveAuthorizationMapper mapper = mock(EveAuthorizationMapper.class);
        EveAuthorizationDO current = new EveAuthorizationDO();
        current.setFailureCount(0);
        when(mapper.selectOwnedById(10L, 20L, 9L)).thenReturn(current);
        when(mapper.update(any(), any())).thenReturn(1);
        EveAuthorizationTokenService service = new EveAuthorizationTokenService(mapper);
        service.recordFailure(10L, 20L, 9L, OAuthFailureCode.UNAUTHORIZED);

        org.mockito.ArgumentCaptor<UpdateWrapper> captor = org.mockito.ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet()).contains("access_token", "refresh_token");
    }

    /** 临时失败只累加分类并保持授权可重试，不得写成永久失败状态。 */
    @Test
    @SuppressWarnings("rawtypes")
    void shouldKeepAuthorizationActiveAfterTransientFailure() {
        EveAuthorizationMapper mapper = mock(EveAuthorizationMapper.class);
        EveAuthorizationDO current = new EveAuthorizationDO();
        current.setFailureCount(0);
        when(mapper.selectOwnedById(10L, 20L, 9L)).thenReturn(current);
        when(mapper.update(any(), any())).thenReturn(1);
        EveAuthorizationTokenService service = service(mapper);

        service.recordFailure(10L, 20L, 9L, OAuthFailureCode.TRANSIENT);

        org.mockito.ArgumentCaptor<UpdateWrapper> captor = org.mockito.ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getParamNameValuePairs().values()).contains(OAuthFailureCode.TRANSIENT
            .name(), EveAuthorizationStatus.ACTIVE).doesNotContain(EveAuthorizationStatus.REAUTH_REQUIRED);
        assertThat(captor.getValue().getSqlSet()).doesNotContain("last_verified_at");
    }

    /** 更新条件未命中时必须拒绝，防止通过可猜测授权 ID 越权写入。 */
    @Test
    void shouldRejectUpdateWhenAuthorizationIsNotOwned() {
        EveAuthorizationMapper mapper = mock(EveAuthorizationMapper.class);
        when(mapper.update(any(), any())).thenReturn(0);
        EveAuthorizationTokenService service = service(mapper);

        assertThatThrownBy(() -> service.markVerified(10L, 20L, 9L, java.time.LocalDateTime.now()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("当前用户无权更新该 EVE 授权");
    }

    /** 创建授权令牌服务。 */
    private static EveAuthorizationTokenService service(EveAuthorizationMapper mapper) {
        return new EveAuthorizationTokenService(mapper);
    }
}
