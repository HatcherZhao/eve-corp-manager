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

package top.continew.admin.common.enums;

import java.util.Set;

/**
 * 由 EVE 军团真实身份派生的站内身份。
 *
 * @author zhaoyuqing
 */
public enum EveDerivedIdentity {
    /** 不再具备当前军团成员身份，清除全部派生角色。 */
    NONE,
    /** 军团 CEO。 */
    OWNER,
    /** 军团总监。 */
    ADMIN,
    /** 普通军团成员。 */
    MEMBER;

    /** 军团所有者派生角色编码。 */
    public static final String OWNER_ROLE_CODE = "corp_owner";
    /** 军团管理员派生角色编码。 */
    public static final String ADMIN_ROLE_CODE = "corp_admin";
    /** 军团成员派生角色编码。 */
    public static final String MEMBER_ROLE_CODE = "corp_member";
    /** 所有不可人工授予的派生角色编码。 */
    public static final Set<String> ROLE_CODES = Set.of(OWNER_ROLE_CODE, ADMIN_ROLE_CODE, MEMBER_ROLE_CODE);

    /** CEO 优先于总监生成唯一管理身份。 */
    public static EveDerivedIdentity from(boolean ceo, boolean director) {
        if (ceo) {
            return OWNER;
        }
        return director ? ADMIN : MEMBER;
    }
}
