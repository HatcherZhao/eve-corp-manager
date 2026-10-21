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

package top.continew.admin.eve.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 工业利润机会列表与详情共用的返回对象。 */
public record EveIndustryOpportunityResp(Long id, Integer productTypeId, String productName, String activity,
                                         String blueprintName, String facilityName, String systemName, String status,
                                         Integer recommendedRuns, Long recommendedQuantity, BigDecimal materialCost,
                                         BigDecimal installCost, BigDecimal immediateRevenue,
                                         BigDecimal sellOrderRevenue, BigDecimal immediateProfit,
                                         BigDecimal sellOrderProfit, BigDecimal margin, BigDecimal hourlyProfit,
                                         BigDecimal dailyProfit, BigDecimal liquidityScore, BigDecimal riskScore,
                                         List<String> riskTags,
                                         @EveDateTime(zone = "Asia/Shanghai") LocalDateTime calculatedAt) {
}
