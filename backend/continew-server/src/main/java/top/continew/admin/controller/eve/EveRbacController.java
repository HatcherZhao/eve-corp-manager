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

package top.continew.admin.controller.eve;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.api.system.EveRbacApi;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.common.model.dto.EveRbacOverviewDTO;
import top.continew.admin.controller.eve.model.EveBusinessRoleReq;
import top.continew.admin.controller.eve.model.EveMemberRoleAssignReq;

/**
 * EVE 军团租户业务角色与成员授权控制面。
 *
 * @author zhaoyuqing
 */
@Tag(name = "EVE 军团权限管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/eve/rbac")
public class EveRbacController {

    private final EveRbacApi rbacApi;

    /** 查询可管理权限、角色与成员。 */
    @GetMapping("/overview")
    @Operation(summary = "查询军团权限管理总览")
    public EveRbacOverviewDTO getOverview() {
        UserContext context = UserContextHolder.getContext();
        return rbacApi.getOverview(context.getTenantId(), context.getId());
    }

    /** 创建站内业务角色。 */
    @PostMapping("/roles")
    @Operation(summary = "创建军团业务角色")
    public Long createRole(@Valid @RequestBody EveBusinessRoleReq req) {
        UserContext context = UserContextHolder.getContext();
        return rbacApi.createRole(context.getTenantId(), context.getId(), req.name(), req.description(), req
            .permissions());
    }

    /** 更新站内业务角色。 */
    @PutMapping("/roles/{roleId}")
    @Operation(summary = "更新军团业务角色")
    public void updateRole(@PathVariable Long roleId, @Valid @RequestBody EveBusinessRoleReq req) {
        UserContext context = UserContextHolder.getContext();
        rbacApi.updateRole(context.getTenantId(), context.getId(), roleId, req.name(), req.description(), req
            .permissions());
    }

    /** 删除站内业务角色。 */
    @DeleteMapping("/roles/{roleId}")
    @Operation(summary = "删除军团业务角色")
    public void deleteRole(@PathVariable Long roleId) {
        UserContext context = UserContextHolder.getContext();
        rbacApi.deleteRole(context.getTenantId(), context.getId(), roleId);
    }

    /** 替换指定成员的站内业务角色。 */
    @PutMapping("/members/{userId}/roles")
    @Operation(summary = "分配成员业务角色")
    public void assignMemberRoles(@PathVariable Long userId, @RequestBody EveMemberRoleAssignReq req) {
        UserContext context = UserContextHolder.getContext();
        rbacApi.assignMemberRoles(context.getTenantId(), context.getId(), userId, req.roleIds());
    }
}
