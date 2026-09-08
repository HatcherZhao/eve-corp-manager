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
import top.continew.admin.common.enums.EveDerivedIdentity;
import top.continew.admin.system.mapper.RoleMapper;
import top.continew.admin.system.mapper.UserRoleMapper;
import top.continew.admin.system.model.entity.RoleDO;
import top.continew.admin.system.model.entity.UserRoleDO;
import top.continew.admin.system.service.RoleMenuService;
import top.continew.admin.system.service.RoleService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * EVE 派生角色精确同步测试。
 *
 * @author zhaoyuqing
 */
class EveDerivedRoleApiImplTest {

    private RoleMapper roleMapper;
    private UserRoleMapper userRoleMapper;
    private RoleMenuService roleMenuService;
    private EveDerivedRoleApiImpl service;

    /** 初始化角色与关联持久层替身。 */
    @BeforeEach
    void setUp() {
        roleMapper = mock(RoleMapper.class);
        userRoleMapper = mock(UserRoleMapper.class);
        roleMenuService = mock(RoleMenuService.class);
        service = new EveDerivedRoleApiImpl(roleMapper, userRoleMapper, roleMenuService, mock(RoleService.class));
        when(roleMapper.selectByCode(any())).thenAnswer(invocation -> role(invocation.getArgument(0), roleId(invocation
            .getArgument(0))));
        when(roleMapper.selectByCodes(any())).thenReturn(List
            .of(role("corp_owner", 1L), role("corp_admin", 2L), role("corp_member", 3L), role("admin", 4L)));
    }

    /** CEO 同步为成员加所有者，并清除全部旧派生角色和租户管理员角色。 */
    @Test
    void shouldSynchronizeOwnerExactly() {
        List<UserRoleDO> roles = captureInsertedRoles(EveDerivedIdentity.OWNER);
        assertThat(roles).extracting(UserRoleDO::getRoleId).containsExactlyInAnyOrder(3L, 1L);
        verify(userRoleMapper).deleteByUserAndRoleIds(20L, List.of(1L, 2L, 3L, 4L));
    }

    /** 总监同步为成员加管理员，不能残留所有者角色。 */
    @Test
    void shouldSynchronizeDirectorExactly() {
        assertThat(captureInsertedRoles(EveDerivedIdentity.ADMIN)).extracting(UserRoleDO::getRoleId)
            .containsExactlyInAnyOrder(3L, 2L);
    }

    /** 普通成员只保留成员派生角色。 */
    @Test
    void shouldSynchronizeMemberExactly() {
        assertThat(captureInsertedRoles(EveDerivedIdentity.MEMBER)).extracting(UserRoleDO::getRoleId)
            .containsExactly(3L);
    }

    /** 离团身份必须只清除旧角色，不再写入任何派生角色。 */
    @Test
    void shouldClearAllDerivedRolesForNoneIdentity() {
        service.synchronizeInTenant(20L, EveDerivedIdentity.NONE);

        verify(userRoleMapper).deleteByUserAndRoleIds(20L, List.of(1L, 2L, 3L, 4L));
        verify(userRoleMapper, never()).insertBatch(anyList());
    }

    /** 事务提交前不得刷新在线权限，成功提交后才同步会话。 */
    @Test
    void shouldRefreshOnlineContextOnlyAfterCommit() {
        EveDerivedRoleApiImpl spyService = spy(service);
        org.mockito.Mockito.doNothing().when(spyService).refreshUserContext(20L);
        TransactionSynchronizationManager.initSynchronization();
        try {
            spyService.synchronizeInTenant(20L, EveDerivedIdentity.ADMIN);
            verify(spyService, never()).refreshUserContext(20L);
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(spyService).refreshUserContext(20L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 事务回滚时不得刷新在线权限，避免数据库回滚后仍保留提权。 */
    @Test
    void shouldNotRefreshOnlineContextAfterRollback() {
        EveDerivedRoleApiImpl spyService = spy(service);
        org.mockito.Mockito.doNothing().when(spyService).refreshUserContext(20L);
        TransactionSynchronizationManager.initSynchronization();
        try {
            spyService.synchronizeInTenant(20L, EveDerivedIdentity.OWNER);
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization
                    .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(spyService, never()).refreshUserContext(20L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 捕获同步写入的最终角色集合。 */
    @SuppressWarnings("unchecked")
    private List<UserRoleDO> captureInsertedRoles(EveDerivedIdentity identity) {
        final List<UserRoleDO>[] captured = new List[1];
        doAnswer(invocation -> {
            captured[0] = invocation.getArgument(0);
            return true;
        }).when(userRoleMapper).insertBatch(anyList());
        service.synchronizeInTenant(20L, identity);
        return captured[0];
    }

    /** 创建角色记录。 */
    private static RoleDO role(String code, Long id) {
        RoleDO role = new RoleDO();
        role.setId(id);
        role.setCode(code);
        return role;
    }

    /** 返回固定测试角色 ID。 */
    private static Long roleId(String code) {
        return switch (code) {
            case "corp_owner" -> 1L;
            case "corp_admin" -> 2L;
            default -> 3L;
        };
    }
}
