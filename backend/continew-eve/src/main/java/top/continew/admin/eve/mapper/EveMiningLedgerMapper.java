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

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.continew.admin.eve.model.entity.EveMiningLedgerDO;
import top.continew.starter.data.mapper.BaseMapper;

import java.time.LocalDate;
import java.util.Map;

/**
 * 国服采矿账本明细 Mapper。
 *
 * @author zhaoyuqing
 */
@Mapper
public interface EveMiningLedgerMapper extends BaseMapper<EveMiningLedgerDO> {

    /** 在数据库内汇总当前筛选条件，避免为统计结果加载全部账本明细。 */
    @Select("SELECT COALESCE(SUM(quantity), 0) AS quantity, COUNT(*) AS entryCount, " + "COUNT(DISTINCT observer_id) AS observerCount, COUNT(DISTINCT character_id) AS characterCount, " + "COUNT(DISTINCT type_id) AS mineralTypeCount, MAX(recorded_at) AS latestRecordedAt, " + "MAX(last_seen_at) AS latestSynchronizedAt FROM eve_mining_ledger " + "WHERE tenant_id = #{tenantId} AND corporation_ref_id = #{corporationRefId} AND deleted = 0 " + "AND (#{observerId} IS NULL OR observer_id = #{observerId}) " + "AND (#{characterId} IS NULL OR character_id = #{characterId}) " + "AND (#{typeId} IS NULL OR type_id = #{typeId}) " + "AND (#{fromDate} IS NULL OR recorded_at >= #{fromDate}) " + "AND (#{toDate} IS NULL OR recorded_at <= #{toDate}) " + "AND (#{keyword} IS NULL OR observer_name LIKE CONCAT('%', #{keyword}, '%') " + "OR character_name LIKE CONCAT('%', #{keyword}, '%') OR type_name LIKE CONCAT('%', #{keyword}, '%'))")
    Map<String, Object> summarize(@Param("tenantId") Long tenantId,
                                  @Param("corporationRefId") Long corporationRefId,
                                  @Param("observerId") Long observerId,
                                  @Param("characterId") Long characterId,
                                  @Param("typeId") Integer typeId,
                                  @Param("fromDate") LocalDate fromDate,
                                  @Param("toDate") LocalDate toDate,
                                  @Param("keyword") String keyword);
}
