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

package top.continew.admin.eve.service;

import org.junit.jupiter.api.Test;
import top.continew.admin.eve.model.EveIndustryMaterial;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 工业核心取整、时间和逐档市场成本规则测试。 */
class EveIndustryCalculatorTest {

    @Test
    void shouldRoundEachMaterialWithCeveIndustryFormula() {
        assertThat(EveIndustryCalculator.materialQuantity(new BigDecimal("916"), 1, 10, BigDecimal.ONE)).isEqualTo(825);
        assertThat(EveIndustryCalculator.materialQuantity(new BigDecimal("64"), 1, 10, BigDecimal.ONE)).isEqualTo(58);
        assertThat(EveIndustryCalculator.materialQuantity(new BigDecimal("0.2"), 3, 0, BigDecimal.ONE)).isEqualTo(1);
        assertThat(EveIndustryCalculator
            .materialQuantity(new BigDecimal("100"), 11, new BigDecimal("10"), 10, BigDecimal.ONE)).isEqualTo(990);
    }

    @Test
    void shouldCalculateTimeWithCeveIndustrySkillAndTeMultipliers() {
        assertThat(EveIndustryCalculator
            .activitySeconds(new BigDecimal("900"), 1, 5, BigDecimal.ONE, BigDecimal.ONE, "MANUFACTURING"))
            .isEqualByComparingTo("550.80");
        assertThat(EveIndustryCalculator
            .activitySeconds(new BigDecimal("900"), 1, 20, BigDecimal.ONE, BigDecimal.ONE, "REACTION"))
            .isEqualByComparingTo("720.00");
    }

    @Test
    void shouldFillOrdersByPriceLevels() {
        EveIndustryCalculator.OrderFill fill = EveIndustryCalculator.fill(List
            .of(new EveIndustryCalculator.OrderLevel(new BigDecimal("10"), 2), new EveIndustryCalculator.OrderLevel(new BigDecimal("12"), 3)), 4);
        assertThat(fill.filledQuantity()).isEqualTo(4);
        assertThat(fill.total()).isEqualByComparingTo("44");
        assertThat(fill.averagePrice()).isEqualByComparingTo("11");
    }

    @Test
    void shouldCapSafeDemandByOrderDepth() {
        assertThat(EveIndustryCalculator.safeDemand(1000, new BigDecimal("1"), new BigDecimal("0.1"), 60))
            .isEqualTo(60);
    }

    @Test
    void shouldCalculateEivFromBaseMaterials() {
        assertThat(EveIndustryCalculator.eiv(List.of(new EveIndustryMaterial(34, "矿物", new BigDecimal("10"))), List
            .of(new BigDecimal("2.5")))).isEqualByComparingTo("25");
    }

    @Test
    void shouldNotAddUnconfiguredInternationalSurcharge() {
        BigDecimal result = EveIndustryCalculator
            .installationCost(new BigDecimal("1000000"), new BigDecimal("0.05"), new BigDecimal("0.97"), new BigDecimal("0.01"), BigDecimal.ZERO);

        assertThat(result).isEqualByComparingTo("58500.00");
    }

    @Test
    void shouldCalculateInstallationCostWithConfiguredVersionedSurcharge() {
        BigDecimal result = EveIndustryCalculator
            .installationCost(new BigDecimal("1000000"), new BigDecimal("0.05"), new BigDecimal("0.97"), new BigDecimal("0.01"), new BigDecimal("0.04"));

        assertThat(result).isEqualByComparingTo("98500.00");
    }
}
