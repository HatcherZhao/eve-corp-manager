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
 * 国服军团资产接口单条响应。
 *
 * @author zhaoyuqing
 */
public record SerenityCorporationAssetResponse(@JsonProperty("item_id") Long itemId,
                                               @JsonProperty("type_id") Integer typeId,
                                               @JsonProperty("quantity") Integer quantity,
                                               @JsonProperty("location_id") Long locationId,
                                               @JsonProperty("location_type") String locationType,
                                               @JsonProperty("location_flag") String locationFlag,
                                               @JsonProperty("is_singleton") Boolean singleton,
                                               @JsonProperty("is_blueprint_copy") Boolean blueprintCopy) {
}
