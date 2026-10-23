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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.mapper.EveMoonOreProfileMapper;
import top.continew.admin.eve.model.EveMoonOreItemResp;
import top.continew.admin.eve.model.EveMoonOreProfileResp;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.entity.EveMoonExtractionDO;
import top.continew.admin.eve.model.entity.EveMoonOreProfileDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.model.enums.EveCorporationStatus;
import top.continew.starter.core.exception.BusinessException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 从当前授权角色的本地游戏通知缓存初始化并维护月矿堡长期矿种档案。
 *
 * <p>本服务绝不外呼 ESI；自动初始化只填充 PENDING 档案，READY 档案仅能由显式手动刷新替换。</p>
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveMoonOreProfileService {

    private static final String NOTIFICATION_READ_SCOPE = "esi-characters.read_notifications.v1";
    private static final String ACTIVE = "ACTIVE";
    private static final String READY = "READY";
    private static final String PENDING = "PENDING";
    private static final long CACHE_RECHECK_MINUTES = 10;

    private final EveMoonOreProfileMapper profileMapper;
    private final EveCharacterMapper characterMapper;
    private final EveAuthorizationMapper authorizationMapper;
    private final EveCorporationMapper corporationMapper;
    private final EveGameNotificationPresentationService notificationPresentationService;
    private final ObjectMapper objectMapper;

    /**
     * 为月矿时间线中的矿堡创建占位，并仅用当前本人缓存通知补齐缺失矿种档案。
     *
     * @return 以建筑 ID 为键的档案响应
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<Long, EveMoonOreProfileResp> initializeMissing(EveCorporationDO corporation,
                                                              List<EveMoonExtractionDO> extractions,
                                                              Long userId,
                                                              Long characterRefId) {
        requireCorporation(corporation);
        List<EveMoonExtractionDO> activeExtractions = validActiveExtractions(corporation, extractions);
        return initializeMissing(corporation, activeExtractions, userId, characterRefId, false);
    }

    /**
     * 通知同步成功后按角色归属查找军团及完整 ACTIVE 时间线，并尝试补齐 PENDING 档案。
     *
     * <p>该 hook 只读取刚同步到本地的通知缓存，不会再次请求国服接口。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void initializeMissingForCharacter(Long tenantId, Long userId, Long characterRefId) {
        EveCharacterDO character = tenantId == null || userId == null || characterRefId == null
            ? null
            : characterMapper.selectActiveByTenantUserAndRefId(tenantId, userId, characterRefId);
        if (character == null) {
            return;
        }
        EveCorporationDO corporation = corporationMapper.selectByTenantAndCorporationId(tenantId, character
            .getCorporationId());
        if (corporation == null || !EveCorporationStatus.ACTIVE.equals(corporation.getStatus()) || !Objects
            .equals(character.getServer(), corporation.getServer())) {
            return;
        }
        List<EveMoonExtractionDO> extractions = profileMapper.selectActiveExtractions(tenantId, corporation.getId());
        if (!extractions.isEmpty()) {
            initializeMissing(corporation, extractions, userId, characterRefId, true);
        }
    }

    /**
     * 手动从当前本人缓存通知中重选目标矿堡的最新有效矿种，未命中时保留旧档案。
     */
    @Transactional(rollbackFor = Exception.class)
    public EveMoonOreProfileResp refresh(EveCorporationDO corporation,
                                         EveMoonExtractionDO extraction,
                                         Long userId,
                                         Long characterRefId) {
        NotificationOwner owner = requireOwner(corporation, userId, characterRefId);
        List<EveMoonExtractionDO> activeExtractions = profileMapper.selectActiveExtractions(corporation
            .getTenantId(), corporation.getId());
        EveMoonExtractionDO target = activeExtractions.stream()
            .filter(item -> sameExtraction(item, extraction))
            .findFirst()
            .orElseThrow(() -> new BusinessException("月矿时间线不存在或已失效"));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        profileMapper.insertPending(corporation.getTenantId(), corporation.getId(), target.getStructureId(), target
            .getMoonId(), userId, now);
        profileMapper.resetForMoonChange(corporation.getTenantId(), corporation.getId(), target.getStructureId(), target
            .getMoonId(), userId, now);
        EveMoonOreProfileDO existing = selectProfiles(corporation, List.of(target)).get(target.getStructureId());
        ParsedNotification latest = findLatest(target, activeExtractions, parseNotifications(owner));
        if (latest != null) {
            profileMapper.forceUpdateReady(toReadyProfile(corporation, target, latest), userId, now);
        } else if (existing != null && PENDING.equals(existing.getStatus())) {
            profileMapper.touchPendingChecked(corporation.getTenantId(), corporation.getId(), target
                .getStructureId(), target.getMoonId(), userId, now);
        }
        EveMoonOreProfileDO result = selectProfiles(corporation, List.of(target)).get(target.getStructureId());
        return toResponse(result);
    }

    /** 对已校验的数据源执行幂等占位和自动补齐。 */
    private Map<Long, EveMoonOreProfileResp> initializeMissing(EveCorporationDO corporation,
                                                               List<EveMoonExtractionDO> extractions,
                                                               Long userId,
                                                               Long characterRefId,
                                                               boolean forceCacheCheck) {
        if (extractions.isEmpty()) {
            return Map.of();
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Map<Long, EveMoonExtractionDO> structures = extractions.stream()
            .collect(Collectors.toMap(EveMoonExtractionDO::getStructureId, Function.identity(), (first,
                                                                                                 second) -> first, LinkedHashMap::new));
        structures.values()
            .forEach(item -> profileMapper.insertPending(corporation.getTenantId(), corporation.getId(), item
                .getStructureId(), item.getMoonId(), userId, now));
        structures.values()
            .forEach(item -> profileMapper.resetForMoonChange(corporation.getTenantId(), corporation.getId(), item
                .getStructureId(), item.getMoonId(), userId, now));
        Map<Long, EveMoonOreProfileDO> initialProfiles = selectProfiles(corporation, structures.values());
        List<EveMoonExtractionDO> pending = structures.values()
            .stream()
            .filter(item -> isPendingCandidate(initialProfiles.get(item.getStructureId()), now, forceCacheCheck))
            .toList();
        NotificationOwner owner = pending.isEmpty() ? null : findOwner(corporation, userId, characterRefId);
        if (!pending.isEmpty() && owner != null) {
            List<ParsedNotification> notifications = parseNotifications(owner);
            for (EveMoonExtractionDO extraction : pending) {
                ParsedNotification latest = findLatest(extraction, extractions, notifications);
                if (latest == null) {
                    profileMapper.touchPendingChecked(corporation.getTenantId(), corporation.getId(), extraction
                        .getStructureId(), extraction.getMoonId(), owner.userId(), now);
                } else {
                    profileMapper.updateReadyIfPending(toReadyProfile(corporation, extraction, latest), owner
                        .userId(), now);
                }
            }
        }
        Map<Long, EveMoonOreProfileDO> profiles = pending.isEmpty() || owner == null
            ? initialProfiles
            : selectProfiles(corporation, structures.values());
        Map<Long, EveMoonOreProfileResp> result = new LinkedHashMap<>();
        structures.keySet().forEach(structureId -> result.put(structureId, toResponse(profiles.get(structureId))));
        return result;
    }

    /** 校验当前本站用户、有效角色、当前军团及通知 Scope 必须形成同一条归属链。 */
    private NotificationOwner requireOwner(EveCorporationDO corporation, Long userId, Long characterRefId) {
        requireCorporation(corporation);
        EveCharacterDO character = requireCharacter(corporation.getTenantId(), userId, characterRefId);
        if (!Objects.equals(character.getCorporationId(), corporation.getCorporationId()) || !Objects.equals(character
            .getServer(), corporation.getServer())) {
            throw new BusinessException("当前角色已不属于目标军团");
        }
        EveAuthorizationDO authorization = authorizationMapper.selectByUserCharacter(corporation
            .getTenantId(), userId, characterRefId)
            .stream()
            .filter(item -> EveAuthorizationStatus.ACTIVE.equals(item.getStatus()))
            .filter(item -> item.getScopes() != null && item.getScopes().contains(NOTIFICATION_READ_SCOPE))
            .findFirst()
            .orElseThrow(() -> new BusinessException("当前角色缺少游戏通知授权，请重新授权后再试"));
        return new NotificationOwner(corporation.getTenantId(), userId, characterRefId, character, authorization);
    }

    /** 自动初始化按可选能力工作，缺少本人通知授权时保留 PENDING 且不阻断列表。 */
    private NotificationOwner findOwner(EveCorporationDO corporation, Long userId, Long characterRefId) {
        EveCharacterDO character = corporation.getTenantId() == null || userId == null || characterRefId == null
            ? null
            : characterMapper.selectActiveByTenantUserAndRefId(corporation.getTenantId(), userId, characterRefId);
        if (character == null || !Objects.equals(character.getCorporationId(), corporation
            .getCorporationId()) || !Objects.equals(character.getServer(), corporation.getServer())) {
            return null;
        }
        EveAuthorizationDO authorization = authorizationMapper.selectByUserCharacter(corporation
            .getTenantId(), userId, characterRefId)
            .stream()
            .filter(item -> EveAuthorizationStatus.ACTIVE.equals(item.getStatus()))
            .filter(item -> item.getScopes() != null && item.getScopes().contains(NOTIFICATION_READ_SCOPE))
            .findFirst()
            .orElse(null);
        return authorization == null
            ? null
            : new NotificationOwner(corporation.getTenantId(), userId, characterRefId, character, authorization);
    }

    /** 校验军团绑定本身可用于当前租户的档案读取。 */
    private static void requireCorporation(EveCorporationDO corporation) {
        if (corporation == null || corporation.getId() == null || corporation
            .getTenantId() == null || !EveCorporationStatus.ACTIVE.equals(corporation.getStatus())) {
            throw new BusinessException("当前军团绑定不可用");
        }
    }

    /** 严格读取传入租户和用户拥有的有效角色绑定。 */
    private EveCharacterDO requireCharacter(Long tenantId, Long userId, Long characterRefId) {
        EveCharacterDO character = tenantId == null || userId == null || characterRefId == null
            ? null
            : characterMapper.selectActiveByTenantUserAndRefId(tenantId, userId, characterRefId);
        if (character == null) {
            throw new BusinessException("当前授权角色已不可用");
        }
        return character;
    }

    /** 过滤并校验调用方提供的本军团 ACTIVE 时间线。 */
    private static List<EveMoonExtractionDO> validActiveExtractions(EveCorporationDO corporation,
                                                                    Collection<EveMoonExtractionDO> extractions) {
        if (extractions == null) {
            return List.of();
        }
        return extractions.stream()
            .filter(Objects::nonNull)
            .filter(item -> Objects.equals(item.getTenantId(), corporation.getTenantId()))
            .filter(item -> Objects.equals(item.getCorporationRefId(), corporation.getId()))
            .filter(item -> ACTIVE.equals(item.getStatus()) && Objects.equals(item.getDeleted(), 0L))
            .filter(item -> item.getStructureId() != null && item.getMoonId() != null)
            .toList();
    }

    /** 只在无 READY 数据且十分钟节流窗口已过时检查本地通知缓存。 */
    private static boolean shouldCheck(EveMoonOreProfileDO profile, LocalDateTime now) {
        return profile != null && !READY.equals(profile.getStatus()) && (profile
            .getLastCacheCheckedAt() == null || !profile.getLastCacheCheckedAt()
                .isAfter(now.minusMinutes(CACHE_RECHECK_MINUTES)));
    }

    /** 判断 PENDING 档案是否应在本次调用检查缓存，新通知 hook 可绕过时间节流。 */
    private static boolean isPendingCandidate(EveMoonOreProfileDO profile, LocalDateTime now, boolean forceCacheCheck) {
        return profile != null && !READY.equals(profile.getStatus()) && (forceCacheCheck || shouldCheck(profile, now));
    }

    /** 读取并安全解析当前角色的月矿通知，畸形报文直接忽略。 */
    private List<ParsedNotification> parseNotifications(NotificationOwner owner) {
        return profileMapper.selectMoonNotifications(owner.tenantId(), owner.userId(), owner.characterRefId())
            .stream()
            .map(item -> {
                EveGameNotificationPresentationService.MoonOreNotification parsed = notificationPresentationService
                    .parseMoonOreNotification(item.getNotificationType(), item.getContent());
                return parsed == null || item.getNotificationId() == null || item.getSentAt() == null
                    ? null
                    : new ParsedNotification(item.getNotificationId(), item.getSentAt(), parsed);
            })
            .filter(Objects::nonNull)
            .toList();
    }

    /** 按月球严格匹配，通知有建筑 ID 时还必须同时匹配建筑。 */
    private static ParsedNotification findLatest(EveMoonExtractionDO target,
                                                 List<EveMoonExtractionDO> activeExtractions,
                                                 List<ParsedNotification> notifications) {
        boolean uniqueMoonStructure = activeExtractions.stream()
            .filter(item -> Objects.equals(item.getMoonId(), target.getMoonId()))
            .map(EveMoonExtractionDO::getStructureId)
            .filter(Objects::nonNull)
            .distinct()
            .limit(2)
            .count() == 1;
        return notifications.stream()
            .filter(item -> Objects.equals(item.data().moonId(), target.getMoonId()))
            .filter(item -> item.data().structureId() == null
                ? uniqueMoonStructure
                : Objects.equals(item.data().structureId(), target.getStructureId()))
            .findFirst()
            .orElse(null);
    }

    /** 将匹配通知转换为可原子写入的 READY 档案。 */
    private EveMoonOreProfileDO toReadyProfile(EveCorporationDO corporation,
                                               EveMoonExtractionDO extraction,
                                               ParsedNotification notification) {
        EveMoonOreProfileDO profile = new EveMoonOreProfileDO();
        profile.setTenantId(corporation.getTenantId());
        profile.setCorporationRefId(corporation.getId());
        profile.setStructureId(extraction.getStructureId());
        profile.setMoonId(extraction.getMoonId());
        profile.setStatus(READY);
        profile.setOresJson(writeOres(notification.data().ores()));
        profile.setOreNames(notification.data()
            .ores()
            .stream()
            .map(EveMoonOreItemResp::typeName)
            .collect(Collectors.joining("|", "|", "|")));
        profile.setSourceNotificationId(notification.notificationId());
        profile.setSourceSentAt(notification.sentAt());
        return profile;
    }

    /** 批量读取档案并按建筑 ID 建立稳定映射。 */
    private Map<Long, EveMoonOreProfileDO> selectProfiles(EveCorporationDO corporation,
                                                          Collection<EveMoonExtractionDO> extractions) {
        if (extractions.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> moonsByStructure = extractions.stream()
            .collect(Collectors.toMap(EveMoonExtractionDO::getStructureId, EveMoonExtractionDO::getMoonId, (first,
                                                                                                            second) -> first));
        return profileMapper.selectProfiles(corporation.getTenantId(), corporation.getId(), moonsByStructure.keySet())
            .stream()
            .filter(item -> Objects.equals(item.getMoonId(), moonsByStructure.get(item.getStructureId())))
            .collect(Collectors.toMap(EveMoonOreProfileDO::getStructureId, Function.identity(), (first,
                                                                                                 second) -> first));
    }

    /** 档案实体转换为不包含通知正文、操作人或原始体积的页面响应。 */
    private EveMoonOreProfileResp toResponse(EveMoonOreProfileDO profile) {
        if (profile == null) {
            return new EveMoonOreProfileResp(PENDING, List.of(), null, null);
        }
        return new EveMoonOreProfileResp(profile.getStatus(), readOres(profile.getOresJson()), profile
            .getSourceSentAt(), profile.getUpdateTime() == null ? profile.getCreateTime() : profile.getUpdateTime());
    }

    /** 序列化归一化后的矿种比例；失败时中止写入，不能产生半成品档案。 */
    private String writeOres(List<EveMoonOreItemResp> ores) {
        try {
            return objectMapper.writeValueAsString(ores);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("月矿种类序列化失败", e);
        }
    }

    /** 读取档案矿种 JSON，历史脏数据不得阻断月矿列表。 */
    private List<EveMoonOreItemResp> readOres(String oresJson) {
        if (oresJson == null || oresJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(oresJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    /** 判断目标提取记录确实存在于完整 ACTIVE 时间线。 */
    private static boolean sameExtraction(EveMoonExtractionDO candidate, EveMoonExtractionDO requested) {
        return requested != null && Objects.equals(candidate.getId(), requested.getId()) && Objects.equals(candidate
            .getStructureId(), requested.getStructureId()) && Objects.equals(candidate.getMoonId(), requested
                .getMoonId());
    }

    /** 已通过严格角色、军团和 Scope 校验的本地通知所有者。 */
    private record NotificationOwner(Long tenantId, Long userId, Long characterRefId, EveCharacterDO character,
                                     EveAuthorizationDO authorization) {
    }

    /** 已安全解析的缓存通知及其来源时间。 */
    private record ParsedNotification(Long notificationId, LocalDateTime sentAt,
                                      EveGameNotificationPresentationService.MoonOreNotification data) {
    }
}
