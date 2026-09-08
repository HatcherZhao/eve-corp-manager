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
import top.continew.admin.controller.eve.model.EveRegistrationCallbackReq;
import top.continew.admin.eve.auth.SerenityAuthorizationStart;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.model.EveMeContextResp;
import top.continew.admin.eve.security.OAuthSecurityUtils;
import top.continew.admin.eve.service.EveCharacterBindingService;
import top.continew.admin.eve.service.EveContextService;
import top.continew.starter.log.annotation.Log;

import java.util.Arrays;

/**
 * 当前已登录用户绑定同军团 EVE 角色的接口。
 *
 * @author zhaoyuqing
 */
@Tag(name = "EVE 角色绑定")
@RestController
@RequiredArgsConstructor
@Log(ignore = true)
@RequestMapping("/eve/characters/binding")
public class EveCharacterBindingController {

    private static final String BINDING_COOKIE = "EVE_CHARACTER_BINDING";

    private final EveCharacterBindingService bindingService;
    private final EveContextService contextService;
    private final SerenityProperties properties;

    /** 发起严格绑定当前用户、租户与浏览器的 EVE 角色授权。 */
    @PostMapping("/start")
    @Operation(summary = "发起当前账号的 EVE 角色绑定")
    public SerenityAuthorizationStart start(HttpServletRequest request, HttpServletResponse response) {
        return bindingService.startCurrent(digest(getOrCreateBrowserBinding(request, response)));
    }

    /** 消费固定回调并返回已刷新会话中的最新 EVE 上下文。 */
    @PostMapping("/callback")
    @Operation(summary = "完成当前账号的 EVE 角色绑定")
    public EveMeContextResp callback(@RequestBody @Valid EveRegistrationCallbackReq req, HttpServletRequest request) {
        bindingService.bindCurrent(req.callbackUrl(), digest(requireBrowserBinding(request)));
        return contextService.getCurrentContext();
    }

    /** 获取或签发 HttpOnly、SameSite=Lax 的浏览器绑定 Cookie。 */
    private String getOrCreateBrowserBinding(HttpServletRequest request, HttpServletResponse response) {
        String existing = readCookie(request);
        if (existing != null) {
            return existing;
        }
        String binding = OAuthSecurityUtils.generateState();
        ResponseCookie cookie = ResponseCookie.from(BINDING_COOKIE, binding)
            .httpOnly(true)
            .secure(request.isSecure())
            .sameSite("Lax")
            .path("/")
            .maxAge(properties.getSso().getTransactionTtl())
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return binding;
    }

    /** 要求当前浏览器携带角色绑定 Cookie。 */
    private String requireBrowserBinding(HttpServletRequest request) {
        String binding = readCookie(request);
        if (binding == null) {
            throw new IllegalStateException("EVE 角色绑定浏览器会话已失效，请重新发起");
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
