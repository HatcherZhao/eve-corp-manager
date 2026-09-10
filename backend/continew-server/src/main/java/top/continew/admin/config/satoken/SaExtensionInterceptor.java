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

import cn.dev33.satoken.fun.SaParamFunction;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.system.service.EveSessionPermissionRefreshService;
import top.continew.starter.core.util.ServletUtils;
import top.continew.starter.extension.tenant.context.TenantContextHolder;
import top.continew.starter.json.jackson.util.JSONUtils;
import top.continew.starter.web.model.R;

import java.util.Objects;

/**
 * Sa-Token 扩展拦截器
 *
 * @author Charles7c
 * @since 2024/10/10 20:25
 */
@Slf4j
public class SaExtensionInterceptor extends SaInterceptor {

    private static final String EVE_PATH_PREFIX = "/eve/";

    private final EveSessionPermissionRefreshService eveSessionPermissionRefreshService;

    public SaExtensionInterceptor(SaParamFunction<Object> auth) {
        this(auth, null);
    }

    /**
     * 创建认证拦截器，并在 EVE 请求进入注解鉴权前刷新在线用户的本站权限。
     *
     * @param auth                               Sa-Token 路由认证规则
     * @param eveSessionPermissionRefreshService EVE 会话权限刷新服务
     */
    public SaExtensionInterceptor(SaParamFunction<Object> auth,
                                  EveSessionPermissionRefreshService eveSessionPermissionRefreshService) {
        super(auth);
        this.eveSessionPermissionRefreshService = eveSessionPermissionRefreshService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if (isLogin() && getUserContext() == null) {
            return rejectMissingUserContext(response);
        }
        refreshEveSessionPermissions(request);
        boolean flag = super.preHandle(request, response, handler);
        if (!flag || !isLogin()) {
            return flag;
        }
        // 设置上下文
        UserContext userContext = getUserContext();
        if (userContext == null) {
            return rejectMissingUserContext(response);
        }
        // 检查用户租户权限
        if (TenantContextHolder.isTenantEnabled()) {
            Long userTenantId = userContext.getTenantId();
            Long tenantId = TenantContextHolder.getTenantId();
            if (!hasTenantAccess(userTenantId, tenantId)) {
                R r = R.fail(String.valueOf(HttpStatus.FORBIDDEN.value()), "您当前没有访问该租户的权限");
                response.setStatus(HttpStatus.FORBIDDEN.value());
                ServletUtils.writeJSON(response, JSONUtils.toJsonStr(r));
                return false;
            }
        }
        UserContextHolder.getExtraContext();
        return true;
    }

    /** 判断当前请求是否处于登录态，独立方法便于验证异常会话分支。 */
    boolean isLogin() {
        return StpUtil.isLogin();
    }

    /** 获取当前登录用户上下文。 */
    UserContext getUserContext() {
        return UserContextHolder.getContext();
    }

    /** 在 EVE 权限注解校验前更新会话，确保角色菜单迁移或总监调权立即对在线用户生效。 */
    void refreshEveSessionPermissions(HttpServletRequest request) {
        if (eveSessionPermissionRefreshService != null && isLogin() && isEveRequest(request)) {
            eveSessionPermissionRefreshService.refreshCurrentEveUser();
        }
    }

    /** 判断当前请求是否属于需要同步本站军团权限的 EVE 接口。 */
    static boolean isEveRequest(HttpServletRequest request) {
        return request != null && request.getRequestURI() != null && request.getRequestURI()
            .startsWith(EVE_PATH_PREFIX);
    }

    /** 注销当前异常登录会话。 */
    void logout() {
        StpUtil.logout();
    }

    /** 注销并拒绝缺失用户上下文的异常登录会话。 */
    private boolean rejectMissingUserContext(HttpServletResponse response) throws Exception {
        log.warn("已登录会话缺少用户上下文，已注销异常会话");
        try {
            logout();
        } catch (RuntimeException e) {
            log.error("异常登录会话注销失败，当前请求仍按未授权拒绝", e);
        }
        writeInvalidSessionResponse(response);
        return false;
    }

    /** 返回标准未授权响应，避免异常会话继续进入业务控制器。 */
    void writeInvalidSessionResponse(HttpServletResponse response) throws Exception {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"code\":\"401\",\"msg\":\"登录会话已失效，请重新登录\"}");
    }

    /** 登录会话租户必须与请求解析出的租户完全一致，空值也不得放行。 */
    static boolean hasTenantAccess(Long userTenantId, Long requestTenantId) {
        return userTenantId != null && Objects.equals(userTenantId, requestTenantId);
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                @Nullable Exception e) throws Exception {
        // 清除上下文
        try {
            super.afterCompletion(request, response, handler, e);
        } finally {
            UserContextHolder.clearContext();
        }
    }
}
