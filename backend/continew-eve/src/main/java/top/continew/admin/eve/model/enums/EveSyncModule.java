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

package top.continew.admin.eve.model.enums;

import java.time.Duration;

/**
 * 自动同步模块及其默认成功调度间隔。
 *
 * @author zhaoyuqing
 */
public enum EveSyncModule {
    /** 军团成员名册。 */
    MEMBER_ROSTER(Duration.ofHours(1)),
    /** 军团成员追踪。 */
    MEMBER_TRACKING(Duration.ofHours(1)),
    /** 军团自有建筑。 */
    STRUCTURES(Duration.ofMinutes(30)),
    /** 月矿提取状态。 */
    MOON_EXTRACTIONS(Duration.ofHours(1)),
    /** 军团资产完整快照。 */
    ASSETS(Duration.ofHours(6)),
    /** 军团采矿账本。 */
    MINING_LEDGER(Duration.ofHours(3)),
    /** 当前授权角色的游戏内邮件头。 */
    GAME_MAIL(Duration.ofMinutes(10)),
    /** 当前授权角色的游戏内通知。 */
    GAME_NOTIFICATIONS(Duration.ofMinutes(10));

    private final Duration defaultInterval;

    EveSyncModule(Duration defaultInterval) {
        this.defaultInterval = defaultInterval;
    }

    /** 返回模块成功同步后的默认调度间隔。 */
    public Duration getDefaultInterval() {
        return defaultInterval;
    }
}
