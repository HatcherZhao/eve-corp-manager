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

package top.continew.admin.eve.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * EVE 业务数据自动同步的保守调度参数。
 *
 * <p>自动调度必须尊重国服响应的 Expires 时间；这里的间隔仅表示后台任务的最长新鲜度目标。
 * 用户主动同步会立即执行，并以同一间隔重新排定后续自动任务。</p>
 *
 * @author zhaoyuqing
 */
@Data
@Component
@ConfigurationProperties(prefix = "eve.serenity.auto-sync")
public class EveAutoSyncProperties {

    /** 是否启用业务数据自动同步。 */
    private boolean enabled = true;
    /** 调度器扫描到期任务的周期。 */
    private Duration scanInterval = Duration.ofMinutes(1);
    /** 单轮最多领取的任务数，避免服务重启后形成请求洪峰。 */
    private int maxJobsPerCycle = 1;
    /** 全部服务实例共享的国服 ESI 请求最小间隔，默认每分钟最多约 20 次。 */
    private Duration upstreamRequestInterval = Duration.ofSeconds(3);
    /** 军团资产完整快照的默认间隔。 */
    private Duration assetsInterval = Duration.ofHours(6);
    /** 军团建筑快照的默认间隔。 */
    private Duration structuresInterval = Duration.ofMinutes(30);
    /** 月矿提取时间线的默认间隔。 */
    private Duration moonExtractionsInterval = Duration.ofHours(1);
    /** 采矿账本的默认间隔。 */
    private Duration miningLedgerInterval = Duration.ofHours(3);
    /** 成员名册和追踪信息的默认间隔。 */
    private Duration membersInterval = Duration.ofHours(1);
    /** 授权角色个人邮箱邮件头的默认间隔。 */
    private Duration mailInterval = Duration.ofMinutes(10);
    /** 授权角色个人游戏通知的默认间隔。 */
    private Duration notificationsInterval = Duration.ofMinutes(10);
    /** 首次建任务或服务恢复后的最大随机延迟。 */
    private Duration startupJitter = Duration.ofMinutes(10);
}
