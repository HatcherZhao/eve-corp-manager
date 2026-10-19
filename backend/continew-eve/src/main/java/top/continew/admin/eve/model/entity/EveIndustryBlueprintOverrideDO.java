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

/** 军团为一张蓝图保存的研究等级与流程数。 */
@Data
@TableName("eve_industry_blueprint_override")
public class EveIndustryBlueprintOverrideDO extends TenantBaseDO {
    /** 蓝图类型 ID。 */
    private Integer blueprintTypeId;
    /** BPO 或 BPC。 */
    private String blueprintKind;
    /** 材料研究等级。 */
    private Integer materialEfficiency;
    /** 时间研究等级。 */
    private Integer timeEfficiency;
    /** BPC 剩余流程数。 */
    private Integer remainingRuns;
    /** 配置来源。 */
    private String source;
    /** 配置版本。 */
    private Integer version;
}
