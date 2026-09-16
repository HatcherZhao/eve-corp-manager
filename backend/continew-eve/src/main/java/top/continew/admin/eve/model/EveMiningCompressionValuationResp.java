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
 * 按高密度压缩月矿吉他卖单计算的开采估值。
 *
 * @author zhaoyuqing
 */
public record EveMiningCompressionValuationResp(long rawQuantity, long compressedQuantity, long remainderQuantity,
                                                BigDecimal estimatedValue, int pricedTypeCount, int unpricedTypeCount,
                                                List<Item> items) {

    /** 单种月矿原矿按完整压缩批次折算出的估值。 */
    public record Item(int rawTypeId, String rawTypeName, int compressedTypeId, String compressedTypeName,
                       long rawQuantity, long compressedQuantity, long remainderQuantity, BigDecimal lowestSellPrice,
                       BigDecimal estimatedValue, LocalDateTime priceUpdatedAt) {
    }
}
