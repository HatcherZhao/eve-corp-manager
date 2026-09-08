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

package top.continew.admin.common.api.system;

import top.continew.admin.common.enums.EveDerivedIdentity;

/**
 * EVE 游戏身份派生站内角色的内部 API。
 *
 * @author zhaoyuqing
 */
public interface EveDerivedRoleApi {

    /**
     * 按最新游戏身份精确同步用户派生角色。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param identity 最新派生身份
     */
    void synchronize(Long tenantId, Long userId, EveDerivedIdentity identity);
}
