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

package top.continew.admin.auth.handler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import top.continew.admin.common.api.system.EveLoginIdentityReviewApi;
import top.continew.admin.common.model.dto.EveLoginIdentityReviewDTO;
import top.continew.admin.system.model.entity.user.UserDO;
import top.continew.starter.extension.tenant.context.TenantContext;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 账号登录处理器的 EVE 会话前复核测试。
 *
 * @author zhaoyuqing
 */
class AccountLoginHandlerEveReviewTest {

    /** 清理租户线程上下文。 */
    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    /** 未安装 EVE 模块时普通本地账号应继续登录。 */
    @Test
    void shouldAllowLocalLoginWithoutEveModule() {
        @SuppressWarnings("unchecked") ObjectProvider<EveLoginIdentityReviewApi> provider = mock(ObjectProvider.class);
        AccountLoginHandler handler = new AccountLoginHandler(mock(PasswordEncoder.class), provider);

        assertThatCode(() -> handler.reviewEveIdentity(user())).doesNotThrowAnyException();
    }

    /** 已绑定账号应携带当前租户和用户标识执行复核。 */
    @Test
    void shouldReviewBoundUserBeforeSessionCreation() {
        EveLoginIdentityReviewApi reviewApi = mock(EveLoginIdentityReviewApi.class);
        AccountLoginHandler handler = handler(reviewApi);
        when(reviewApi.review(10L, 20L))
            .thenReturn(new EveLoginIdentityReviewDTO(EveLoginIdentityReviewDTO.Status.VERIFIED));

        assertThatCode(() -> handler.reviewEveIdentity(user())).doesNotThrowAnyException();

        verify(reviewApi).review(10L, 20L);
    }

    /** 离团结果必须阻止创建带旧权限的新会话。 */
    @Test
    void shouldRejectLoginAfterLeavingCorporation() {
        assertRejected(EveLoginIdentityReviewDTO.Status.MEMBERSHIP_INVALID, "游戏角色已离开当前军团");
    }

    /** 永久授权失败必须阻止创建带旧权限的新会话。 */
    @Test
    void shouldRejectLoginAfterPermanentAuthorizationFailure() {
        assertRejected(EveLoginIdentityReviewDTO.Status.REAUTHORIZATION_REQUIRED, "EVE 授权已失效");
    }

    /** 临时上游故障也必须按 fail-closed 策略拒绝本次登录。 */
    @Test
    void shouldFailClosedOnTemporaryUpstreamFailure() {
        assertRejected(EveLoginIdentityReviewDTO.Status.UPSTREAM_UNAVAILABLE, "暂时无法复核游戏身份");
    }

    /** 复核实现自身异常必须原样暴露，避免把程序错误伪装成上游临时故障。 */
    @Test
    void shouldPropagateUnexpectedReviewFailure() {
        EveLoginIdentityReviewApi reviewApi = mock(EveLoginIdentityReviewApi.class);
        AccountLoginHandler handler = handler(reviewApi);
        IllegalStateException failure = new IllegalStateException("local failure");
        when(reviewApi.review(10L, 20L)).thenThrow(failure);

        assertThatThrownBy(() -> handler.reviewEveIdentity(user())).isSameAs(failure);
    }

    /** 断言指定复核状态会阻止登录。 */
    private void assertRejected(EveLoginIdentityReviewDTO.Status status, String message) {
        EveLoginIdentityReviewApi reviewApi = mock(EveLoginIdentityReviewApi.class);
        AccountLoginHandler handler = handler(reviewApi);
        when(reviewApi.review(10L, 20L)).thenReturn(new EveLoginIdentityReviewDTO(status));

        assertThatThrownBy(() -> handler.reviewEveIdentity(user())).hasMessageContaining(message);
    }

    /** 创建包含 EVE 复核实现的登录处理器。 */
    private AccountLoginHandler handler(EveLoginIdentityReviewApi reviewApi) {
        @SuppressWarnings("unchecked") ObjectProvider<EveLoginIdentityReviewApi> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(reviewApi);
        TenantContext tenantContext = new TenantContext();
        tenantContext.setTenantId(10L);
        TenantContextHolder.setContext(tenantContext);
        return new AccountLoginHandler(mock(PasswordEncoder.class), provider);
    }

    /** 创建当前登录用户。 */
    private static UserDO user() {
        UserDO user = new UserDO();
        user.setId(20L);
        return user;
    }
}
