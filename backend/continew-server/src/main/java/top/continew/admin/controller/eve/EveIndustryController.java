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
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.continew.admin.eve.model.EveIndustryBlueprintOverrideReq;
import top.continew.admin.eve.model.EveIndustryBlueprintBatchReq;
import top.continew.admin.eve.model.EveIndustryBlueprintReq;
import top.continew.admin.eve.model.EveIndustryCalculationReq;
import top.continew.admin.eve.model.EveIndustryConfigReq;
import top.continew.admin.eve.model.EveIndustryDataStatusResp;
import top.continew.admin.eve.model.EveIndustryFacilityReq;
import top.continew.admin.eve.model.EveIndustryGoldenCaseReq;
import top.continew.admin.eve.model.EveIndustryScanReq;
import top.continew.admin.eve.model.EveIndustryPlanCreateReq;
import top.continew.admin.eve.model.EveIndustryPlanStatusReq;
import top.continew.admin.eve.service.EveIndustryService;
import top.continew.starter.core.exception.BusinessException;

import java.io.IOException;

/** 工业制造配置、静态数据和利润机会接口。 */
@Tag(name = "EVE 工业制造")
@RestController
@RequiredArgsConstructor
@RequestMapping("/eve/industry")
public class EveIndustryController {

    private final EveIndustryService industryService;

    /** 查询工业机会排行榜。 */
    @GetMapping("/opportunities")
    @Operation(summary = "查询工业利润机会")
    @SaCheckPermission("eve:industry:view")
    public Object opportunities(@RequestParam(defaultValue = "50") @Min(1) int limit) {
        return industryService.opportunities(limit);
    }

    /** 查询机会的材料采购明细。 */
    @GetMapping("/opportunities/{id}/materials")
    @Operation(summary = "查询工业机会材料明细")
    @SaCheckPermission("eve:industry:view")
    public Object opportunityMaterials(@PathVariable @Min(1) Long id) {
        return industryService.opportunityMaterials(id);
    }

    /** 即时试算单张蓝图的材料、工时、安装费与买卖利润。 */
    @PostMapping("/calculate")
    @Operation(summary = "试算单张工业蓝图")
    @SaCheckPermission("eve:industry:view")
    public Object calculate(@Valid @RequestBody EveIndustryCalculationReq req) {
        return industryService.calculate(req);
    }

    /** 查询工业机会扫描所需行情进度；该接口不会写入机会快照。 */
    @PostMapping("/opportunities/scan/status")
    @Operation(summary = "查询工业扫描行情准备进度")
    @SaCheckPermission("eve:industry:view")
    public Object scanMarketStatus(@Valid @RequestBody EveIndustryScanReq req) {
        return industryService.scanMarketStatus(req);
    }

    /** 手动触发一次工业机会扫描。 */
    @PostMapping("/opportunities/scan")
    @Operation(summary = "扫描工业利润机会")
    @SaCheckPermission("eve:industry:view")
    public Object scan(@Valid @RequestBody EveIndustryScanReq req) {
        return industryService.scan(req);
    }

    /** 查询当前租户生产计划。 */
    @GetMapping("/plans")
    @Operation(summary = "查询工业生产计划")
    @SaCheckPermission("eve:industry:plan:view")
    public Object plans() {
        return industryService.plans();
    }

    /** 查询生产计划详情及步骤。 */
    @GetMapping("/plans/{id}")
    @SaCheckPermission("eve:industry:plan:view")
    public Object plan(@PathVariable @Min(1) Long id) {
        return industryService.plan(id);
    }

    /** 从机会快照创建生产计划草稿。 */
    @PostMapping("/plans")
    @Operation(summary = "从机会创建工业生产计划")
    @SaCheckPermission("eve:industry:plan:manage")
    public Object createPlan(@Valid @RequestBody EveIndustryPlanCreateReq req) {
        return industryService.createPlan(req);
    }

    /** 更新生产计划状态。 */
    @PutMapping("/plans/{id}/status")
    @SaCheckPermission("eve:industry:plan:manage")
    public Object updatePlanStatus(@PathVariable @Min(1) Long id, @Valid @RequestBody EveIndustryPlanStatusReq req) {
        return industryService.updatePlanStatus(id, req);
    }

    /** 复制生产计划为新的草稿。 */
    @PostMapping("/plans/{id}/copy")
    @SaCheckPermission("eve:industry:plan:manage")
    public Object copyPlan(@PathVariable @Min(1) Long id) {
        return industryService.copyPlan(id);
    }

    /** 基于固化机会快照重新计算草稿计划。 */
    @PostMapping("/plans/{id}/recalculate")
    @SaCheckPermission("eve:industry:plan:manage")
    public Object recalculatePlan(@PathVariable @Min(1) Long id) {
        return industryService.recalculatePlan(id);
    }

    /** 查询工业配置。 */
    @GetMapping("/configs")
    @SaCheckPermission("eve:industry:view")
    public Object configs() {
        return industryService.configs();
    }

    /** 新增工业配置。 */
    @PostMapping("/configs")
    @SaCheckPermission("eve:industry:config:manage")
    public Object createConfig(@Valid @RequestBody EveIndustryConfigReq req) {
        return industryService.saveConfig(null, req);
    }

    /** 修改工业配置。 */
    @PutMapping("/configs/{id}")
    @SaCheckPermission("eve:industry:config:manage")
    public Object updateConfig(@PathVariable @Min(1) Long id, @Valid @RequestBody EveIndustryConfigReq req) {
        return industryService.saveConfig(id, req);
    }

    /** 查询设施配置。 */
    @GetMapping("/facilities")
    @SaCheckPermission("eve:industry:view")
    public Object facilities() {
        return industryService.facilities();
    }

    /** 新增设施配置。 */
    @PostMapping("/facilities")
    @SaCheckPermission("eve:industry:config:manage")
    public Object createFacility(@Valid @RequestBody EveIndustryFacilityReq req) {
        return industryService.saveFacility(null, req);
    }

    /** 修改设施配置。 */
    @PutMapping("/facilities/{id}")
    @SaCheckPermission("eve:industry:config:manage")
    public Object updateFacility(@PathVariable @Min(1) Long id, @Valid @RequestBody EveIndustryFacilityReq req) {
        return industryService.saveFacility(id, req);
    }

    /** 从国服 ESI 更新设施所在星系的 SCI。 */
    @PostMapping("/facilities/{id}/sync-sci")
    @SaCheckPermission("eve:industry:config:manage")
    public Object synchronizeFacilitySci(@PathVariable @Min(1) Long id) {
        return industryService.synchronizeFacilitySci(id);
    }

    /** 查询蓝图研究等级覆盖。 */
    @GetMapping("/blueprints/overrides")
    @SaCheckPermission("eve:industry:view")
    public Object blueprintOverrides() {
        return industryService.blueprintOverrides();
    }

    /** 保存蓝图研究等级覆盖。 */
    @PostMapping("/blueprints/overrides")
    @SaCheckPermission("eve:industry:config:manage")
    public Object saveBlueprintOverride(@Valid @RequestBody EveIndustryBlueprintOverrideReq req) {
        return industryService.saveBlueprintOverride(null, req);
    }

    /** 修改蓝图研究等级覆盖。 */
    @PutMapping("/blueprints/overrides/{id}")
    @SaCheckPermission("eve:industry:config:manage")
    public Object updateBlueprintOverride(@PathVariable @Min(1) Long id,
                                          @Valid @RequestBody EveIndustryBlueprintOverrideReq req) {
        return industryService.saveBlueprintOverride(id, req);
    }

    /** 查询工业数据状态。 */
    @GetMapping("/data-status")
    @SaCheckPermission("eve:industry:view")
    public EveIndustryDataStatusResp dataStatus() {
        return industryService.dataStatus();
    }

    /** 查询静态工业数据版本。 */
    @GetMapping("/static-versions")
    @SaCheckPermission("eve:industry:view")
    public Object staticVersions() {
        return industryService.staticVersions();
    }

    /** 创建静态数据版本。 */
    @PostMapping("/static-versions")
    @SaCheckPermission("eve:industry:data:manage")
    public Object createStaticVersion(@RequestParam String source,
                                      @RequestParam(required = false) String serverVersion,
                                      @RequestParam(required = false) Long buildNumber,
                                      @RequestParam(required = false) String sha256,
                                      @RequestParam(required = false) String schemaVersion) {
        return industryService.createStaticVersion(source, serverVersion, buildNumber, sha256, schemaVersion);
    }

    /** 上传规范化 JSON 或 JSONL 静态工业数据。 */
    @PostMapping(value = "/static-versions/import", consumes = "multipart/form-data")
    @Operation(summary = "导入规范化工业静态数据")
    @SaCheckPermission("eve:industry:data:manage")
    public Object importStaticData(@RequestParam("file") MultipartFile file,
                                   @RequestParam String source,
                                   @RequestParam(required = false) String serverVersion,
                                   @RequestParam(required = false) Long buildNumber,
                                   @RequestParam(required = false) String schemaVersion) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessException("工业静态数据文件不能为空");
        }
        if (file.getSize() > 50L * 1024 * 1024) {
            throw new BusinessException("工业静态数据文件不能超过 50 MB");
        }
        String fileName = file.getOriginalFilename() == null ? "industry.json" : file.getOriginalFilename();
        String lowerName = fileName.toLowerCase();
        if (!lowerName.endsWith(".json") && !lowerName.endsWith(".jsonl")) {
            throw new BusinessException("只支持 .json 或 .jsonl 工业静态数据文件");
        }
        return industryService.importStaticData(fileName, file
            .getBytes(), source, serverVersion, buildNumber, schemaVersion);
    }

    /** 从 ceve-industry.cn 公开接口同步蓝图和反应配方。 */
    @PostMapping("/static-versions/sync-ceve")
    @Operation(summary = "同步 ceve-industry.cn 公开蓝图")
    @SaCheckPermission("eve:industry:data:manage")
    public Object synchronizeCevePublicBlueprints() {
        return industryService.synchronizeCevePublicBlueprints();
    }

    /** 查询蓝图活动静态数据。 */
    @GetMapping("/static-blueprints")
    @SaCheckPermission("eve:industry:view")
    public Object blueprints(@RequestParam(required = false) Long staticVersionId) {
        return industryService.blueprints(staticVersionId);
    }

    /** 导入一条蓝图活动规则。 */
    @PostMapping("/static-blueprints")
    @SaCheckPermission("eve:industry:data:manage")
    public Object importBlueprint(@Valid @RequestBody EveIndustryBlueprintReq req) {
        return industryService.importBlueprint(req);
    }

    /** 分批导入规范化蓝图活动。 */
    @PostMapping("/static-blueprints/batch")
    @SaCheckPermission("eve:industry:data:manage")
    public Object importBlueprints(@Valid @RequestBody EveIndustryBlueprintBatchReq req) {
        return industryService.importBlueprints(req);
    }

    /** 查询国服工业 Golden Case。 */
    @GetMapping("/golden-cases")
    @SaCheckPermission("eve:industry:view")
    public Object goldenCases() {
        return industryService.goldenCases();
    }

    /** 保存并立即校验一项国服游戏内真实任务结果。 */
    @PostMapping("/golden-cases")
    @Operation(summary = "创建并校验工业 Golden Case")
    @SaCheckPermission("eve:industry:data:manage")
    public Object createGoldenCase(@Valid @RequestBody EveIndustryGoldenCaseReq req) {
        return industryService.createGoldenCase(req);
    }
}
