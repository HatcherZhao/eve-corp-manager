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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.eve.model.EveGameNotificationDetailItem;
import top.continew.admin.eve.model.EveGameNotificationPresentation;
import top.continew.admin.eve.model.EveMoonOreItemResp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将国服 YAML 风格通知报文解析为中文摘要和详情，避免向页面泄露游戏内部 ID。
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveGameNotificationPresentationService {

    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("^(\\s*)([A-Za-z][A-Za-z0-9_]*):\\s*(.*)$");
    private static final Pattern LINK_NAME_PATTERN = Pattern.compile(">([^<]+)<");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final Pattern ID_FIELD_PATTERN = Pattern
        .compile("(?m)^(?:charID|corpID|againstID|declaredByID|cancelledBy|startedBy|firedBy|allianceID|quitterID|opponentID|oldOwnerCorpID|newOwnerCorpID|oldCeoID|newCeoID|creditorID|debtorID|ownerID1|ownerID2|entityID|offeringID|constellationID):\\s*(\\d+)");
    private static final DateTimeFormatter TICK_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm");
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern STRUCTURE_ID_PATTERN = Pattern.compile("(?m)^structureI[Dd]:\\s*(?:&\\w+\\s+)?(\\d+)");

    /** 按国服原始类型给出统一标题，前端不再各自猜测未知编号。 */
    public static String titleOf(String type) {
        return switch (type == null ? "" : type.trim()) {
            case "MoonminingExtractionStarted" -> "月矿开始提取";
            case "MoonminingExtractionFinished" -> "月矿提取完成";
            case "MoonminingAutomaticFracture" -> "月矿自然碎裂";
            case "MoonminingLaserFired" -> "月矿激光启动";
            case "MoonminingExtractionCancelled" -> "月矿提取已取消";
            case "StructureFuelAlert" -> "建筑燃料不足一天";
            case "StructureServicesOffline" -> "建筑燃料耗尽，服务停用";
            case "unknown notification type (6040)" -> "岩浆气不足一天";
            case "unknown notification type (6041)" -> "岩浆气耗尽，生产停止";
            case "StructureWentHighPower" -> "建筑进入高能状态";
            case "StructureWentLowPower" -> "建筑进入低能状态";
            case "StructureUnderAttack" -> "建筑遭受攻击";
            case "StructureImpendingAbandonmentAssetsAtRisk" -> "建筑即将废弃，资产存在风险";
            case "StructureItemsMovedToSafety" -> "建筑物资已移入资产安全";
            case "StructureAnchoring" -> "建筑开始锚定";
            case "StructureOnline" -> "建筑已上线";
            case "StructureUnanchoring" -> "建筑开始解除锚定";
            case "StructuresReinforcementChanged" -> "建筑增强时间调整";
            case "OwnershipTransferred" -> "建筑所有权转移";
            case "EntosisCaptureStarted" -> "主权建筑正被占领";
            case "SovCommandNodeEventStarted" -> "主权指挥节点开始解除隐形";
            case "SovStructureReinforced" -> "主权建筑进入增强模式";
            case "SovStructureDestroyed" -> "主权建筑被击毁";
            case "SovStructureSelfDestructRequested" -> "主权建筑自毁请求";
            case "SovStructureSelfDestructFinished" -> "主权建筑自毁完成";
            case "SovAllClaimAquiredMsg" -> "主权已获 DED 认可";
            case "AllianceCapitalChanged" -> "联盟首都变更";
            case "CorpAppNewMsg" -> "收到新的军团申请";
            case "CharAppAcceptMsg" -> "成员加入军团";
            case "CorpAppAcceptMsg" -> "收到军团邀请";
            case "CharAppWithdrawMsg" -> "军团申请已撤回";
            case "CharTerminationMsg" -> "成员退出军团";
            case "CorpTaxChangeMsg" -> "军团税率变更";
            case "CorpNewCEOMsg" -> "军团 CEO 更换";
            case "CorpAllBillMsg" -> "军团或联盟账单";
            case "CorpFriendlyFireEnableTimerStarted" -> "军团友军误伤将变为合法行为";
            case "CorpFriendlyFireEnableTimerCompleted" -> "军团友军误伤现在变为合法行为";
            case "CorpFriendlyFireDisableTimerStarted" -> "军团友军误伤将变为非法行为";
            case "CorpFriendlyFireDisableTimerCompleted" -> "军团友军误伤现在变为非法行为";
            case "WarDeclared" -> "战争已宣战";
            case "WarRetractedByConcord" -> "战争已撤销";
            case "WarInherited" -> "战争关系更新";
            case "WarAdopted" -> "联盟接管战争";
            case "WarHQRemovedFromSpace" -> "战争总部已移除";
            case "WarInvalid" -> "战争失效";
            case "WarSurrenderOfferMsg" -> "收到战争投降提议";
            case "AcceptedSurrender" -> "战争投降提议已接受";
            case "CorpWarSurrenderMsg" -> "战争投降通知";
            default -> "其他游戏通知";
        };
    }

    private static final Set<String> MOON_ORE_NOTIFICATION_TYPES = Set
        .of("MoonminingExtractionStarted", "MoonminingExtractionFinished", "MoonminingAutomaticFracture", "MoonminingLaserFired");

    private final EveStaticNameReference staticNameReference;

    /** 提取需要公开名称服务补全的角色、军团与联盟 ID。 */
    public List<Long> referencedEntityIds(Collection<String> contents) {
        List<Long> ids = new ArrayList<>();
        for (String content : contents) {
            Matcher matcher = ID_FIELD_PATTERN.matcher(decode(content));
            while (matcher.find()) {
                ids.add(Long.valueOf(matcher.group(1)));
            }
        }
        return ids.stream().distinct().toList();
    }

    /** 建筑名称只由当前租户的军团快照补全，不交给公开实体名称接口。 */
    public List<Long> referencedStructureIds(Collection<String> contents) {
        List<Long> ids = new ArrayList<>();
        for (String content : contents) {
            Matcher matcher = STRUCTURE_ID_PATTERN.matcher(decode(content));
            while (matcher.find()) {
                ids.add(Long.valueOf(matcher.group(1)));
            }
        }
        return ids.stream().distinct().toList();
    }

    /** 将一条原始通知转换为中文摘要及详情字段。 */
    public EveGameNotificationPresentation present(String type, String content, Map<Long, String> entityNames) {
        NotificationData data = NotificationData.parse(decode(content));
        LinkedHashMap<String, String> details = new LinkedHashMap<>();
        addStructureDetails(details, data, entityNames);
        String normalizedType = type == null ? "" : type.trim();
        String summary = switch (normalizedType) {
            case "MoonminingExtractionStarted" -> moon(details, data, entityNames, "月矿已开始提取", "预计可开采时间");
            case "MoonminingExtractionFinished" -> moon(details, data, entityNames, "月矿提取已完成", "自动碎裂时间");
            case "MoonminingAutomaticFracture" -> moon(details, data, entityNames, "月矿已自然碎裂", null);
            case "MoonminingLaserFired" -> moon(details, data, entityNames, "月矿激光已启动", null);
            case "MoonminingExtractionCancelled" -> moon(details, data, entityNames, "月矿提取已取消", null);
            case "StructureFuelAlert" -> fuelAlert(details, data);
            case "StructureServicesOffline" -> servicesOffline(details, data);
            case "unknown notification type (6040)" -> structureSummary(details, "建筑剩余岩浆气不足一天用量");
            case "unknown notification type (6041)" -> structureSummary(details, "建筑岩浆气已耗尽，卫星材料生产已停止");
            case "StructureUnderAttack" -> underAttack(details, data, entityNames);
            case "StructureImpendingAbandonmentAssetsAtRisk" -> abandonmentRisk(details, data);
            case "StructureItemsMovedToSafety" -> assetsMovedToSafety(details, data);
            case "StructureWentHighPower" -> structureSummary(details, "建筑已进入高能状态");
            case "StructureWentLowPower" -> structureSummary(details, "建筑已进入低能状态");
            case "StructureAnchoring" -> anchoring(details, data, "建筑开始锚定");
            case "StructureOnline" -> structureSummary(details, "建筑已上线");
            case "StructureUnanchoring" -> anchoring(details, data, "建筑开始解除锚定");
            case "StructuresReinforcementChanged" -> reinforcementChanged(details, data);
            case "OwnershipTransferred" -> ownershipTransferred(details, data, entityNames);
            case "EntosisCaptureStarted", "SovCommandNodeEventStarted", "SovStructureReinforced",
                "SovStructureDestroyed", "SovStructureSelfDestructRequested", "SovStructureSelfDestructFinished",
                "SovAllClaimAquiredMsg" -> sovereignty(details, data, entityNames, normalizedType);
            case "AllianceCapitalChanged" -> allianceCapital(details, data, entityNames);
            case "CorpAppNewMsg" -> application(details, data, entityNames, "收到新的军团申请");
            case "CharAppAcceptMsg" -> application(details, data, entityNames, "成员已接受邀请并加入军团");
            case "CorpAppAcceptMsg" -> application(details, data, entityNames, "收到加入军团的邀请");
            case "CharAppWithdrawMsg" -> application(details, data, entityNames, "军团申请已撤回");
            case "CharTerminationMsg" -> application(details, data, entityNames, "成员已退出军团");
            case "CorpTaxChangeMsg" -> taxChange(details, data, entityNames);
            case "CorpNewCEOMsg" -> ceoChanged(details, data, entityNames);
            case "CorpAllBillMsg" -> bill(details, data, entityNames);
            case "CorpFriendlyFireEnableTimerStarted", "CorpFriendlyFireEnableTimerCompleted",
                "CorpFriendlyFireDisableTimerStarted", "CorpFriendlyFireDisableTimerCompleted" ->
                friendlyFire(details, data, entityNames, normalizedType);
            case "WarDeclared" -> warDeclared(details, data, entityNames);
            case "WarRetractedByConcord" -> warRetracted(details, data, entityNames);
            case "WarInherited", "WarAdopted", "WarHQRemovedFromSpace", "WarInvalid", "WarSurrenderOfferMsg",
                "AcceptedSurrender", "CorpWarSurrenderMsg" -> warUpdate(details, data, entityNames, normalizedType);
            default -> generic(details, data, entityNames);
        };
        return new EveGameNotificationPresentation(titleOf(normalizedType), summary, details.entrySet()
            .stream()
            .map(item -> new EveGameNotificationDetailItem(item.getKey(), item.getValue()))
            .toList());
    }

    /**
     * 从支持的月矿通知中提取可长期保存的月球、建筑、矿种及归一化产出比例。
     *
     * <p>通知内容属于不可信文本；缺少月球、矿种或正体积时返回空，调用方不得据此覆盖已有档案。</p>
     */
    public MoonOreNotification parseMoonOreNotification(String type, String content) {
        if (type == null || !MOON_ORE_NOTIFICATION_TYPES.contains(type)) {
            return null;
        }
        try {
            NotificationData data = NotificationData.parse(decode(content));
            Long moonId = firstNonNull(data.longValue("moonID"), data.longValue("moonId"));
            if (moonId == null || moonId <= 0) {
                return null;
            }
            Long structureId = firstNonNull(data.longValue("structureID"), data.longValue("structureId"));
            // 显式但无效的建筑标识不能退化为仅按月球匹配。
            if ((data.value("structureID") != null || data
                .value("structureId") != null) && (structureId == null || structureId <= 0)) {
                return null;
            }
            List<ParsedOreVolume> volumes = new ArrayList<>();
            for (Map.Entry<String, String> item : data.children("oreVolumeByType").entrySet()) {
                Integer typeId = positiveInteger(item.getKey());
                BigDecimal volume = notificationVolume(item.getValue());
                if (typeId == null || volume == null) {
                    return null;
                }
                if (volume.signum() == 0) {
                    continue;
                }
                String typeName = staticNameReference.findTypeName(typeId.longValue());
                if (typeName == null || typeName.isBlank()) {
                    typeName = "未知矿物（类型 " + typeId + "）";
                }
                volumes.add(new ParsedOreVolume(typeId, typeName, volume));
            }
            if (volumes.isEmpty()) {
                return null;
            }
            volumes.sort(java.util.Comparator.comparing(ParsedOreVolume::typeId));
            BigDecimal total = volumes.stream().map(ParsedOreVolume::volume).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<EveMoonOreItemResp> ores = new ArrayList<>();
            BigDecimal allocated = BigDecimal.ZERO;
            for (int index = 0; index < volumes.size(); index++) {
                ParsedOreVolume volume = volumes.get(index);
                BigDecimal proportion = index == volumes.size() - 1
                    ? BigDecimal.ONE.subtract(allocated)
                    : volume.volume().divide(total, 10, RoundingMode.DOWN);
                ores.add(new EveMoonOreItemResp(volume.typeId(), volume.typeName(), proportion));
                allocated = allocated.add(proportion);
            }
            return new MoonOreNotification(moonId, structureId, List.copyOf(ores));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** 补充建筑名称、建筑类型和星系等所有建筑类通知共享的信息。 */
    private void addStructureDetails(Map<String, String> details, NotificationData data, Map<Long, String> names) {
        String structureName = firstNonBlank(data.value("structureName"), linkName(data
            .value("structureLink")), firstEntityName(data, names, "structureID", "structureId"));
        if (structureName == null) {
            Long id = firstNonNull(data.longValue("structureID"), data.longValue("structureId"));
            EveStaticNameReference.StaticLocation location = id == null
                ? null
                : staticNameReference.findPublicStructure(id);
            structureName = location == null ? null : location.name();
        }
        if (structureName != null) {
            details.put("建筑", structureName);
        }
        Long typeId = data.longValue("structureTypeID");
        String typeName = typeId == null ? null : staticNameReference.findTypeName(typeId);
        if (typeName != null) {
            details.put("建筑类型", typeName);
        }
        Long solarSystemId = firstNonNull(data.longValue("solarSystemID"), data.longValue("solarsystemID"));
        String systemName = solarSystemId == null ? null : staticNameReference.findSolarSystemName(solarSystemId);
        if (systemName != null) {
            details.put("所在星系", systemName);
        }
    }

    /** 解析月矿通知中共同的操作者、矿物和时间信息。 */
    private String moon(Map<String, String> details,
                        NotificationData data,
                        Map<Long, String> entityNames,
                        String action,
                        String timeLabel) {
        String actor = firstNonBlank(linkName(data.value("startedByLink")), linkName(data
            .value("firedByLink")), linkName(data.value("cancelledByLink")));
        if (actor == null) {
            actor = firstEntityName(data, entityNames, "startedBy", "firedBy", "cancelledBy");
        }
        if (actor != null) {
            details.put("操作人", actor);
        }
        addOreDetails(details, data);
        if (timeLabel != null) {
            Long ticks = "预计可开采时间".equals(timeLabel) ? data.longValue("readyTime") : data.longValue("autoTime");
            String time = tickTime(ticks);
            if (time != null) {
                details.put(timeLabel, time);
            }
        }
        if ("预计可开采时间".equals(timeLabel)) {
            putTime(details, "自动碎裂时间", data.longValue("autoTime"));
        }
        return structureSummary(details, action);
    }

    /** 将矿物类型 ID 和体积转换为名称清单。 */
    private void addOreDetails(Map<String, String> details, NotificationData data) {
        List<String> ores = data.children("oreVolumeByType").entrySet().stream().map(item -> {
            try {
                String name = staticNameReference.findTypeName(Long.valueOf(item.getKey()));
                return name == null ? null : name + " " + number(item.getValue()) + " m³";
            } catch (NumberFormatException ignored) {
                return null;
            }
        }).filter(item -> item != null).toList();
        if (!ores.isEmpty()) {
            details.put("矿物", String.join("；", ores));
        }
    }

    /** 展示游戏报告的剩余燃料，不能将当前数量误解为需要补充的数量。 */
    private String fuelAlert(Map<String, String> details, NotificationData data) {
        List<String> items = data.pairs("listOfTypesAndQty").stream().map(pair -> {
            String typeName = staticNameReference.findTypeName(pair.typeId());
            return typeName == null ? null : typeName + " × " + number(pair.quantity());
        }).filter(item -> item != null).toList();
        if (!items.isEmpty()) {
            details.put("剩余燃料", String.join("；", items));
        }
        return structureSummary(details, "建筑剩余燃料不足以支撑一天服务装备运转");
    }

    /** 展示当前离线的建筑服务模块。 */
    private String servicesOffline(Map<String, String> details, NotificationData data) {
        List<String> names = data.list("listOfServiceModuleIDs")
            .stream()
            .map(id -> staticNameReference.findTypeName(id))
            .filter(item -> item != null)
            .toList();
        if (!names.isEmpty()) {
            details.put("离线服务", String.join("；", names));
        }
        return structureSummary(details, "建筑燃料已耗尽，服务装备已停用");
    }

    /** 展示建筑攻击者和护盾、装甲、结构的剩余比例。 */
    private String underAttack(Map<String, String> details, NotificationData data, Map<Long, String> entityNames) {
        String attacker = firstNonBlank(data.value("corpName"), data
            .value("allianceName"), firstEntityName(data, entityNames, "charID"));
        if (attacker != null) {
            details.put("攻击方", attacker);
        }
        addPercentage(details, "护盾", data.value("shieldPercentage"));
        addPercentage(details, "装甲", data.value("armorPercentage"));
        addPercentage(details, "结构", data.value("hullPercentage"));
        return structureSummary(details, "建筑正在遭受攻击");
    }

    /** 展示建筑废弃前的剩余天数。 */
    private String abandonmentRisk(Map<String, String> details, NotificationData data) {
        Long days = data.longValue("daysUntilAbandon");
        if (days != null) {
            details.put("剩余时间", days + " 天");
        }
        return structureSummary(details, "建筑即将废弃，资产存在风险");
    }

    /** 展示资产安全的最短与完整转移时间。 */
    private String assetsMovedToSafety(Map<String, String> details, NotificationData data) {
        addDuration(details, "最短取回时间", data.longValue("assetSafetyDurationMinimum"));
        addDuration(details, "完整取回时间", data.longValue("assetSafetyDurationFull"));
        return structureSummary(details, "建筑物资已移入资产安全");
    }

    /** 展示锚定过程原文提供的剩余时长，不将持续时间当作绝对时间。 */
    private String anchoring(Map<String, String> details, NotificationData data, String action) {
        addDuration(details, "剩余时间", data.longValue("timeLeft"));
        addDuration(details, "易受攻击时长", data.longValue("vulnerableTime"));
        return structureSummary(details, action);
    }

    /** 展示增强时段及受影响建筑，未确认的星期编码不猜测含义。 */
    private String reinforcementChanged(Map<String, String> details, NotificationData data) {
        Long count = data.longValue("numStructures");
        put(details, "涉及建筑数", count == null ? null : count.toString());
        put(details, "涉及建筑", String.join("；", data.structureNames()));
        Long hour = data.longValue("hour");
        if (hour != null && hour >= 0 && hour <= 23) {
            details.put("增强时段（UTC）", String.format("%02d:00", hour));
        }
        putTime(details, "调整时间（北京时间）", data.longValue("timestamp"));
        return "建筑增强时段已调整";
    }

    /** 保留所有权转出和转入方向，名称缺失时仍展示安全的事件结果。 */
    private String ownershipTransferred(Map<String, String> details, NotificationData data, Map<Long, String> names) {
        putEntity(details, "操作人", data, names, "charID");
        putEntity(details, "原所有者", data, names, "oldOwnerCorpID");
        putEntity(details, "新所有者", data, names, "newOwnerCorpID");
        return structureSummary(details, "建筑所有权已转移") + (details.get("新所有者") == null
            ? ""
            : "，新所有者为「" + details.get("新所有者") + "」");
    }

    /** 将主权攻防阶段、主动自毁和 DED 认可分开，并仅解释已知战役类型。 */
    private String sovereignty(Map<String, String> details,
                               NotificationData data,
                               Map<Long, String> names,
                               String type) {
        String building = details.get("建筑类型");
        if (building == null && Long.valueOf(2).equals(data.longValue("campaignEventType"))) {
            building = "主权中心";
        }
        if (building == null) {
            building = "主权建筑";
        }
        details.put("主权建筑", building);
        putEntity(details, "所属联盟", data, names, "allianceID");
        putEntity(details, "相关军团", data, names, "corpID");
        putEntity(details, "操作人", data, names, "charID");
        put(details, "操作军团", data.value("corpName"));
        putEntity(details, "节点所在星座", data, names, "constellationID");
        putTime(details, "指挥节点解除隐形时间（北京时间）", data.longValue("decloakTime"));
        putTime(details, "自毁时间（北京时间）", data.longValue("destructTime"));
        String action = switch (type) {
            case "EntosisCaptureStarted" -> "正被占领，已有飞行员开始使用侵噬链接";
            case "SovCommandNodeEventStarted" -> "的指挥节点开始解除隐形，可使用侵噬链接占领";
            case "SovStructureReinforced" -> "已进入增强模式";
            case "SovStructureDestroyed" -> "被击毁，敌方占领指挥节点后建筑已自毁";
            case "SovStructureSelfDestructRequested" -> "已提交自毁请求";
            case "SovStructureSelfDestructFinished" -> "自毁已完成";
            default -> "的主权宣称已获得 DED 认可";
        };
        if ("EntosisCaptureStarted".equals(type)) {
            details.put("通知间隔", "60 分钟内或联盟成功保卫建筑前，不再发送同类侵噬链接警报");
        }
        String system = details.get("所在星系");
        return (system == null ? "" : system + " 星系的") + building + action;
    }

    /** 展示联盟和新首都，原文未提供旧首都或加成数值时不补造。 */
    private String allianceCapital(Map<String, String> details, NotificationData data, Map<Long, String> names) {
        putEntity(details, "联盟", data, names, "allianceID");
        String system = details.get("所在星系");
        return (system == null ? "联盟首都已变更" : system + " 已成为联盟首都") + "，联盟主权建筑享受防御加成";
    }

    /** 分别展示原 CEO、新 CEO 和军团名称，避免把角色名称当作职位。 */
    private String ceoChanged(Map<String, String> details, NotificationData data, Map<Long, String> names) {
        putEntity(details, "军团", data, names, "corpID");
        putEntity(details, "原 CEO", data, names, "oldCeoID");
        putEntity(details, "新 CEO", data, names, "newCeoID");
        return details.get("新 CEO") == null ? "军团 CEO 已更换" : "「" + details.get("新 CEO") + "」已成为军团新 CEO";
    }

    /** 按已确认的办公室租赁账单解释用途，其余账单保留可验证金额和时间。 */
    private String bill(Map<String, String> details, NotificationData data, Map<Long, String> names) {
        putEntity(details, "开票方", data, names, "creditorID");
        putEntity(details, "缴费方", data, names, "debtorID");
        String amount = data.value("amount");
        put(details, "金额", amount == null ? null : number(amount) + " ISK");
        putTime(details, "开票时间（北京时间）", data.longValue("currentDate"));
        putTime(details, "缴费截止时间（北京时间）", data.longValue("dueDate"));
        Long stationId = data.longValue("externalID2");
        EveStaticNameReference.StaticLocation station = stationId == null
            ? null
            : staticNameReference.findNpcStation(stationId);
        put(details, "空间站", station == null ? null : station.name());
        boolean office = Long.valueOf(2).equals(data.longValue("billTypeID"));
        if (office) {
            details.put("账单用途", "延长军团办公室租期");
        }
        return (office ? "已发出军团办公室租赁账单" : "已发出军团或联盟账单") + (amount == null ? "" : "，金额 " + number(amount) + " ISK");
    }

    /** 将友军误伤规则的变更倒计时与已经生效的状态分开展示。 */
    private String friendlyFire(Map<String, String> details,
                                NotificationData data,
                                Map<Long, String> names,
                                String type) {
        putEntity(details, "军团", data, names, "corpID");
        putEntity(details, "操作人", data, names, "charID");
        boolean enable = type.contains("Enable");
        boolean pending = type.endsWith("Started");
        details.put("目标规则", enable ? "军团成员之间的攻击为合法行为，统合部不干预" : "军团成员之间的攻击为非法行为");
        putTime(details, "生效时间（北京时间）", data.longValue("timeFinished"));
        return "军团友军误伤" + (pending ? "将变为" : "现在变为") + (enable ? "合法行为" : "非法行为");
    }

    /** 战争关系更新展示参与方与有效时间，不凭缺失字段推断战争结束或费用。 */
    private String warUpdate(Map<String, String> details, NotificationData data, Map<Long, String> names, String type) {
        putEntity(details, "宣战方", data, names, "declaredByID");
        putEntity(details, "被宣战方", data, names, "againstID");
        putEntity(details, "原联盟", data, names, "allianceID");
        putEntity(details, "离盟军团", data, names, "quitterID");
        putEntity(details, "战争对手", data, names, "opponentID");
        putEntity(details, "提议方", data, names, "ownerID1", "offeringID");
        putEntity(details, "接收方", data, names, "ownerID2", "entityID");
        putEntity(details, "操作人", data, names, "charID");
        put(details, "战争总部", stripHtml(data.value("warHQ")));
        putTime(details, "宣战时间（北京时间）", data.longValue("timeDeclared"));
        putTime(details, "结束时间（北京时间）", data.longValue("endDate"));
        String isk = data.value("iskValue");
        put(details, "投降金额", isk == null ? null : number(isk) + " ISK");
        if ("WarInherited".equals(type)) {
            return (details.get("离盟军团") == null ? "军团" : details.get("离盟军团")) + "已离开原联盟，与原联盟战争对手之间的战争关系已更新";
        }
        return switch (type) {
            case "WarAdopted" -> "联盟已为成员接管战争";
            case "WarHQRemovedFromSpace" -> "战争总部已从太空移除";
            case "WarInvalid" -> "统合部宣布战争无效";
            case "WarSurrenderOfferMsg" -> "收到战争投降提议";
            case "AcceptedSurrender" -> "战争投降提议已接受";
            default -> "军团战争发生投降";
        };
    }

    /** 只加入已解析的非空文字，不向业务详情暴露 ID 或空值占位符。 */
    private static void put(Map<String, String> details, String label, String value) {
        if (value != null && !value.isBlank()) {
            details.put(label, value);
        }
    }

    /** 从受控实体名称映射中加入详情，解析失败时安全省略。 */
    private static void putEntity(Map<String, String> details,
                                  String label,
                                  NotificationData data,
                                  Map<Long, String> names,
                                  String... keys) {
        put(details, label, firstEntityName(data, names, keys));
    }

    /** 统一把绝对 ticks 时间转换成北京时间并省略无效时间。 */
    private static void putTime(Map<String, String> details, String label, Long ticks) {
        put(details, label, tickTime(ticks));
    }

    /** 将军团成员类通知转换为名称和可读结果。 */
    private String application(Map<String, String> details,
                               NotificationData data,
                               Map<Long, String> entityNames,
                               String summary) {
        String character = firstEntityName(data, entityNames, "charID");
        if (character != null) {
            details.put("角色", character);
        }
        String corporation = firstEntityName(data, entityNames, "corpID");
        if (corporation != null) {
            details.put("军团", corporation);
        }
        String applicationText = data.value("applicationText");
        if (applicationText != null && !applicationText.isBlank()) {
            details.put("申请说明", applicationText);
        }
        return summary;
    }

    /** 显示军团税率变更的前后值。 */
    private String taxChange(Map<String, String> details, NotificationData data, Map<Long, String> entityNames) {
        String corporation = firstEntityName(data, entityNames, "corpID");
        if (corporation != null) {
            details.put("军团", corporation);
        }
        String oldRate = percentage(data.value("oldTaxRate"));
        String newRate = percentage(data.value("newTaxRate"));
        if (oldRate != null) {
            details.put("原税率", oldRate);
        }
        if (newRate != null) {
            details.put("新税率", newRate);
        }
        return oldRate != null && newRate != null ? "军团税率已从 " + oldRate + " 调整为 " + newRate : "军团税率已变更";
    }

    /** 展示战争发起方、总部与生效时间。 */
    private String warDeclared(Map<String, String> details, NotificationData data, Map<Long, String> entityNames) {
        String declaredBy = firstEntityName(data, entityNames, "declaredByID");
        if (declaredBy != null) {
            details.put("宣战方", declaredBy);
        }
        String headquarters = stripHtml(data.value("warHQ"));
        if (headquarters != null) {
            details.put("战争总部", headquarters);
        }
        String startsAt = tickTime(data.longValue("timeStarted"));
        if (startsAt != null) {
            details.put("生效时间", startsAt);
        }
        Long delayHours = data.longValue("delayHours");
        if (delayHours != null) {
            details.put("延迟生效", delayHours + " 小时");
        }
        return declaredBy == null ? "新的战争已宣战" : declaredBy + " 已发起战争";
    }

    /** 展示被系统撤销的战争及其结束时间。 */
    private String warRetracted(Map<String, String> details, NotificationData data, Map<Long, String> entityNames) {
        String declaredBy = firstEntityName(data, entityNames, "declaredByID");
        if (declaredBy != null) {
            details.put("原宣战方", declaredBy);
        }
        String endAt = tickTime(data.longValue("endDate"));
        if (endAt != null) {
            details.put("结束时间", endAt);
        }
        return "战争已被 CONCORD 撤销";
    }

    /** 未识别的未来通知仅展示已能安全解释的字段，内部 ID 一律隐藏。 */
    private String generic(Map<String, String> details, NotificationData data, Map<Long, String> entityNames) {
        String owner = firstNonBlank(data.value("ownerCorpName"), data
            .value("corpName"), firstEntityName(data, entityNames, "corpID"));
        if (owner != null) {
            details.put("相关军团", owner);
        }
        return details.keySet().stream().anyMatch(key -> key.startsWith("建筑")) ? "收到一条建筑相关游戏通知" : "收到一条新的游戏通知";
    }

    /** 以建筑名称优先生成简短摘要。 */
    private static String structureSummary(Map<String, String> details, String action) {
        String structure = details.get("建筑");
        return structure == null ? action : "建筑「" + structure + "」" + action.substring(2);
    }

    private static void addPercentage(Map<String, String> details, String label, String value) {
        String percentage = percentage(value);
        if (percentage != null) {
            details.put(label, percentage);
        }
    }

    /** 持续时间保留天、小时和分钟，避免不足一小时被夸大为一小时。 */
    private static void addDuration(Map<String, String> details, String label, Long ticks) {
        if (ticks == null || ticks <= 0) {
            return;
        }
        long minutes = ticks / 600_000_000L;
        List<String> parts = new ArrayList<>();
        if (minutes >= 1440) {
            parts.add(minutes / 1440 + " 天");
        }
        if (minutes % 1440 / 60 > 0) {
            parts.add(minutes % 1440 / 60 + " 小时");
        }
        if (minutes % 60 > 0) {
            parts.add(minutes % 60 + " 分钟");
        }
        details.put(label, parts.isEmpty() ? "不足 1 分钟" : String.join(" ", parts));
    }

    private static String firstEntityName(NotificationData data, Map<Long, String> entityNames, String... keys) {
        for (String key : keys) {
            Long id = data.longValue(key);
            if (id != null && entityNames.get(id) != null) {
                return entityNames.get(id);
            }
        }
        return null;
    }

    private static String percentage(String value) {
        return value == null || !NUMBER_PATTERN.matcher(value).matches() ? null : number(value) + "%";
    }

    private static String number(String value) {
        try {
            // DecimalFormat 不是线程安全对象，通知请求并发时使用局部实例。
            return new DecimalFormat("#,##0.##").format(new BigDecimal(value));
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    /** 国服时间使用以 1601 年为基准的 ticks，每 tick 为 100ns。 */
    private static String tickTime(Long ticks) {
        if (ticks == null || ticks <= 0) {
            return null;
        }
        try {
            return LocalDateTime.of(1601, 1, 1, 0, 0)
                .plusSeconds(ticks / 10_000_000L)
                .plusNanos((ticks % 10_000_000L) * 100L)
                .atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(DISPLAY_ZONE)
                .format(TICK_TIME_FORMATTER);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** 解析正整数物品类型 ID。 */
    private static Integer positiveInteger(String value) {
        try {
            int result = Integer.parseInt(value);
            return result > 0 ? result : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** 解析有限精度的非负通知体积；零量后续忽略，负数或异常值拒绝整条通知。 */
    private static BigDecimal notificationVolume(String value) {
        try {
            if (value == null || value.length() > 80) {
                return null;
            }
            BigDecimal result = new BigDecimal(value);
            return result.signum() >= 0 && result.precision() <= 38 && Math.abs(result.scale()) <= 18 ? result : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String linkName(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = LINK_NAME_PATTERN.matcher(value);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static String stripHtml(String value) {
        return value == null ? null : value.replaceAll("<[^>]*>", "").trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /** 解码国服正文中保存的 JSON 转义字符，不执行或信任其中的 HTML。 */
    private static String decode(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        StringBuilder output = new StringBuilder(content.length());
        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (current == '\\' && index + 1 < content.length()) {
                char next = content.charAt(index + 1);
                if (next == 'u' && index + 5 < content.length()) {
                    try {
                        output.append((char)Integer.parseInt(content.substring(index + 2, index + 6), 16));
                        index += 5;
                        continue;
                    } catch (NumberFormatException ignored) {
                        // 保留无法识别的转义序列，后续按普通文本处理。
                    }
                }
                if (next == '"' || next == '\\') {
                    output.append(next);
                    index++;
                    continue;
                }
            }
            output.append(current);
        }
        return output.toString();
    }

    /** 只解析国服通知需要的扁平 YAML 子集，未知复杂结构会安全忽略。 */
    private static final class NotificationData {

        private final Map<String, String> values = new LinkedHashMap<>();
        private final Map<String, Map<String, String>> children = new LinkedHashMap<>();
        private final Map<String, List<String>> lists = new LinkedHashMap<>();
        private final Map<String, List<TypeQuantity>> pairs = new LinkedHashMap<>();
        private final Map<String, List<String>> rawChildren = new LinkedHashMap<>();

        static NotificationData parse(String content) {
            NotificationData data = new NotificationData();
            String parent = null;
            List<String> parentLines = new ArrayList<>();
            for (String line : content.split("\\R")) {
                Matcher matcher = KEY_VALUE_PATTERN.matcher(line);
                if (matcher.matches() && matcher.group(1).isEmpty()) {
                    data.parseParent(parent, parentLines);
                    parent = matcher.group(2);
                    parentLines = new ArrayList<>();
                    String value = cleanValue(matcher.group(3));
                    if (!value.isEmpty()) {
                        data.values.put(parent, value);
                        parent = null;
                    }
                } else if (parent != null) {
                    parentLines.add(line);
                }
            }
            data.parseParent(parent, parentLines);
            return data;
        }

        String value(String key) {
            return values.get(key);
        }

        Long longValue(String key) {
            String value = value(key);
            if (value == null) {
                return null;
            }
            Matcher matcher = NUMBER_PATTERN.matcher(value.replaceFirst("^&\\w+\\s+", ""));
            if (!matcher.find()) {
                return null;
            }
            try {
                return Long.valueOf(matcher.group());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        Map<String, String> children(String key) {
            return children.getOrDefault(key, Map.of());
        }

        List<Long> list(String key) {
            return lists.getOrDefault(key, List.of()).stream().map(value -> {
                try {
                    return Long.valueOf(value);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }).filter(value -> value != null).toList();
        }

        List<TypeQuantity> pairs(String key) {
            return pairs.getOrDefault(key, List.of());
        }

        /** 增强时间调整中的建筑列表为 ID、名称、类型三元组，只保留名称。 */
        List<String> structureNames() {
            List<String> names = new ArrayList<>();
            boolean nextIsName = false;
            for (String line : rawChildren.getOrDefault("allStructureInfo", List.of())) {
                if (line.matches("^\\s*-\\s*-\\s*\\d+\\s*$")) {
                    nextIsName = true;
                } else if (nextIsName && line.matches("^\\s+-\\s*.+$")) {
                    String name = cleanValue(line.replaceFirst("^\\s*-\\s*", ""));
                    if (!name.isBlank()) {
                        names.add(name);
                    }
                    nextIsName = false;
                }
            }
            return List.copyOf(names);
        }

        private void parseParent(String parent, List<String> lines) {
            if (parent == null || lines.isEmpty()) {
                return;
            }
            rawChildren.put(parent, List.copyOf(lines));
            Map<String, String> map = new LinkedHashMap<>();
            List<String> list = new ArrayList<>();
            List<TypeQuantity> typeQuantities = new ArrayList<>();
            String pendingQuantity = null;
            for (String line : lines) {
                Matcher mapEntry = Pattern.compile("^\\s+([^:\\s]+):\\s*(.+)$").matcher(line);
                if (mapEntry.matches()) {
                    map.put(cleanValue(mapEntry.group(1)), cleanValue(mapEntry.group(2)));
                    continue;
                }
                Matcher nestedPair = Pattern.compile("^\\s*-\\s*-\\s*(\\d+)\\s*$").matcher(line);
                if (nestedPair.matches()) {
                    pendingQuantity = nestedPair.group(1);
                    continue;
                }
                Matcher listItem = Pattern.compile("^\\s*-\\s*(\\d+)\\s*$").matcher(line);
                if (listItem.matches()) {
                    if (pendingQuantity != null) {
                        typeQuantities.add(new TypeQuantity(Long.valueOf(listItem.group(1)), pendingQuantity));
                        pendingQuantity = null;
                    } else {
                        list.add(listItem.group(1));
                    }
                }
            }
            if (!map.isEmpty()) {
                children.put(parent, map);
            }
            if (!list.isEmpty()) {
                lists.put(parent, list);
            }
            if (!typeQuantities.isEmpty()) {
                pairs.put(parent, typeQuantities);
            }
        }

        private static String cleanValue(String value) {
            String result = value == null ? "" : value.trim();
            if (result.length() >= 2 && ((result.startsWith("\"") && result.endsWith("\"")) || (result
                .startsWith("'") && result.endsWith("'")))) {
                boolean singleQuoted = result.startsWith("'");
                result = result.substring(1, result.length() - 1);
                return singleQuoted ? result.replace("''", "'") : result;
            }
            return Set.of("null", "~", "[]", "{}").contains(result) ? "" : result;
        }
    }

    /** 游戏报文按数量、类型编码给出的剩余物品，转换后以类型优先存储。 */
    private record TypeQuantity(Long typeId, String quantity) {
    }

    /** 月矿通知中的结构化矿种事实，不包含原始邮件正文或操作人。 */
    public record MoonOreNotification(Long moonId, Long structureId, List<EveMoonOreItemResp> ores) {
    }

    /** 归一化前的单个合法矿种体积，仅在解析期间短暂存在。 */
    private record ParsedOreVolume(Integer typeId, String typeName, BigDecimal volume) {
    }
}
