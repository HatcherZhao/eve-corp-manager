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

package top.continew.admin.eve.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.common.base.model.entity.TenantBaseDO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 一次工业扫描产生的不可变利润机会快照。 */
@Data
@TableName("eve_industry_opportunity_snapshot")
public class EveIndustryOpportunityDO extends TenantBaseDO {
    private String scanId;
    private Long blueprintId;
    private Long staticVersionId;
    private Integer productTypeId;
    private String productName;
    private Long configId;
    private Long facilityId;
    private Integer configVersion;
    private Integer blueprintOverrideVersion;
    private Long marketSnapshotId;
    private Long systemSnapshotId;
    private String ruleVersion;
    private String status;
    private Integer recommendedRuns;
    private Long recommendedQuantity;
    private BigDecimal materialCost;
    private BigDecimal installCost;
    private BigDecimal immediateRevenue;
    private BigDecimal sellOrderRevenue;
    private BigDecimal immediateProfit;
    private BigDecimal sellOrderProfit;
    private BigDecimal margin;
    private BigDecimal hourlyProfit;
    private BigDecimal dailyProfit;
    private BigDecimal liquidityScore;
    private BigDecimal riskScore;
    private String riskTagsJson;
    private String assumptionsJson;
    private String inputSnapshotJson;
    private LocalDateTime calculatedAt;
}
