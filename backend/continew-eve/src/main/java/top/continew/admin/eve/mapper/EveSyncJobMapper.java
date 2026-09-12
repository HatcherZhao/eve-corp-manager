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
import org.apache.ibatis.annotations.Update;
import top.continew.admin.eve.model.entity.EveSyncJobDO;
import top.continew.admin.eve.model.enums.EveSyncJobState;
import top.continew.admin.eve.model.enums.EveSyncModule;
import top.continew.admin.eve.model.enums.EveSyncTargetType;
import top.continew.starter.data.mapper.BaseMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * EVE 自动同步任务 Mapper。
 *
 * @author zhaoyuqing
 */
@Mapper
public interface EveSyncJobMapper extends BaseMapper<EveSyncJobDO> {

    /** 按租户、目标和模块查询唯一任务。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM eve_sync_job WHERE tenant_id = #{tenantId} AND target_type = #{targetType} " + "AND target_ref_id = #{targetRefId} AND module = #{module} AND deleted = 0 LIMIT 1")
    EveSyncJobDO selectByIdentity(@Param("tenantId") Long tenantId,
                                  @Param("targetType") EveSyncTargetType targetType,
                                  @Param("targetRefId") Long targetRefId,
                                  @Param("module") EveSyncModule module);

    /** 跨租户查询到期候选，也允许重新领取已超过租约时间的遗留运行任务。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM eve_sync_job WHERE deleted = 0 AND " + "((state = 'PENDING' AND next_run_at <= #{now}) " + "OR (state = 'RUNNING' AND claim_expires_at IS NOT NULL AND claim_expires_at <= #{now})) " + "ORDER BY CASE WHEN state = 'RUNNING' THEN claim_expires_at ELSE next_run_at END ASC, id ASC LIMIT #{limit}")
    List<EveSyncJobDO> selectDueCandidates(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** 原子领取任务并写入有限租约；并发实例只有一个能够更新成功。 */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE eve_sync_job SET state = 'RUNNING', claim_token = #{claimToken}, " + "claim_expires_at = #{claimExpiresAt}, last_started_at = #{startedAt}, update_time = #{startedAt} " + "WHERE id = #{jobId} AND tenant_id = #{tenantId} AND deleted = 0 AND " + "((state = 'PENDING' AND next_run_at <= #{startedAt}) " + "OR (state = 'RUNNING' AND claim_expires_at IS NOT NULL AND claim_expires_at <= #{startedAt}))")
    int claim(@Param("jobId") Long jobId,
              @Param("tenantId") Long tenantId,
              @Param("claimToken") String claimToken,
              @Param("startedAt") LocalDateTime startedAt,
              @Param("claimExpiresAt") LocalDateTime claimExpiresAt);

    /** 按领取令牌读取当前任务，确保调用方取得数据库中的最新状态。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM eve_sync_job WHERE id = #{jobId} AND tenant_id = #{tenantId} " + "AND state = 'RUNNING' AND claim_token = #{claimToken} AND deleted = 0 LIMIT 1")
    EveSyncJobDO selectClaimed(@Param("jobId") Long jobId,
                               @Param("tenantId") Long tenantId,
                               @Param("claimToken") String claimToken);

    /** 仅允许当前租约持有者提交成功结果，防止超时执行者覆盖新结果。 */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE eve_sync_job SET state = 'PENDING', next_run_at = #{nextRunAt}, " + "cooldown_until = #{cooldownUntil}, last_finished_at = #{finishedAt}, consecutive_failures = 0, " + "last_failure_code = NULL, claim_token = NULL, claim_expires_at = NULL, update_time = #{finishedAt} " + "WHERE id = #{jobId} AND tenant_id = #{tenantId} AND state = 'RUNNING' " + "AND claim_token = #{claimToken} AND deleted = 0")
    int completeSuccess(@Param("jobId") Long jobId,
                        @Param("tenantId") Long tenantId,
                        @Param("claimToken") String claimToken,
                        @Param("finishedAt") LocalDateTime finishedAt,
                        @Param("nextRunAt") LocalDateTime nextRunAt,
                        @Param("cooldownUntil") LocalDateTime cooldownUntil);

    /** 手动同步成功后重排未运行的自动任务，避免后台立即重复拉取同一资源。 */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE eve_sync_job SET state = 'PENDING', next_run_at = #{nextRunAt}, " + "cooldown_until = #{cooldownUntil}, last_finished_at = #{finishedAt}, consecutive_failures = 0, " + "last_failure_code = NULL, update_time = #{finishedAt} " + "WHERE tenant_id = #{tenantId} AND target_type = #{targetType} AND target_ref_id = #{targetRefId} " + "AND module = #{module} AND state <> 'RUNNING' AND deleted = 0")
    int completeManualSuccess(@Param("tenantId") Long tenantId,
                              @Param("targetType") EveSyncTargetType targetType,
                              @Param("targetRefId") Long targetRefId,
                              @Param("module") EveSyncModule module,
                              @Param("finishedAt") LocalDateTime finishedAt,
                              @Param("nextRunAt") LocalDateTime nextRunAt,
                              @Param("cooldownUntil") LocalDateTime cooldownUntil);

    /** 仅允许当前租约持有者提交失败结果，并按结果决定退避或暂停。 */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE eve_sync_job SET state = #{state}, next_run_at = #{nextRunAt}, " + "cooldown_until = #{cooldownUntil}, last_finished_at = #{finishedAt}, " + "consecutive_failures = #{consecutiveFailures}, last_failure_code = #{failureCode}, " + "claim_token = NULL, claim_expires_at = NULL, update_time = #{finishedAt} " + "WHERE id = #{jobId} AND tenant_id = #{tenantId} AND state = 'RUNNING' " + "AND claim_token = #{claimToken} AND deleted = 0")
    int completeFailure(@Param("jobId") Long jobId,
                        @Param("tenantId") Long tenantId,
                        @Param("claimToken") String claimToken,
                        @Param("state") EveSyncJobState state,
                        @Param("finishedAt") LocalDateTime finishedAt,
                        @Param("nextRunAt") LocalDateTime nextRunAt,
                        @Param("cooldownUntil") LocalDateTime cooldownUntil,
                        @Param("consecutiveFailures") int consecutiveFailures,
                        @Param("failureCode") String failureCode);

}
