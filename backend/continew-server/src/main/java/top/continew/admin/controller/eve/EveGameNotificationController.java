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

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.eve.model.EveGameNotificationResp;
import top.continew.admin.eve.model.EveSyncRequestResp;
import top.continew.admin.eve.service.EveGameNotificationService;
import top.continew.admin.eve.service.EveManualSyncRequestService;
import top.continew.starter.extension.crud.model.resp.PageResp;

/**
 * 当前授权角色的游戏通知查询与同步接口。
 *
 * @author zhaoyuqing
 */
@Tag(name = "EVE 游戏通知")
@RestController
@RequiredArgsConstructor
@RequestMapping("/eve/notifications")
public class EveGameNotificationController {

    private final EveGameNotificationService gameNotificationService;
    private final EveManualSyncRequestService manualSyncRequestService;

    /** 分页查询当前授权角色已同步的游戏通知。 */
    @GetMapping
    @Operation(summary = "查询当前角色游戏通知")
    @SaCheckPermission("eve:notifications:view")
    public PageResp<EveGameNotificationResp> page(@RequestParam(defaultValue = "1") @Min(1) int page,
                                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                                  @RequestParam(required = false) String category,
                                                  @RequestParam(required = false) String keyword) {
        return gameNotificationService.page(page, size, category, keyword);
    }

    /** 请求后台同步当前授权角色的最近游戏通知。 */
    @PostMapping("/sync")
    @Operation(summary = "同步当前角色游戏通知")
    @SaCheckPermission("eve:notifications:view")
    public EveSyncRequestResp sync() {
        return manualSyncRequestService.requestCurrentNotifications();
    }
}
