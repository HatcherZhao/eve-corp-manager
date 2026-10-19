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

/** 利润机会实际采用的材料数量与市场采购成本。 */
@Data
@TableName("eve_industry_opportunity_material")
public class EveIndustryOpportunityMaterialDO extends TenantBaseDO {
    private Long opportunityId;
    private Integer materialTypeId;
    private String materialName;
    private Long requiredQuantity;
    private BigDecimal purchaseCost;
    private BigDecimal averagePrice;
}
