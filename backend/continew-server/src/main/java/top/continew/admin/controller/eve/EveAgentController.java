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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.controller.eve.model.EveAgentApplicationCreateReq;
import top.continew.admin.eve.agent.auth.EveAgentApplicationService;
import top.continew.admin.eve.agent.model.EveAgentApplicationResp;
import top.continew.admin.eve.agent.model.EveAgentAuditResp;
import top.continew.admin.eve.agent.model.EveAgentCredentialResp;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.util.List;

/**
 * 军团级外部 AI Agent 只读授权管理接口。
 *
 * @author zhaoyuqing
 */
@Tag(name = "EVE 外部 Agent")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/eve/agents")
public class EveAgentController {

    private final EveAgentApplicationService applicationService;

    /** 授权管理响应包含一次性凭据，不允许浏览器或反向代理缓存。 */
    @ModelAttribute
    public void preventCaching(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    /** 查询当前军团的外部 Agent 应用。 */
    @GetMapping
    @Operation(summary = "查询外部 Agent 应用")
    @SaCheckPermission("eve:agents:manage")
    public List<EveAgentApplicationResp> list() {
        return applicationService.listApplications();
    }

    /** 创建应用并仅返回一次原始凭据。 */
    @PostMapping
    @Operation(summary = "创建外部 Agent 应用")
    @SaCheckPermission("eve:agents:manage")
    public EveAgentCredentialResp create(@Valid @RequestBody EveAgentApplicationCreateReq request) {
        return applicationService.createApplication(request.name(), request.expiresInDays());
    }

    /** 轮换应用凭据，旧凭据在事务提交后失效。 */
    @PostMapping("/{id}/rotate")
    @Operation(summary = "轮换外部 Agent 凭据")
    @SaCheckPermission("eve:agents:manage")
    public EveAgentCredentialResp rotate(@PathVariable @Min(1) Long id) {
        return applicationService.rotateCredential(id);
    }

    /** 撤销应用，后续请求不能再通过认证。 */
    @PostMapping("/{id}/revoke")
    @Operation(summary = "撤销外部 Agent 应用")
    @SaCheckPermission("eve:agents:manage")
    public void revoke(@PathVariable @Min(1) Long id) {
        applicationService.revoke(id);
    }

    /** 分页查询当前军团的脱敏授权与调用审计。 */
    @GetMapping("/audit")
    @Operation(summary = "查询外部 Agent 审计")
    @SaCheckPermission("eve:agents:manage")
    public PageResp<EveAgentAuditResp> audit(@RequestParam(defaultValue = "1") @Min(1) int page,
                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return applicationService.pageAudit(page, size);
    }
}
