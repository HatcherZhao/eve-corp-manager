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
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.controller.eve.model.EveGameMailRecipientReq;
import top.continew.admin.controller.eve.model.EveGameMailSendReq;
import top.continew.admin.eve.model.EveGameMailDetailResp;
import top.continew.admin.eve.model.EveGameMailLabelResp;
import top.continew.admin.eve.model.EveGameMailPartyResp;
import top.continew.admin.eve.model.EveGameMailResp;
import top.continew.admin.eve.model.EveGameMailSendResp;
import top.continew.admin.eve.model.EveGameMailRecipientNameReq;
import top.continew.admin.eve.model.EveSyncRequestResp;
import top.continew.admin.eve.service.EveGameMailService;
import top.continew.admin.eve.service.EveManualSyncRequestService;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.util.List;

/**
 * 当前授权角色的游戏内邮件查询、同步与发送接口。
 *
 * @author zhaoyuqing
 */
@Tag(name = "EVE 游戏内邮件")
@RestController
@RequiredArgsConstructor
@RequestMapping("/eve/mail")
public class EveGameMailController {

    private final EveGameMailService gameMailService;
    private final EveManualSyncRequestService manualSyncRequestService;

    /** 分页查询当前授权角色已同步的邮件头。 */
    @GetMapping
    @Operation(summary = "查询当前角色游戏内邮件")
    @SaCheckPermission("eve:mail:view")
    public PageResp<EveGameMailResp> page(@RequestParam(defaultValue = "1") @Min(1) int page,
                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) @Min(1) Integer labelId) {
        return gameMailService.page(page, size, keyword, labelId);
    }

    /** 获取当前角色在游戏中的邮件分类、未读数和自定义文件夹。 */
    @GetMapping("/labels")
    @Operation(summary = "查询游戏内邮件分类")
    @SaCheckPermission("eve:mail:view")
    public List<EveGameMailLabelResp> labels() {
        return gameMailService.labels();
    }

    /** 请求后台同步当前授权角色最近 50 封邮件头。 */
    @PostMapping("/sync")
    @Operation(summary = "同步当前角色游戏内邮件")
    @SaCheckPermission("eve:mail:view")
    public EveSyncRequestResp sync() {
        return manualSyncRequestService.requestCurrentMail();
    }

    /** 按需读取当前角色已同步邮件的正文。 */
    @GetMapping("/{mailId}")
    @Operation(summary = "查看游戏内邮件详情")
    @SaCheckPermission("eve:mail:view")
    public EveGameMailDetailResp detail(@PathVariable @Min(1) Long mailId) {
        return gameMailService.detail(mailId);
    }

    /** 显式将当前角色的邮件设为已读。 */
    @PutMapping("/{mailId}/read")
    @Operation(summary = "将游戏内邮件设为已读")
    @SaCheckPermission("eve:mail:view")
    public EveGameMailDetailResp markRead(@PathVariable @Min(1) Long mailId) {
        return gameMailService.markRead(mailId);
    }

    /** 按名称预校验游戏内邮件收件人；该操作不发送邮件。 */
    @PostMapping("/recipients/resolve")
    @Operation(summary = "按名称校验游戏内邮件收件人")
    @SaCheckPermission("eve:mail:send")
    public EveGameMailPartyResp resolveRecipient(@RequestBody @Valid EveGameMailRecipientReq request) {
        return gameMailService.resolveRecipientName(new EveGameMailRecipientNameReq(request.recipientName(), request
            .recipientType()));
    }

    /** 以当前授权角色立即向国服提交游戏内邮件。 */
    @PostMapping("/send")
    @Operation(summary = "发送游戏内邮件")
    @SaCheckPermission("eve:mail:send")
    public EveGameMailSendResp send(@RequestBody @Valid EveGameMailSendReq request) {
        return gameMailService.sendByRecipientNames(request.recipients()
            .stream()
            .map(item -> new EveGameMailRecipientNameReq(item.recipientName(), item.recipientType()))
            .toList(), request.subject(), request.body(), request.approvedCost());
    }
}
