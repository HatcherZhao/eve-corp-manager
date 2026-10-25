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

package top.continew.admin.eve.service;

import org.junit.jupiter.api.Test;
import org.apache.ibatis.annotations.Update;
import org.mockito.ArgumentCaptor;
import top.continew.admin.eve.mapper.EveSyncJobMapper;
import top.continew.admin.eve.model.entity.EveSyncJobDO;
import top.continew.admin.eve.model.enums.EveSyncJobState;
import top.continew.admin.eve.model.enums.EveSyncModule;
import top.continew.admin.eve.model.enums.EveSyncTargetType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 自动同步队列的缓存有效期与暂停恢复规则测试。 */
class EveSyncJobServiceTest {

    /** 上游 Expires 晚于配置周期时，成功任务不得被提前再次执行。 */
    @Test
    void shouldRespectSourceExpiresWhenSchedulingSuccess() {
        EveSyncJobMapper mapper = mock(EveSyncJobMapper.class);
        EveSyncJobService service = new EveSyncJobService(mapper);
        EveSyncJobDO job = claimedJob();
        LocalDateTime finishedAt = LocalDateTime.of(2026, 9, 10, 10, 0);
        LocalDateTime sourceExpiresAt = finishedAt.plusHours(2);
        when(mapper.completeSuccess(any(), any(), any(), any(), any(), any())).thenReturn(1);

        boolean completed = service.completeSuccess(job, sourceExpiresAt, Duration.ofMinutes(30), finishedAt);

        ArgumentCaptor<LocalDateTime> nextRunAt = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> cooldownUntil = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).completeSuccess(eq(job.getId()), eq(job.getTenantId()), eq(job
            .getClaimToken()), eq(finishedAt), nextRunAt.capture(), cooldownUntil.capture());
        assertThat(completed).isTrue();
        assertThat(nextRunAt.getValue()).isEqualTo(sourceExpiresAt);
        assertThat(cooldownUntil.getValue()).isEqualTo(sourceExpiresAt);
    }

    /** 页面直接同步成功后，应仅延后自动任务而不走手动入队逻辑。 */
    @Test
    void shouldScheduleFollowingAutomaticJobAfterManualSuccess() {
        EveSyncJobMapper mapper = mock(EveSyncJobMapper.class);
        EveSyncJobService service = new EveSyncJobService(mapper);
        EveSyncJobDO existingJob = new EveSyncJobDO();
        LocalDateTime sourceExpiresAt = LocalDateTime.now().plusHours(2);
        when(mapper.selectByIdentity(10L, EveSyncTargetType.CORPORATION, 20L, EveSyncModule.STRUCTURES))
            .thenReturn(existingJob);

        service.scheduleAfterManualSuccess(10L, EveSyncTargetType.CORPORATION, 20L, EveSyncModule.STRUCTURES, Duration
            .ofMinutes(30), sourceExpiresAt);

        verify(mapper)
            .completeManualSuccess(eq(10L), eq(EveSyncTargetType.CORPORATION), eq(20L), eq(EveSyncModule.STRUCTURES), any(), eq(sourceExpiresAt), eq(sourceExpiresAt));
    }

    /** 周期缩短时，健康任务交给原子 SQL 提前，并传入周期与旧版手动冷却识别下限。 */
    @Test
    void shouldAdvanceHealthyPendingScheduleWithCacheFloors() {
        EveSyncJobMapper mapper = mock(EveSyncJobMapper.class);
        EveSyncJobService service = new EveSyncJobService(mapper);
        LocalDateTime finishedAt = LocalDateTime.of(2026, 10, 25, 10, 0);
        EveSyncJobDO job = healthyPendingJob(finishedAt);
        when(mapper
            .advanceHealthyPendingSchedule(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(1);

        boolean advanced = service.advanceHealthyPendingSchedule(job, Duration.ofMinutes(30));

        verify(mapper).advanceHealthyPendingSchedule(eq(job.getId()), eq(job.getTenantId()), eq(job
            .getTargetType()), eq(job.getTargetRefId()), eq(job.getModule()), eq(finishedAt), eq(job
                .getNextRunAt()), eq(job.getCooldownUntil()), eq(finishedAt.plusMinutes(1)), eq(finishedAt
                    .plusMinutes(30)), any());
        assertThat(advanced).isTrue();
    }

    /** CAS 未更新表示任务已被并发领取或重排，调用方不能把它当成本次修改成功。 */
    @Test
    void shouldReportConcurrentScheduleChangeWithoutOverwrite() {
        EveSyncJobMapper mapper = mock(EveSyncJobMapper.class);
        EveSyncJobService service = new EveSyncJobService(mapper);
        EveSyncJobDO job = healthyPendingJob(LocalDateTime.of(2026, 10, 25, 10, 0));
        when(mapper
            .advanceHealthyPendingSchedule(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(0);

        assertThat(service.advanceHealthyPendingSchedule(job, Duration.ofMinutes(30))).isFalse();
    }

    /** 失败、暂停、运行及仍符合新周期的任务都不得进入历史排期调整。 */
    @Test
    void shouldNotAdvanceUnsafeOrAlreadyCompliantSchedules() {
        EveSyncJobMapper mapper = mock(EveSyncJobMapper.class);
        EveSyncJobService service = new EveSyncJobService(mapper);
        LocalDateTime finishedAt = LocalDateTime.of(2026, 10, 25, 10, 0);
        EveSyncJobDO failed = healthyPendingJob(finishedAt);
        failed.setConsecutiveFailures(1);
        failed.setLastFailureCode("TRANSIENT");
        EveSyncJobDO paused = healthyPendingJob(finishedAt);
        paused.setState(EveSyncJobState.PAUSED);
        EveSyncJobDO running = healthyPendingJob(finishedAt);
        running.setState(EveSyncJobState.RUNNING);
        running.setClaimToken("claim");
        EveSyncJobDO compliant = healthyPendingJob(finishedAt);
        compliant.setNextRunAt(finishedAt.plusMinutes(30));

        assertThat(service.advanceHealthyPendingSchedule(failed, Duration.ofMinutes(30))).isFalse();
        assertThat(service.advanceHealthyPendingSchedule(paused, Duration.ofMinutes(30))).isFalse();
        assertThat(service.advanceHealthyPendingSchedule(running, Duration.ofMinutes(30))).isFalse();
        assertThat(service.advanceHealthyPendingSchedule(compliant, Duration.ofMinutes(30))).isFalse();
        verifyNoInteractions(mapper);
    }

    /** 原子重排 SQL 必须同时保留军团新鲜度下限并校验任务完整并发状态。 */
    @Test
    void shouldKeepFreshnessFloorAndCompareAndSetGuardsInMapper() {
        Update update = Arrays.stream(EveSyncJobMapper.class.getDeclaredMethods())
            .filter(method -> "advanceHealthyPendingSchedule".equals(method.getName()))
            .findFirst()
            .orElseThrow()
            .getAnnotation(Update.class);
        String sql = String.join(" ", update.value());

        assertThat(sql)
            .contains("LEFT JOIN eve_data_sync_status", "s.source_expires_at", "j.state = 'PENDING'", "j.consecutive_failures = 0", "j.claim_token IS NULL", "j.next_run_at = #{expectedNextRunAt}", "j.cooldown_until = #{expectedCooldownUntil}", "j.cooldown_until IS NULL");
    }

    /** 构造具有有效数据库领取令牌的运行中任务。 */
    private static EveSyncJobDO claimedJob() {
        EveSyncJobDO job = new EveSyncJobDO();
        job.setId(1L);
        job.setTenantId(10L);
        job.setModule(EveSyncModule.STRUCTURES);
        job.setState(EveSyncJobState.RUNNING);
        job.setClaimToken("claim-token");
        return job;
    }

    /** 构造具备旧成功排期和来源缓存冷却的健康等待任务。 */
    private static EveSyncJobDO healthyPendingJob(LocalDateTime finishedAt) {
        EveSyncJobDO job = new EveSyncJobDO();
        job.setId(2L);
        job.setTenantId(10L);
        job.setTargetType(EveSyncTargetType.CORPORATION);
        job.setTargetRefId(20L);
        job.setModule(EveSyncModule.ASSETS);
        job.setState(EveSyncJobState.PENDING);
        job.setLastFinishedAt(finishedAt);
        job.setNextRunAt(finishedAt.plusHours(6));
        job.setCooldownUntil(finishedAt.plusHours(1));
        job.setConsecutiveFailures(0);
        return job;
    }
}
