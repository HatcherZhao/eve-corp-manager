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

package top.continew.admin.eve.model.serenity;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 提交给国服的游戏内邮件发送请求。
 *
 * @author zhaoyuqing
 */
public record SerenityGameMailSendRequest(@JsonProperty("approved_cost") Long approvedCost, String body,
                                          List<SerenityGameMailRecipient> recipients, String subject) {

    /**
     * 国服将该字段定义为带默认值的整数，但显式提交 null 会被判为无效请求。
     *
     * @param approvedCost 邮件列表要求支付时允许的最高 ISK 费用
     * @param body         邮件正文
     * @param recipients   收件人列表
     * @param subject      邮件主题
     */
    public SerenityGameMailSendRequest {
        approvedCost = approvedCost == null ? 0L : approvedCost;
    }
}
