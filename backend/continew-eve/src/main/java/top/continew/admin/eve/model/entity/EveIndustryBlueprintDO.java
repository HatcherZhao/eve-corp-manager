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

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.common.base.model.entity.BaseDO;

import java.math.BigDecimal;

/** 一张蓝图的一项工业活动静态规则。 */
@Data
@TableName("eve_industry_blueprint")
@InterceptorIgnore(tenantLine = "true")
public class EveIndustryBlueprintDO extends BaseDO {
    /** 静态数据版本 ID。 */
    private Long staticVersionId;
    /** 蓝图类型 ID。 */
    private Integer blueprintTypeId;
    /** 蓝图名称。 */
    private String blueprintName;
    /** 工业活动类型。 */
    private String activity;
    /** 主产物类型 ID。 */
    private Integer productTypeId;
    /** 主产物名称。 */
    private String productName;
    /** 每流程产物数量。 */
    private BigDecimal productQuantity;
    /** 每流程基础时间秒数。 */
    private BigDecimal baseTimeSeconds;
    /** 随机活动成功概率。 */
    private BigDecimal probability;
    /** 原材料 JSON。 */
    private String materialsJson;
    /** 全部产物 JSON。 */
    private String productsJson;
    /** 数据来源。 */
    private String source;
    /** 校验状态。 */
    private String validationStatus;
    /** 是否允许进入正式可执行计算。 */
    private Boolean supported;
}
