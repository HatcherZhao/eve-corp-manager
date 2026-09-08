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

package top.continew.admin.controller.eve;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import top.continew.admin.controller.eve.model.EvePasswordRecoveryCompleteReq;
import top.continew.admin.controller.eve.model.EveRegistrationCallbackReq;
import top.continew.admin.controller.eve.model.EveRegistrationCompleteReq;
import top.continew.starter.ratelimiter.annotation.RateLimiter;
import top.continew.starter.ratelimiter.enums.LimitType;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EVE 公开身份入口限流契约测试。
 *
 * @author zhaoyuqing
 */
class EvePublicIdentityRateLimiterContractTest {

    /** 注册流程按入口风险设置独立 IP 限流。 */
    @Test
    void shouldRateLimitRegistrationEndpointsByIp() throws Exception {
        assertRateLimiter(EveRegistrationController.class
            .getDeclaredMethod("start", HttpServletRequest.class, HttpServletResponse.class), "EVE_REGISTRATION_START", 30, 1);
        assertRateLimiter(EveRegistrationController.class
            .getDeclaredMethod("callback", EveRegistrationCallbackReq.class, HttpServletRequest.class), "EVE_REGISTRATION_CALLBACK", 60, 5);
        assertRateLimiter(EveRegistrationController.class
            .getDeclaredMethod("complete", EveRegistrationCompleteReq.class, HttpServletRequest.class), "EVE_REGISTRATION_COMPLETE", 10, 10);
    }

    /** 找回密码流程按入口风险设置独立 IP 限流。 */
    @Test
    void shouldRateLimitPasswordRecoveryEndpointsByIp() throws Exception {
        assertRateLimiter(EvePasswordRecoveryController.class
            .getDeclaredMethod("start", HttpServletRequest.class, HttpServletResponse.class), "EVE_PASSWORD_RECOVERY_START", 30, 1);
        assertRateLimiter(EvePasswordRecoveryController.class
            .getDeclaredMethod("callback", EveRegistrationCallbackReq.class, HttpServletRequest.class), "EVE_PASSWORD_RECOVERY_CALLBACK", 60, 5);
        assertRateLimiter(EvePasswordRecoveryController.class
            .getDeclaredMethod("complete", EvePasswordRecoveryCompleteReq.class, HttpServletRequest.class), "EVE_PASSWORD_RECOVERY_COMPLETE", 10, 10);
    }

    /** 校验入口使用指定名称和窗口的 IP 限流规则。 */
    private static void assertRateLimiter(Method method, String name, int rate, int interval) {
        RateLimiter rateLimiter = method.getAnnotation(RateLimiter.class);

        assertThat(rateLimiter).isNotNull();
        assertThat(rateLimiter.name()).isEqualTo(name);
        assertThat(rateLimiter.type()).isEqualTo(LimitType.IP);
        assertThat(rateLimiter.rate()).isEqualTo(rate);
        assertThat(rateLimiter.interval()).isEqualTo(interval);
        assertThat(rateLimiter.unit()).isEqualTo(TimeUnit.MINUTES);
        assertThat(rateLimiter.message()).isNotBlank();
    }
}
