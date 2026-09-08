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

package top.continew.admin.config.satoken;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 登录会话租户边界拦截测试。
 *
 * @author zhaoyuqing
 */
class SaExtensionInterceptorTest {

    /** 伪造租户请求头对应的上下文与登录租户不一致时必须返回拒绝。 */
    @Test
    void shouldRejectForgedTenantContext() {
        assertThat(SaExtensionInterceptor.hasTenantAccess(10L, 11L)).isFalse();
    }

    /** 任一租户上下文缺失时必须安全拒绝，不能空指针放行或返回 500。 */
    @Test
    void shouldRejectMissingTenantContext() {
        assertThat(SaExtensionInterceptor.hasTenantAccess(null, 11L)).isFalse();
        assertThat(SaExtensionInterceptor.hasTenantAccess(10L, null)).isFalse();
    }

    /** 登录租户与请求租户一致时允许继续处理。 */
    @Test
    void shouldAllowMatchingTenantContext() {
        assertThat(SaExtensionInterceptor.hasTenantAccess(10L, 10L)).isTrue();
    }

    /** 已登录但用户上下文丢失时必须注销会话并返回未授权，不能继续进入控制器。 */
    @Test
    void shouldLogoutAndRejectAuthenticatedSessionWithoutUserContext() throws Exception {
        MissingContextInterceptor interceptor = new MissingContextInterceptor();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(new MockHttpServletRequest(), response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("401", "登录会话已失效");
        assertThat(interceptor.loggedOut).isTrue();
    }

    /** 构造真实执行 preHandle 的缺失上下文拦截器。 */
    private static class MissingContextInterceptor extends SaExtensionInterceptor {
        private boolean loggedOut;

        MissingContextInterceptor() {
            super(ignored -> {
            });
        }

        /** 模拟已登录状态。 */
        @Override
        boolean isLogin() {
            return true;
        }

        /** 模拟会话缓存中用户上下文丢失。 */
        @Override
        top.continew.admin.common.context.UserContext getUserContext() {
            return null;
        }

        /** 记录异常会话已执行注销。 */
        @Override
        void logout() {
            loggedOut = true;
        }
    }
}
