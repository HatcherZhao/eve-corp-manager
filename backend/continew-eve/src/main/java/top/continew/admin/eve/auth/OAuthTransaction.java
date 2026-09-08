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

package top.continew.admin.eve.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Set;

/**
 * Redis 中短期保存的国服 OAuth 事务。
 *
 * @author zhaoyuqing
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthTransaction implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 浏览器绑定摘要。 */
    private String browserBindingDigest;
    /** 事务用途。 */
    private OAuthTransactionPurpose purpose;
    /** 已登录绑定场景中的本站用户 ID。 */
    private Long boundUserId;
    /** 已登录绑定场景中的租户 ID。 */
    private Long boundTenantId;
    /** 重新授权必须匹配的服务器代码。 */
    private String boundServer;
    /** 重新授权必须匹配的游戏角色 ID。 */
    private Long boundCharacterId;
    /** 授权完成后的站内相对跳转路径，禁止保存外部绝对 URL。 */
    private String redirectUri;
    /** 本次请求的 Scope。 */
    private Set<String> requestedScopes;
    /** PKCE verifier。 */
    private String verifier;
    /** 事务过期时间。 */
    private Instant expiresAt;

    /** 防止日志输出浏览器摘要、跳转地址、Scope 和 verifier。 */
    @Override
    public String toString() {
        return "OAuthTransaction[purpose=" + purpose + ", boundUserId=" + boundUserId + ", boundTenantId=" + boundTenantId + ", expiresAt=" + expiresAt + "]";
    }
}
