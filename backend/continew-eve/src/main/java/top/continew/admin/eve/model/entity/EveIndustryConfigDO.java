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

/** 军团可复用的工业分析配置。 */
@Data
@TableName("eve_industry_config")
public class EveIndustryConfigDO extends TenantBaseDO {
    /** 配置名称。 */
    private String name;
    /** 固定生产技能 JSON。 */
    private String productionSkillJson;
    /** 市场费用 JSON。 */
    private String marketFeeJson;
    /** 默认目标周期天数。 */
    private BigDecimal targetDays;
    /** 安全市场份额上限。 */
    private BigDecimal marketShareLimit;
    /** 数据新鲜度门槛 JSON。 */
    private String freshnessPolicyJson;
    /** 配置版本。 */
    private Integer version;
    /** 是否启用。 */
    private Boolean enabled;
}
