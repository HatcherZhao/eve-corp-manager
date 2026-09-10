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
import top.continew.admin.eve.model.EveCorporationStructureResp;
import top.continew.admin.eve.model.EveSyncRequestResp;
import top.continew.admin.eve.model.enums.EveSyncModule;
import top.continew.admin.eve.service.EveCorporationStructureService;
import top.continew.admin.eve.service.EveManualSyncRequestService;
import top.continew.starter.extension.crud.model.resp.PageResp;

/**
 * EVE 军团建筑快照查询与同步接口。
 *
 * @author zhaoyuqing
 */
@Tag(name = "EVE 军团建筑")
@RestController
@RequiredArgsConstructor
@RequestMapping("/eve/structures")
public class EveCorporationStructureController {

    private final EveCorporationStructureService structureService;
    private final EveManualSyncRequestService manualSyncRequestService;

    /** 分页查询当前军团建筑快照。 */
    @GetMapping
    @Operation(summary = "查询当前军团建筑")
    @SaCheckPermission("eve:structures:view")
    public PageResp<EveCorporationStructureResp> page(@RequestParam(defaultValue = "1") @Min(1) int page,
                                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String state) {
        return structureService.page(page, size, keyword, state);
    }

    /** 请求后台同步当前军团建筑快照。 */
    @PostMapping("/sync")
    @Operation(summary = "同步当前军团建筑")
    @SaCheckPermission("eve:structures:manage")
    public EveSyncRequestResp sync() {
        return manualSyncRequestService.requestCurrentCorporation(EveSyncModule.STRUCTURES);
    }
}
