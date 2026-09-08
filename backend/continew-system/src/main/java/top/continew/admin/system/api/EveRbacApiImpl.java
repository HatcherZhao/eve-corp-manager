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

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.continew.admin.common.api.system.EveRbacApi;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.common.enums.DataScopeEnum;
import top.continew.admin.common.model.dto.EveRbacOverviewDTO;
import top.continew.admin.system.mapper.MenuMapper;
import top.continew.admin.system.mapper.RoleMapper;
import top.continew.admin.system.mapper.UserRoleMapper;
import top.continew.admin.system.mapper.user.UserMapper;
import top.continew.admin.system.model.entity.MenuDO;
import top.continew.admin.system.model.entity.RoleDO;
import top.continew.admin.system.model.entity.UserRoleDO;
import top.continew.admin.system.model.entity.user.UserDO;
import top.continew.admin.system.service.RoleMenuService;
import top.continew.admin.system.service.RoleService;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.tenant.util.TenantUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * EVE 军团租户受控角色管理实现。
 *
 * <p>该入口只管理本租户的非系统业务角色，游戏派生身份始终由 EVE 同步流程维护。</p>
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveRbacApiImpl implements EveRbacApi {

    private static final String BUSINESS_ROLE_PREFIX = "eve_business_";
    private static final Set<String> MANAGER_ROLE_CODES = Set.of("corp_owner", "corp_admin");
    private static final Map<String, String> ALLOWED_PERMISSIONS = new LinkedHashMap<>();

    static {
        ALLOWED_PERMISSIONS.put("eve:assets:view", "军团资产");
        ALLOWED_PERMISSIONS.put("eve:structures:view", "建筑设施");
        ALLOWED_PERMISSIONS.put("eve:extractions:view", "月矿计划");
        ALLOWED_PERMISSIONS.put("eve:mining:view", "采矿账本");
        ALLOWED_PERMISSIONS.put("eve:members:view", "军团人员");
    }

    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserMapper userMapper;
    private final MenuMapper menuMapper;
    private final RoleMenuService roleMenuService;
    private final RoleService roleService;

    /** {@inheritDoc} */
    @Override
    public EveRbacOverviewDTO getOverview(Long tenantId, Long actorUserId) {
        AtomicReference<EveRbacOverviewDTO> result = new AtomicReference<>();
        TenantUtils.execute(tenantId, () -> {
            checkManager(actorUserId);
            result.set(buildOverview());
        });
        return result.get();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRole(Long tenantId, Long actorUserId, String name, String description, List<String> permissions) {
        AtomicLong roleId = new AtomicLong();
        TenantUtils.execute(tenantId, () -> {
            checkManager(actorUserId);
            List<String> normalized = validatePermissions(permissions);
            rejectIf(name == null || name.isBlank(), "角色名称不能为空");
            RoleDO role = new RoleDO();
            role.setName(name.trim());
            role.setCode(BUSINESS_ROLE_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            role.setDataScope(DataScopeEnum.ALL);
            role.setDescription(description == null ? null : description.trim());
            role.setSort(100);
            role.setIsSystem(false);
            role.setMenuCheckStrictly(true);
            role.setDeptCheckStrictly(true);
            role.setCreateUser(actorUserId);
            role.setDeleted(0L);
            roleMapper.insert(role);
            replaceRoleMenus(role.getId(), normalized);
            roleId.set(role.getId());
        });
        return roleId.get();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long tenantId,
                           Long actorUserId,
                           Long roleId,
                           String name,
                           String description,
                           List<String> permissions) {
        TenantUtils.execute(tenantId, () -> {
            checkManager(actorUserId);
            RoleDO role = requireBusinessRole(roleId);
            rejectIf(name == null || name.isBlank(), "角色名称不能为空");
            role.setName(name.trim());
            role.setDescription(description == null ? null : description.trim());
            role.setUpdateUser(actorUserId);
            roleMapper.updateById(role);
            replaceRoleMenus(roleId, validatePermissions(permissions));
            refreshAssignedUsersAfterCommit(roleId);
        });
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long tenantId, Long actorUserId, Long roleId) {
        TenantUtils.execute(tenantId, () -> {
            checkManager(actorUserId);
            requireBusinessRole(roleId);
            List<Long> users = userRoleMapper.lambdaQuery()
                .eq(UserRoleDO::getRoleId, roleId)
                .list()
                .stream()
                .map(UserRoleDO::getUserId)
                .distinct()
                .toList();
            userRoleMapper.lambdaUpdate().eq(UserRoleDO::getRoleId, roleId).remove();
            roleMenuService.deleteByRoleIds(List.of(roleId));
            roleMapper.deleteById(roleId);
            users.forEach(this::refreshUserContextAfterCommit);
        });
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMemberRoles(Long tenantId, Long actorUserId, Long userId, List<Long> roleIds) {
        TenantUtils.execute(tenantId, () -> {
            checkManager(actorUserId);
            rejectIf(userMapper.selectById(userId) == null, "成员不属于当前军团租户");
            List<RoleDO> businessRoles = listBusinessRoles();
            Map<Long, RoleDO> businessById = businessRoles.stream()
                .collect(Collectors.toMap(RoleDO::getId, Function.identity()));
            List<Long> normalized = roleIds == null ? List.of() : roleIds.stream().distinct().toList();
            rejectIf(normalized.stream().anyMatch(id -> !businessById.containsKey(id)), "只能分配当前军团创建的业务角色");
            List<Long> allBusinessRoleIds = businessRoles.stream().map(RoleDO::getId).toList();
            if (!allBusinessRoleIds.isEmpty()) {
                userRoleMapper.deleteBusinessRoles(userId, allBusinessRoleIds);
            }
            if (!normalized.isEmpty()) {
                userRoleMapper.insertBatch(normalized.stream().map(id -> new UserRoleDO(userId, id)).toList());
            }
            refreshUserContextAfterCommit(userId);
        });
    }

    /** 校验操作者具备由游戏身份派生的 CEO 或总监角色。 */
    void checkManager(Long actorUserId) {
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUser(actorUserId);
        Set<String> codes = roleIds.isEmpty()
            ? Set.of()
            : roleMapper.selectByIds(roleIds).stream().map(RoleDO::getCode).collect(Collectors.toSet());
        rejectIf(codes.stream().noneMatch(MANAGER_ROLE_CODES::contains), "仅军团 CEO 或总监可管理业务角色");
    }

    /** 构造当前租户角色与成员总览。 */
    private EveRbacOverviewDTO buildOverview() {
        List<RoleDO> roles = listBusinessRoles();
        List<Long> businessRoleIds = roles.stream().map(RoleDO::getId).toList();
        Map<Long, List<String>> rolePermissions = roles.stream()
            .collect(Collectors.toMap(RoleDO::getId, role -> menuMapper.selectListByRoleId(role.getId())
                .stream()
                .map(MenuDO::getPermission)
                .filter(ALLOWED_PERMISSIONS::containsKey)
                .distinct()
                .toList()));
        List<EveRbacOverviewDTO.RoleItem> roleItems = roles.stream()
            .map(role -> new EveRbacOverviewDTO.RoleItem(role.getId(), role.getName(), role.getCode(), role
                .getDescription(), rolePermissions.get(role.getId())))
            .toList();
        List<EveRbacOverviewDTO.MemberItem> members = userMapper.selectList(Wrappers.<UserDO>lambdaQuery()
            .eq(UserDO::getDeleted, 0L)
            .orderByAsc(UserDO::getNickname)).stream().map(user -> {
                List<Long> assigned = userRoleMapper.selectRoleIdsByUser(user.getId());
                List<RoleDO> assignedRoles = assigned.isEmpty() ? List.of() : roleMapper.selectByIds(assigned);
                Set<String> codes = assignedRoles.stream().map(RoleDO::getCode).collect(Collectors.toSet());
                String identity = codes.contains("corp_owner")
                    ? "OWNER"
                    : codes.contains("corp_admin") ? "ADMIN" : codes.contains("corp_member") ? "MEMBER" : "NONE";
                List<Long> assignedBusiness = assigned.stream().filter(businessRoleIds::contains).toList();
                return new EveRbacOverviewDTO.MemberItem(user.getId(), user.getUsername(), user
                    .getNickname(), identity, assignedBusiness);
            }).toList();
        List<EveRbacOverviewDTO.PermissionItem> permissions = ALLOWED_PERMISSIONS.entrySet()
            .stream()
            .map(entry -> new EveRbacOverviewDTO.PermissionItem(entry.getKey(), entry.getValue()))
            .toList();
        return new EveRbacOverviewDTO(permissions, roleItems, members);
    }

    /** 查询当前租户全部可编辑业务角色。 */
    private List<RoleDO> listBusinessRoles() {
        return roleMapper.selectList(Wrappers.<RoleDO>lambdaQuery()
            .likeRight(RoleDO::getCode, BUSINESS_ROLE_PREFIX)
            .eq(RoleDO::getIsSystem, false)
            .eq(RoleDO::getDeleted, 0L)
            .orderByAsc(RoleDO::getSort, RoleDO::getId));
    }

    /** 校验并返回当前租户可编辑业务角色。 */
    RoleDO requireBusinessRole(Long roleId) {
        RoleDO role = roleMapper.selectById(roleId);
        rejectIf(role == null || Boolean.TRUE.equals(role.getIsSystem()) || role.getCode() == null || !role.getCode()
            .startsWith(BUSINESS_ROLE_PREFIX), "角色不存在或不允许编辑");
        return role;
    }

    /** 将输入权限限制到固定五项 EVE 查看权限。 */
    List<String> validatePermissions(List<String> permissions) {
        List<String> normalized = permissions == null ? List.of() : permissions.stream().distinct().toList();
        rejectIf(normalized.stream()
            .anyMatch(permission -> !ALLOWED_PERMISSIONS.containsKey(permission)), "角色包含不可授予的权限");
        return normalized;
    }

    /** 原子替换业务角色关联的五项能力菜单。 */
    private void replaceRoleMenus(Long roleId, List<String> permissions) {
        roleMenuService.deleteByRoleIds(List.of(roleId));
        if (permissions.isEmpty()) {
            return;
        }
        List<Long> menuIds = menuMapper.selectByPermissions(permissions)
            .stream()
            .map(MenuDO::getId)
            .distinct()
            .toList();
        rejectIf(menuIds.size() != permissions.size(), "EVE 能力权限菜单配置不完整");
        roleMenuService.add(menuIds, roleId);
    }

    /** 在事务提交后刷新拥有指定角色的在线用户上下文。 */
    private void refreshAssignedUsersAfterCommit(Long roleId) {
        userRoleMapper.lambdaQuery()
            .eq(UserRoleDO::getRoleId, roleId)
            .list()
            .stream()
            .map(UserRoleDO::getUserId)
            .distinct()
            .forEach(this::refreshUserContextAfterCommit);
    }

    /**
     * 仅在角色事务成功提交后刷新在线会话，回滚时不得泄漏未提交权限。
     *
     * <p>无事务同步上下文时立即执行，兼容受控的直接调用与测试场景。</p>
     */
    void refreshUserContextAfterCommit(Long userId) {
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

    /** 角色事务提交后刷新在线用户的角色和权限。 */
    void refreshUserContext(Long userId) {
        UserContext context = UserContextHolder.getContext(userId);
        if (context == null) {
            return;
        }
        context.setRoles(roleService.listByUserId(userId));
        context.setPermissions(roleService.listPermissionByUserId(userId));
        UserContextHolder.updateSessionContext(context);
    }

    /** 在受控管理入口统一抛出可读业务异常。 */
    private static void rejectIf(boolean condition, String message) {
        if (condition) {
            throw new BusinessException(message);
        }
    }
}
