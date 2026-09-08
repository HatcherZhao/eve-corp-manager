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
import top.continew.admin.controller.eve.model.EveRegistrationCallbackReq;
import top.continew.admin.controller.eve.model.EveRegistrationCompleteReq;
import top.continew.admin.eve.auth.OAuthTransactionPurpose;
import top.continew.admin.eve.auth.SerenityAuthorizationStart;
import top.continew.admin.eve.auth.SerenityAuthorizationStartService;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.registration.EveRegistrationCompleteCommand;
import top.continew.admin.eve.registration.EveRegistrationCompleteResult;
import top.continew.admin.eve.registration.EveRegistrationService;
import top.continew.admin.eve.registration.RegistrationCallbackResult;
import top.continew.admin.eve.registration.SerenityRegistrationCallbackService;
import top.continew.admin.eve.model.enums.EveCapability;
import top.continew.admin.eve.security.OAuthSecurityUtils;
import top.continew.starter.core.util.validation.ValidationUtils;
import top.continew.starter.extension.tenant.annotation.TenantIgnore;
import top.continew.starter.log.annotation.Log;
import top.continew.starter.ratelimiter.annotation.RateLimiter;
import top.continew.starter.ratelimiter.enums.LimitType;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 未登录用户通过 EVE 国服身份验证注册本站账号。
 *
 * @author zhaoyuqing
 */
@Tag(name = "EVE 国服注册")
@RestController
@SaIgnore
@TenantIgnore
@Log(ignore = true)
@RequiredArgsConstructor
@RequestMapping("/eve/registration")
public class EveRegistrationController {

    private static final String BINDING_COOKIE = "EVE_REG_BINDING";

    private final SerenityAuthorizationStartService authorizationStartService;
    private final SerenityRegistrationCallbackService callbackService;
    private final EveRegistrationService registrationService;
    private final SerenityProperties properties;

    /** 发起带 state 和 PKCE S256 的国服注册授权。 */
    @PostMapping("/start")
    @Operation(summary = "发起 EVE 国服注册授权")
    @RateLimiter(name = "EVE_REGISTRATION_START", rate = 30, interval = 1, unit = TimeUnit.MINUTES, type = LimitType.IP, message = "EVE 注册授权发起过于频繁，请稍后再试")
    public SerenityAuthorizationStart start(HttpServletRequest request, HttpServletResponse response) {
        String binding = getOrCreateBrowserBinding(request, response);
        Set<String> scopes = new LinkedHashSet<>(properties.getSso().getRequiredScopes());
        Arrays.stream(EveCapability.values()).map(EveCapability::getScope).forEach(scopes::add);
        return authorizationStartService.start(OAuthTransactionPurpose.REGISTER, null, digest(binding), scopes);
    }

    /** 导入并验证固定国服回调地址，返回一次性本站注册凭证。 */
    @PostMapping("/callback")
    @Operation(summary = "验证 EVE 国服注册回调")
    @RateLimiter(name = "EVE_REGISTRATION_CALLBACK", rate = 60, interval = 5, unit = TimeUnit.MINUTES, type = LimitType.IP, message = "EVE 注册回调验证过于频繁，请稍后再试")
    public RegistrationCallbackResult callback(@RequestBody @Valid EveRegistrationCallbackReq req,
                                               HttpServletRequest request) {
        return callbackService.handle(req.callbackUrl(), digest(requireBrowserBinding(request)));
    }

    /** 设置本站用户名和密码，原子认领或加入军团并建立会话。 */
    @PostMapping("/complete")
    @Operation(summary = "激活 EVE 军团本站账号")
    @RateLimiter(name = "EVE_REGISTRATION_COMPLETE", rate = 10, interval = 10, unit = TimeUnit.MINUTES, type = LimitType.IP, message = "EVE 账号激活操作过于频繁，请稍后再试")
    public EveRegistrationCompleteResult complete(@RequestBody @Valid EveRegistrationCompleteReq req,
                                                  HttpServletRequest request) {
        String password = SecureUtils.decryptPasswordByRsaPrivateKey(req.password(), "密码解密失败", true);
        String confirmPassword = SecureUtils.decryptPasswordByRsaPrivateKey(req.confirmPassword(), "确认密码解密失败", true);
        ValidationUtils.throwIfNotEqual(password, confirmPassword, "两次输入的密码不一致");
        return registrationService.complete(new EveRegistrationCompleteCommand(req
            .credential(), digest(requireBrowserBinding(request)), req.username().trim().toLowerCase(Locale.ROOT), req
                .password(), req.clientId()));
    }

    /** 获取已有浏览器绑定；不存在时签发 HttpOnly、SameSite=Lax Cookie。 */
    private String getOrCreateBrowserBinding(HttpServletRequest request, HttpServletResponse response) {
        String existing = readCookie(request);
        if (existing != null) {
            return existing;
        }
        String binding = OAuthSecurityUtils.generateState();
        Duration ttl = properties.getSso().getTransactionTtl().plus(properties.getRegistration().getCredentialTtl());
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

    /** 要求当前浏览器携带注册绑定 Cookie。 */
    private String requireBrowserBinding(HttpServletRequest request) {
        String binding = readCookie(request);
        if (binding == null) {
            throw new IllegalStateException("EVE 注册浏览器会话已失效");
        }
        return binding;
    }

    /** 读取并严格校验浏览器绑定 Cookie。 */
    private String readCookie(HttpServletRequest request) {
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

    /** 对浏览器随机绑定值生成不可逆摘要。 */
    private static String digest(String binding) {
        return OAuthSecurityUtils.sha256Base64Url(binding);
    }
}
