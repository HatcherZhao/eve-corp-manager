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

package top.continew.admin.eve.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 聚合军团资产快照，并关联全站共享的吉他价格缓存。
 *
 * @author zhaoyuqing
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface EveCorporationAssetValuationMapper {

    /** 按物品类型汇总当前军团有效资产及其吉他报价。 */
    @Select("""
        SELECT asset.type_id AS typeId,
               COALESCE(MAX(NULLIF(asset.type_name, '')), MAX(NULLIF(reference.type_name, '')),
                        CONCAT('类型 #', asset.type_id)) AS typeName,
               SUM(asset.quantity) AS quantity,
               COUNT(*) AS itemCount,
               snapshot.highest_buy_price AS highestBuyPrice,
               snapshot.lowest_sell_price AS lowestSellPrice,
               snapshot.quote_synchronized_at AS priceUpdatedAt
        FROM eve_corporation_asset AS asset
        LEFT JOIN eve_static_type_reference AS reference ON reference.type_id = asset.type_id
        LEFT JOIN eve_market_snapshot AS snapshot ON snapshot.type_id = asset.type_id
        WHERE asset.tenant_id = #{tenantId}
          AND asset.corporation_ref_id = #{corporationRefId}
          AND asset.status = 'ACTIVE'
          AND asset.deleted = 0
          AND asset.type_id IS NOT NULL
        GROUP BY asset.type_id, snapshot.highest_buy_price, snapshot.lowest_sell_price,
                 snapshot.quote_synchronized_at
        ORDER BY quantity DESC, typeName ASC
        """)
    List<Map<String, Object>> summarizeValuation(@Param("tenantId") Long tenantId,
                                                 @Param("corporationRefId") Long corporationRefId);
}
