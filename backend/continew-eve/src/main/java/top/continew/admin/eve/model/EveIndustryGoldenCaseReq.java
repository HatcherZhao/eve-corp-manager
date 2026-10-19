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

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 创建并立即校验国服游戏内工业任务样例的请求。 */
public record EveIndustryGoldenCaseReq(@NotBlank String name, @NotNull Long blueprintId, @NotNull Long configId,
                                       @NotNull Long facilityId, @NotNull @Min(1) Integer runs,
                                       @NotNull @Min(0) Integer materialEfficiency,
                                       @NotNull @Min(0) Integer timeEfficiency,
                                       @NotEmpty List<@Valid EveIndustryGoldenMaterial> expectedMaterials,
                                       @NotNull @DecimalMin("0") BigDecimal expectedTimeSeconds,
                                       @NotNull @DecimalMin("0") BigDecimal expectedInstallCost,
                                       Map<Integer, BigDecimal> adjustedPrices, @Min(0) Long materialTolerance,
                                       @DecimalMin("0") BigDecimal timeToleranceSeconds,
                                       @DecimalMin("0") BigDecimal installCostTolerance) {
}
