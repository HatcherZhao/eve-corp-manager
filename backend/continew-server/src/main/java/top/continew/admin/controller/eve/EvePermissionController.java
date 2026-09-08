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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import top.continew.admin.controller.eve.model.EveRegistrationCallbackReq;
import top.continew.admin.eve.auth.SerenityAuthorizationStart;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.model.EveAuthorizationRevocationResp;
import top.continew.admin.eve.model.EvePermissionRefreshResp;
import top.continew.admin.eve.model.EvePermissionTreeResp;
import top.continew.admin.eve.security.OAuthSecurityUtils;
import top.continew.admin.eve.service.EvePermissionReauthorizationService;
import top.continew.admin.eve.service.EveAuthorizationRevocationService;
import top.continew.admin.eve.service.EvePermissionRefreshService;
import top.continew.admin.eve.service.EvePermissionTreeService;
import top.continew.starter.log.annotation.Log;

import java.time.Duration;
import java.util.Arrays;

/**
 * 当前登录用户的 EVE 权限刷新与重新授权接口。
 *
 * @author zhaoyuqing
 */
@Tag(name = "EVE 权限刷新")
@RestController
@RequiredArgsConstructor
@Log(ignore = true)
@RequestMapping("/eve/permissions")
public class EvePermissionController {

    private static final String BINDING_COOKIE = "EVE_PERMISSION_BINDING";

    private final EvePermissionRefreshService permissionRefreshService;
    private final EvePermissionReauthorizationService reauthorizationService;
    private final EvePermissionTreeService permissionTreeService;
    private final EveAuthorizationRevocationService revocationService;
    private final SerenityProperties properties;

    /** 查询当前用户只读的游戏权限、本站角色与授权 Scope 分区。 */
    @GetMapping("/tree")
    @Operation(summary = "查询当前用户的只读 EVE 权限树")
    public EvePermissionTreeResp tree() {
        return permissionTreeService.getCurrentTree();
    }

    /** 从当前会话定位游戏角色并主动刷新权限。 */
    @PostMapping("/refresh")
    @Operation(summary = "主动刷新当前用户的 EVE 权限")
    public EvePermissionRefreshResp refresh() {
        return permissionRefreshService.refreshCurrent();
    }

    /** 撤销当前用户拥有的指定 EVE 授权并立即收敛权限。 */
    @PostMapping("/{authorizationId}/revoke")
    @Operation(summary = "撤销当前用户的 EVE 授权")
    public EveAuthorizationRevocationResp revoke(@PathVariable("authorizationId") Long authorizationId) {
        return revocationService.revokeCurrent(authorizationId);
    }

    /** 发起严格绑定当前角色的 Scope 扩展授权。 */
    @PostMapping("/reauthorization/start")
    @Operation(summary = "发起当前 EVE 角色重新授权")
    public SerenityAuthorizationStart startReauthorization(HttpServletRequest request, HttpServletResponse response) {
        String binding = getOrCreateBrowserBinding(request, response);
        return reauthorizationService.startCurrent(digest(binding));
    }

    /** 导入固定回调地址并立即刷新当前角色权限。 */
    @PostMapping("/reauthorization/callback")
    @Operation(summary = "完成当前 EVE 角色重新授权")
    public EvePermissionRefreshResp completeReauthorization(@RequestBody @Valid EveRegistrationCallbackReq req,
                                                            HttpServletRequest request) {
        return reauthorizationService.handleCurrent(req.callbackUrl(), digest(requireBrowserBinding(request)));
    }

    /** 获取或签发 HttpOnly、SameSite=Lax 的重新授权浏览器绑定。 */
    private String getOrCreateBrowserBinding(HttpServletRequest request, HttpServletResponse response) {
        String existing = readCookie(request);
        if (existing != null) {
            return existing;
        }
        String binding = OAuthSecurityUtils.generateState();
        Duration ttl = properties.getSso().getTransactionTtl();
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

    /** 要求当前浏览器携带重新授权绑定 Cookie。 */
    private String requireBrowserBinding(HttpServletRequest request) {
        String binding = readCookie(request);
        if (binding == null) {
            throw new IllegalStateException("EVE 重新授权浏览器会话已失效");
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

    /** 对随机浏览器绑定生成不可逆摘要。 */
    private static String digest(String binding) {
        return OAuthSecurityUtils.sha256Base64Url(binding);
    }
}
