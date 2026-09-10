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
import org.mockito.ArgumentCaptor;
import top.continew.admin.eve.mapper.EveSyncJobMapper;
import top.continew.admin.eve.model.entity.EveSyncJobDO;
import top.continew.admin.eve.model.enums.EveSyncJobState;
import top.continew.admin.eve.model.enums.EveSyncModule;
import top.continew.admin.eve.model.enums.EveSyncTargetType;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

    /** 已暂停任务在用户完成重新授权后手动请求时必须恢复，而不是永久卡住。 */
    @Test
    void shouldResumePausedJobForManualRequest() {
        EveSyncJobMapper mapper = mock(EveSyncJobMapper.class);
        EveSyncJobService service = new EveSyncJobService(mapper);
        LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 10, 10, 0);
        when(mapper.requestManual(any(), any(), any(), any(), any())).thenReturn(0);
        when(mapper.resumeManual(any(), any(), any(), any(), any())).thenReturn(1);

        boolean accepted = service
            .requestManual(10L, EveSyncTargetType.CORPORATION, 20L, EveSyncModule.STRUCTURES, requestedAt);

        assertThat(accepted).isTrue();
        verify(mapper).resumeManual(10L, EveSyncTargetType.CORPORATION, 20L, EveSyncModule.STRUCTURES, requestedAt);
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
}
