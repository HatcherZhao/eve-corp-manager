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

package top.continew.admin.eve.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** 导入或维护一项蓝图工业活动的请求。 */
public record EveIndustryBlueprintReq(@NotNull Long staticVersionId, @NotNull Integer blueprintTypeId,
                                      @NotBlank String blueprintName, @NotBlank String activity,
                                      @NotNull Integer productTypeId, @NotBlank String productName,
                                      @NotNull @DecimalMin("0.000001") BigDecimal productQuantity,
                                      @NotNull @DecimalMin("0") BigDecimal baseTimeSeconds, BigDecimal probability,
                                      @NotBlank String materialsJson, String productsJson, @NotBlank String source,
                                      @NotBlank String validationStatus, @NotNull Boolean supported) {
}
