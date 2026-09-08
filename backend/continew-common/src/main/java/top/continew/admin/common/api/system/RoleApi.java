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

import top.continew.admin.common.model.dto.EveSiteRoleDTO;

import java.util.List;

/**
 * 角色业务 API
 *
 * @author Charles7c
 * @since 2025/7/26 9:39
 */
public interface RoleApi {

    /**
     * 根据编码查询 ID
     *
     * @param code 编码
     * @return 角色 ID
     */
    Long getIdByCode(String code);

    /**
     * 更新用户上下文
     *
     * @param roleId 角色 ID
     */
    void updateUserContext(Long roleId);

    /**
     * 查询指定租户用户的本站角色及逐角色权限摘要。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 本站角色安全摘要
     */
    List<EveSiteRoleDTO> listEveSiteRoles(Long tenantId, Long userId);
}
