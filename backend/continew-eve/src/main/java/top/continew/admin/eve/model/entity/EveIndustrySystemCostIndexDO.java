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
import java.time.LocalDateTime;

/** 国服 ESI 返回的一项星系工业活动成本指数缓存。 */
@Data
@TableName("eve_industry_system_cost_index")
@InterceptorIgnore(tenantLine = "true")
public class EveIndustrySystemCostIndexDO extends BaseDO {
    /** 太阳系 ID。 */
    private Long systemId;
    /** 工业活动类型。 */
    private String activity;
    /** 成本指数。 */
    private BigDecimal costIndex;
    /** 数据来源。 */
    private String source;
    /** 上游 ETag。 */
    private String etag;
    /** 抓取时间。 */
    private LocalDateTime fetchedAt;
    /** 上游建议过期时间。 */
    private LocalDateTime expiresAt;
}
