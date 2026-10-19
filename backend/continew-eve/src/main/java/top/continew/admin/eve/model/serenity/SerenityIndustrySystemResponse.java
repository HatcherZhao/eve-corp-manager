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

package top.continew.admin.eve.model.serenity;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/** 国服星系工业成本指数响应。 */
public record SerenityIndustrySystemResponse(@JsonProperty("solar_system_id") Long solarSystemId,
                                             @JsonProperty("cost_indices") List<CostIndex> costIndices) {
    /** 一项工业活动的系统成本指数。 */
    public record CostIndex(String activity, @JsonProperty("cost_index") BigDecimal costIndex) {
    }
}
