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

import top.continew.admin.common.enums.DataScopeEnum;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.model.enums.EvePermissionTreeStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 当前用户只读权限视图，游戏角色、本站角色与 OAuth Scope 分区返回。
 *
 * @param status          数据状态
 * @param message         可操作的状态说明
 * @param character       当前主角色摘要
 * @param gamePermissions 游戏权限树分区
 * @param siteRoles       本站角色分区
 * @param scopes          OAuth Scope 分区
 * @author zhaoyuqing
 */
public record EvePermissionTreeResp(EvePermissionTreeStatus status, String message, CharacterInfo character,
                                    GamePermissionSection gamePermissions, SiteRoleSection siteRoles,
                                    ScopeSection scopes) {

    /** 当前会话绑定的主游戏角色安全摘要。 */
    public record CharacterInfo(Long characterRefId, Long characterId, String name, Long corporationId) {
    }

    /** 游戏权限树及其数据时间。 */
    public record GamePermissionSection(GamePermissionNode ceo, List<GamePermissionGroup> groups,
                                        LocalDateTime checkedAt, LocalDateTime sourceExpiresAt,
                                        boolean sourceExpiryEstimated, String dataSource) {
    }

    /** CEO 或单个游戏角色权限节点。 */
    public record GamePermissionNode(String key, String displayName, String code, String description, boolean owned,
                                     boolean known, String sourceScope, List<String> capabilities,
                                     LocalDateTime checkedAt, String dataSource) {
    }

    /** 严格对应国服角色接口字段的范围分组。 */
    public record GamePermissionGroup(String key, String displayName, String sourceScope,
                                      List<GamePermissionNode> children) {
    }

    /** 当前用户的本站角色及聚合有效权限。 */
    public record SiteRoleSection(List<SiteRoleItem> roles, List<String> effectivePermissions, LocalDateTime checkedAt,
                                  String dataSource) {
    }

    /** 单个本站角色摘要。 */
    public record SiteRoleItem(Long id, String code, String name, String description, String source, boolean derived,
                               boolean system, DataScopeEnum dataScope, List<String> permissions) {
    }

    /** 当前角色授权 Scope 状态。 */
    public record ScopeSection(EveAuthorizationStatus authorizationStatus, List<ScopeItem> items,
                               LocalDateTime updatedAt, String dataSource) {
    }

    /** 单个已授权、身份必需或模块相关 Scope。 */
    public record ScopeItem(String key, String scope, String displayName, boolean owned, boolean identityRequired,
                            List<String> capabilities) {
    }
}
