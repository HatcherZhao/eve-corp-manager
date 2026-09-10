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

package top.continew.admin.controller.eve.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 游戏内邮件发送请求。
 *
 * @author zhaoyuqing
 */
public record EveGameMailSendReq(@NotEmpty @Size(max = 50) List<@Valid EveGameMailRecipientReq> recipients,
                                 @NotBlank @Size(max = 1000) String subject, @NotBlank @Size(max = 10000) String body,
                                 Long approvedCost) {

    /** 防止正文在参数日志中泄漏。 */
    @Override
    public String toString() {
        return "EveGameMailSendReq[recipients=" + (recipients == null
            ? 0
            : recipients.size()) + ", secrets=<redacted>]";
    }
}
