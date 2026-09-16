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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.eve.model.EveMarketDetailResp;
import top.continew.admin.eve.model.EveMarketItemResp;
import top.continew.admin.eve.model.EveMarketSyncResp;
import top.continew.admin.eve.service.EveMarketService;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.util.List;

/**
 * 吉他贸易中心公开市场查询接口。
 *
 * @author zhaoyuqing
 */
@Tag(name = "EVE 吉他贸易中心")
@RestController
@RequiredArgsConstructor
@RequestMapping("/eve/market")
public class EveMarketController {

    private final EveMarketService marketService;

    /** 按本系统的 evedata 市场分类分页查询吉他报价。 */
    @GetMapping
    @Operation(summary = "查询吉他贸易中心行情")
    @SaCheckPermission("eve:market:view")
    public PageResp<EveMarketItemResp> page(@RequestParam(defaultValue = "1") @Min(1) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(20) int size,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String marketCategoryL1,
                                            @RequestParam(required = false) String marketCategoryL2,
                                            @RequestParam(required = false) String marketCategoryL3,
                                            @RequestParam(required = false) String marketCategoryL4,
                                            @RequestParam(required = false) String marketCategoryL5,
                                            @RequestParam(required = false) String marketCategoryL6,
                                            @RequestParam(required = false) Boolean unclassified) {
        return marketService.page(page, size, keyword, List
            .of(blank(marketCategoryL1), blank(marketCategoryL2), blank(marketCategoryL3), blank(marketCategoryL4), blank(marketCategoryL5), blank(marketCategoryL6)), Boolean.TRUE
                .equals(unclassified));
    }

    /** 查看物品的吉他报价、买卖单与每日价格历史。 */
    @GetMapping("/{typeId}")
    @Operation(summary = "查询单物品吉他市场详情")
    @SaCheckPermission("eve:market:view")
    public EveMarketDetailResp detail(@PathVariable @Min(1) int typeId,
                                      @RequestParam(defaultValue = "false") boolean forceRefresh) {
        return marketService.detail(typeId, forceRefresh);
    }

    /** CEO 或总监立刻刷新一个物品的吉他订单簿和价格历史。 */
    @PostMapping("/{typeId}/sync")
    @Operation(summary = "立即刷新单物品吉他市场")
    @SaCheckPermission("eve:market:sync")
    public EveMarketSyncResp synchronize(@PathVariable @Min(1) int typeId) {
        return marketService.synchronize(typeId);
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }
}
