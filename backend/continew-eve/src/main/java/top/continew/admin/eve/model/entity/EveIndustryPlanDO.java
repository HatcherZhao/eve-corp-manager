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

/** 可审计的工业生产计划主记录。 */
@Data
@TableName("eve_industry_plan")
public class EveIndustryPlanDO extends TenantBaseDO {
    private String name;
    private Long sourceOpportunityId;
    private String status;
    private String activity;
    private Integer productTypeId;
    private String productName;
    private Integer runs;
    private Long targetQuantity;
    private Long configId;
    private Long facilityId;
    private Long blueprintId;
    private Long staticVersionId;
    private Integer configVersion;
    private Integer blueprintOverrideVersion;
    private Long marketSnapshotId;
    private Long systemSnapshotId;
    private String ruleVersion;
    private BigDecimal totalMaterialCost;
    private BigDecimal totalInstallCost;
    private BigDecimal expectedRevenue;
    private BigDecimal expectedProfit;
    private BigDecimal expectedTimeSeconds;
    private String riskTagsJson;
    private String assumptionsJson;
    private String inputSnapshotJson;
    private LocalDateTime confirmedAt;
    private LocalDateTime archivedAt;
}
