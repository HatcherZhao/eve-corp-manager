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
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.eve.model.EveMeContextResp;
import top.continew.admin.eve.model.EveDataFreshnessResp;
import top.continew.admin.eve.service.EveContextService;
import top.continew.admin.eve.service.EvePermissionRefreshService;
import top.continew.admin.system.service.EveSessionPermissionRefreshService;
import top.continew.starter.log.annotation.Log;

import java.util.List;

/**
 * 当前登录用户的 EVE 身份与能力接口。
 *
 * @author zhaoyuqing
 */
@Tag(name = "EVE 当前用户上下文")
@RestController
@RequiredArgsConstructor
@Log(ignore = true)
@RequestMapping("/eve/me")
public class EveMeController {

    private final EveContextService contextService;
    private final EvePermissionRefreshService permissionRefreshService;
    private final EveSessionPermissionRefreshService sessionPermissionRefreshService;

    /** 查询当前用户的游戏身份、军团和模块能力；过期游戏快照会在首屏自动复核。 */
    @GetMapping("/context")
    @Operation(summary = "查询当前 EVE 用户上下文")
    public EveMeContextResp getContext() {
        permissionRefreshService.refreshIfSnapshotExpiredCurrent();
        sessionPermissionRefreshService.refreshCurrentEveUser();
        return contextService.getCurrentContext();
    }

    /** 查询当前军团各已上线数据模块的新鲜度与最近同步失败提醒。 */
    @GetMapping("/data-freshness")
    @Operation(summary = "查询当前军团数据新鲜度")
    public List<EveDataFreshnessResp> getDataFreshness() {
        return contextService.getCurrentContext().dataFreshness();
    }
}
