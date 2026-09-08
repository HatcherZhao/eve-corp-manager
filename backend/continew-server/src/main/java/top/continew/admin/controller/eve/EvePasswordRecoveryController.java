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

import cn.dev33.satoken.annotation.SaIgnore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.util.SecureUtils;
import top.continew.admin.controller.eve.model.EvePasswordRecoveryCompleteReq;
import top.continew.admin.controller.eve.model.EveRegistrationCallbackReq;
import top.continew.admin.eve.auth.SerenityAuthorizationStart;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.recovery.EvePasswordRecoveryService;
import top.continew.admin.eve.recovery.PasswordRecoveryCallbackResult;
import top.continew.admin.eve.security.OAuthSecurityUtils;
import top.continew.starter.extension.tenant.annotation.TenantIgnore;
import top.continew.starter.log.annotation.Log;
import top.continew.starter.ratelimiter.annotation.RateLimiter;
import top.continew.starter.ratelimiter.enums.LimitType;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 未登录用户通过已绑定 EVE 角色验证身份并重置本站密码的接口。
 *
 * @author zhaoyuqing
 */
@Tag(name = "EVE 找回密码")
@RestController
@SaIgnore
@TenantIgnore
@Log(ignore = true)
@RequiredArgsConstructor
@RequestMapping("/eve/password-recovery")
public class EvePasswordRecoveryController {

    private static final String BINDING_COOKIE = "EVE_PASSWORD_RECOVERY_BINDING";

    private final EvePasswordRecoveryService recoveryService;
    private final SerenityProperties properties;

    /** 发起带 state 和 PKCE S256 的 EVE 身份验证找回密码授权。 */
    @PostMapping("/start")
    @Operation(summary = "发起 EVE 身份验证找回密码授权")
    @RateLimiter(name = "EVE_PASSWORD_RECOVERY_START", rate = 30, interval = 1, unit = TimeUnit.MINUTES, type = LimitType.IP, message = "EVE 找回密码授权发起过于频繁，请稍后再试")
    public SerenityAuthorizationStart start(HttpServletRequest request, HttpServletResponse response) {
        return recoveryService.start(digest(getOrCreateBrowserBinding(request, response)));
    }

    /** 验证固定 EVE 回调地址并签发一次性密码重置凭证。 */
    @PostMapping("/callback")
    @Operation(summary = "验证 EVE 找回密码回调")
    @RateLimiter(name = "EVE_PASSWORD_RECOVERY_CALLBACK", rate = 60, interval = 5, unit = TimeUnit.MINUTES, type = LimitType.IP, message = "EVE 找回密码回调验证过于频繁，请稍后再试")
    public PasswordRecoveryCallbackResult callback(@RequestBody @Valid EveRegistrationCallbackReq req,
                                                   HttpServletRequest request) {
        return recoveryService.verify(req.callbackUrl(), digest(requireBrowserBinding(request)));
    }

    /** 以短期一次性凭证重置本站密码，不创建或绑定任何账号。 */
    @PostMapping("/complete")
    @Operation(summary = "完成 EVE 找回本站密码")
    @RateLimiter(name = "EVE_PASSWORD_RECOVERY_COMPLETE", rate = 10, interval = 10, unit = TimeUnit.MINUTES, type = LimitType.IP, message = "EVE 密码重置操作过于频繁，请稍后再试")
    public void complete(@RequestBody @Valid EvePasswordRecoveryCompleteReq req, HttpServletRequest request) {
        String password = SecureUtils.decryptPasswordByRsaPrivateKey(req.password(), "新密码解密失败", true);
        String confirmPassword = SecureUtils.decryptPasswordByRsaPrivateKey(req.confirmPassword(), "确认密码解密失败", true);
        recoveryService.reset(req.credential(), digest(requireBrowserBinding(request)), password, confirmPassword);
    }

    /** 获取或签发 HttpOnly、SameSite=Lax 的浏览器绑定 Cookie。 */
    private String getOrCreateBrowserBinding(HttpServletRequest request, HttpServletResponse response) {
        String existing = readCookie(request);
        if (existing != null) {
            return existing;
        }
        String binding = OAuthSecurityUtils.generateState();
        Duration ttl = properties.getSso()
            .getTransactionTtl()
            .plus(properties.getPasswordRecovery().getCredentialTtl());
        ResponseCookie cookie = ResponseCookie.from(BINDING_COOKIE, binding)
            .httpOnly(true)
            .secure(request.isSecure())
            .sameSite("Lax")
            .path("/")
            .maxAge(ttl)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return binding;
    }

    /** 要求当前浏览器携带找回密码绑定 Cookie。 */
    private String requireBrowserBinding(HttpServletRequest request) {
        String binding = readCookie(request);
        if (binding == null) {
            throw new IllegalStateException("EVE 找回密码浏览器会话已失效，请重新发起");
        }
        return binding;
    }

    /** 读取并严格校验浏览器绑定 Cookie。 */
    private static String readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
            .filter(cookie -> BINDING_COOKIE.equals(cookie.getName()))
            .map(Cookie::getValue)
            .filter(value -> value.matches("[A-Za-z0-9_-]{43}"))
            .findFirst()
            .orElse(null);
    }

    /** 对浏览器随机绑定生成不可逆摘要。 */
    private static String digest(String binding) {
        return OAuthSecurityUtils.sha256Base64Url(binding);
    }
}
