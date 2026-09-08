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

package top.continew.admin.eve.model.serenity;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 国服角色接口响应，完整保留全局、总部、基地和其他地点四类范围。
 *
 * @param roles        全局角色代码
 * @param rolesAtHq    总部角色代码
 * @param rolesAtBase  基地角色代码
 * @param rolesAtOther 其他地点角色代码
 * @author zhaoyuqing
 */
public record SerenityCorporationRolesResponse(List<String> roles, @JsonProperty("roles_at_hq") List<String> rolesAtHq,
                                               @JsonProperty("roles_at_base") List<String> rolesAtBase,
                                               @JsonProperty("roles_at_other") List<String> rolesAtOther) {

    /**
     * 将四类范围解析为可展示的完整角色树。
     *
     * @return 按范围组织的角色定义
     */
    public Map<CorporationRoleScope, List<CorporationRoleDefinition>> resolveByScope() {
        Map<CorporationRoleScope, List<CorporationRoleDefinition>> result = new EnumMap<>(CorporationRoleScope.class);
        result.put(CorporationRoleScope.ROLES, EveCorporationRoleCatalog.resolveAll(roles));
        result.put(CorporationRoleScope.ROLES_AT_HQ, EveCorporationRoleCatalog.resolveAll(rolesAtHq));
        result.put(CorporationRoleScope.ROLES_AT_BASE, EveCorporationRoleCatalog.resolveAll(rolesAtBase));
        result.put(CorporationRoleScope.ROLES_AT_OTHER, EveCorporationRoleCatalog.resolveAll(rolesAtOther));
        return Map.copyOf(result);
    }
}
