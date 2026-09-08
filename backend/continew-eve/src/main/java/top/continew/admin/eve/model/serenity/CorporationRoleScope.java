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

/**
 * 游戏军团角色的生效范围。
 *
 * @author zhaoyuqing
 */
public enum CorporationRoleScope {

    /** 全局军团角色。 */
    ROLES("roles", "全局"),

    /** 总部角色。 */
    ROLES_AT_HQ("roles_at_hq", "总部"),

    /** 基地角色。 */
    ROLES_AT_BASE("roles_at_base", "基地"),

    /** 其他地点角色。 */
    ROLES_AT_OTHER("roles_at_other", "其他地点");

    private final String fieldName;
    private final String displayName;

    CorporationRoleScope(String fieldName, String displayName) {
        this.fieldName = fieldName;
        this.displayName = displayName;
    }

    /** 获取国服接口字段名。 */
    public String getFieldName() {
        return fieldName;
    }

    /** 获取中文范围名称。 */
    public String getDisplayName() {
        return displayName;
    }
}
