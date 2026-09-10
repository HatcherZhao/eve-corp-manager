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

package top.continew.admin.eve.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import top.continew.admin.eve.model.serenity.SerenityDatasource;

import jakarta.annotation.PostConstruct;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * EVE 国服 SSO 与 ESI 契约配置。
 *
 * @author zhaoyuqing
 */
@Data
@Component
@ConfigurationProperties(prefix = "eve.serenity")
public class SerenityProperties implements EnvironmentAware {

    /** 当前 Spring 环境，仅用于限制不安全回环回调必须运行在 local profile。 */
    private transient Environment environment;

    /** SSO 端点配置。 */
    private Sso sso = new Sso();

    /** ESI 端点配置。 */
    private Esi esi = new Esi();

    /** 本站注册流程配置。 */
    private Registration registration = new Registration();

    /** EVE 身份验证找回本站密码配置。 */
    private PasswordRecovery passwordRecovery = new PasswordRecovery();

    /** 主动权限刷新配置。 */
    private PermissionRefresh permissionRefresh = new PermissionRefresh();

    /** 国服 HTTP 客户端超时配置。 */
    private HttpClient httpClient = new HttpClient();

    /** 启动时校验国服 OAuth 安全配置。 */
    @PostConstruct
    public void validate() {
        sso.validateEnabledConfiguration(isLocalProfileActive());
        if (sso.isEnabled()) {
            registration.validate();
            passwordRecovery.validate();
            permissionRefresh.validate();
            httpClient.validate();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setEnvironment(@NonNull Environment environment) {
        this.environment = environment;
    }

    /** 判断是否显式启用了 local profile。 */
    private boolean isLocalProfileActive() {
        return environment != null && Set.of(environment.getActiveProfiles()).contains("local");
    }

    /**
     * 国服 SSO 端点。
     *
     * @author zhaoyuqing
     */
    @Data
    public static class Sso {

        /** 是否启用国服 OAuth；默认关闭，避免未配置凭据时暴露入口。 */
        private boolean enabled;

        /** OAuth 客户端 ID。 */
        private String clientId;

        /** OAuth 客户端密钥；国服 Swagger 公共客户端不需要配置。 */
        private String clientSecret;

        /** 本系统固定回调地址。 */
        private String callbackUrl;

        /** 国服登录链路要求的设备标识，用于区分共享公共客户端下的调用方。 */
        private String deviceId = "eve-corp-manager";

        /** 允许接收自动或手动授权结果的精确回调地址集合。 */
        private Set<String> allowedCallbackUris = new LinkedHashSet<>();

        /** 是否显式允许本地回环 HTTP 回调；生产环境必须保持关闭。 */
        private boolean allowInsecureLocalhostCallback;

        /** OAuth 事务有效期。 */
        private Duration transactionTtl = Duration.ofMinutes(10);

        /** 回调 URL 最大长度。 */
        private int maxCallbackUrlLength = 2048;

        /** 国服 JWT subject 的完整正则；未知格式时保持为空并拒绝启用。 */
        private String subjectPattern;

        /** 国服 JWT Scope 声明名称；未知格式时保持为空并拒绝启用。 */
        private String scopeClaim;

        /** 单个 Scope 值的完整正则；未知格式时保持为空并拒绝启用。 */
        private String scopeValuePattern;

        /** 身份令牌必须包含的 Scope。 */
        private Set<String> requiredScopes = new LinkedHashSet<>(Set.of("esi-characters.read_corporation_roles.v1"));

        /** 令牌签发者。 */
        private String issuer = "login.evepc.163.com";

        /** OAuth2 授权端点。 */
        private String authorizationEndpoint = "https://login.evepc.163.com/v2/oauth/authorize";

        /** OAuth2 令牌端点。 */
        private String tokenEndpoint = "https://login.evepc.163.com/v2/oauth/token";

        /** JWT 公钥端点。 */
        private String jwksEndpoint = "https://login.evepc.163.com/oauth/jwks";

        /** OAuth2 令牌撤销端点。 */
        private String revocationEndpoint = "https://login.evepc.163.com/v2/oauth/revoke";

        /**
         * 启用 OAuth 时校验所有安全关键配置，缺失时阻止应用启动。
         */
        public void validateEnabledConfiguration(boolean localProfileActive) {
            if (!enabled) {
                return;
            }
            requireText(clientId, "client-id");
            requireText(deviceId, "device-id");
            if (!deviceId.matches("[A-Za-z0-9._-]{1,64}")) {
                throw new IllegalStateException("国服 OAuth 设备标识格式无效");
            }
            requireExactCallbackUri(callbackUrl, "callback-url", localProfileActive);
            if (allowedCallbackUris == null || allowedCallbackUris.isEmpty()) {
                throw new IllegalStateException("国服 OAuth 启用时必须配置允许回调地址");
            }
            allowedCallbackUris
                .forEach(uri -> requireExactCallbackUri(uri, "allowed-callback-uris", localProfileActive));
            if (!allowedCallbackUris.contains(callbackUrl)) {
                throw new IllegalStateException("国服 OAuth 固定回调地址必须包含在允许回调地址中");
            }
            requireText(subjectPattern, "subject-pattern");
            requireText(scopeClaim, "scope-claim");
            requireText(scopeValuePattern, "scope-value-pattern");
            if (requiredScopes == null || requiredScopes.isEmpty()) {
                throw new IllegalStateException("国服 OAuth 启用时必须配置角色读取 Scope");
            }
            if (transactionTtl == null || transactionTtl.isNegative() || transactionTtl.isZero()) {
                throw new IllegalStateException("国服 OAuth 事务有效期必须大于零");
            }
            if (maxCallbackUrlLength < 256 || maxCallbackUrlLength > 8192) {
                throw new IllegalStateException("国服 OAuth 回调 URL 长度限制无效");
            }
        }

        /** 判断当前是否使用需要 Basic 认证的机密客户端。 */
        public boolean hasClientSecret() {
            return clientSecret != null && !clientSecret.isBlank();
        }

        /** 校验必填文本。 */
        private static void requireText(String value, String property) {
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("国服 OAuth 启用时缺少安全配置：" + property);
            }
        }

        /** 校验不含用户信息、查询和片段的精确回调地址。 */
        private void requireExactCallbackUri(String value, String property, boolean localProfileActive) {
            requireText(value, property);
            URI uri;
            try {
                uri = URI.create(value);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("国服 OAuth 地址配置无效：" + property, e);
            }
            boolean https = "https".equalsIgnoreCase(uri.getScheme());
            boolean allowedLoopbackHttp = allowInsecureLocalhostCallback && localProfileActive && "http"
                .equalsIgnoreCase(uri.getScheme()) && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1"
                    .equals(uri.getHost()));
            if ((!https && !allowedLoopbackHttp) || uri.getHost() == null || uri.getUserInfo() != null || uri
                .getQuery() != null || uri.getFragment() != null || uri.getPath() == null || uri.getPath().isBlank()) {
                throw new IllegalStateException("国服 OAuth 地址必须是精确 HTTPS 地址或显式允许的本地回环地址：" + property);
            }
        }
    }

    /**
     * 国服 ESI 端点。
     *
     * @author zhaoyuqing
     */
    @Data
    public static class Esi {

        /** ESI latest 版本基址。 */
        private String baseUrl = "https://ali-esi.evepc.163.com/latest";

        /** 默认数据源。 */
        private SerenityDatasource datasource = SerenityDatasource.SERENITY;
    }

    /**
     * 本站 EVE 注册流程配置。
     *
     * @author zhaoyuqing
     */
    @Data
    public static class Registration {

        /** 一次性注册凭证有效期。 */
        private Duration credentialTtl = Duration.ofMinutes(10);

        /** EVE 军团租户使用的专用套餐 ID。 */
        private Long tenantPackageId = 20001L;

        /** 启用 OAuth 时校验本站注册安全配置。 */
        private void validate() {
            if (credentialTtl == null || credentialTtl.isZero() || credentialTtl.isNegative()) {
                throw new IllegalStateException("EVE 注册凭证有效期必须大于零");
            }
            if (tenantPackageId == null || tenantPackageId <= 0) {
                throw new IllegalStateException("EVE 租户套餐 ID 无效");
            }
        }
    }

    /**
     * EVE 身份验证找回本站密码配置。
     *
     * @author zhaoyuqing
     */
    @Data
    public static class PasswordRecovery {

        /** 一次性密码重置凭证有效期。 */
        private Duration credentialTtl = Duration.ofMinutes(5);

        /** 校验短期密码重置凭证有效期。 */
        private void validate() {
            if (credentialTtl == null || credentialTtl.isZero() || credentialTtl.isNegative()) {
                throw new IllegalStateException("EVE 密码重置凭证有效期必须大于零");
            }
        }
    }

    /**
     * 主动权限刷新与后台复核配置。
     *
     * @author zhaoyuqing
     */
    @Data
    public static class PermissionRefresh {

        /** 同一用户两次主动刷新之间的冷却时间。 */
        private Duration cooldown = Duration.ofMinutes(5);

        /** 分布式锁最大等待时间。 */
        private Duration lockWait = Duration.ofSeconds(3);

        /** 访问令牌到期前主动轮换的提前量。 */
        private Duration tokenRefreshSkew = Duration.ofMinutes(2);

        /** 后台授权扫描间隔；纳入到期窗口，确保任务延迟不会错过令牌主动轮换。 */
        private Duration backgroundReviewInterval = Duration.ofMinutes(1);

        /** 国服角色事实的默认缓存周期。 */
        private Duration roleCacheTtl = Duration.ofHours(1);

        /** 后台单批复核的最大授权数。 */
        private int batchSize = 50;

        /** 校验刷新时间和批大小。 */
        private void validate() {
            requirePositive(cooldown, "permission-refresh.cooldown");
            requirePositive(lockWait, "permission-refresh.lock-wait");
            requirePositive(tokenRefreshSkew, "permission-refresh.token-refresh-skew");
            requirePositive(backgroundReviewInterval, "permission-refresh.background-review-interval");
            requirePositive(roleCacheTtl, "permission-refresh.role-cache-ttl");
            if (batchSize < 1 || batchSize > 500) {
                throw new IllegalStateException("国服权限复核批大小必须在 1 到 500 之间");
            }
        }

        /** 校验持续时间为正数。 */
        private static void requirePositive(Duration duration, String property) {
            if (duration == null || duration.isZero() || duration.isNegative()) {
                throw new IllegalStateException("国服权限刷新配置无效：" + property);
            }
        }
    }

    /**
     * 国服 HTTP 客户端有界超时。
     *
     * @author zhaoyuqing
     */
    @Data
    public static class HttpClient {

        /** 建立上游连接的最大等待时间。 */
        private Duration connectTimeout = Duration.ofSeconds(5);

        /** 读取单次上游响应的最大等待时间。 */
        private Duration readTimeout = Duration.ofSeconds(15);

        /** 校验连接和读取超时均为正数。 */
        private void validate() {
            PermissionRefresh.requirePositive(connectTimeout, "http-client.connect-timeout");
            PermissionRefresh.requirePositive(readTimeout, "http-client.read-timeout");
        }
    }
}
