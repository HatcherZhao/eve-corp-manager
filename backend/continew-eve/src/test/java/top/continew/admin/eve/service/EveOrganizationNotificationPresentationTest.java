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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import top.continew.admin.eve.model.EveGameNotificationPresentation;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 游戏截图校准后的建筑、军团和联盟通知回归，样本使用虚构实体名称。 */
class EveOrganizationNotificationPresentationTest {

    private final EveGameNotificationPresentationService service = new EveGameNotificationPresentationService(new EveStaticNameReference());

    /** 资源低位和耗尽必须具有不同标题，建筑 YAML 锚点不能变成业务正文。 */
    @Test
    void shouldDistinguishMagmaticGasWarningsAndResolveOwnedStructureName() {
        String content = "solarsystemID: 30000892\nstructureID: &id001 1000000000001\nstructureTypeID: 81826\n";
        var low = service.present("unknown notification type (6040)", content, Map.of(1000000000001L, "测试钻机"));
        var empty = service.present("unknown notification type (6041)", content, Map.of(1000000000001L, "测试钻机"));
        assertThat(low.title()).isEqualTo("岩浆气不足一天");
        assertThat(low.summary()).contains("测试钻机", "不足一天").doesNotContain("已耗尽");
        assertThat(empty.title()).isEqualTo("岩浆气耗尽，生产停止");
        assertThat(empty.summary()).contains("测试钻机", "已耗尽", "生产已停止");
        assertThat(empty.details().toString()).contains("C8VC-S", "星诺卫星钻机").doesNotContain("1000000000001", "&id001");
    }

    /** 国服燃料对为数量、类型，不能反读为类型 116 或需要补充 4051 个。 */
    @Test
    void shouldReadRemainingFuelQuantityBeforeType() {
        var result = service.present("StructureFuelAlert", "listOfTypesAndQty:\n- - 116\n  - 4051\n", Map.of());
        assertThat(details(result)).contains("剩余燃料=氮燃料块 × 116").doesNotContain("燃料需求", "4,051");
        assertThat(result.summary()).contains("不足以支撑一天");
        var offline = service.present("StructureServicesOffline", "listOfServiceModuleIDs:\n- 45009\n", Map.of());
        assertThat(details(offline)).contains("离线服务=屹立卫星钻探 I");
        assertThat(offline.summary()).contains("燃料已耗尽", "已停用");
    }

    /** 空申请说明需要消失，成员接受邀请要明确展示加入军团。 */
    @Test
    void shouldOmitEmptyApplicationAndDistinguishMembershipInvitation() {
        String content = "applicationText: ''\ncharID: 90000001\ncorpID: 90000002\n";
        var result = service.present("CorpAppNewMsg", content, Map.of(90000001L, "测试申请人", 90000002L, "测试军团"));
        assertThat(details(result)).contains("角色=测试申请人", "军团=测试军团").doesNotContain("申请说明", "''");
        var joined = service.present("CharAppAcceptMsg", content, Map.of());
        assertThat(joined.title()).isEqualTo("成员加入军团");
        assertThat(joined.summary()).contains("接受邀请", "加入军团");
        var invitation = service.present("CorpAppAcceptMsg", content, Map.of());
        assertThat(invitation.title()).isEqualTo("收到军团邀请");
        assertThat(invitation.summary()).isEqualTo("收到加入军团的邀请");
        var quoted = service.present("CorpAppNewMsg", "applicationText: 'It''s a test'\n", Map.of());
        assertThat(details(quoted)).contains("申请说明=It's a test");
    }

    /** 锚定剩余时长不向上取整，增强列表支持带引号和不带引号的建筑名称。 */
    @Test
    void shouldPreserveDurationsAndReinforcementStructureNames() {
        var anchoring = service
            .present("StructureAnchoring", "timeLeft: 18000000000\nvulnerableTime: 900000000000\n", Map.of());
        assertThat(details(anchoring)).contains("剩余时间=30 分钟", "易受攻击时长=1 天 1 小时");
        String content = "numStructures: 2\nhour: 17\nallStructureInfo:\n- - 1000000000001\n  - '测试建筑一'\n  - 35832\n- - 1000000000002\n  - Test Structure Two\n  - 35833\n";
        var changed = service.present("StructuresReinforcementChanged", content, Map.of());
        assertThat(details(changed)).contains("涉及建筑数=2", "涉及建筑=测试建筑一；Test Structure Two", "17:00")
            .doesNotContain("1000000000001", "35832");
    }

    /** 指挥节点绝对时间统一为北京时间，敌方节点占领与主动自毁不能混淆。 */
    @Test
    void shouldPresentSovereigntyPhasesAndBeijingDecloakTime() {
        var reinforced = service
            .present("SovStructureReinforced", "solarSystemID: 30004607\ncampaignEventType: 2\ndecloakTime: 134358414144403912\n", Map
                .of());
        assertThat(reinforced.summary()).contains("Z30S-A", "主权中心", "增强模式");
        assertThat(details(reinforced)).contains("2026年10月7日 18:10").doesNotContain("10:10");
        var destroyed = service
            .present("SovStructureDestroyed", "solarSystemID: 30004607\nstructureTypeID: 32458\n", Map.of());
        assertThat(destroyed.summary()).contains("敌方占领指挥节点", "自毁");
        var selfDestruct = service.present("SovStructureSelfDestructFinished", "structureTypeID: 32458\n", Map.of());
        assertThat(selfDestruct.summary()).doesNotContain("敌方");
        var capture = service.present("EntosisCaptureStarted", "structureTypeID: 32458\n", Map.of());
        assertThat(details(capture)).contains("60 分钟").doesNotContain("60 分钟后占领");
    }

    /** 友军规则在次日生效，开始倒计时不能提前显示为已生效。 */
    @Test
    void shouldDistinguishFriendlyFireTimerFromCompletion() {
        String content = "corpID: 90000002\ntimeFinished: 134341846940000000\n";
        var timer = service.present("CorpFriendlyFireEnableTimerStarted", content, Map.of());
        assertThat(timer.summary()).contains("将变为合法行为");
        assertThat(details(timer)).contains("2026年9月18日 13:58");
        assertThat(service.present("CorpFriendlyFireEnableTimerCompleted", content, Map.of()).summary())
            .contains("现在变为合法行为");
        assertThat(service.present("CorpFriendlyFireDisableTimerCompleted", content, Map.of()).summary())
            .contains("现在变为非法行为");
    }

    /** 账单准确显示剩余金额、用途及应缴日期，未知账单不能一律推断为办公室租赁。 */
    @Test
    void shouldPresentOfficeBillWithoutGuessingOtherBillTypes() {
        String content = "amount: 4534287\nbillTypeID: 2\ncreditorID: 90000001\ndebtorID: 90000002\ndueDate: 134395514900000000\nexternalID2: 60003976\n";
        var bill = service.present("CorpAllBillMsg", content, Map.of(90000001L, "测试开票方", 90000002L, "测试军团"));
        assertThat(details(bill)).contains("4,534,287 ISK", "办公室租期", "2026年11月19日", "测试开票方", "测试军团");
        assertThat(service.present("CorpAllBillMsg", content.replace("billTypeID: 2", "billTypeID: 999"), Map.of())
            .summary()).doesNotContain("办公室");
    }

    /** 名称必须按所有权方向、CEO 任命关系和离盟战争参与方准确对应。 */
    @Test
    void shouldPreserveOwnershipLeadershipAndWarParticipantDirections() {
        Map<Long, String> names = Map.of(90000001L, "原军团", 90000002L, "新军团", 90000003L, "原 CEO", 90000004L, "新 CEO");
        var transfer = service
            .present("OwnershipTransferred", "oldOwnerCorpID: 90000001\nnewOwnerCorpID: 90000002\n", names);
        assertThat(details(transfer)).contains("原所有者=原军团", "新所有者=新军团");
        var ceo = service.present("CorpNewCEOMsg", "oldCeoID: 90000003\nnewCeoID: 90000004\ncorpID: 90000002\n", names);
        assertThat(details(ceo)).contains("原 CEO=原 CEO", "新 CEO=新 CEO", "军团=新军团");
        var war = service
            .present("WarInherited", "quitterID: 90000001\nallianceID: 90000002\nopponentID: 90000003\n", names);
        assertThat(war.summary()).contains("原军团已离开原联盟");
        assertThat(details(war)).contains("离盟军团=原军团", "原联盟=新军团", "战争对手=原 CEO");
    }

    /** 所有本轮组织级缺失类型均有专门标题与摘要，包括类型末尾空格。 */
    @ParameterizedTest
    @ValueSource(strings = {"unknown notification type (6040)", "unknown notification type (6041)",
        "OwnershipTransferred", "StructuresReinforcementChanged", "EntosisCaptureStarted", "SovCommandNodeEventStarted",
        "SovStructureReinforced", "SovStructureDestroyed", "SovStructureSelfDestructRequested",
        "SovStructureSelfDestructFinished", "SovAllClaimAquiredMsg", "WarInherited", "WarAdopted ",
        "WarHQRemovedFromSpace", "WarInvalid", "WarSurrenderOfferMsg", "AcceptedSurrender", "CorpWarSurrenderMsg",
        "CorpAllBillMsg", "CorpNewCEOMsg", "CorpFriendlyFireEnableTimerStarted", "CorpFriendlyFireEnableTimerCompleted",
        "CorpFriendlyFireDisableTimerCompleted", "CorpAppAcceptMsg", "AllianceCapitalChanged"})
    void shouldRenderAllOrganizationTypesWithoutGenericFallback(String type) {
        var result = service.present(type, "", Map.of());
        assertThat(result.title()).isNotEqualTo("其他游戏通知");
        assertThat(result.summary()).doesNotContain("收到一条新的游戏通知", "收到一条建筑相关游戏通知");
        assertThat(result.details().toString()).doesNotContain("null");
    }

    /** 批量名称请求覆盖新增参与方和星座，建筑 ID 交给独立且带军团边界的快照查询。 */
    @Test
    void shouldCollectEntityAndStructureReferencesSeparately() {
        List<String> content = List
            .of("newCeoID: 90000001\nnewOwnerCorpID: 90000002\nconstellationID: 20000672\nstructureID: &id001 1000000000001\n");
        assertThat(service.referencedEntityIds(content)).containsExactly(90000001L, 90000002L, 20000672L);
        assertThat(service.referencedStructureIds(content)).containsExactly(1000000000001L);
    }

    /** 将详情转为可读文本，便于同时检查标签、值和敏感 ID 未出现。 */
    private static String details(EveGameNotificationPresentation result) {
        return result.details()
            .stream()
            .map(item -> item.label() + "=" + item.value())
            .reduce("", (left, right) -> left + "\n" + right);
    }
}
