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

package top.continew.admin.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;

import java.util.Set;

/**
 * 在访问 EVE 页面时同步在线军团成员的本站角色与权限。
 *
 * <p>角色菜单由迁移或总监调整后，已登录用户不必退出再登录即可获得最新本站权限。</p>
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveSessionPermissionRefreshService {

    private static final Set<String> EVE_ROLE_CODES = Set.of("corp_owner", "corp_admin", "corp_member");

    private final RoleService roleService;

    /** 刷新当前在线 EVE 军团成员的会话权限；非 EVE 会话不产生额外查询。 */
    public void refreshCurrentEveUser() {
        UserContext context = UserContextHolder.getContext();
        if (context == null || context.getRoleCodes() == null || context.getRoleCodes()
            .stream()
            .noneMatch(EVE_ROLE_CODES::contains)) {
            return;
        }
        Long userId = context.getId();
        context.setRoles(roleService.listByUserId(userId));
        context.setPermissions(roleService.listPermissionByUserId(userId));
        UserContextHolder.updateSessionContext(context);
    }
}
