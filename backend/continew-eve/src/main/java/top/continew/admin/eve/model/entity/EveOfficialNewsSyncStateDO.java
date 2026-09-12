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

/**
 * 单个官网资讯栏目的同步状态与可恢复失败信息。
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_official_news_sync_state")
@InterceptorIgnore(tenantLine = "true")
public class EveOfficialNewsSyncStateDO {

    /** 官网栏目编码。 */
    @TableId
    private String sourceCode;
    /** 官网栏目链接。 */
    private String sourceUrl;
    /** 最近检查时间。 */
    private LocalDateTime lastCheckedAt;
    /** 最近成功时间。 */
    private LocalDateTime lastSuccessfulAt;
    /** 最近失败时间。 */
    private LocalDateTime lastFailureAt;
    /** 连续失败次数。 */
    private Integer failureCount;
    /** 面向运维的脱敏失败摘要。 */
    private String lastFailureMessage;
    /** 最近一次发现或修订数量。 */
    private Integer lastDiscoveredCount;
}
