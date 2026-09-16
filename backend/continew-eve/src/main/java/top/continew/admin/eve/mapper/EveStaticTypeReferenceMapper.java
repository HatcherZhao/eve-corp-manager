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
import top.continew.admin.eve.model.entity.EveStaticTypeReferenceDO;
import top.continew.starter.data.mapper.BaseMapper;

import java.util.List;

/**
 * EVE 静态类型资料 Mapper。
 *
 * @author zhaoyuqing
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface EveStaticTypeReferenceMapper extends BaseMapper<EveStaticTypeReferenceDO> {

    /** 查询当前军团月矿观察者账本中出现过的月矿类型，数量仅用于识别品类而不会下发。 */
    @Select("SELECT DISTINCT ledger.type_id FROM eve_mining_ledger AS ledger "
        + "INNER JOIN eve_static_type_reference AS reference ON reference.type_id = ledger.type_id "
        + "WHERE ledger.tenant_id = #{tenantId} AND ledger.corporation_ref_id = #{corporationRefId} "
        + "AND ledger.deleted = 0 AND reference.market_category_l4 = '卫星矿石'")
    List<Integer> selectCorporationMoonOreTypeIds(@Param("tenantId") Long tenantId,
                                                  @Param("corporationRefId") Long corporationRefId);

    /**
     * 选择尚未拥有有效价格历史的少量物品，供后台平稳补全行情曲线。
     *
     * @param limit 单次处理上限
     * @return 游戏物品类型 ID
     */
    @Select("""
        SELECT `reference`.`type_id`
        FROM `eve_static_type_reference` AS `reference`
        LEFT JOIN `eve_market_snapshot` AS `snapshot` ON `snapshot`.`type_id` = `reference`.`type_id`
        WHERE `reference`.`market_category_l1` <> ''
          AND (`snapshot`.`history_synchronized_at` IS NULL
            OR `snapshot`.`history_synchronized_at` < DATE_SUB(NOW(), INTERVAL 1 DAY))
        -- 优先补齐用户已经浏览过、已有报价快照的物品，再渐进覆盖其余基础资料。
        ORDER BY CASE WHEN `snapshot`.`type_id` IS NULL THEN 1 ELSE 0 END ASC,
                 `snapshot`.`history_synchronized_at` ASC,
                 `reference`.`type_id` ASC
        LIMIT #{limit}
        """)
    List<Integer> selectHistoryWarmupTypeIds(@Param("limit") int limit);

    /**
     * 选择需要更新报价的可交易物品，供后台轮转刷新整个市场目录。
     *
     * @param limit 单次处理上限
     * @return 游戏物品类型 ID
     */
    @Select("""
        SELECT `reference`.`type_id`
        FROM `eve_static_type_reference` AS `reference`
        LEFT JOIN `eve_market_snapshot` AS `snapshot` ON `snapshot`.`type_id` = `reference`.`type_id`
        LEFT JOIN `eve_mining_compression_mapping` AS `compression`
            ON `compression`.`compressed_type_id` = `reference`.`type_id`
        LEFT JOIN (
            SELECT DISTINCT `type_id`
            FROM `eve_corporation_asset`
            WHERE `status` = 'ACTIVE' AND `deleted` = 0 AND `type_id` IS NOT NULL
        ) AS `asset` ON `asset`.`type_id` = `reference`.`type_id`
        WHERE `reference`.`market_category_l1` <> ''
          AND (`snapshot`.`quote_synchronized_at` IS NULL
            OR `snapshot`.`quote_synchronized_at` < DATE_SUB(NOW(), INTERVAL 15 MINUTE))
        -- 优先补齐当前军团资产和月矿估值所需报价，随后仍按全市场轮转。
        ORDER BY CASE WHEN `asset`.`type_id` IS NULL THEN 1 ELSE 0 END ASC,
                 CASE WHEN `compression`.`compressed_type_id` IS NULL THEN 1 ELSE 0 END ASC,
                 `snapshot`.`quote_synchronized_at` ASC, `reference`.`type_id` ASC
        LIMIT #{limit}
        """)
    List<Integer> selectQuoteSyncTypeIds(@Param("limit") int limit);
}
