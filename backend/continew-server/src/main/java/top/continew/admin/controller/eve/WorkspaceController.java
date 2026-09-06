/*
 * Copyright 2026 EVE Corp Manager contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package top.continew.admin.controller.eve;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.eve.model.WorkspaceResp;
import top.continew.admin.eve.service.WorkspaceService;
import top.continew.starter.log.annotation.Log;

@Tag(name = "EVE 军团工作台")
@RestController
@Log(ignore = true)
@RequestMapping("/eve/workspace")
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    @Operation(summary = "查询 EVE 模块接入状态")
    @SaCheckPermission("eve:workspace:view")
    public WorkspaceResp getWorkspace() {
        return workspaceService.getWorkspace();
    }
}
