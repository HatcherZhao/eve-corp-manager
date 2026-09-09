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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 国服 Excel 名称资料加载测试。
 *
 * @author zhaoyuqing
 */
class EveStaticNameReferenceTest {

    /** 类型、星系、NPC 空间站和公开建筑均应使用资料中的中文名称。 */
    @Test
    void shouldResolveChineseNamesFromEveDataReference() {
        EveStaticNameReference reference = new EveStaticNameReference();

        assertThat(reference.findTypeName(35835L)).isEqualTo("阿塔诺");
        assertThat(reference.findSolarSystemName(30000142L)).isEqualTo("吉他");
        assertThat(reference.findNpcStation(60000004L))
            .isEqualTo(new EveStaticNameReference.StaticLocation("姆沃莱伦 X - 卫星 3 - CBD社团 储存工厂", 30002780L));
        assertThat(reference.findPublicStructure(1007950854380L))
            .isEqualTo(new EveStaticNameReference.StaticLocation("纳勒 - 星光城购物中心Q群306426869", 30005054L));
    }

    /** 静态资料应覆盖同 ID 的上游名称，未知 ID 仍可保留上游兜底。 */
    @Test
    void shouldKeepReferenceAsNameBaselineWithoutDroppingUnknownIds() {
        EveStaticNameReference reference = new EveStaticNameReference();

        Map<Long, String> names = reference.applyTypeNames(List.of(35835L, 999999999L), Map
            .of(35835L, "Upwell Refinery", 999999999L, "上游名称"));

        assertThat(names).containsEntry(35835L, "阿塔诺").containsEntry(999999999L, "上游名称");
        assertThat(EveStaticNameReference.unresolvedIds(List.of(35835L, 999999999L), names)).isEmpty();
    }
}
