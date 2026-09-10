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

    /** 舰船仓舱和装配必须分离，已装配模块不能与货仓物品一起展示。 */
    @Test
    void shouldSeparateShipStorageFromFittings() {
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
        assertThat(shipNode.children()).extracting(EveCorporationAssetTreeNodeResp::title)
            .containsExactlyInAnyOrder("舰船仓舱", "装配");
        EveCorporationAssetTreeNodeResp storage = childByTitle(shipNode, "舰船仓舱");
        EveCorporationAssetTreeNodeResp fitting = childByTitle(shipNode, "装配");
        assertThat(storage.kind()).isEqualTo("ship_storage_group");
        assertThat(fitting.kind()).isEqualTo("ship_fitting_group");
        assertThat(storage.children()).extracting(EveCorporationAssetTreeNodeResp::title).containsExactly("货仓");
        assertThat(fitting.children()).extracting(EveCorporationAssetTreeNodeResp::title)
            .containsExactlyInAnyOrder("高能量槽 1", "中能量槽 1");
        EveCorporationAssetTreeNodeResp highSlot = childByTitle(fitting, "高能量槽 1");
        EveCorporationAssetTreeNodeResp cargoCompartment = childByTitle(storage, "货仓");
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

    /** 军团资产接口中的 CorpSAG 编号必须使用国服返回的玩家自定义机库名称。 */
    @Test
    void shouldUseCorporationCustomHangarNameInsteadOfInternalDivisionNumber() {
        EveCorporationAssetDO asset = asset(251L, "三钛合金", "station", 60001738L, "CorpSAG3", 30001421L, "奥塔列托");

        List<EveCorporationAssetTreeNodeResp> tree = EveCorporationAssetService.buildTree(List.of(asset), Map.of(), Map
            .of(3, "工业材料库"));

        assertThat(tree.get(0).children().get(0).children()).singleElement()
            .extracting(EveCorporationAssetTreeNodeResp::title)
            .isEqualTo("工业材料库");
    }

    /** 有物品的军团机库和空机库均应显示游戏内配置的全部自定义分区名称。 */
    @Test
    void shouldKeepEmptyCorporationHangarsVisibleWithCustomNames() {
        EveCorporationAssetDO office = asset(271L, "军团办公室", "station", 60000001L, "OfficeFolder", 30000142L, "吉他");
        office.setTypeId(27);
        EveCorporationAssetDO asset = asset(272L, "三钛合金", "item", 271L, "CorpSAG3", null, null);

        List<EveCorporationAssetTreeNodeResp> tree = EveCorporationAssetService.buildTree(List.of(office, asset), Map
            .of(), Map.of(1, "军备库", 3, "工业材料库", 7, "外交物资库"));

        EveCorporationAssetTreeNodeResp officeNode = tree.get(0).children().get(0).children().get(0).children().get(0);
        assertThat(officeNode.children()).extracting(EveCorporationAssetTreeNodeResp::title)
            .containsExactlyInAnyOrder("军备库", "工业材料库", "外交物资库");
        assertThat(childByTitle(officeNode, "工业材料库").children()).singleElement()
            .extracting(EveCorporationAssetTreeNodeResp::title)
            .isEqualTo("三钛合金");
        assertThat(childByTitle(officeNode, "军备库").children()).isEmpty();
    }

    /** 空间站根级军团机库应归入同站办公室，不能与办公室分支重复展示。 */
    @Test
    void shouldAttachStationHangarsToOfficeInsteadOfRenderingDuplicateRootBranch() {
        EveCorporationAssetDO office = asset(281L, "军团办公室", "station", 60000001L, "OfficeFolder", 30000142L, "吉他");
        office.setTypeId(27);
        EveCorporationAssetDO securityHangar = asset(282L, "三钛合金", "station", 60000001L, "CorpSAG1", 30000142L, "吉他");
        EveCorporationAssetDO productionHangar = asset(283L, "斜长岩", "station", 60000001L, "CorpSAG3", 30000142L, "吉他");

        List<EveCorporationAssetTreeNodeResp> tree = EveCorporationAssetService.buildTree(List
            .of(office, securityHangar, productionHangar), Map.of(), Map.of(1, "安全环保部", 3, "生产技术部"));

        EveCorporationAssetTreeNodeResp station = tree.get(0).children().get(0);
        assertThat(station.children()).singleElement()
            .extracting(EveCorporationAssetTreeNodeResp::title)
            .isEqualTo("办公室");
        EveCorporationAssetTreeNodeResp officeNode = station.children().get(0).children().get(0);
        assertThat(officeNode.children()).extracting(EveCorporationAssetTreeNodeResp::title)
            .containsExactlyInAnyOrder("安全环保部", "生产技术部");
        assertThat(childByTitle(officeNode, "安全环保部").children()).singleElement()
            .extracting(EveCorporationAssetTreeNodeResp::title)
            .isEqualTo("三钛合金");
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

    /** 自有阿塔诺必须区分仓库、军团机库和建筑装备，建筑自身的部署标记不是内部仓位。 */
    @Test
    void shouldSeparateCorporationStructureStorageHangarsAndFittings() {
        EveCorporationAssetDO structure = asset(401L, "阿塔诺", "solar_system", 30001421L, "AutoFit", 30001421L, "奥塔列托");
        structure.setItemName("月矿一号");
        structure.setCorporationStructure(true);
        EveCorporationAssetDO fuel = asset(402L, "氮燃料块", "item", 401L, "StructureFuel", null, null);
        EveCorporationAssetDO core = asset(403L, "阿塔诺昇威量子芯", "item", 401L, "QuantumCoreRoom", null, null);
        EveCorporationAssetDO service = asset(404L, "月矿钻", "item", 401L, "ServiceSlot0", null, null);
        EveCorporationAssetDO privateStorage = asset(405L, "三钛合金", "item", 401L, "Hangar", null, null);
        EveCorporationAssetDO corporationHangar = asset(406L, "斜长岩", "item", 401L, "CorpSAG1", null, null);
        EveCorporationAssetDO highSlot = asset(407L, "建筑防御炮", "item", 401L, "HiSlot0", null, null);
        EveCorporationAssetDO cargo = asset(408L, "建筑备用物资", "item", 401L, "Cargo", null, null);
        EveCorporationAssetDO fighterTube = asset(409L, "铁骑无人机", "item", 401L, "FighterTube0", null, null);

        List<EveCorporationAssetTreeNodeResp> tree = EveCorporationAssetService.buildTree(List
            .of(structure, fuel, core, service, privateStorage, corporationHangar, highSlot, cargo, fighterTube));

        EveCorporationAssetTreeNodeResp building = tree.get(0).children().get(0);
        assertThat(building.title()).isEqualTo("月矿一号");
        assertThat(building.kind()).isEqualTo("corporation_structure");
        assertThat(building.children()).extracting(EveCorporationAssetTreeNodeResp::title)
            .containsExactlyInAnyOrder("仓库", "军团机库", "建筑装备");
        EveCorporationAssetTreeNodeResp storage = childByTitle(building, "仓库");
        EveCorporationAssetTreeNodeResp corporationHangars = childByTitle(building, "军团机库");
        EveCorporationAssetTreeNodeResp fittings = childByTitle(building, "建筑装备");
        assertThat(storage.kind()).isEqualTo("structure_storage_group");
        assertThat(corporationHangars.kind()).isEqualTo("structure_corporation_hangar_group");
        assertThat(fittings.kind()).isEqualTo("structure_fitting_group");
        assertThat(storage.children()).extracting(EveCorporationAssetTreeNodeResp::title).containsExactly("机库");
        assertThat(corporationHangars.children()).extracting(EveCorporationAssetTreeNodeResp::title)
            .containsExactly("军团机库 1");
        assertThat(fittings.children()).extracting(EveCorporationAssetTreeNodeResp::title)
            .containsExactlyInAnyOrder("建筑燃料仓", "量子核心仓", "服务槽 1", "高能量槽 1", "货仓", "铁骑发射管 1");
    }

    /** 按标题获取唯一子节点，避免测试依赖中文标题的排序顺序。 */
    private static EveCorporationAssetTreeNodeResp childByTitle(EveCorporationAssetTreeNodeResp node, String title) {
        return node.children().stream().filter(child -> title.equals(child.title())).findFirst().orElseThrow();
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
