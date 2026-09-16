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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 当前军团资产快照按吉他报价汇总的参考估值。
 *
 * @author zhaoyuqing
 */
public record EveCorporationAssetValuationResp(long itemCount, long totalQuantity, BigDecimal highestBuyEstimatedValue,
                                               BigDecimal lowestSellEstimatedValue, int highestBuyPricedTypeCount,
                                               int lowestSellPricedTypeCount, int unpricedTypeCount, List<Item> items) {

    /** 单个物品类型在当前资产快照中的数量、价格和两种估值口径。 */
    public record Item(int typeId, String typeName, long quantity, long itemCount, BigDecimal highestBuyPrice,
                       BigDecimal lowestSellPrice, BigDecimal highestBuyEstimatedValue,
                       BigDecimal lowestSellEstimatedValue, LocalDateTime priceUpdatedAt) {
    }
}
