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
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.controller.eve.model.EveMemberOrganizationReq;
import top.continew.admin.eve.model.EveMemberResp;
import top.continew.admin.eve.model.EveMemberOperationAuditResp;
import top.continew.admin.eve.model.EveMemberSyncRunResp;
import top.continew.admin.eve.model.EveSyncRequestResp;
import top.continew.admin.eve.model.enums.EveSyncModule;
import top.continew.admin.eve.service.EveCorporationMemberService;
import top.continew.admin.eve.service.EveManualSyncRequestService;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.util.List;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * EVE 军团完整游戏成员名册与追踪数据接口。
 *
 * @author zhaoyuqing
 */
@Tag(name = "EVE 军团成员管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/eve/members")
public class EveCorporationMemberController {

    private final EveCorporationMemberService memberService;
    private final EveManualSyncRequestService manualSyncRequestService;

    /** 查询当前军团游戏成员名册；服务层按权限裁剪追踪字段。 */
    @GetMapping
    @Operation(summary = "查询当前军团成员名册")
    @SaCheckPermission("eve:members:view")
    public PageResp<EveMemberResp> page(@RequestParam(defaultValue = "1") @Min(1) int page,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String status) {
        return memberService.page(page, size, keyword, status);
    }

    /** 查询单个成员；追踪字段仍由服务层按当前用户权限裁剪。 */
    @GetMapping("/{characterId}")
    @Operation(summary = "查询当前军团成员详情")
    @SaCheckPermission("eve:members:view")
    public EveMemberResp get(@PathVariable Long characterId) {
        return memberService.get(characterId);
    }

    /** 更新成员的军团内部组织信息。 */
    @PutMapping("/{characterId}/organization")
    @Operation(summary = "更新成员分组与备注")
    @SaCheckPermission("eve:members:organize")
    public EveMemberResp updateOrganization(@PathVariable Long characterId,
                                            @Valid @RequestBody EveMemberOrganizationReq req) {
        return memberService.updateOrganization(characterId, req.organizationGroup(), req.memberNote());
    }

    /** 查询最近成员资源同步批次，供管理者诊断数据新鲜度和失败原因。 */
    @GetMapping("/sync-runs")
    @Operation(summary = "查询成员同步历史")
    @SaCheckPermission("eve:members:history:view")
    public List<EveMemberSyncRunResp> listSyncRuns(@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return memberService.listSyncRuns(limit);
    }

    /** 查询成员分组和备注调整的脱敏审计。 */
    @GetMapping("/activities")
    @Operation(summary = "查询成员组织操作审计")
    @SaCheckPermission("eve:members:organize")
    public List<EveMemberOperationAuditResp> listActivities(@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return memberService.listOrganizationActivities(limit);
    }

    /** 导出筛选后的成员资料；追踪字段由服务层按当前导出者权限决定是否包含。 */
    @ExcludeFromGracefulResponse
    @GetMapping("/export")
    @Operation(summary = "导出成员数据")
    @SaCheckPermission("eve:members:export")
    public void export(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String status,
                       HttpServletResponse response) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=corp-members.csv");
        StringBuilder csv = new StringBuilder("\uFEFF角色名称,角色ID,分组,备注,状态,入团时间,离团时间,最近同步,位置,舰船,最近登录,最近登出\n");
        for (EveMemberResp member : memberService.listForExport(keyword, status)) {
            csv.append(csv(member.characterName()))
                .append(',')
                .append(csv(member.characterId()))
                .append(',')
                .append(csv(member.organizationGroup()))
                .append(',')
                .append(csv(member.memberNote()))
                .append(',')
                .append(csv("ACTIVE".equals(member.status()) ? "在团" : "已离团"))
                .append(',')
                .append(csv(member.joinedAt()))
                .append(',')
                .append(csv(member.leftAt()))
                .append(',')
                .append(csv(member.lastSeenAt()))
                .append(',')
                .append(csv(member.tracking() == null ? null : member.tracking().locationName()))
                .append(',')
                .append(csv(member.tracking() == null ? null : member.tracking().shipTypeName()))
                .append(',')
                .append(csv(member.tracking() == null ? null : member.tracking().lastLogonAt()))
                .append(',')
                .append(csv(member.tracking() == null ? null : member.tracking().lastLogoffAt()))
                .append('\n');
        }
        response.getOutputStream().write(csv.toString().getBytes(StandardCharsets.UTF_8));
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

    /** 请求后台同步基础名册及可用的追踪快照。 */
    @PostMapping("/sync")
    @Operation(summary = "同步当前军团成员数据")
    @SaCheckPermission("eve:members:manage")
    public EveSyncRequestResp sync() {
        return manualSyncRequestService.requestCurrentCorporation(EveSyncModule.MEMBER_ROSTER);
    }
}
