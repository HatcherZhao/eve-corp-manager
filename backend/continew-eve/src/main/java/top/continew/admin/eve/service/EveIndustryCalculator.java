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

import top.continew.admin.eve.model.EveIndustryMaterial;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 工业数量、时间、订单簿逐档成本等纯计算规则。 */
public final class EveIndustryCalculator {

    private EveIndustryCalculator() {
    }

    /** 按 ceve-industry.cn 规则计算材料：流程数直接表示活动执行次数，每种材料分别向上取整。 */
    public static long materialQuantity(BigDecimal perRun,
                                        int runs,
                                        BigDecimal ignoredProductQuantity,
                                        int me,
                                        BigDecimal facilityMultiplier) {
        // 单流程产量参数仅用于保留旧调用签名；流程数不能再次除以单流程产量。
        BigDecimal processCount = BigDecimal.valueOf(runs);
        BigDecimal materialMultiplier = BigDecimal.valueOf(0.9)
            .add(BigDecimal.valueOf(10L - Math.max(0, Math.min(10, me))).multiply(BigDecimal.valueOf(0.01)))
            .multiply(facilityMultiplier);
        return perRun.multiply(processCount).multiply(materialMultiplier).setScale(0, RoundingMode.CEILING).longValue();
    }

    /** 单产物蓝图的材料计算便捷方法。 */
    public static long materialQuantity(BigDecimal perRun, int runs, int me, BigDecimal facilityMultiplier) {
        return materialQuantity(perRun, runs, BigDecimal.ONE, me, facilityMultiplier);
    }

    /** 按普通制造规则计算活动时间秒数，技能参数表示自定义满技能倍率。 */
    public static BigDecimal activitySeconds(BigDecimal baseSeconds,
                                             int runs,
                                             int te,
                                             BigDecimal skillMultiplier,
                                             BigDecimal facilityMultiplier) {
        return activitySeconds(baseSeconds, runs, te, skillMultiplier, facilityMultiplier, "MANUFACTURING");
    }

    /** 按网站规则计算制造或反应活动时间。 */
    public static BigDecimal activitySeconds(BigDecimal baseSeconds,
                                             int runs,
                                             int te,
                                             BigDecimal skillMultiplier,
                                             BigDecimal facilityMultiplier,
                                             String activity) {
        BigDecimal skillBase = "REACTION".equalsIgnoreCase(activity)
            ? BigDecimal.valueOf(0.8)
            : BigDecimal.valueOf(0.68);
        BigDecimal teMultiplier = "REACTION".equalsIgnoreCase(activity)
            ? BigDecimal.ONE
            : BigDecimal.ONE.subtract(BigDecimal.valueOf(Math.max(0, Math.min(20, te)))
                .multiply(BigDecimal.valueOf(0.02)));
        return baseSeconds.multiply(BigDecimal.valueOf(runs))
            .multiply(teMultiplier)
            .multiply(skillBase)
            .multiply(skillMultiplier == null ? BigDecimal.ONE : skillMultiplier)
            .multiply(facilityMultiplier)
            .setScale(2, RoundingMode.HALF_UP);
    }

    /** 从按价格排序的订单档位计算指定数量的逐档成本或收入。 */
    public static OrderFill fill(List<OrderLevel> levels, long quantity) {
        long remaining = quantity;
        BigDecimal total = BigDecimal.ZERO;
        long filled = 0;
        for (OrderLevel level : levels) {
            if (remaining <= 0) {
                break;
            }
            long use = Math.min(remaining, level.quantity());
            total = total.add(level.price().multiply(BigDecimal.valueOf(use)));
            filled += use;
            remaining -= use;
        }
        return new OrderFill(filled, total, filled == 0
            ? null
            : total.divide(BigDecimal.valueOf(filled), 6, RoundingMode.HALF_UP));
    }

    /** 安全可消化产量，按市场历史、周期和份额上限取整。 */
    public static long safeDemand(long averageDailyVolume,
                                  BigDecimal targetDays,
                                  BigDecimal shareLimit,
                                  long orderDepth) {
        BigDecimal history = BigDecimal.valueOf(averageDailyVolume).multiply(targetDays).multiply(shareLimit);
        long demand = history.setScale(0, RoundingMode.FLOOR).longValue();
        return Math.max(0, Math.min(demand, orderDepth));
    }

    /** 计算 EIV。 */
    public static BigDecimal eiv(List<EveIndustryMaterial> materials, List<BigDecimal> adjustedPrices) {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < materials.size() && i < adjustedPrices.size(); i++) {
            total = total.add(materials.get(i).quantity().multiply(adjustedPrices.get(i)));
        }
        return total;
    }

    /** 按 EIV、SCI、设施成本乘数、设施税和版本化附加费计算任务安装费。 */
    public static BigDecimal installationCost(BigDecimal eiv,
                                              BigDecimal sci,
                                              BigDecimal facilityCostMultiplier,
                                              BigDecimal facilityTax,
                                              BigDecimal versionedSurcharge) {
        BigDecimal effectiveSci = sci.multiply(facilityCostMultiplier);
        BigDecimal surcharge = versionedSurcharge == null ? BigDecimal.ZERO : versionedSurcharge;
        return eiv.multiply(effectiveSci.add(facilityTax).add(surcharge)).setScale(2, RoundingMode.HALF_UP);
    }

    public record OrderLevel(BigDecimal price, long quantity) {
    }

    public record OrderFill(long filledQuantity, BigDecimal total, BigDecimal averagePrice) {
    }
}
