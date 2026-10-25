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

import org.junit.jupiter.api.Test;
import top.continew.admin.eve.model.enums.EveSyncModule;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** 动态业务数据默认同步周期测试。 */
class EveAutoSyncPropertiesTest {

    /** 所有动态模块默认半小时检查一次，单轮仍按顺序最多处理三项。 */
    @Test
    void shouldUseThirtyMinuteDynamicDataIntervals() {
        EveAutoSyncProperties properties = new EveAutoSyncProperties();

        assertThat(properties.getMaxJobsPerCycle()).isEqualTo(3);
        assertThat(properties.getAssetsInterval()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.getStructuresInterval()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.getMoonExtractionsInterval()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.getMiningLedgerInterval()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.getMembersInterval()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.getMailInterval()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.getNotificationsInterval()).isEqualTo(Duration.ofMinutes(30));
        assertThat(EveSyncModule.values()).allSatisfy(module -> assertThat(module.getDefaultInterval())
            .isEqualTo(Duration.ofMinutes(30)));
    }
}
