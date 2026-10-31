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

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 持久化的按需市场刷新任务，保证进程重启后仍能继续推进。 */
@Data
@TableName("eve_market_refresh_queue")
@InterceptorIgnore(tenantLine = "true")
public class EveMarketRefreshQueueDO {

    /** 物品类型 ID，同时作为全局去重键。 */
    @TableId
    private Integer typeId;
    /** 是否同时刷新官方区域历史。 */
    private Boolean includeHistory;
    /** 已失败次数，用于退避。 */
    private Integer attemptCount;
    /** 任务版本；重新入队时递增。 */
    private Long taskVersion;
    /** 下一次允许执行的时间。 */
    private LocalDateTime nextAttemptAt;
    /** 最近失败摘要。 */
    private String lastError;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
