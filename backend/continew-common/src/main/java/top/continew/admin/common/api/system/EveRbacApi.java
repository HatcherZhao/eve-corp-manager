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

import top.continew.admin.common.model.dto.EveRbacOverviewDTO;

import java.util.List;

/**
 * EVE 军团租户受控角色管理 API。
 *
 * @author zhaoyuqing
 */
public interface EveRbacApi {

    /** 查询当前军团可管理的业务角色与成员。 */
    EveRbacOverviewDTO getOverview(Long tenantId, Long actorUserId);

    /** 创建军团业务角色。 */
    Long createRole(Long tenantId, Long actorUserId, String name, String description, List<String> permissions);

    /** 更新军团业务角色。 */
    void updateRole(Long tenantId,
                    Long actorUserId,
                    Long roleId,
                    String name,
                    String description,
                    List<String> permissions);

    /** 删除军团业务角色。 */
    void deleteRole(Long tenantId, Long actorUserId, Long roleId);

    /** 替换军团成员的站内业务角色，派生身份不受影响。 */
    void assignMemberRoles(Long tenantId, Long actorUserId, Long userId, List<Long> roleIds);
}
