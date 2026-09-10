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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 游戏通知类型分类测试。
 *
 * @author zhaoyuqing
 */
class EveGameNotificationServiceTest {

    /** 已知关键类型应进入稳定菜单分类，未知类型必须保留在其他。 */
    @Test
    void shouldClassifyNotificationTypesWithoutDroppingUnknownTypes() {
        assertThat(EveGameNotificationService.categoryOf("CorpAppNewMsg")).isEqualTo("CORPORATION_MEMBER");
        assertThat(EveGameNotificationService.categoryOf("StructureFuelAlert")).isEqualTo("STRUCTURE_ASSET_SAFETY");
        assertThat(EveGameNotificationService.categoryOf("WarDeclaredMsg")).isEqualTo("WAR_SOVEREIGNTY");
        assertThat(EveGameNotificationService.categoryOf("MoonMiningExtractionStarted")).isEqualTo("MOON_INDUSTRY");
        assertThat(EveGameNotificationService.categoryOf("FutureGameMessage")).isEqualTo("OTHER");
    }
}
