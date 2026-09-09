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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.eve.model.EveAssetSyncResp;
import top.continew.admin.eve.model.EveCorporationAssetResp;
import top.continew.admin.eve.model.EveCorporationAssetTreeResp;
import top.continew.admin.eve.model.EveStaticReferenceImportResp;
import top.continew.admin.eve.service.EveCorporationAssetService;
import top.continew.admin.eve.service.EveStaticReferenceService;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * EVE 军团资产当前快照接口。
 *
 * @author zhaoyuqing
 */
@Tag(name = "EVE 军团资产")
@RestController
@RequiredArgsConstructor
@RequestMapping("/eve/assets")
public class EveCorporationAssetController {

    private final EveCorporationAssetService assetService;
    private final EveStaticReferenceService staticReferenceService;

    /** 查询当前军团最近完整资产快照。 */
    @GetMapping
    @Operation(summary = "查询当前军团资产快照")
    @SaCheckPermission("eve:assets:view")
    public PageResp<EveCorporationAssetResp> page(@RequestParam(defaultValue = "1") @Min(1) int page,
                                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String locationType) {
        return assetService.page(page, size, keyword, locationType);
    }

    /** 查询当前军团按物理位置与容器关系组织的完整资产树。 */
    @GetMapping("/tree")
    @Operation(summary = "查询当前军团资产树")
    @SaCheckPermission("eve:assets:view")
    public EveCorporationAssetTreeResp tree() {
        return assetService.tree();
    }

    /** 使用当前军团的有效总监授权同步完整资产快照。 */
    @PostMapping("/sync")
    @Operation(summary = "同步当前军团资产快照")
    @SaCheckPermission("eve:assets:manage")
    public EveAssetSyncResp sync() {
        return assetService.syncCurrentTenant();
    }

    /** 导入管理员提供的 evedata.xlsx，并原子替换数据库中的静态参考资料。 */
    @PostMapping(value = "/reference/import", consumes = "multipart/form-data")
    @Operation(summary = "导入 EVE 静态参考资料")
    @SaCheckPermission("eve:assets:manage")
    public EveStaticReferenceImportResp importReference(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || file.getOriginalFilename() == null || !file.getOriginalFilename().endsWith(".xlsx")) {
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
}
