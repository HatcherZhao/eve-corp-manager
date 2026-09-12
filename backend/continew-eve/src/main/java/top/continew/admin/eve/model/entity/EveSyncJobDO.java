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

package top.continew.admin.eve.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.common.base.model.entity.TenantBaseDO;
import top.continew.admin.eve.model.enums.EveSyncJobState;
import top.continew.admin.eve.model.enums.EveSyncModule;
import top.continew.admin.eve.model.enums.EveSyncTargetType;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 自动同步任务的持久化调度状态。
 *
 * <p>领取令牌用于隔离并发调度实例，也用于阻止过期执行者覆盖新执行结果。</p>
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_sync_job")
public class EveSyncJobDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 同步目标类型。 */
    private EveSyncTargetType targetType;
    /** 军团或角色绑定记录 ID。 */
    private Long targetRefId;
    /** 同步模块。 */
    private EveSyncModule module;
    /** 当前任务状态。 */
    private EveSyncJobState state;
    /** 下次允许调度时间。 */
    private LocalDateTime nextRunAt;
    /** 自动任务在此时间前不得提前执行的冷却截止时间。 */
    private LocalDateTime cooldownUntil;
    /** 最近开始执行时间。 */
    private LocalDateTime lastStartedAt;
    /** 最近结束执行时间。 */
    private LocalDateTime lastFinishedAt;
    /** 连续失败次数。 */
    private Integer consecutiveFailures;
    /** 最近脱敏失败分类。 */
    private String lastFailureCode;
    /** 当前执行租约的随机令牌。 */
    private String claimToken;
    /** 当前执行租约的过期时间。 */
    private LocalDateTime claimExpiresAt;
}
