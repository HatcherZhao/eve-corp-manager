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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 晨曦官方单个物品类型详情。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SerenityUniverseTypeResponse(@JsonProperty("type_id") Integer typeId,
                                           String name,
                                           String description,
                                           @JsonProperty("market_group_id") Integer marketGroupId,
                                           Boolean published) {
}
