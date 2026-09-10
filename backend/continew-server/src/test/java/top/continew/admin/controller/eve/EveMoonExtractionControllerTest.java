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

package top.continew.admin.controller.eve;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 月矿情报接口边界测试。
 *
 * @author zhaoyuqing
 */
class EveMoonExtractionControllerTest {

    /** 月矿接口只保留时间线查询、同步和简短备注维护能力。 */
    @Test
    void shouldExposeOnlyMoonIntelligenceOperations() {
        assertThat(Arrays.stream(EveMoonExtractionController.class.getDeclaredMethods())
            .map(method -> method.getName())).containsExactlyInAnyOrder("page", "sync", "saveNote");
    }
}
