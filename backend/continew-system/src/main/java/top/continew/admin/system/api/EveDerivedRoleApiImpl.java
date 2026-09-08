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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.continew.admin.common.api.system.EveDerivedRoleApi;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.common.enums.DataScopeEnum;
import top.continew.admin.common.enums.EveDerivedIdentity;
import top.continew.admin.common.enums.RoleCodeEnum;
import top.continew.admin.system.mapper.RoleMapper;
import top.continew.admin.system.mapper.UserRoleMapper;
import top.continew.admin.system.model.entity.RoleDO;
import top.continew.admin.system.model.entity.UserRoleDO;
import top.continew.admin.system.service.RoleMenuService;
import top.continew.admin.system.service.RoleService;
import top.continew.starter.extension.tenant.util.TenantUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static top.continew.admin.common.enums.EveDerivedIdentity.ADMIN_ROLE_CODE;
import static top.continew.admin.common.enums.EveDerivedIdentity.MEMBER_ROLE_CODE;
import static top.continew.admin.common.enums.EveDerivedIdentity.OWNER_ROLE_CODE;

/**
 * EVE 派生角色同步实现，唯一依据是已验证的游戏身份。
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveDerivedRoleApiImpl implements EveDerivedRoleApi {

    private static final List<Long> IDENTITY_MENU_IDS = List.of(20000L, 20010L, 20011L);
    private static final List<Long> MANAGER_MENU_IDS = List
        .of(20000L, 20010L, 20011L, 20020L, 20021L, 20022L, 20023L, 20024L);

    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMenuService roleMenuService;
    private final RoleService roleService;

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void synchronize(Long tenantId, Long userId, EveDerivedIdentity identity) {
        TenantUtils.execute(tenantId, () -> synchronizeInTenant(userId, identity));
    }

    /** 在租户上下文内替换派生角色并清除初始化管理员角色。 */
    void synchronizeInTenant(Long userId, EveDerivedIdentity identity) {
        Map<String, Long> roleIds = Map
            .of(MEMBER_ROLE_CODE, getOrCreateRole(MEMBER_ROLE_CODE, "军团成员", 30, IDENTITY_MENU_IDS), OWNER_ROLE_CODE, getOrCreateRole(OWNER_ROLE_CODE, "军团 CEO", 10, MANAGER_MENU_IDS), ADMIN_ROLE_CODE, getOrCreateRole(ADMIN_ROLE_CODE, "军团总监", 20, MANAGER_MENU_IDS));
        List<String> removableCodes = new ArrayList<>(EveDerivedIdentity.ROLE_CODES);
        removableCodes.add(RoleCodeEnum.TENANT_ADMIN.getCode());
        List<Long> removableIds = roleMapper.selectByCodes(removableCodes).stream().map(RoleDO::getId).toList();
        if (!removableIds.isEmpty()) {
            userRoleMapper.deleteByUserAndRoleIds(userId, removableIds);
        }
        List<UserRoleDO> targetRoles = new ArrayList<>();
        if (!EveDerivedIdentity.NONE.equals(identity)) {
            targetRoles.add(new UserRoleDO(userId, roleIds.get(MEMBER_ROLE_CODE)));
            if (EveDerivedIdentity.OWNER.equals(identity)) {
                targetRoles.add(new UserRoleDO(userId, roleIds.get(OWNER_ROLE_CODE)));
            } else if (EveDerivedIdentity.ADMIN.equals(identity)) {
                targetRoles.add(new UserRoleDO(userId, roleIds.get(ADMIN_ROLE_CODE)));
            }
        }
        if (!targetRoles.isEmpty()) {
            userRoleMapper.insertBatch(targetRoles);
        }
        refreshUserContextAfterCommit(userId);
    }

    /** 查询或创建指定派生角色，并校准其固定菜单模板。 */
    private Long getOrCreateRole(String code, String name, int sort, List<Long> menuIds) {
        RoleDO role = roleMapper.selectByCode(code);
        if (role == null) {
            role = new RoleDO();
            role.setName(name);
            role.setCode(code);
            role.setDataScope(DataScopeEnum.ALL);
            role.setDescription("由 EVE 军团真实身份自动维护，不可人工授予");
            role.setSort(sort);
            role.setIsSystem(true);
            role.setMenuCheckStrictly(true);
            role.setDeptCheckStrictly(true);
            role.setCreateUser(1L);
            role.setDeleted(0L);
            roleMapper.insert(role);
            // 角色仅首次创建时写入菜单；已存在角色已由数据库迁移统一补齐，登录时不得重复改写菜单。
            roleMenuService.add(menuIds, role.getId());
        }
        return role.getId();
    }

    /** 仅在角色事务成功提交后刷新在线会话，回滚时不得残留提权上下文。 */
    private void refreshUserContextAfterCommit(Long userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            refreshUserContext(userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                refreshUserContext(userId);
            }
        });
    }

    /** 角色增删提交后按用户刷新现有在线会话。 */
    void refreshUserContext(Long userId) {
        UserContext context = UserContextHolder.getContext(userId);
        if (context == null) {
            return;
        }
        context.setRoles(roleService.listByUserId(userId));
        context.setPermissions(roleService.listPermissionByUserId(userId));
        UserContextHolder.updateSessionContext(context);
    }
}
