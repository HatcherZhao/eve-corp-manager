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

package top.continew.admin.system.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;
import top.continew.admin.system.mapper.UserRoleMapper;
import top.continew.admin.system.model.entity.RoleDO;
import top.continew.admin.system.model.entity.UserRoleDO;
import top.continew.admin.system.service.RoleService;
import top.continew.admin.system.service.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通用角色分配入口安全测试。
 *
 * @author zhaoyuqing
 */
class UserRoleServiceImplTest {

    private UserRoleMapper mapper;
    private RoleService roleService;
    private UserService userService;
    private UserRoleServiceImpl service;

    /** 为框架校验工具注册最小 Validator，避免纯单元测试依赖完整 Spring 上下文。 */
    @BeforeAll
    static void configureValidator() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("validator", mock(Validator.class));
        new SpringUtil().postProcessBeanFactory(beanFactory);
    }

    /** 初始化通用角色服务替身。 */
    @BeforeEach
    void setUp() {
        mapper = mock(UserRoleMapper.class);
        roleService = mock(RoleService.class);
        service = new UserRoleServiceImpl(mapper);
        ReflectionTestUtils.setField(service, "roleService", roleService);
        userService = mock(UserService.class);
        ReflectionTestUtils.setField(service, "userService", userService);
    }

    /** 批量用户分配入口拒绝派生角色。 */
    @Test
    void shouldRejectAssigningDerivedRoleToUsers() {
        when(roleService.listByIds(List.of(1L))).thenReturn(List.of(role("corp_owner")));
        assertThatThrownBy(() -> service.assignRoleToUsers(1L, List.of(20L))).hasMessage("EVE 派生角色由游戏身份自动维护，不允许人工授予");
        verify(mapper, never()).insertBatch(org.mockito.ArgumentMatchers.anyList());
    }

    /** 用户角色编辑入口拒绝夹带派生角色。 */
    @Test
    void shouldRejectDerivedRoleInUserRoleSet() {
        when(roleService.listByIds(List.of(8L, 2L))).thenReturn(List.of(role("general"), role("corp_admin")));
        assertThatThrownBy(() -> service.assignRolesToUser(List.of(8L, 2L), 20L))
            .hasMessage("EVE 派生角色由游戏身份自动维护，不允许人工授予");
    }

    /** 通用批量关联写入也必须拒绝派生角色，避免导入等内部入口绕过。 */
    @Test
    void shouldRejectDerivedRoleInBatchAssociationWrite() {
        when(roleService.listByIds(List.of(2L))).thenReturn(List.of(role("corp_member")));

        assertThatThrownBy(() -> service.saveBatch(List.of(new UserRoleDO(20L, 2L))))
            .hasMessage("EVE 派生角色由游戏身份自动维护，不允许人工授予");

        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.anyList());
    }

    /** 更新人工角色时必须保留已有 EVE 派生角色，并移除不再选择的旧人工角色。 */
    @Test
    void shouldPreserveDerivedRoleAndReplaceManualRoles() {
        List<UserRoleDO> savedRoles = new ArrayList<>();
        when(roleService.listByIds(List.of(8L))).thenReturn(List.of(role(8L, "general_new")));
        when(mapper.selectRoleIdsByUser(20L)).thenReturn(List.of(2L, 7L));
        when(roleService.listByIds(List.of(2L, 7L))).thenReturn(List
            .of(role(2L, "corp_admin"), role(7L, "general_old")));
        when(userService.getById(20L)).thenReturn(new top.continew.admin.system.model.entity.user.UserDO());
        when(roleService.listByUserId(20L)).thenReturn(Set.of());
        when(mapper.insertBatch(anyList())).thenAnswer(invocation -> {
            List<UserRoleDO> roles = invocation.getArgument(0);
            savedRoles.addAll(roles);
            return true;
        });

        boolean changed = service.assignRolesToUser(List.of(8L), 20L);

        verify(mapper).deleteByUserId(20L);
        verify(mapper).insertBatch(anyList());
        assertThat(changed).isTrue();
        assertThat(savedRoles).extracting(UserRoleDO::getRoleId).containsExactly(8L, 2L).doesNotContain(7L);
    }

    /** 创建测试角色。 */
    private static RoleDO role(String code) {
        return role(null, code);
    }

    /** 创建带主键的测试角色。 */
    private static RoleDO role(Long id, String code) {
        RoleDO role = new RoleDO();
        role.setId(id);
        role.setCode(code);
        return role;
    }
}
