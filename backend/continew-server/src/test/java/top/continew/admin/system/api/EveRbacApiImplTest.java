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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.continew.admin.system.mapper.MenuMapper;
import top.continew.admin.system.mapper.RoleMapper;
import top.continew.admin.system.mapper.UserRoleMapper;
import top.continew.admin.system.mapper.user.UserMapper;
import top.continew.admin.system.model.entity.RoleDO;
import top.continew.admin.system.service.RoleMenuService;
import top.continew.admin.system.service.RoleService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EVE 受控角色管理安全边界测试。
 *
 * @author zhaoyuqing
 */
class EveRbacApiImplTest {

    private RoleMapper roleMapper;
    private UserRoleMapper userRoleMapper;
    private EveRbacApiImpl service;

    @BeforeEach
    void setUp() {
        roleMapper = mock(RoleMapper.class);
        userRoleMapper = mock(UserRoleMapper.class);
        service = new EveRbacApiImpl(roleMapper, userRoleMapper, mock(UserMapper.class), mock(MenuMapper.class), mock(RoleMenuService.class), mock(RoleService.class));
    }

    @Test
    void shouldRejectOrdinaryMemberAsManager() {
        when(userRoleMapper.selectRoleIdsByUser(20L)).thenReturn(List.of(3L));
        when(roleMapper.selectByIds(List.of(3L))).thenReturn(List.of(role(3L, "corp_member", true)));

        assertThatThrownBy(() -> service.checkManager(20L)).hasMessage("仅军团 CEO 或总监可管理业务角色");
    }

    @Test
    void shouldAllowOwnerAndDirectorAsManager() {
        when(userRoleMapper.selectRoleIdsByUser(20L)).thenReturn(List.of(1L));
        when(roleMapper.selectByIds(List.of(1L))).thenReturn(List.of(role(1L, "corp_owner", true)));
        when(userRoleMapper.selectRoleIdsByUser(21L)).thenReturn(List.of(2L));
        when(roleMapper.selectByIds(List.of(2L))).thenReturn(List.of(role(2L, "corp_admin", true)));

        service.checkManager(20L);
        service.checkManager(21L);
    }

    @Test
    void shouldRejectDerivedAndSystemRoleEditing() {
        when(roleMapper.selectById(2L)).thenReturn(role(2L, "corp_admin", true));

        assertThatThrownBy(() -> service.requireBusinessRole(2L)).hasMessage("角色不存在或不允许编辑");
    }

    @Test
    void shouldOnlyAcceptFiveCapabilityPermissions() {
        assertThat(service.validatePermissions(List.of("eve:assets:view", "eve:mining:view")))
            .containsExactly("eve:assets:view", "eve:mining:view");
        assertThatThrownBy(() -> service.validatePermissions(List.of("system:user:list"))).hasMessage("角色包含不可授予的权限");
    }

    /** 事务提交前不刷新会话，提交成功后才加载数据库中的最终权限。 */
    @Test
    void shouldRefreshOnlineContextOnlyAfterCommit() {
        EveRbacApiImpl spyService = spy(service);
        doNothing().when(spyService).refreshUserContext(20L);
        TransactionSynchronizationManager.initSynchronization();
        try {
            spyService.refreshUserContextAfterCommit(20L);
            verify(spyService, never()).refreshUserContext(20L);
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(spyService).refreshUserContext(20L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 事务回滚时不触发会话刷新，避免未提交授权残留在 Sa-Token 会话。 */
    @Test
    void shouldNotRefreshOnlineContextAfterRollback() {
        EveRbacApiImpl spyService = spy(service);
        doNothing().when(spyService).refreshUserContext(20L);
        TransactionSynchronizationManager.initSynchronization();
        try {
            spyService.refreshUserContextAfterCommit(20L);
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization
                    .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(spyService, never()).refreshUserContext(20L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 构造测试角色。 */
    private static RoleDO role(Long id, String code, boolean system) {
        RoleDO role = new RoleDO();
        role.setId(id);
        role.setCode(code);
        role.setIsSystem(system);
        return role;
    }
}
