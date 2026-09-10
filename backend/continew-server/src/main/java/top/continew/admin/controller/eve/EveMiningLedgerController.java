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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.eve.model.EveMiningLedgerResp;
import top.continew.admin.eve.model.EveMiningLedgerSummaryResp;
import top.continew.admin.eve.model.EveSyncRequestResp;
import top.continew.admin.eve.model.enums.EveSyncModule;
import top.continew.admin.eve.service.EveMiningLedgerService;
import top.continew.admin.eve.service.EveManualSyncRequestService;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.time.LocalDate;

/**
 * EVE 军团采矿账本查询、汇总与同步接口。
 *
 * @author zhaoyuqing
 */
@Tag(name = "EVE 军团采矿账本")
@RestController
@RequiredArgsConstructor
@RequestMapping("/eve/mining")
public class EveMiningLedgerController {

    private final EveMiningLedgerService miningLedgerService;
    private final EveManualSyncRequestService manualSyncRequestService;

    /** 分页查询当前军团已发布的观察者采矿账本。 */
    @GetMapping
    @Operation(summary = "查询当前军团采矿账本")
    @SaCheckPermission("eve:mining:view")
    public PageResp<EveMiningLedgerResp> page(@RequestParam(defaultValue = "1") @Min(1) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                              @RequestParam(required = false) Long observerId,
                                              @RequestParam(required = false) Long characterId,
                                              @RequestParam(required = false) Integer typeId,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                              @RequestParam(required = false) String keyword) {
        return miningLedgerService.page(page, size, observerId, characterId, typeId, fromDate, toDate, keyword);
    }

    /** 汇总当前筛选条件下的账本数量、成员、矿物和最近数据时间。 */
    @GetMapping("/summary")
    @Operation(summary = "汇总当前军团采矿账本")
    @SaCheckPermission("eve:mining:view")
    public EveMiningLedgerSummaryResp summary(@RequestParam(required = false) Long observerId,
                                              @RequestParam(required = false) Long characterId,
                                              @RequestParam(required = false) Integer typeId,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                              @RequestParam(required = false) String keyword) {
        return miningLedgerService.summary(observerId, characterId, typeId, fromDate, toDate, keyword);
    }

    /** 请求后台同步当前军团完整观察者账本。 */
    @PostMapping("/sync")
    @Operation(summary = "同步当前军团采矿账本")
    @SaCheckPermission("eve:mining:sync")
    public EveSyncRequestResp sync() {
        return manualSyncRequestService.requestCurrentCorporation(EveSyncModule.MINING_LEDGER);
    }
}
