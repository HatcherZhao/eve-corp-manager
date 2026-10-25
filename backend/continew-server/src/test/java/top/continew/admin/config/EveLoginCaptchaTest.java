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

package top.continew.admin.config;

import java.time.Duration;

import cn.hutool.extra.spring.SpringUtil;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import top.continew.admin.auth.enums.AuthTypeEnum;
import top.continew.admin.auth.handler.AccountLoginHandler;
import top.continew.admin.auth.model.req.AccountLoginReq;
import top.continew.admin.common.config.CaptchaProperties;
import top.continew.admin.common.constant.CacheConstants;
import top.continew.admin.common.util.SecureUtils;
import top.continew.admin.controller.CaptchaController;
import top.continew.admin.system.model.resp.ClientResp;
import top.continew.admin.system.service.OptionService;
import top.continew.admin.system.service.SmsConfigService;
import top.continew.admin.system.service.UserService;
import top.continew.starter.cache.redisson.util.RedisUtils;
import top.continew.starter.captcha.graphic.core.GraphicCaptchaService;
import top.continew.starter.core.autoconfigure.application.ApplicationProperties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 登录验证码关闭、缓存更新和密码错误保护的回归测试。 */
class EveLoginCaptchaTest {

    /** 注册静态工具初始化所需的最小依赖，不连接真实 Redis。 */
    @BeforeAll
    static void configureTools() {
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("validator", mock(Validator.class));
        beans.registerSingleton("redissonClient", mock(RedissonClient.class));
        new SpringUtil().postProcessBeanFactory(beans);
    }

    /** 开关关闭后只返回关闭状态，不生成图形或访问验证码存储。 */
    @Test
    void disabledEndpointDoesNotGenerateCaptcha() {
        var options = mock(OptionService.class);
        when(options.getValueByCode2Int("LOGIN_CAPTCHA_ENABLED")).thenReturn(0);
        var graphic = mock(GraphicCaptchaService.class);
        var controller = new CaptchaController(mock(ApplicationProperties.class), mock(CaptchaProperties.class), graphic, options, mock(SmsConfigService.class));
        try (var redis = mockStatic(RedisUtils.class, withSettings().mockMaker("mock-maker-inline"))) {
            var response = controller.getImageCaptcha();
            assertEquals(false, response.getIsEnabled());
            assertNull(response.getImg());
            verifyNoInteractions(graphic);
            redis.verifyNoInteractions();
        }
    }

    /** 关闭时账号登录允许不传 captcha 和 uuid。 */
    @Test
    void disabledLoginAcceptsMissingCaptcha() {
        var options = mock(OptionService.class);
        when(options.getValueByCode2Int("LOGIN_CAPTCHA_ENABLED")).thenReturn(0);
        var handler = handler(options);
        try (var redis = mockStatic(RedisUtils.class, withSettings().mockMaker("mock-maker-inline"))) {
            assertDoesNotThrow(() -> handler.preLogin(request(), new ClientResp(), new MockHttpServletRequest()));
            redis.verifyNoInteractions();
        }
    }

    /** 管理员未来重新启用开关后，不能无验证码绕过登录。 */
    @Test
    void enabledLoginStillRequiresCaptcha() {
        var options = mock(OptionService.class);
        when(options.getValueByCode2Int("LOGIN_CAPTCHA_ENABLED")).thenReturn(1);
        var handler = handler(options);
        var failure = assertThrows(RuntimeException.class, () -> handler
            .preLogin(request(), new ClientResp(), new MockHttpServletRequest()));
        assertTrue(failure.getMessage().contains("验证码不能为空"));
    }

    /** 缓存失效精确到一个选项，不删除其他配置、会话或 Redis 数据。 */
    @Test
    void startupEvictsOnlyCaptchaOption() {
        try (var redis = mockStatic(RedisUtils.class, withSettings().mockMaker("mock-maker-inline"))) {
            new EveLoginCaptchaCacheInitializer().run(new DefaultApplicationArguments());
            redis.verify(() -> RedisUtils.delete("OPTION:LOGIN_CAPTCHA_ENABLED"));
            redis.verifyNoMoreInteractions();
        }
    }

    /** 现有菜单启动任务会清理旧动态路由，确保迁移新增的外部 Agent 入口可见。 */
    @Test
    void startupEvictsRoleMenusWithoutClearingSessions() throws Exception {
        try (var redis = mockStatic(RedisUtils.class, withSettings().mockMaker("mock-maker-inline"))) {
            new EveRoleMenuCacheInvalidator().eveRoleMenuCacheInvalidationRunner()
                .run(new DefaultApplicationArguments());
            redis.verify(() -> RedisUtils.deleteByPattern("ROLE_MENU:*"));
            redis.verifyNoMoreInteractions();
        }
    }

    /** 即使不要求验证码，错误密码仍会增加锁定计数并拒绝登录。 */
    @Test
    void invalidPasswordStillCountsTowardAccountLock() {
        var options = mock(OptionService.class);
        when(options.getValueByCode2Int("PASSWORD_ERROR_LOCK_COUNT")).thenReturn(3);
        when(options.getValueByCode2Int("PASSWORD_ERROR_LOCK_MINUTES")).thenReturn(10);
        var handler = handler(options);
        ReflectionTestUtils.setField(handler, "userService", mock(UserService.class));
        try (var redis = mockStatic(RedisUtils.class, withSettings().mockMaker("mock-maker-inline"));
            var secure = mockStatic(SecureUtils.class, withSettings().mockMaker("mock-maker-inline"))) {
            secure.when(() -> SecureUtils.decryptPasswordByRsaPrivateKey(anyString(), anyString()))
                .thenReturn("synthetic-password");
            var http = new MockHttpServletRequest();
            http.setRemoteAddr("127.0.0.1");
            var failure = assertThrows(RuntimeException.class, () -> handler.login(request(), new ClientResp(), http));
            assertTrue(failure.getMessage().contains("用户名或密码不正确"));
            redis.verify(() -> RedisUtils
                .set(startsWith(CacheConstants.USER_PASSWORD_ERROR_KEY_PREFIX), eq(1), eq(Duration.ofMinutes(10))));
        }
    }

    /** 创建只注入本次测试依赖的真实账号登录处理器。 */
    private AccountLoginHandler handler(OptionService options) {
        var handler = new AccountLoginHandler(mock(PasswordEncoder.class));
        ReflectionTestUtils.setField(handler, "optionService", options);
        return handler;
    }

    /** 使用不包含任何实际账号或密码的合法参数验证条件校验。 */
    private AccountLoginReq request() {
        var request = new AccountLoginReq();
        request.setClientId("synthetic-client");
        request.setAuthType(AuthTypeEnum.ACCOUNT);
        request.setUsername("synthetic-user");
        request.setPassword("synthetic-encrypted-value");
        return request;
    }
}
