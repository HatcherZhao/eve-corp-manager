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
import top.continew.admin.eve.model.EveCorporationAssetTreeNodeResp;
import top.continew.admin.eve.model.entity.EveCorporationAssetDO;
import top.continew.admin.eve.model.entity.EveStaticTypeReferenceDO;

import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 军团资产层级组装测试。
 *
 * @author zhaoyuqing
 */
class EveCorporationAssetServiceTest {

    /** 没有物品箱时，物品应直接归属仓库；有下级资产的物品才是容器节点。 */
    @Test
    void shouldKeepAssetsDirectlyUnderWarehouseUnlessTheyHaveChildren() {
        EveCorporationAssetDO directAsset = asset(101L, "三钛合金", "station", 60000001L, "Hangar", 30000142L, "吉他");
        EveCorporationAssetDO container = asset(102L, "矿石箱", "station", 60000001L, "Hangar", 30000142L, "吉他");
        EveCorporationAssetDO containedAsset = asset(103L, "斜长岩", "item", 102L, "AutoFit", null, null);

        List<EveCorporationAssetTreeNodeResp> tree = EveCorporationAssetService.buildTree(List
            .of(directAsset, container, containedAsset));

        EveCorporationAssetTreeNodeResp warehouse = tree.get(0).children().get(0).children().get(0);
        assertThat(tree).singleElement().extracting(EveCorporationAssetTreeNodeResp::title).isEqualTo("吉他");
        assertThat(warehouse.title()).isEqualTo("机库");
        assertThat(warehouse.children()).extracting(EveCorporationAssetTreeNodeResp::title)
            .containsExactly("三钛合金", "矿石箱");
        assertThat(warehouse.children().get(1).kind()).isEqualTo("container");
        assertThat(warehouse.children().get(1).children()).singleElement()
            .extracting(EveCorporationAssetTreeNodeResp::title)
            .isEqualTo("斜长岩");
    }

    /** 舰船应按实际槽位与货仓分层，不能将装配模块和货物平铺为物品。 */
    @Test
    void shouldGroupFittedShipAndCargoByCompartment() {
        EveCorporationAssetDO ship = asset(151L, "强制者级", "station", 60000001L, "Hangar", 30000142L, "吉他");
        ship.setTypeId(16236);
        EveCorporationAssetDO highSlotModule = asset(152L, "小型聚焦增强模式激光器 I", "item", 151L, "HiSlot0", null, null);
        EveCorporationAssetDO mediumSlotModule = asset(153L, "1MN民用加力燃烧器", "item", 151L, "MedSlot0", null, null);
        EveCorporationAssetDO cargo = asset(154L, "多频晶体 S", "item", 151L, "Cargo", null, null);

        List<EveCorporationAssetTreeNodeResp> tree = EveCorporationAssetService.buildTree(List
            .of(ship, highSlotModule, mediumSlotModule, cargo));

        EveCorporationAssetTreeNodeResp shipNode = tree.get(0).children().get(0).children().get(0).children().get(0);
        assertThat(shipNode.title()).isEqualTo("强制者级");
        assertThat(shipNode.kind()).isEqualTo("ship");
        EveCorporationAssetTreeNodeResp compartments = shipNode.children().get(0);
        assertThat(compartments.title()).isEqualTo("舰船分区");
        assertThat(compartments.kind()).isEqualTo("ship_compartment_group");
        assertThat(compartments.children()).extracting(EveCorporationAssetTreeNodeResp::title)
            .containsExactlyInAnyOrder("高能量槽 1", "中能量槽 1", "货仓");
        assertThat(compartments.children()).extracting(EveCorporationAssetTreeNodeResp::kind)
            .containsOnly("ship_compartment");
        EveCorporationAssetTreeNodeResp highSlot = compartments.children()
            .stream()
            .filter(item -> "高能量槽 1".equals(item.title()))
            .findFirst()
            .orElseThrow();
        EveCorporationAssetTreeNodeResp cargoCompartment = compartments.children()
            .stream()
            .filter(item -> "货仓".equals(item.title()))
            .findFirst()
            .orElseThrow();
        assertThat(highSlot.children()).singleElement()
            .extracting(EveCorporationAssetTreeNodeResp::title)
            .isEqualTo("小型聚焦增强模式激光器 I");
        assertThat(cargoCompartment.children()).singleElement()
            .extracting(EveCorporationAssetTreeNodeResp::title)
            .isEqualTo("多频晶体 S");
    }

    /** 没有装配内容的舰船仍应依赖静态市场分类显示为舰船。 */
    @Test
    void shouldIdentifyUnfittedShipByStaticCategoryInsteadOfNamePattern() {
        EveCorporationAssetDO ship = asset(161L, "未命名测试对象", "station", 60000001L, "Hangar", 30000142L, "吉他");
        ship.setTypeId(16236);

        List<EveCorporationAssetTreeNodeResp> tree = EveCorporationAssetService.buildTree(List.of(ship), Map
            .of(16236, typeReference(16236, "舰船")));

        EveCorporationAssetTreeNodeResp shipNode = tree.get(0).children().get(0).children().get(0).children().get(0);
        assertThat(shipNode.kind()).isEqualTo("ship");
    }

    /** 军团办公室与资产安全包裹应按机库归位，不能显示为泛化物品箱。 */
    @Test
    void shouldKeepOfficeAndAssetSafetyPackageSemantics() {
        EveCorporationAssetDO office = asset(171L, "军团办公室", "station", 60000001L, "OfficeFolder", 30000142L, "吉他");
        office.setTypeId(27);
        EveCorporationAssetDO officeItem = asset(172L, "三钛合金", "item", 171L, "CorpSAG4", null, null);
        EveCorporationAssetDO packageNode = asset(173L, "资产安全包裹", "station", 60000001L, "AssetSafety", 30000142L, "吉他");
        packageNode.setTypeId(60);
        EveCorporationAssetDO recoveredShip = asset(174L, "未命名测试对象", "item", 173L, "CorpSAG1", null, null);
        recoveredShip.setTypeId(16236);

        List<EveCorporationAssetTreeNodeResp> tree = EveCorporationAssetService.buildTree(List
            .of(office, officeItem, packageNode, recoveredShip), Map.of(16236, typeReference(16236, "舰船")));

        EveCorporationAssetTreeNodeResp station = tree.get(0).children().get(0);
        EveCorporationAssetTreeNodeResp officeNode = station.children().get(0).children().get(0);
        EveCorporationAssetTreeNodeResp packageTreeNode = station.children().get(1).children().get(0);
        assertThat(officeNode.kind()).isEqualTo("corporation_office");
        assertThat(officeNode.children()).singleElement()
            .extracting(EveCorporationAssetTreeNodeResp::kind)
            .isEqualTo("corporation_hangar");
        assertThat(packageTreeNode.kind()).isEqualTo("asset_safety_package");
        assertThat(packageTreeNode.children()).singleElement()
            .extracting(EveCorporationAssetTreeNodeResp::kind)
            .isEqualTo("asset_safety_origin");
    }

    /** 已确认格式的 NPC 空间站和固定仓位应使用中文标题，未知内容不能猜测翻译。 */
    @Test
    void shouldLocalizeVerifiedNpcStationAndWarehouseNames() {
        EveCorporationAssetDO asset = asset(201L, "三钛合金", "station", 60001738L, "CorpSAG3", 30001421L, "奥塔列托");
        asset.setLocationName("Otalieto 7 - Moon 2 - Caldari Steel Warehouse");

        List<EveCorporationAssetTreeNodeResp> tree = EveCorporationAssetService.buildTree(List.of(asset));

        assertThat(tree).singleElement().extracting(EveCorporationAssetTreeNodeResp::title).isEqualTo("奥塔列托");
        assertThat(tree.get(0).children()).singleElement()
            .extracting(EveCorporationAssetTreeNodeResp::title)
            .isEqualTo("奥塔列托 7 - 卫星 2 - 加达里钢铁仓库");
        assertThat(tree.get(0).children().get(0).children()).singleElement()
            .extracting(EveCorporationAssetTreeNodeResp::title)
            .isEqualTo("军团机库 3");
    }

    /** 带零基序号的固定槽位应改为玩家可读的一位起始中文编号。 */
    @Test
    void shouldLocalizeIndexedStorageSlotName() {
        EveCorporationAssetDO asset = asset(301L, "激光炮", "station", 60000001L, "HiSlot0", 30000142L, "吉他");

        List<EveCorporationAssetTreeNodeResp> tree = EveCorporationAssetService.buildTree(List.of(asset));

        assertThat(tree.get(0).children().get(0).children()).singleElement()
            .extracting(EveCorporationAssetTreeNodeResp::title)
            .isEqualTo("高能量槽 1");
    }

    /** 自有阿塔诺必须作为建筑节点展示，建筑自身的部署标记不能被误认为内部仓位。 */
    @Test
    void shouldPlaceCorporationStructureAboveItsActualStorageAreas() {
        EveCorporationAssetDO structure = asset(401L, "阿塔诺", "solar_system", 30001421L, "AutoFit", 30001421L, "奥塔列托");
        structure.setItemName("月矿一号");
        structure.setCorporationStructure(true);
        EveCorporationAssetDO fuel = asset(402L, "氮燃料块", "item", 401L, "StructureFuel", null, null);
        EveCorporationAssetDO core = asset(403L, "阿塔诺昇威量子芯", "item", 401L, "QuantumCoreRoom", null, null);

        List<EveCorporationAssetTreeNodeResp> tree = EveCorporationAssetService.buildTree(List
            .of(structure, fuel, core));

        EveCorporationAssetTreeNodeResp building = tree.get(0).children().get(0);
        assertThat(building.title()).isEqualTo("月矿一号");
        assertThat(building.kind()).isEqualTo("corporation_structure");
        EveCorporationAssetTreeNodeResp compartments = building.children().get(0);
        assertThat(compartments.title()).isEqualTo("建筑分区");
        assertThat(compartments.kind()).isEqualTo("structure_compartment_group");
        assertThat(compartments.children()).extracting(EveCorporationAssetTreeNodeResp::title)
            .containsExactly("建筑燃料仓", "量子核心仓");
        assertThat(compartments.children()).extracting(EveCorporationAssetTreeNodeResp::kind)
            .containsOnly("structure_compartment");
    }

    /** 创建用于层级测试的最小资产快照条目。 */
    private static EveCorporationAssetDO asset(Long itemId,
                                               String typeName,
                                               String locationType,
                                               Long locationId,
                                               String locationFlag,
                                               Long solarSystemId,
                                               String solarSystemName) {
        EveCorporationAssetDO asset = new EveCorporationAssetDO();
        asset.setItemId(itemId);
        asset.setTypeId(34);
        asset.setTypeName(typeName);
        asset.setLocationType(locationType);
        asset.setLocationId(locationId);
        asset.setLocationFlag(locationFlag);
        asset.setQuantity(1);
        asset.setSolarSystemId(solarSystemId);
        asset.setSolarSystemName(solarSystemName);
        asset.setLocationName("吉他 IV - 月面 4 - 加达里海军物流支援站");
        return asset;
    }

    /** 创建最小静态类型资料，用于验证树不依赖类型名称推断。 */
    private static EveStaticTypeReferenceDO typeReference(int typeId, String marketCategoryL1) {
        EveStaticTypeReferenceDO reference = new EveStaticTypeReferenceDO();
        reference.setTypeId(typeId);
        reference.setMarketCategoryL1(marketCategoryL1);
        return reference;
    }
}
