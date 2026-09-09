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

/**
 * 国服军团建筑清单中用于资产位置识别的字段。
 *
 * @author zhaoyuqing
 */
public record SerenityCorporationStructureResponse(@JsonProperty("structure_id") Long structureId,
                                                   @JsonProperty("type_id") Integer typeId,
                                                   @JsonProperty("system_id") Long solarSystemId, String name) {
}
