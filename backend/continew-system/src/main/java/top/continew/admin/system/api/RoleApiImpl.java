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

package top.continew.admin.system.api;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.common.api.system.RoleApi;
import top.continew.admin.common.model.dto.EveSiteRoleDTO;
import top.continew.admin.system.model.entity.RoleDO;
import top.continew.admin.system.model.resp.MenuResp;
import top.continew.admin.system.service.MenuService;
import top.continew.admin.system.service.RoleService;
import top.continew.admin.system.service.UserRoleService;
import top.continew.starter.extension.tenant.util.TenantUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 角色业务 API 实现
 * 
 * @author Charles7c
 * @since 2025/7/26 9:39
 */
@Service
@RequiredArgsConstructor
public class RoleApiImpl implements RoleApi {

    private final RoleService baseService;
    private final UserRoleService userRoleService;
    private final MenuService menuService;

    @Override
    public Long getIdByCode(String code) {
        return baseService.getIdByCode(code);
    }

    @Override
    public void updateUserContext(Long roleId) {
        baseService.updateUserContext(roleId);
    }

    /** {@inheritDoc} */
    @Override
    public List<EveSiteRoleDTO> listEveSiteRoles(Long tenantId, Long userId) {
        AtomicReference<List<EveSiteRoleDTO>> result = new AtomicReference<>(List.of());
        TenantUtils.execute(tenantId, () -> result.set(listEveSiteRolesInTenant(userId)));
        return result.get();
    }

    /** 在已建立的租户上下文内查询用户角色，避免跨租户角色 ID 被解析。 */
    List<EveSiteRoleDTO> listEveSiteRolesInTenant(Long userId) {
        List<Long> roleIds = userRoleService.listRoleIdByUserId(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<RoleDO> roles = new ArrayList<>(baseService.listByIds(roleIds));
        roles.sort(Comparator.comparing(RoleDO::getSort, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(RoleDO::getId));
        return roles.stream().map(this::toEveSiteRole).toList();
    }

    /** 转换角色并仅返回有效权限码，不暴露菜单结构。 */
    private EveSiteRoleDTO toEveSiteRole(RoleDO role) {
        List<String> permissions = menuService.listByRoleId(role.getId())
            .stream()
            .map(MenuResp::getPermission)
            .filter(Objects::nonNull)
            .filter(permission -> !permission.isBlank())
            .distinct()
            .sorted()
            .toList();
        return new EveSiteRoleDTO(role.getId(), role.getCode(), role.getName(), role.getDescription(), role
            .getDataScope(), Boolean.TRUE.equals(role.getIsSystem()), permissions);
    }
}
