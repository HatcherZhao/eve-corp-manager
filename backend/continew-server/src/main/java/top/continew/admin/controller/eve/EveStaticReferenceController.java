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
import com.feiniaojin.gracefulresponse.api.ExcludeFromGracefulResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.continew.admin.eve.model.EveStaticLocationReferenceResp;
import top.continew.admin.eve.model.EveStaticReferenceImportResp;
import top.continew.admin.eve.model.EveStaticTypeReferenceResp;
import top.continew.admin.eve.service.EveStaticReferenceService;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * EVE 静态基础资料查询、导出与全量更新接口。
 *
 * @author zhaoyuqing
 */
@Tag(name = "EVE 静态基础资料")
@RestController
@RequiredArgsConstructor
@RequestMapping("/eve/reference")
public class EveStaticReferenceController {

    private final EveStaticReferenceService staticReferenceService;

    /** 分页查询 evedata.xlsx 中的物品类型资料。 */
    @GetMapping("/types")
    @Operation(summary = "查询 EVE 静态物品资料")
    @SaCheckPermission("eve:reference:view")
    public PageResp<EveStaticTypeReferenceResp> pageTypes(@RequestParam(defaultValue = "1") @Min(1) int page,
                                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) String marketCategoryL1) {
        return staticReferenceService.pageTypes(page, size, keyword, marketCategoryL1);
    }

    /** 分页查询 evedata.xlsx 中的星域、星座、星系和建筑位置资料。 */
    @GetMapping("/locations")
    @Operation(summary = "查询 EVE 静态位置资料")
    @SaCheckPermission("eve:reference:view")
    public PageResp<EveStaticLocationReferenceResp> pageLocations(@RequestParam(defaultValue = "1") @Min(1) int page,
                                                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                                                  @RequestParam(required = false) String keyword,
                                                                  @RequestParam(required = false) String referenceType) {
        return staticReferenceService.pageLocations(page, size, keyword, referenceType);
    }

    /** 导出筛选后的物品类型资料，CSV 可直接由 Excel 打开。 */
    @ExcludeFromGracefulResponse
    @GetMapping("/types/export")
    @Operation(summary = "导出 EVE 静态物品资料")
    @SaCheckPermission("eve:reference:export")
    public void exportTypes(@RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String marketCategoryL1,
                            HttpServletResponse response) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=eve-static-types.csv");
        StringBuilder csv = new StringBuilder("\uFEFF物品类型ID,物品名称,物品说明,一级市场分类,二级市场分类,三级市场分类,四级市场分类,五级市场分类,六级市场分类,资料更新时间\n");
        for (EveStaticTypeReferenceResp item : staticReferenceService.listTypesForExport(keyword, marketCategoryL1)) {
            csv.append(csv(item.typeId()))
                .append(',')
                .append(csv(item.typeName()))
                .append(',')
                .append(csv(item.typeDescription()))
                .append(',')
                .append(csv(item.marketCategoryL1()))
                .append(',')
                .append(csv(item.marketCategoryL2()))
                .append(',')
                .append(csv(item.marketCategoryL3()))
                .append(',')
                .append(csv(item.marketCategoryL4()))
                .append(',')
                .append(csv(item.marketCategoryL5()))
                .append(',')
                .append(csv(item.marketCategoryL6()))
                .append(',')
                .append(csv(item.sourceUpdatedAt()))
                .append('\n');
        }
        response.getOutputStream().write(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 导出筛选后的星域、星座、星系和建筑位置资料。 */
    @ExcludeFromGracefulResponse
    @GetMapping("/locations/export")
    @Operation(summary = "导出 EVE 静态位置资料")
    @SaCheckPermission("eve:reference:export")
    public void exportLocations(@RequestParam(required = false) String keyword,
                                @RequestParam(required = false) String referenceType,
                                HttpServletResponse response) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=eve-static-locations.csv");
        StringBuilder csv = new StringBuilder("\uFEFF资料类型,位置ID,位置名称,所属星系ID,所属星座ID,所属星域ID,安全等级,资料更新时间\n");
        for (EveStaticLocationReferenceResp item : staticReferenceService
            .listLocationsForExport(keyword, referenceType)) {
            csv.append(csv(item.referenceType()))
                .append(',')
                .append(csv(item.referenceId()))
                .append(',')
                .append(csv(item.referenceName()))
                .append(',')
                .append(csv(item.solarSystemId()))
                .append(',')
                .append(csv(item.constellationId()))
                .append(',')
                .append(csv(item.regionId()))
                .append(',')
                .append(csv(item.securityStatus()))
                .append(',')
                .append(csv(item.sourceUpdatedAt()))
                .append('\n');
        }
        response.getOutputStream().write(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 上传完整 evedata.xlsx 并原子替换物品与位置资料快照。 */
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @Operation(summary = "批量更新 EVE 静态资料")
    @SaCheckPermission("eve:reference:manage")
    public EveStaticReferenceImportResp importReference(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || file.getOriginalFilename() == null || !file.getOriginalFilename()
            .toLowerCase()
            .endsWith(".xlsx")) {
            throw new BusinessException("请上传 evedata.xlsx 格式的 Excel 文件");
        }
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile("eve-static-reference-", ".xlsx");
            file.transferTo(temporaryFile);
            return staticReferenceService.importWorkbook(temporaryFile, file.getOriginalFilename(), true);
        } catch (IOException e) {
            throw new BusinessException("无法接收 EVE 静态资料文件");
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException ignored) {
                    // 临时文件由操作系统最终回收，不影响已完成的导入事务。
                }
            }
        }
    }

    /** 转义 CSV 字段，防止公式注入并保留逗号、引号和换行。 */
    private static String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).replace("\r", " ").replace("\n", " ");
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }
}
