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
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;
import top.continew.admin.eve.model.entity.EveMarketSnapshotDO;
import top.continew.starter.data.mapper.BaseMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;

/** 吉他公开市场缓存快照 Mapper。 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface EveMarketSnapshotMapper extends BaseMapper<EveMarketSnapshotDO> {

    /** 仅合并报价与订单字段，显式覆盖空价格，且不覆盖并行维护的区域历史。 */
    @Insert("""
        <script>
        INSERT INTO eve_market_snapshot
            (type_id, highest_buy_price, lowest_sell_price, buy_volume, sell_volume,
             source_updated_at, quote_synchronized_at, quote_next_request_at, quote_validated_at,
             detail_synchronized_at,
             buy_orders_json, sell_orders_json, create_time, update_time)
        VALUES
        <foreach collection="snapshots" item="row" separator=",">
            (#{row.typeId}, #{row.highestBuyPrice}, #{row.lowestSellPrice}, #{row.buyVolume}, #{row.sellVolume},
             #{row.sourceUpdatedAt}, #{row.quoteSynchronizedAt}, #{row.quoteNextRequestAt}, #{row.quoteValidatedAt},
             #{row.detailSynchronizedAt},
             #{row.buyOrdersJson}, #{row.sellOrdersJson}, #{row.createTime}, #{row.updateTime})
        </foreach>
        ON DUPLICATE KEY UPDATE
            highest_buy_price = VALUES(highest_buy_price), lowest_sell_price = VALUES(lowest_sell_price),
            buy_volume = VALUES(buy_volume), sell_volume = VALUES(sell_volume),
            source_updated_at = VALUES(source_updated_at), quote_synchronized_at = VALUES(quote_synchronized_at),
            quote_next_request_at = VALUES(quote_next_request_at),
            quote_validated_at = VALUES(quote_validated_at),
            detail_synchronized_at = VALUES(detail_synchronized_at), buy_orders_json = VALUES(buy_orders_json),
            sell_orders_json = VALUES(sell_orders_json), order_failure_message = NULL, order_failure_at = NULL,
            update_time = VALUES(update_time)
        </script>
        """)
    void upsertOrders(@Param("snapshots") List<EveMarketSnapshotDO> snapshots);

    /** 完整订单抓取成功后清空未出现物品的旧订单，保留历史、来源和访问时间。 */
    @Update("""
        <script>
        UPDATE eve_market_snapshot
        SET highest_buy_price = NULL, lowest_sell_price = NULL, buy_volume = 0, sell_volume = 0,
            buy_orders_json = '[]', sell_orders_json = '[]', source_updated_at = #{now},
            quote_synchronized_at = #{now}, quote_next_request_at = NULL,
            detail_synchronized_at = #{now}, update_time = #{now}
        <if test="typeIds != null and !typeIds.isEmpty()">
        WHERE type_id NOT IN
        <foreach collection="typeIds" item="typeId" open="(" close=")" separator=",">#{typeId}</foreach>
        </if>
        </script>
        """)
    void clearMissingOrders(@Param("typeIds") Collection<Integer> typeIds, @Param("now") LocalDateTime now);

    /** 原子保存历史及缓存期限，不改写订单同步字段，兼容首次同步并发插入。 */
    @Insert("""
        INSERT INTO eve_market_snapshot
            (type_id, history_json, history_synchronized_at, history_source, history_next_request_at, update_time)
        VALUES (#{typeId}, #{history}, #{now}, #{source}, #{nextRequestAt}, #{now})
        ON DUPLICATE KEY UPDATE history_json = VALUES(history_json), history_source = VALUES(history_source),
            history_synchronized_at = VALUES(history_synchronized_at),
            history_next_request_at = VALUES(history_next_request_at), history_failure_message = NULL,
            history_failure_at = NULL,
            update_time = VALUES(update_time)
        """)
    void upsertHistory(@Param("typeId") int typeId,
                       @Param("history") String history,
                       @Param("source") String source,
                       @Param("now") LocalDateTime now,
                       @Param("nextRequestAt") LocalDateTime nextRequestAt);

    /** 失败物品延后重试，避免同一个未初始化物品持续占据补温队列。 */
    @Insert("""
        INSERT INTO eve_market_snapshot
            (type_id, history_failure_message, history_failure_at, history_next_request_at, update_time)
        VALUES (#{typeId}, #{message}, #{now}, #{nextRequestAt}, #{now})
        ON DUPLICATE KEY UPDATE history_failure_message = VALUES(history_failure_message),
            history_failure_at = VALUES(history_failure_at),
            history_source = CASE WHEN history_source = #{source} THEN history_source ELSE NULL END,
            history_next_request_at = VALUES(history_next_request_at), update_time = VALUES(update_time)
        """)
    void recordHistoryFailure(@Param("typeId") int typeId,
                              @Param("message") String message,
                              @Param("source") String source,
                              @Param("now") LocalDateTime now,
                              @Param("nextRequestAt") LocalDateTime nextRequestAt);

    /** 保存按需行情失败状态；保留最后成功的订单、报价和历史。 */
    @Insert("""
        INSERT INTO eve_market_snapshot
            (type_id, order_failure_message, order_failure_at, create_time, update_time)
        VALUES (#{typeId}, #{message}, #{now}, #{now}, #{now})
        ON DUPLICATE KEY UPDATE order_failure_message = VALUES(order_failure_message),
            order_failure_at = VALUES(order_failure_at), update_time = VALUES(update_time)
        """)
    void recordMarketFailure(@Param("typeId") int typeId,
                             @Param("message") String message,
                             @Param("now") LocalDateTime now);

    /** 官方缓存仍有效时记录本次校验并清除订单失败，不影响独立的历史失败状态。 */
    @Update("""
        UPDATE eve_market_snapshot
        SET quote_validated_at = #{now}, order_failure_message = NULL, order_failure_at = NULL
        WHERE type_id = #{typeId}
        """)
    int markQuoteValidated(@Param("typeId") int typeId, @Param("now") LocalDateTime now);

    /** 页面访问仅更新访问时间，不能把读取时的陈旧行情写回数据库。 */
    @Update("UPDATE eve_market_snapshot SET last_accessed_at = #{now} WHERE type_id = #{typeId}")
    void touch(@Param("typeId") int typeId, @Param("now") LocalDateTime now);
}
