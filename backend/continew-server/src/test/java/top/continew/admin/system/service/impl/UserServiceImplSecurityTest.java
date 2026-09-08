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

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import top.continew.admin.auth.service.OnlineUserService;
import top.continew.admin.system.mapper.user.UserMapper;
import top.continew.admin.system.model.entity.RoleDO;
import top.continew.admin.system.model.entity.UserRoleDO;
import top.continew.admin.system.model.entity.user.UserDO;
import top.continew.admin.system.service.FileService;
import top.continew.admin.system.service.OptionService;
import top.continew.admin.system.service.RoleService;
import top.continew.admin.system.service.UserPasswordHistoryService;
import top.continew.admin.system.service.UserRoleService;
import top.continew.admin.system.service.UserSocialService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 用户导入角色边界安全测试。
 *
 * @author zhaoyuqing
 */
class UserServiceImplSecurityTest {

    /** 用户导入必须拒绝授予任何 EVE 派生角色。 */
    @Test
    void shouldRejectDerivedRoleFromImport() {
        RoleDO role = new RoleDO();
        role.setCode("corp_admin");

        assertThatThrownBy(() -> UserServiceImpl.rejectDerivedImportRoles(List.of(role)))
            .hasMessage("EVE 派生角色由游戏身份自动维护，不允许通过用户导入授予");
    }

    /** 更新导入必须经角色服务合并角色，从而保留用户既有派生角色。 */
    @Test
    void shouldPreserveDerivedRolesWhenImportUpdatesExistingUser() {
        UserRoleService userRoleService = mock(UserRoleService.class);
        UserMapper userMapper = mock(UserMapper.class);
        UserServiceImpl service = service(userRoleService, userMapper);
        UserDO updatedUser = new UserDO();
        updatedUser.setId(20L);

        ReflectionTestUtils.invokeMethod(service, "doImportUser", List.of(), List.of(updatedUser), List
            .of(new UserRoleDO(20L, 8L)));

        verify(userRoleService).assignRolesToUser(List.of(8L), 20L);
    }

    /** 创建仅覆盖导入角色逻辑所需依赖的服务实例。 */
    private static UserServiceImpl service(UserRoleService userRoleService, UserMapper userMapper) {
        UserServiceImpl service = new UserServiceImpl(mock(org.springframework.security.crypto.password.PasswordEncoder.class), mock(UserPasswordHistoryService.class), mock(UserSocialService.class), userRoleService, mock(OptionService.class), mock(RoleService.class), mock(OnlineUserService.class), mock(FileService.class), mock(org.dromara.x.file.storage.core.FileStorageService.class));
        ReflectionTestUtils.setField(service, "baseMapper", userMapper);
        return service;
    }
}
