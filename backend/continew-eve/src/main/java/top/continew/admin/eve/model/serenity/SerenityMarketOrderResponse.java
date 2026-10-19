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
import java.time.Instant;

/** 国服 ESI 区域市场的一条公开订单。 */
public record SerenityMarketOrderResponse(@JsonProperty("is_buy_order") boolean buyOrder,
                                          @JsonProperty("issued") Instant issuedAt,
                                          @JsonProperty("location_id") Long locationId,
                                          @JsonProperty("min_volume") Long minVolume,
                                          @JsonProperty("order_id") Long orderId,
                                          BigDecimal price,
                                          String range,
                                          @JsonProperty("system_id") Long systemId,
                                          @JsonProperty("type_id") Integer typeId,
                                          @JsonProperty("volume_remain") Long volumeRemain,
                                          @JsonProperty("volume_total") Long volumeTotal) {
}
