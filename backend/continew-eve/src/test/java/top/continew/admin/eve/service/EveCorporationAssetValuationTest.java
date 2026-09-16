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
import top.continew.admin.eve.model.EveCorporationAssetValuationResp;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 军团资产吉他估值聚合测试。
 *
 * @author zhaoyuqing
 */
class EveCorporationAssetValuationTest {

    /** 零价和缺失报价不得计入资产估值，收购和卖单两种口径必须独立累计。 */
    @Test
    void shouldKeepBuyAndSellValuationsSeparateAndExcludeUnpricedTypes() {
        List<Map<String, Object>> rows = List.of(Map
            .of("typeId", 34, "typeName", "三钛合金", "quantity", 3L, "itemCount", 2L, "highestBuyPrice", new BigDecimal("90"), "lowestSellPrice", new BigDecimal("110"), "priceUpdatedAt", Timestamp
                .valueOf(LocalDateTime.of(2026, 9, 16, 10, 0))), Map
                    .of("typeId", 35, "typeName", "类晶体胶矿", "quantity", 4L, "itemCount", 1L, "highestBuyPrice", BigDecimal.ZERO));

        EveCorporationAssetValuationResp result = EveCorporationAssetService.toValuation(rows);

        assertThat(result.itemCount()).isEqualTo(3L);
        assertThat(result.totalQuantity()).isEqualTo(7L);
        assertThat(result.highestBuyEstimatedValue()).isEqualByComparingTo("270");
        assertThat(result.lowestSellEstimatedValue()).isEqualByComparingTo("330");
        assertThat(result.highestBuyPricedTypeCount()).isEqualTo(1);
        assertThat(result.lowestSellPricedTypeCount()).isEqualTo(1);
        assertThat(result.unpricedTypeCount()).isEqualTo(1);
        assertThat(result.items()).element(1).satisfies(item -> {
            assertThat(item.highestBuyPrice()).isNull();
            assertThat(item.lowestSellEstimatedValue()).isNull();
        });
    }
}
