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

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import top.continew.admin.eve.model.entity.EveStaticLocationReferenceDO;
import top.continew.admin.eve.model.entity.EveStaticTypeReferenceDO;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从随应用发布的国服基础资料中解析中文名称。
 *
 * <p>资料由 {@code docs/evedata.xlsx} 提取。它只补充稳定的静态名称，资产归属、物品自定义名称和
 * 本军团建筑名称仍以国服 ESI 为准。</p>
 *
 * @author zhaoyuqing
 */
@Component
public class EveStaticNameReference {

    private static final String RESOURCE_PATH = "eve/reference/evedata-name-reference.tsv";

    private final Map<Long, String> typeNames = new HashMap<>();
    private final Map<Long, String> solarSystemNames = new HashMap<>();
    private final Map<Long, StaticLocation> npcStations = new HashMap<>();
    private final Map<Long, StaticLocation> publicStructures = new HashMap<>();
    private final EveStaticReferenceService databaseReference;

    /** 供单元测试和数据库不可用的降级场景使用内置名称索引。 */
    public EveStaticNameReference() {
        this(null);
    }

    /** 应用启动时加载内置索引，并优先使用数据库中由 evedata.xlsx 导入的更新资料。 */
    @Autowired
    public EveStaticNameReference(EveStaticReferenceService databaseReference) {
        this.databaseReference = databaseReference;
        load();
    }

    /** 返回 Excel 中存在的物品中文名称，未收录时返回空。 */
    public String findTypeName(Long typeId) {
        EveStaticTypeReferenceDO reference = findDatabaseType(typeId);
        return reference == null ? typeNames.get(typeId) : reference.getTypeName();
    }

    /** 返回 Excel 中存在的星系中文名称，未收录时返回空。 */
    public String findSolarSystemName(Long solarSystemId) {
        EveStaticLocationReferenceDO reference = findDatabaseLocation("SOLAR_SYSTEM", solarSystemId);
        return reference == null ? solarSystemNames.get(solarSystemId) : reference.getReferenceName();
    }

    /** 返回 Excel 中存在的 NPC 空间站名称和所属星系，未收录时返回空。 */
    public StaticLocation findNpcStation(Long stationId) {
        EveStaticLocationReferenceDO reference = findDatabaseLocation("NPC_STATION", stationId);
        return reference == null
            ? npcStations.get(stationId)
            : new StaticLocation(reference.getReferenceName(), reference.getSolarSystemId());
    }

    /** 返回 Excel 中存在的公开玩家建筑名称和所属星系，未收录时返回空。 */
    public StaticLocation findPublicStructure(Long structureId) {
        EveStaticLocationReferenceDO reference = findDatabaseLocation("PUBLIC_STRUCTURE", structureId);
        return reference == null
            ? publicStructures.get(structureId)
            : new StaticLocation(reference.getReferenceName(), reference.getSolarSystemId());
    }

    /** 用 Excel 已收录的类型中文名作为基准，其余 ID 保留上游名称。 */
    public Map<Long, String> applyTypeNames(Collection<Long> ids, Map<Long, String> upstreamNames) {
        Map<Long, String> names = applyNames(ids, upstreamNames, typeNames);
        if (databaseReference == null) {
            return names;
        }
        databaseReference.findTypes(ids.stream()
            .filter(id -> id != null && id <= Integer.MAX_VALUE)
            .map(Long::intValue)
            .toList()).forEach((id, item) -> names.put(id.longValue(), item.getTypeName()));
        return names;
    }

    /** 用 Excel 已收录的星系中文名作为基准，其余 ID 保留上游名称。 */
    public Map<Long, String> applySolarSystemNames(Collection<Long> ids, Map<Long, String> upstreamNames) {
        Map<Long, String> names = applyNames(ids, upstreamNames, solarSystemNames);
        applyLocationNames(names, ids, "SOLAR_SYSTEM");
        return names;
    }

    /** 用 Excel 已收录的公共位置名称作为基准，其余 ID 保留上游名称。 */
    public Map<Long, String> applyPublicLocationNames(Collection<Long> ids, Map<Long, String> upstreamNames) {
        Map<Long, String> references = new HashMap<>(solarSystemNames);
        npcStations.forEach((id, location) -> references.put(id, location.name()));
        publicStructures.forEach((id, location) -> references.put(id, location.name()));
        Map<Long, String> names = applyNames(ids, upstreamNames, references);
        applyLocationNames(names, ids, "NPC_STATION");
        applyLocationNames(names, ids, "PUBLIC_STRUCTURE");
        return names;
    }

    /** 返回尚未由静态资料解析的 ID，供上游名称接口按需补齐。 */
    public static List<Long> unresolvedIds(Collection<Long> ids, Map<Long, String> names) {
        return ids.stream().filter(id -> id != null && !names.containsKey(id)).distinct().toList();
    }

    /** 读取 TSV，格式为类别、ID、星系 ID（位置记录）和中文名称。 */
    private void load() {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource
            .getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split("\\t", 4);
                if (fields.length != 4) {
                    throw new IllegalStateException("国服名称资料格式错误");
                }
                Long id = Long.valueOf(fields[1]);
                switch (fields[0]) {
                    case "TYPE" -> typeNames.put(id, fields[3]);
                    case "SYSTEM" -> solarSystemNames.put(id, fields[3]);
                    case "STATION" -> npcStations.put(id, new StaticLocation(fields[3], Long.valueOf(fields[2])));
                    case "STRUCTURE" -> publicStructures.put(id, new StaticLocation(fields[3], Long
                        .valueOf(fields[2])));
                    default -> throw new IllegalStateException("国服名称资料类别错误");
                }
            }
        } catch (IOException | NumberFormatException e) {
            throw new IllegalStateException("无法加载国服基础名称资料", e);
        }
    }

    /** 静态位置资料，包含显示名称和所属星系。 */
    public record StaticLocation(String name, Long solarSystemId) {
    }

    /** 将参考资料覆盖到上游结果，避免将未知 ID 错误替换为空。 */
    private static Map<Long, String> applyNames(Collection<Long> ids,
                                                Map<Long, String> upstreamNames,
                                                Map<Long, String> references) {
        Map<Long, String> names = new HashMap<>(upstreamNames);
        ids.stream().filter(id -> id != null).distinct().forEach(id -> {
            String name = references.get(id);
            if (name != null && !name.isBlank()) {
                names.put(id, name);
            }
        });
        return names;
    }

    /** 从数据库覆盖指定类型的位置名称；内置索引作为数据库尚未导入时的后备。 */
    private void applyLocationNames(Map<Long, String> names, Collection<Long> ids, String referenceType) {
        if (databaseReference == null) {
            return;
        }
        databaseReference.findLocations(referenceType, ids)
            .forEach((id, item) -> names.put(id, item.getReferenceName()));
    }

    /** 查询单个数据库类型，异常时仍允许内置索引为调用方提供结果。 */
    private EveStaticTypeReferenceDO findDatabaseType(Long typeId) {
        if (databaseReference == null || typeId == null || typeId > Integer.MAX_VALUE) {
            return null;
        }
        return databaseReference.findTypes(List.of(typeId.intValue())).get(typeId.intValue());
    }

    /** 查询单个数据库位置，异常时仍允许内置索引为调用方提供结果。 */
    private EveStaticLocationReferenceDO findDatabaseLocation(String referenceType, Long referenceId) {
        return databaseReference == null ? null : databaseReference.findLocation(referenceType, referenceId);
    }
}
