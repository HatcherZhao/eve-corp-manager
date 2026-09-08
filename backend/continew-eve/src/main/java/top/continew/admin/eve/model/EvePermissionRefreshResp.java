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

package top.continew.admin.eve.model;

import top.continew.admin.eve.model.enums.EvePermissionRefreshStatus;
import top.continew.admin.eve.model.enums.EveCapabilityStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * EVE 权限刷新安全响应，不包含令牌、授权码或完整回调地址。
 *
 * @param status                 刷新状态
 * @param requestedAt            本次请求时间
 * @param upstreamCheckedAt      最近上游检查时间
 * @param sourceExpiresAt        上游角色事实缓存过期时间
 * @param sourceExpiryEstimated  缓存过期时间是否为本地 TTL 估算值
 * @param lastRoleChangedAt      最近检测到角色变化的时间
 * @param nextSuggestedRefreshAt 下一建议刷新时间
 * @param missingScopes          需要重新授权补充的 Scope
 * @param reauthorizationPath    本站重新授权入口
 * @param addedGameRoles         本次新增的游戏角色
 * @param removedGameRoles       本次移除的游戏角色
 * @param derivedIdentityChange  本次派生身份变化
 * @param capabilityChanges      本次模块能力变化
 * @author zhaoyuqing
 */
public record EvePermissionRefreshResp(EvePermissionRefreshStatus status, LocalDateTime requestedAt,
                                       LocalDateTime upstreamCheckedAt, LocalDateTime sourceExpiresAt,
                                       boolean sourceExpiryEstimated, LocalDateTime lastRoleChangedAt,
                                       LocalDateTime nextSuggestedRefreshAt, List<String> missingScopes,
                                       String reauthorizationPath, List<GameRoleChange> addedGameRoles,
                                       List<GameRoleChange> removedGameRoles,
                                       DerivedIdentityChange derivedIdentityChange,
                                       List<CapabilityChange> capabilityChanges) {

    /** 单个游戏角色变化，保留原始范围以避免误解为全局权限。 */
    public record GameRoleChange(String sourceScope, String role) {
    }

    /** 刷新前后的派生管理身份变化。 */
    public record DerivedIdentityChange(String before, String after) {
    }

    /** 刷新前后的模块能力状态变化。 */
    public record CapabilityChange(String key, EveCapabilityStatus before, EveCapabilityStatus after) {
    }
}
