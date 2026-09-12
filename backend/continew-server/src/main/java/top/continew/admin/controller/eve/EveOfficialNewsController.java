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
import top.continew.admin.eve.model.EveOfficialNewsResp;
import top.continew.admin.eve.model.EveOfficialNewsSyncResp;
import top.continew.admin.eve.model.EveOfficialNewsSyncStatusResp;
import top.continew.admin.eve.service.EveOfficialNewsService;
import top.continew.starter.extension.crud.model.resp.PageResp;

/**
 * 网易 EVE 国服官网新闻活动的站内阅读与同步接口。
 *
 * @author zhaoyuqing
 */
@Tag(name = "EVE 国服官网资讯")
@RestController
@RequiredArgsConstructor
@RequestMapping("/eve/news")
public class EveOfficialNewsController {

    private final EveOfficialNewsService officialNewsService;

    /** 分页阅读全局共享的官网资讯。 */
    @GetMapping
    @Operation(summary = "查询国服官网资讯")
    @SaCheckPermission("eve:news:view")
    public PageResp<EveOfficialNewsResp> page(@RequestParam(defaultValue = "1") @Min(1) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                              @RequestParam(required = false) String category,
                                              @RequestParam(required = false) String keyword) {
        return officialNewsService.page(page, size, category, keyword);
    }

    /** 读取单篇资讯的完整安全正文。 */
    @GetMapping("/detail")
    @Operation(summary = "查询国服官网资讯详情")
    @SaCheckPermission("eve:news:view")
    public EveOfficialNewsResp detail(@RequestParam String originalUrl) {
        return officialNewsService.detail(originalUrl);
    }

    /** 查询页面右上角使用的单行同步状态。 */
    @GetMapping("/sync-status")
    @Operation(summary = "查询官网资讯同步状态")
    @SaCheckPermission("eve:news:view")
    public EveOfficialNewsSyncStatusResp syncStatus() {
        return officialNewsService.syncStatus();
    }

    /** CEO 或总监立即检查官网更新；服务端仍以全局锁合并并发请求。 */
    @PostMapping("/sync")
    @Operation(summary = "立即检查官网资讯更新")
    @SaCheckPermission("eve:news:sync")
    public EveOfficialNewsSyncResp synchronize() {
        return officialNewsService.synchronize();
    }
}
