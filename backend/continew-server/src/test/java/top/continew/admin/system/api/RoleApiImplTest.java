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

import org.junit.jupiter.api.Test;
import top.continew.admin.common.enums.DataScopeEnum;
import top.continew.admin.common.model.dto.EveSiteRoleDTO;
import top.continew.admin.system.model.entity.RoleDO;
import top.continew.admin.system.model.resp.MenuResp;
import top.continew.admin.system.service.MenuService;
import top.continew.admin.system.service.RoleService;
import top.continew.admin.system.service.UserRoleService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EVE 本站角色摘要内部 API 测试。
 *
 * @author zhaoyuqing
 */
class RoleApiImplTest {

    /** 查询在指定租户上下文中执行，并按角色分别返回去重后的权限码。 */
    @Test
    void shouldListRoleSummariesInsideRequestedTenant() {
        RoleService roleService = mock(RoleService.class);
        UserRoleService userRoleService = mock(UserRoleService.class);
        MenuService menuService = mock(MenuService.class);
        RoleApiImpl api = new RoleApiImpl(roleService, userRoleService, menuService);
        RoleDO role = new RoleDO();
        role.setId(7L);
        role.setCode("corp_admin");
        role.setName("军团总监");
        role.setDescription("由游戏身份派生");
        role.setDataScope(DataScopeEnum.ALL);
        role.setIsSystem(true);
        role.setSort(10);
        MenuResp first = new MenuResp();
        first.setPermission("eve:assets:view");
        MenuResp duplicate = new MenuResp();
        duplicate.setPermission("eve:assets:view");
        MenuResp blank = new MenuResp();
        blank.setPermission(" ");
        when(userRoleService.listRoleIdByUserId(20L)).thenReturn(List.of(7L));
        when(roleService.listByIds(List.of(7L))).thenReturn(List.of(role));
        when(menuService.listByRoleId(7L)).thenReturn(List.of(first, duplicate, blank));

        List<EveSiteRoleDTO> result = api.listEveSiteRolesInTenant(20L);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo("corp_admin");
            assertThat(item.name()).isEqualTo("军团总监");
            assertThat(item.system()).isTrue();
            assertThat(item.permissions()).containsExactly("eve:assets:view");
        });
        verify(userRoleService).listRoleIdByUserId(20L);
        verify(roleService).listByIds(List.of(7L));
    }
}
