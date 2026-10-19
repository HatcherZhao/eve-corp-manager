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

/** 工业生产计划中的采购、生产和销售步骤。 */
@Data
@TableName("eve_industry_plan_step")
public class EveIndustryPlanStepDO extends TenantBaseDO {
    private Long planId;
    private String stepKey;
    private String stepType;
    private Integer sequenceNo;
    private String dependsOnJson;
    private String status;
    private String title;
    private Integer typeId;
    private Long quantity;
    private BigDecimal unitCost;
    private BigDecimal totalCost;
    private BigDecimal durationSeconds;
    private String payloadJson;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
