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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 查询月矿压缩映射及按筛选范围聚合的吉他估值输入。
 *
 * @author zhaoyuqing
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface EveMiningCompressionMapper {

    /** 按月矿原矿类型聚合数量，并带出对应高密度矿的最新吉他报价快照。 */
    @Select("""
        SELECT ledger.type_id AS rawTypeId,
               COALESCE(MAX(NULLIF(ledger.type_name, '')), CONCAT('类型 #', ledger.type_id)) AS rawTypeName,
               mapping.compressed_type_id AS compressedTypeId,
               COALESCE(reference.type_name, CONCAT('类型 #', mapping.compressed_type_id)) AS compressedTypeName,
               SUM(ledger.quantity) AS rawQuantity,
               FLOOR(SUM(ledger.quantity) / mapping.compression_ratio) AS compressedQuantity,
               MOD(SUM(ledger.quantity), mapping.compression_ratio) AS remainderQuantity,
               snapshot.lowest_sell_price AS lowestSellPrice,
               snapshot.quote_synchronized_at AS priceUpdatedAt
        FROM eve_mining_ledger AS ledger
        INNER JOIN eve_mining_compression_mapping AS mapping ON mapping.raw_type_id = ledger.type_id
        LEFT JOIN eve_static_type_reference AS reference ON reference.type_id = mapping.compressed_type_id
        LEFT JOIN eve_market_snapshot AS snapshot ON snapshot.type_id = mapping.compressed_type_id
        WHERE ledger.tenant_id = #{tenantId} AND ledger.corporation_ref_id = #{corporationRefId}
          AND ledger.deleted = 0
          AND (#{observerId} IS NULL OR ledger.observer_id = #{observerId})
          AND (#{characterId} IS NULL OR ledger.character_id = #{characterId})
          AND (#{typeId} IS NULL OR ledger.type_id = #{typeId})
          AND (#{fromDate} IS NULL OR ledger.recorded_at >= #{fromDate})
          AND (#{toDate} IS NULL OR ledger.recorded_at <= #{toDate})
          AND (#{keyword} IS NULL OR ledger.observer_name LIKE CONCAT('%', #{keyword}, '%')
               OR ledger.character_name LIKE CONCAT('%', #{keyword}, '%')
               OR ledger.type_name LIKE CONCAT('%', #{keyword}, '%'))
        GROUP BY ledger.type_id, mapping.compressed_type_id, mapping.compression_ratio, reference.type_name,
                 snapshot.lowest_sell_price, snapshot.quote_synchronized_at
        ORDER BY rawQuantity DESC, rawTypeName ASC
        """)
    List<Map<String, Object>> summarizeCompressionValuation(@Param("tenantId") Long tenantId,
                                                            @Param("corporationRefId") Long corporationRefId,
                                                            @Param("observerId") Long observerId,
                                                            @Param("characterId") Long characterId,
                                                            @Param("typeId") Integer typeId,
                                                            @Param("fromDate") LocalDate fromDate,
                                                            @Param("toDate") LocalDate toDate,
                                                            @Param("keyword") String keyword);
}
