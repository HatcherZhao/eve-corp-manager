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
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import top.continew.admin.eve.model.entity.EveGameNotificationDO;
import top.continew.admin.eve.model.entity.EveMoonExtractionDO;
import top.continew.admin.eve.model.entity.EveMoonOreProfileDO;
import top.continew.starter.data.mapper.BaseMapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 月矿堡长期矿种档案 Mapper。
 *
 * @author zhaoyuqing
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface EveMoonOreProfileMapper extends BaseMapper<EveMoonOreProfileDO> {

    /** 并发安全地创建 PENDING 占位，已有档案时保持原值。 */
    @Insert("""
        INSERT IGNORE INTO eve_moon_ore_profile
            (tenant_id, corporation_ref_id, structure_id, moon_id, status, create_user, create_time, deleted)
        SELECT #{tenantId}, #{corporationRefId}, #{structureId}, #{moonId}, 'PENDING', #{userId}, #{now}, 0
        FROM DUAL
        WHERE EXISTS (
            SELECT 1 FROM eve_moon_extraction active_extraction
            WHERE active_extraction.tenant_id = #{tenantId}
              AND active_extraction.corporation_ref_id = #{corporationRefId}
              AND active_extraction.structure_id = #{structureId}
              AND active_extraction.moon_id = #{moonId}
              AND active_extraction.status = 'ACTIVE' AND active_extraction.deleted = 0
        )
        """)
    int insertPending(@Param("tenantId") Long tenantId,
                      @Param("corporationRefId") Long corporationRefId,
                      @Param("structureId") Long structureId,
                      @Param("moonId") Long moonId,
                      @Param("userId") Long userId,
                      @Param("now") LocalDateTime now);

    /** 建筑关联月球发生变化时清空旧档案，避免把上一月球的矿种错误展示给新时间线。 */
    @Update("""
        UPDATE eve_moon_ore_profile
        SET moon_id = #{moonId}, status = 'PENDING', ores_json = NULL, ore_names = NULL,
            source_notification_id = NULL, source_sent_at = NULL, last_cache_checked_at = NULL,
            update_user = #{userId}, update_time = #{now}
        WHERE tenant_id = #{tenantId} AND corporation_ref_id = #{corporationRefId}
          AND structure_id = #{structureId} AND deleted = 0
          AND (moon_id IS NULL OR moon_id <> #{moonId})
          AND EXISTS (
              SELECT 1 FROM eve_moon_extraction active_extraction
              WHERE active_extraction.tenant_id = #{tenantId}
                AND active_extraction.corporation_ref_id = #{corporationRefId}
                AND active_extraction.structure_id = #{structureId}
                AND active_extraction.moon_id = #{moonId}
                AND active_extraction.status = 'ACTIVE' AND active_extraction.deleted = 0
          )
        """)
    int resetForMoonChange(@Param("tenantId") Long tenantId,
                           @Param("corporationRefId") Long corporationRefId,
                           @Param("structureId") Long structureId,
                           @Param("moonId") Long moonId,
                           @Param("userId") Long userId,
                           @Param("now") LocalDateTime now);

    /** 查询指定月矿堡的现有长期档案。 */
    @Select("""
        <script>
        SELECT * FROM eve_moon_ore_profile
        WHERE tenant_id = #{tenantId} AND corporation_ref_id = #{corporationRefId} AND deleted = 0
          AND structure_id IN
        <foreach collection="structureIds" item="structureId" open="(" close=")" separator=",">
            #{structureId}
        </foreach>
        </script>
        """)
    List<EveMoonOreProfileDO> selectProfiles(@Param("tenantId") Long tenantId,
                                             @Param("corporationRefId") Long corporationRefId,
                                             @Param("structureIds") Collection<Long> structureIds);

    /** 查询军团完整的 ACTIVE 月矿时间线，供无建筑 ID 的通知做唯一匹配。 */
    @Select("""
        SELECT * FROM eve_moon_extraction
        WHERE tenant_id = #{tenantId} AND corporation_ref_id = #{corporationRefId}
          AND status = 'ACTIVE' AND deleted = 0
        ORDER BY chunk_arrival_at DESC, id DESC
        """)
    List<EveMoonExtractionDO> selectActiveExtractions(@Param("tenantId") Long tenantId,
                                                      @Param("corporationRefId") Long corporationRefId);

    /** 只读取当前本站用户当前授权角色的月矿通知缓存，绝不跨用户拼接。 */
    @Select("""
        SELECT * FROM eve_game_notification
        WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND character_ref_id = #{characterRefId}
          AND deleted = 0
          AND notification_type IN
              ('MoonminingExtractionStarted', 'MoonminingExtractionFinished',
               'MoonminingAutomaticFracture', 'MoonminingLaserFired')
        ORDER BY sent_at DESC, notification_id DESC
        """)
    List<EveGameNotificationDO> selectMoonNotifications(@Param("tenantId") Long tenantId,
                                                        @Param("userId") Long userId,
                                                        @Param("characterRefId") Long characterRefId);

    /** 自动初始化仅允许把 PENDING 档案原子升级为 READY，不能覆盖已有稳定档案。 */
    @Update("""
        UPDATE eve_moon_ore_profile
        SET moon_id = #{profile.moonId}, status = 'READY', ores_json = #{profile.oresJson},
            ore_names = #{profile.oreNames}, source_notification_id = #{profile.sourceNotificationId},
            source_sent_at = #{profile.sourceSentAt}, last_cache_checked_at = #{now},
            update_user = #{userId}, update_time = #{now}
        WHERE tenant_id = #{profile.tenantId} AND corporation_ref_id = #{profile.corporationRefId}
          AND structure_id = #{profile.structureId} AND moon_id = #{profile.moonId}
          AND status = 'PENDING' AND deleted = 0
          AND EXISTS (
              SELECT 1 FROM eve_moon_extraction active_extraction
              WHERE active_extraction.tenant_id = #{profile.tenantId}
                AND active_extraction.corporation_ref_id = #{profile.corporationRefId}
                AND active_extraction.structure_id = #{profile.structureId}
                AND active_extraction.moon_id = #{profile.moonId}
                AND active_extraction.status = 'ACTIVE' AND active_extraction.deleted = 0
          )
        """)
    int updateReadyIfPending(@Param("profile") EveMoonOreProfileDO profile,
                             @Param("userId") Long userId,
                             @Param("now") LocalDateTime now);

    /** 手动刷新以最新有效通知替换档案，但调用方在无有效消息时不会执行本更新。 */
    @Update("""
        UPDATE eve_moon_ore_profile
        SET moon_id = #{profile.moonId}, status = 'READY', ores_json = #{profile.oresJson},
            ore_names = #{profile.oreNames}, source_notification_id = #{profile.sourceNotificationId},
            source_sent_at = #{profile.sourceSentAt}, last_cache_checked_at = #{now},
            update_user = #{userId}, update_time = #{now}
        WHERE tenant_id = #{profile.tenantId} AND corporation_ref_id = #{profile.corporationRefId}
          AND structure_id = #{profile.structureId} AND moon_id = #{profile.moonId} AND deleted = 0
          AND (source_sent_at IS NULL OR source_sent_at < #{profile.sourceSentAt}
               OR (source_sent_at = #{profile.sourceSentAt}
                   AND (source_notification_id IS NULL OR source_notification_id <= #{profile.sourceNotificationId})))
          AND EXISTS (
              SELECT 1 FROM eve_moon_extraction active_extraction
              WHERE active_extraction.tenant_id = #{profile.tenantId}
                AND active_extraction.corporation_ref_id = #{profile.corporationRefId}
                AND active_extraction.structure_id = #{profile.structureId}
                AND active_extraction.moon_id = #{profile.moonId}
                AND active_extraction.status = 'ACTIVE' AND active_extraction.deleted = 0
          )
        """)
    int forceUpdateReady(@Param("profile") EveMoonOreProfileDO profile,
                         @Param("userId") Long userId,
                         @Param("now") LocalDateTime now);

    /** 记录 PENDING 档案本地缓存未命中的检查时间，用于十分钟节流。 */
    @Update("""
        UPDATE eve_moon_ore_profile
        SET last_cache_checked_at = #{now}, update_user = #{userId}, update_time = #{now}
        WHERE tenant_id = #{tenantId} AND corporation_ref_id = #{corporationRefId}
          AND structure_id = #{structureId} AND moon_id = #{moonId}
          AND status = 'PENDING' AND deleted = 0
        """)
    int touchPendingChecked(@Param("tenantId") Long tenantId,
                            @Param("corporationRefId") Long corporationRefId,
                            @Param("structureId") Long structureId,
                            @Param("moonId") Long moonId,
                            @Param("userId") Long userId,
                            @Param("now") LocalDateTime now);
}
