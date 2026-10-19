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

import java.time.LocalDateTime;

/** 工业计算使用的吉他海四市场输入快照。 */
@Data
@TableName("eve_industry_market_input_snapshot")
public class EveIndustryMarketInputSnapshotDO extends TenantBaseDO {
    private String snapshotKey;
    private Long marketRegionId;
    private Long marketStationId;
    private LocalDateTime capturedAt;
    private LocalDateTime sourceUpdatedAt;
    private String source;
    private String quoteJson;
    private String freshnessStatus;
    private String ruleVersion;
}
