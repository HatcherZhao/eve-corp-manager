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
import org.springframework.jdbc.core.JdbcTemplate;
import top.continew.admin.eve.mapper.EveStaticLocationReferenceMapper;
import top.continew.admin.eve.mapper.EveStaticReferenceImportMapper;
import top.continew.admin.eve.mapper.EveStaticTypeReferenceMapper;
import top.continew.admin.eve.model.EveStaticTypeCategoryNodeResp;
import top.continew.admin.eve.model.EveStaticLocationTreeNodeResp;
import top.continew.admin.eve.model.entity.EveStaticLocationReferenceDO;
import top.continew.admin.eve.model.entity.EveStaticTypeReferenceDO;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EVE 静态物品市场分类树测试。
 *
 * @author zhaoyuqing
 */
class EveStaticReferenceServiceTest {

    /** 分类树必须保留多级路径、父节点汇总数量与资料源未分类物品。 */
    @Test
    void shouldBuildMarketCategoryTreeAndCacheResult() {
        EveStaticTypeReferenceMapper typeMapper = mock(EveStaticTypeReferenceMapper.class);
        EveStaticReferenceService service = new EveStaticReferenceService(typeMapper, mock(EveStaticLocationReferenceMapper.class), mock(EveStaticReferenceImportMapper.class), mock(JdbcTemplate.class));
        when(typeMapper.selectList(any())).thenReturn(List
            .of(type("舰船", "护卫舰", "标准护卫舰"), type("舰船", "护卫舰", "势力护卫舰"), type("建筑装备"), type()));

        List<EveStaticTypeCategoryNodeResp> tree = service.listTypeCategories();

        EveStaticTypeCategoryNodeResp ships = tree.get(0);
        EveStaticTypeCategoryNodeResp shipClass = ships.children().get(0);
        assertThat(ships.name()).isEqualTo("舰船");
        assertThat(ships.typeCount()).isEqualTo(2);
        assertThat(shipClass.name()).isEqualTo("护卫舰");
        assertThat(shipClass.children()).hasSize(2);
        assertThat(tree.get(1).name()).isEqualTo("建筑装备");
        assertThat(tree.get(1).unclassified()).isFalse();
        assertThat(tree.get(1).typeCount()).isEqualTo(1L);
        assertThat(tree.get(2).name()).isEqualTo("未分类");
        assertThat(tree.get(2).unclassified()).isTrue();
        assertThat(tree.get(2).typeCount()).isEqualTo(1L);

        assertThat(service.listTypeCategories()).isSameAs(tree);
        verify(typeMapper, times(1)).selectList(any());
    }

    /** 位置树必须按星域、星座、星系衔接，并汇总空间站等下级位置数量。 */
    @Test
    void shouldBuildLocationTreeWithFacilitiesIncludedInSystemCount() {
        EveStaticLocationReferenceMapper locationMapper = mock(EveStaticLocationReferenceMapper.class);
        EveStaticReferenceService service = new EveStaticReferenceService(mock(EveStaticTypeReferenceMapper.class), locationMapper, mock(EveStaticReferenceImportMapper.class), mock(JdbcTemplate.class));
        when(locationMapper.selectList(any())).thenReturn(List
            .of(location("REGION", 1L, "测试星域", null, null, null), location("CONSTELLATION", 2L, "测试星座", 1L, null, null), location("SOLAR_SYSTEM", 3L, "测试星系", 1L, 2L, 3L), location("NPC_STATION", 4L, "测试空间站", 1L, 2L, 3L)));

        List<EveStaticLocationTreeNodeResp> tree = service.listLocationTree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).referenceName()).isEqualTo("测试星域");
        assertThat(tree.get(0).locationCount()).isEqualTo(4L);
        assertThat(tree.get(0).children().get(0).referenceName()).isEqualTo("测试星座");
        assertThat(tree.get(0).children().get(0).children().get(0).referenceName()).isEqualTo("测试星系");
        assertThat(tree.get(0).children().get(0).children().get(0).locationCount()).isEqualTo(2L);
    }

    /** 构造最小市场分类类型资料。 */
    private static EveStaticTypeReferenceDO type(String... categories) {
        EveStaticTypeReferenceDO type = new EveStaticTypeReferenceDO();
        if (categories.length > 0) {
            type.setMarketCategoryL1(categories[0]);
        }
        if (categories.length > 1) {
            type.setMarketCategoryL2(categories[1]);
        }
        if (categories.length > 2) {
            type.setMarketCategoryL3(categories[2]);
        }
        return type;
    }

    /** 构造最小位置层级资料。 */
    private static EveStaticLocationReferenceDO location(String type,
                                                         Long id,
                                                         String name,
                                                         Long regionId,
                                                         Long constellationId,
                                                         Long solarSystemId) {
        EveStaticLocationReferenceDO location = new EveStaticLocationReferenceDO();
        location.setReferenceType(type);
        location.setReferenceId(id);
        location.setReferenceName(name);
        location.setRegionId(regionId);
        location.setConstellationId(constellationId);
        location.setSolarSystemId(solarSystemId);
        return location;
    }
}
