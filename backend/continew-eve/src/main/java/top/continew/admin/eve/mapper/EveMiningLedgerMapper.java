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
import java.util.List;
import java.util.Map;

/**
 * 国服采矿账本明细 Mapper。
 *
 * @author zhaoyuqing
 */
@Mapper
public interface EveMiningLedgerMapper extends BaseMapper<EveMiningLedgerDO> {

    /** 在数据库内汇总当前筛选条件，并分离本军团和外部角色的开采量。 */
    @Select("SELECT COALESCE(SUM(quantity), 0) AS quantity, COUNT(*) AS entryCount, "
        + "COUNT(DISTINCT observer_id) AS observerCount, COUNT(DISTINCT character_id) AS characterCount, "
        + "COUNT(DISTINCT type_id) AS mineralTypeCount, MAX(recorded_at) AS latestRecordedAt, "
        + "MAX(last_seen_at) AS latestSynchronizedAt, "
        + "COALESCE(SUM(CASE WHEN recorded_corporation_id <> #{corporationId} THEN quantity ELSE 0 END), 0) AS externalQuantity, "
        + "COALESCE(SUM(CASE WHEN recorded_corporation_id <> #{corporationId} THEN 1 ELSE 0 END), 0) AS externalEntryCount, "
        + "COUNT(DISTINCT CASE WHEN recorded_corporation_id <> #{corporationId} THEN character_id END) AS externalCharacterCount "
        + "FROM eve_mining_ledger WHERE tenant_id = #{tenantId} AND corporation_ref_id = #{corporationRefId} AND deleted = 0 "
        + "AND (#{observerId} IS NULL OR observer_id = #{observerId}) AND (#{characterId} IS NULL OR character_id = #{characterId}) "
        + "AND (#{typeId} IS NULL OR type_id = #{typeId}) AND (#{fromDate} IS NULL OR recorded_at >= #{fromDate}) "
        + "AND (#{toDate} IS NULL OR recorded_at <= #{toDate}) AND (#{keyword} IS NULL OR observer_name LIKE CONCAT('%', #{keyword}, '%') "
        + "OR character_name LIKE CONCAT('%', #{keyword}, '%') OR type_name LIKE CONCAT('%', #{keyword}, '%'))")
    Map<String, Object> summarize(@Param("tenantId") Long tenantId,
                                  @Param("corporationRefId") Long corporationRefId,
                                  @Param("corporationId") Long corporationId,
                                  @Param("observerId") Long observerId,
                                  @Param("characterId") Long characterId,
                                  @Param("typeId") Integer typeId,
                                  @Param("fromDate") LocalDate fromDate,
                                  @Param("toDate") LocalDate toDate,
                                  @Param("keyword") String keyword);

    /** 按开采日期汇总趋势，并单独带出外部角色的开采量。 */
    @Select("SELECT recorded_at AS recordedAt, COALESCE(SUM(quantity), 0) AS quantity, COUNT(*) AS entryCount, "
        + "COALESCE(SUM(CASE WHEN recorded_corporation_id <> #{corporationId} THEN quantity ELSE 0 END), 0) AS externalQuantity "
        + "FROM eve_mining_ledger WHERE tenant_id = #{tenantId} AND corporation_ref_id = #{corporationRefId} AND deleted = 0 "
        + "AND (#{fromDate} IS NULL OR recorded_at >= #{fromDate}) AND (#{toDate} IS NULL OR recorded_at <= #{toDate}) "
        + "AND (#{keyword} IS NULL OR observer_name LIKE CONCAT('%', #{keyword}, '%') OR character_name LIKE CONCAT('%', #{keyword}, '%') "
        + "OR type_name LIKE CONCAT('%', #{keyword}, '%')) GROUP BY recorded_at ORDER BY recorded_at ASC")
    List<Map<String, Object>> summarizeTimeline(@Param("tenantId") Long tenantId,
                                                @Param("corporationRefId") Long corporationRefId,
                                                @Param("corporationId") Long corporationId,
                                                @Param("fromDate") LocalDate fromDate,
                                                @Param("toDate") LocalDate toDate,
                                                @Param("keyword") String keyword);

    /** 按月矿建筑或观察者汇总开采量，并标注其中的外部采矿量。 */
    @Select("SELECT COALESCE(NULLIF(observer_name, ''), '未命名建筑') AS observerName, COALESCE(SUM(quantity), 0) AS quantity, COUNT(*) AS entryCount, "
        + "COALESCE(SUM(CASE WHEN recorded_corporation_id <> #{corporationId} THEN quantity ELSE 0 END), 0) AS externalQuantity "
        + "FROM eve_mining_ledger WHERE tenant_id = #{tenantId} AND corporation_ref_id = #{corporationRefId} AND deleted = 0 "
        + "AND (#{fromDate} IS NULL OR recorded_at >= #{fromDate}) AND (#{toDate} IS NULL OR recorded_at <= #{toDate}) "
        + "AND (#{keyword} IS NULL OR observer_name LIKE CONCAT('%', #{keyword}, '%') OR character_name LIKE CONCAT('%', #{keyword}, '%') "
        + "OR type_name LIKE CONCAT('%', #{keyword}, '%')) GROUP BY observer_id, observer_name ORDER BY quantity DESC, observerName ASC")
    List<Map<String, Object>> summarizeObservers(@Param("tenantId") Long tenantId,
                                                 @Param("corporationRefId") Long corporationRefId,
                                                 @Param("corporationId") Long corporationId,
                                                 @Param("fromDate") LocalDate fromDate,
                                                 @Param("toDate") LocalDate toDate,
                                                 @Param("keyword") String keyword);

    /** 按采矿角色汇总开采量，并标注其中的外部采矿量。 */
    @Select("SELECT COALESCE(NULLIF(character_name, ''), '未命名玩家') AS characterName, COALESCE(SUM(quantity), 0) AS quantity, COUNT(*) AS entryCount, "
        + "COALESCE(SUM(CASE WHEN recorded_corporation_id <> #{corporationId} THEN quantity ELSE 0 END), 0) AS externalQuantity "
        + "FROM eve_mining_ledger WHERE tenant_id = #{tenantId} AND corporation_ref_id = #{corporationRefId} AND deleted = 0 "
        + "AND (#{fromDate} IS NULL OR recorded_at >= #{fromDate}) AND (#{toDate} IS NULL OR recorded_at <= #{toDate}) "
        + "AND (#{keyword} IS NULL OR observer_name LIKE CONCAT('%', #{keyword}, '%') OR character_name LIKE CONCAT('%', #{keyword}, '%') "
        + "OR type_name LIKE CONCAT('%', #{keyword}, '%')) GROUP BY character_id, character_name ORDER BY quantity DESC, characterName ASC LIMIT #{limit}")
    List<Map<String, Object>> summarizeCharacters(@Param("tenantId") Long tenantId,
                                                  @Param("corporationRefId") Long corporationRefId,
                                                  @Param("corporationId") Long corporationId,
                                                  @Param("fromDate") LocalDate fromDate,
                                                  @Param("toDate") LocalDate toDate,
                                                  @Param("keyword") String keyword,
                                                  @Param("limit") int limit);

    /** 按月矿类型汇总开采量，用于识别产出结构和外部采矿占比。 */
    @Select("SELECT type_id AS typeId, COALESCE(NULLIF(type_name, ''), '矿物名称待补齐') AS typeName, COALESCE(SUM(quantity), 0) AS quantity, COUNT(*) AS entryCount, "
        + "COALESCE(SUM(CASE WHEN recorded_corporation_id <> #{corporationId} THEN quantity ELSE 0 END), 0) AS externalQuantity "
        + "FROM eve_mining_ledger WHERE tenant_id = #{tenantId} AND corporation_ref_id = #{corporationRefId} AND deleted = 0 "
        + "AND (#{fromDate} IS NULL OR recorded_at >= #{fromDate}) AND (#{toDate} IS NULL OR recorded_at <= #{toDate}) "
        + "AND (#{keyword} IS NULL OR observer_name LIKE CONCAT('%', #{keyword}, '%') OR character_name LIKE CONCAT('%', #{keyword}, '%') "
        + "OR type_name LIKE CONCAT('%', #{keyword}, '%')) GROUP BY type_id, type_name ORDER BY quantity DESC, typeName ASC LIMIT #{limit}")
    List<Map<String, Object>> summarizeMinerals(@Param("tenantId") Long tenantId,
                                                @Param("corporationRefId") Long corporationRefId,
                                                @Param("corporationId") Long corporationId,
                                                @Param("fromDate") LocalDate fromDate,
                                                @Param("toDate") LocalDate toDate,
                                                @Param("keyword") String keyword,
                                                @Param("limit") int limit);
}
