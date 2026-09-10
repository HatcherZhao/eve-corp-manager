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

import java.time.LocalDate;

/**
 * 国服观察者采矿账本的单条聚合记录。
 *
 * <p>{@code last_updated} 是国服返回的日期字段，不能解释为精确的采矿发生时间。</p>
 *
 * @author zhaoyuqing
 */
public record SerenityCorporationMiningLedgerResponse(@JsonProperty("character_id") Long characterId,
                                                      @JsonProperty("last_updated") LocalDate lastUpdated,
                                                      Long quantity,
                                                      @JsonProperty("recorded_corporation_id") Long recordedCorporationId,
                                                      @JsonProperty("type_id") Integer typeId) {
}
