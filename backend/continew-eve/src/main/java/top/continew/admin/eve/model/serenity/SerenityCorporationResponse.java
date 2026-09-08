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

import java.math.BigDecimal;

/**
 * 国服公开军团资料。
 *
 * @param name        军团名称
 * @param ticker      军团简称
 * @param ceoId       CEO 角色 ID
 * @param allianceId  联盟 ID
 * @param memberCount 成员数量
 * @param taxRate     军团税率
 * @author zhaoyuqing
 */
public record SerenityCorporationResponse(String name, String ticker, @JsonProperty("ceo_id") Long ceoId,
                                          @JsonProperty("alliance_id") Long allianceId,
                                          @JsonProperty("member_count") Integer memberCount,
                                          @JsonProperty("tax_rate") BigDecimal taxRate) {
}
