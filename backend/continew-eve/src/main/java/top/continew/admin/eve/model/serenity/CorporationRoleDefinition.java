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

import java.util.Set;

/**
 * 可展示的游戏军团角色定义，未知角色仍保留国服返回的原始代码。
 *
 * @param code         国服接口原始代码
 * @param displayName  中文名称或原始代码
 * @param category     权限分类
 * @param description  权限说明
 * @param capabilities 关联的本站候选能力
 * @param known        是否为当前契约已知角色
 * @author zhaoyuqing
 */
public record CorporationRoleDefinition(String code, String displayName, CorporationRoleCategory category,
                                        String description, Set<EveSystemCapability> capabilities, boolean known) {

    /**
     * 从已知目录角色创建定义。
     *
     * @param role 已知角色
     * @return 展示定义
     */
    public static CorporationRoleDefinition known(EveCorporationRole role) {
        return new CorporationRoleDefinition(role.getCode(), role.getDisplayName(), role.getCategory(), role
            .getDescription(), role.getCapabilities(), true);
    }

    /**
     * 创建未知角色的降级定义。
     *
     * @param rawCode 国服接口原始代码
     * @return 保留原始代码的展示定义
     */
    public static CorporationRoleDefinition unknown(String rawCode) {
        String code = rawCode == null ? "" : rawCode;
        return new CorporationRoleDefinition(code, code, CorporationRoleCategory.UNKNOWN, "国服返回了尚未收录的军团角色", Set
            .of(), false);
    }
}
