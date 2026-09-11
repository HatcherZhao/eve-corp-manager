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

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        .compile("(?m)^(?:charID|corpID|againstID|declaredByID|cancelledBy|startedBy|firedBy):\\s*(\\d+)");
    private static final DateTimeFormatter TICK_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm");
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,##0.##");

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

    /** 将一条原始通知转换为中文摘要及详情字段。 */
    public EveGameNotificationPresentation present(String type, String content, Map<Long, String> entityNames) {
        NotificationData data = NotificationData.parse(decode(content));
        LinkedHashMap<String, String> details = new LinkedHashMap<>();
        addStructureDetails(details, data);
        String summary = switch (type == null ? "" : type) {
            case "MoonminingExtractionStarted" -> moon(details, data, entityNames, "月矿已开始提取", "预计可开采时间");
            case "MoonminingExtractionFinished" -> moon(details, data, entityNames, "月矿提取已完成", "自动碎裂时间");
            case "MoonminingAutomaticFracture" -> moon(details, data, entityNames, "月矿已自然碎裂", null);
            case "MoonminingLaserFired" -> moon(details, data, entityNames, "月矿激光已启动", null);
            case "MoonminingExtractionCancelled" -> moon(details, data, entityNames, "月矿提取已取消", null);
            case "StructureFuelAlert" -> fuelAlert(details, data);
            case "StructureServicesOffline" -> servicesOffline(details, data);
            case "StructureUnderAttack" -> underAttack(details, data, entityNames);
            case "StructureImpendingAbandonmentAssetsAtRisk" -> abandonmentRisk(details, data);
            case "StructureItemsMovedToSafety" -> assetsMovedToSafety(details, data);
            case "StructureWentHighPower" -> structureSummary(details, "建筑已进入高能状态");
            case "StructureWentLowPower" -> structureSummary(details, "建筑已进入低能状态");
            case "StructureAnchoring" -> structureSummary(details, "建筑开始锚定");
            case "StructureOnline" -> structureSummary(details, "建筑已上线");
            case "StructureUnanchoring" -> structureSummary(details, "建筑开始解除锚定");
            case "CorpAppNewMsg" -> application(details, data, entityNames, "收到新的军团申请");
            case "CharAppAcceptMsg" -> application(details, data, entityNames, "军团申请已接受");
            case "CharAppWithdrawMsg" -> application(details, data, entityNames, "军团申请已撤回");
            case "CharTerminationMsg" -> application(details, data, entityNames, "角色军团关系已变更");
            case "CorpTaxChangeMsg" -> taxChange(details, data, entityNames);
            case "WarDeclared" -> warDeclared(details, data, entityNames);
            case "WarRetractedByConcord" -> warRetracted(details, data, entityNames);
            default -> generic(details, data, entityNames);
        };
        return new EveGameNotificationPresentation(summary, details.entrySet()
            .stream()
            .map(item -> new EveGameNotificationDetailItem(item.getKey(), item.getValue()))
            .toList());
    }

    /** 补充建筑名称、建筑类型和星系等所有建筑类通知共享的信息。 */
    private void addStructureDetails(Map<String, String> details, NotificationData data) {
        String structureName = firstNonBlank(data.value("structureName"), linkName(data.value("structureLink")));
        if (structureName != null) {
            details.put("建筑", structureName);
        } else {
            Long typeId = data.longValue("structureTypeID");
            String typeName = typeId == null ? null : staticNameReference.findTypeName(typeId);
            if (typeName != null) {
                details.put("建筑类型", typeName);
            }
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

    /** 展示燃料预警中缺少的燃料及其数量。 */
    private String fuelAlert(Map<String, String> details, NotificationData data) {
        List<String> items = data.pairs("listOfTypesAndQty").stream().map(pair -> {
            String typeName = staticNameReference.findTypeName(pair.typeId());
            return typeName == null ? null : typeName + " × " + number(pair.quantity());
        }).filter(item -> item != null).toList();
        if (!items.isEmpty()) {
            details.put("燃料需求", String.join("；", items));
        }
        return structureSummary(details, "建筑触发燃料预警");
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
        return structureSummary(details, "建筑服务已离线");
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

    private static void addDuration(Map<String, String> details, String label, Long ticks) {
        if (ticks == null || ticks <= 0) {
            return;
        }
        long hours = ticks / 36_000_000_000L;
        details.put(label, hours >= 24 ? hours / 24 + " 天" : Math.max(hours, 1) + " 小时");
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
            return NUMBER_FORMAT.format(new BigDecimal(value));
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
                .format(TICK_TIME_FORMATTER);
        } catch (RuntimeException ignored) {
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

        private void parseParent(String parent, List<String> lines) {
            if (parent == null || lines.isEmpty()) {
                return;
            }
            Map<String, String> map = new LinkedHashMap<>();
            List<String> list = new ArrayList<>();
            List<TypeQuantity> typeQuantities = new ArrayList<>();
            Long pendingTypeId = null;
            for (String line : lines) {
                Matcher mapEntry = Pattern.compile("^\\s+([^:\\s]+):\\s*(.+)$").matcher(line);
                if (mapEntry.matches()) {
                    map.put(cleanValue(mapEntry.group(1)), cleanValue(mapEntry.group(2)));
                    continue;
                }
                Matcher nestedPair = Pattern.compile("^\\s*-\\s*-\\s*(\\d+)\\s*$").matcher(line);
                if (nestedPair.matches()) {
                    pendingTypeId = Long.valueOf(nestedPair.group(1));
                    continue;
                }
                Matcher listItem = Pattern.compile("^\\s*-\\s*(\\d+)\\s*$").matcher(line);
                if (listItem.matches()) {
                    if (pendingTypeId != null) {
                        typeQuantities.add(new TypeQuantity(pendingTypeId, listItem.group(1)));
                        pendingTypeId = null;
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
            if (result.length() >= 2 && result.startsWith("\"") && result.endsWith("\"")) {
                result = result.substring(1, result.length() - 1);
            }
            return result;
        }
    }

    /** 燃料预警中的物品类型和缺少数量。 */
    private record TypeQuantity(Long typeId, String quantity) {
    }
}
