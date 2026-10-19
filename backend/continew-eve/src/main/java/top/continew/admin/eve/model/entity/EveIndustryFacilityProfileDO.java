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

/** 用户保存的实际或假设工业设施配置。 */
@Data
@TableName("eve_industry_facility_profile")
public class EveIndustryFacilityProfileDO extends TenantBaseDO {
    /** 配置名称。 */
    private String name;
    /** ACTUAL 或 HYPOTHETICAL。 */
    private String realityType;
    /** 生产星系 ID。 */
    private Long systemId;
    /** 星系名称。 */
    private String systemName;
    /** 星系安等。 */
    private BigDecimal securityStatus;
    /** 建筑类型 ID。 */
    private Integer facilityTypeId;
    /** 建筑名称。 */
    private String facilityName;
    /** 支持活动 JSON。 */
    private String supportedActivitiesJson;
    /** 在线服务 JSON。 */
    private String onlineServicesJson;
    /** Rig 类型 ID JSON。 */
    private String rigTypeIdsJson;
    /** 设施税率。 */
    private BigDecimal facilityTax;
    /** 随游戏版本调整的 SCC 等工业附加费率。 */
    private BigDecimal versionedSurcharge;
    /** 材料乘数。 */
    private BigDecimal materialMultiplier;
    /** 时间乘数。 */
    private BigDecimal timeMultiplier;
    /** SCI 成本乘数。 */
    private BigDecimal costMultiplier;
    /** 制造 SCI。 */
    private BigDecimal manufacturingSci;
    /** 反应 SCI。 */
    private BigDecimal reactionSci;
    /** 参数来源。 */
    private String source;
    /** 人工确认时间。 */
    private LocalDateTime confirmedAt;
    /** 是否启用。 */
    private Boolean enabled;
}
