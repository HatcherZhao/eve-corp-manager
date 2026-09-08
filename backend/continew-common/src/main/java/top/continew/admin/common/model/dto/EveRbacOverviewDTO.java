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

package top.continew.admin.common.model.dto;

import java.util.List;

/**
 * EVE 军团租户角色管理总览。
 *
 * @author zhaoyuqing
 */
public record EveRbacOverviewDTO(List<PermissionItem> permissions, List<RoleItem> roles, List<MemberItem> members) {

    /** 可授予的业务查看权限。 */
    public record PermissionItem(String permission, String name) {
    }

    /** 可编辑的站内业务角色。 */
    public record RoleItem(Long id, String name, String code, String description, List<String> permissions) {
    }

    /** 当前租户成员及其派生身份、业务角色。 */
    public record MemberItem(Long id, String username, String nickname, String derivedIdentity,
                             List<Long> businessRoleIds) {
    }
}
