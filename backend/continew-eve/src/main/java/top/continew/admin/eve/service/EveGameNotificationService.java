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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.client.SerenityEsiClient;
import top.continew.admin.eve.client.SerenityEsiClientException;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveGameNotificationMapper;
import top.continew.admin.eve.model.EveGameNotificationPresentation;
import top.continew.admin.eve.model.EveGameNotificationResp;
import top.continew.admin.eve.model.EveGameNotificationSyncResp;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveGameNotificationDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.model.serenity.SerenityEsiResponse;
import top.continew.admin.eve.model.serenity.SerenityGameNotificationResponse;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 管理当前授权角色的游戏内通知缓存、分类与同步。
 *
 * <p>国服通知是角色私人资源；服务端始终以租户、本站用户及角色绑定三元组隔离，不能借由可猜测的通知 ID
 * 读取其他角色的通知。</p>
 *
 * @author zhaoyuqing
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EveGameNotificationService {

    private static final String NOTIFICATION_READ_SCOPE = "esi-characters.read_notifications.v1";

    private final EveCharacterMapper characterMapper;
    private final EveAuthorizationMapper authorizationMapper;
    private final EveGameNotificationMapper notificationMapper;
    private final EveAuthorizationLifecycleService authorizationLifecycleService;
    private final EveGameNotificationPresentationService notificationPresentationService;
    private final SerenityEsiClient esiClient;

    /** 分页查询当前授权角色已同步的游戏通知。 */
    public PageResp<EveGameNotificationResp> page(int page, int size, String category, String keyword) {
        NotificationSource source = requireCurrentSource();
        LambdaQueryWrapper<EveGameNotificationDO> query = new LambdaQueryWrapper<EveGameNotificationDO>()
            .eq(EveGameNotificationDO::getUserId, source.userId())
            .eq(EveGameNotificationDO::getCharacterRefId, source.character().getId())
            .eq(EveGameNotificationDO::getDeleted, 0L)
            .orderByDesc(EveGameNotificationDO::getSentAt)
            .orderByDesc(EveGameNotificationDO::getNotificationId);
        if (category != null && !category.isBlank() && !"ALL".equals(category)) {
            query.eq(EveGameNotificationDO::getCategory, category);
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            query.and(item -> item.like(EveGameNotificationDO::getNotificationType, value)
                .or()
                .like(EveGameNotificationDO::getContent, value));
        }
        Page<EveGameNotificationDO> result = notificationMapper.selectPage(new Page<>(page, size), query);
        Map<Long, String> entityNames = resolveEntityNames(result.getRecords());
        return new PageResp<>(result.getRecords().stream().map(item -> toResponse(item, entityNames)).toList(), result
            .getTotal());
    }

    /** 同步当前会话中授权角色的最近游戏通知。 */
    @Transactional(rollbackFor = Exception.class)
    public EveGameNotificationSyncResp syncCurrentUser() {
        return synchronize(requireCurrentSource());
    }

    /** 供无浏览器会话的自动同步任务同步指定角色的游戏通知。 */
    @Transactional(rollbackFor = Exception.class)
    public EveGameNotificationSyncResp syncForCharacter(Long tenantId, Long userId, Long characterRefId) {
        return synchronize(requireSource(tenantId, userId, characterRefId));
    }

    /** 拉取一批完整通知并在上游成功后提交本地展示快照。 */
    private EveGameNotificationSyncResp synchronize(NotificationSource source) {
        String accessToken = authorizationLifecycleService.ensureAccessToken(source.authorization());
        SerenityEsiResponse<List<SerenityGameNotificationResponse>> response = esiClient
            .getGameNotificationsWithMetadata(source.character().getCharacterId(), accessToken);
        LocalDateTime synchronizedAt = LocalDateTime.now(ZoneOffset.UTC);
        for (SerenityGameNotificationResponse notification : response.body()) {
            if (notification == null || notification.notificationId() == null || notification.notificationId() <= 0) {
                continue;
            }
            EveGameNotificationDO target = find(source, notification.notificationId());
            if (target == null) {
                target = newNotification(source, notification.notificationId());
            }
            apply(target, notification, synchronizedAt, response.expiresAt());
            save(target);
        }
        return new EveGameNotificationSyncResp(response.body().size(), synchronizedAt, response.expiresAt());
    }

    /** 从当前会话确定唯一有效的角色与通知读取授权。 */
    private NotificationSource requireCurrentSource() {
        UserContext context = UserContextHolder.getContext();
        EveCharacterDO character = characterMapper.selectActiveByUser(context.getTenantId(), context.getId())
            .stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException("当前账号未绑定有效 EVE 角色，请先完成授权"));
        return requireSource(context.getTenantId(), context.getId(), character.getId());
    }

    /** 读取严格匹配三元组的有效授权数据源，供后台任务调用。 */
    private NotificationSource requireSource(Long tenantId, Long userId, Long characterRefId) {
        EveCharacterDO character = characterMapper.selectActiveByTenantUserAndRefId(tenantId, userId, characterRefId);
        if (character == null) {
            throw new BusinessException("自动同步目标角色已不可用");
        }
        EveAuthorizationDO authorization = authorizationMapper.selectByUserCharacter(tenantId, userId, characterRefId)
            .stream()
            .filter(item -> EveAuthorizationStatus.ACTIVE.equals(item.getStatus()))
            .filter(item -> item.getScopes() != null && item.getScopes().contains(NOTIFICATION_READ_SCOPE))
            .findFirst()
            .orElseThrow(() -> new BusinessException("当前角色缺少游戏通知授权，请重新授权后再试"));
        return new NotificationSource(tenantId, userId, character, authorization);
    }

    /** 按三元组与国服通知 ID 查询现有缓存，避免越权读取和跨租户覆盖。 */
    private EveGameNotificationDO find(NotificationSource source, Long notificationId) {
        return notificationMapper.selectOne(new LambdaQueryWrapper<EveGameNotificationDO>()
            .eq(EveGameNotificationDO::getUserId, source.userId())
            .eq(EveGameNotificationDO::getCharacterRefId, source.character().getId())
            .eq(EveGameNotificationDO::getNotificationId, notificationId)
            .eq(EveGameNotificationDO::getDeleted, 0L));
    }

    /** 创建绑定到当前角色的新通知缓存。 */
    private static EveGameNotificationDO newNotification(NotificationSource source, Long notificationId) {
        EveGameNotificationDO target = new EveGameNotificationDO();
        target.setTenantId(source.tenantId());
        target.setUserId(source.userId());
        target.setCharacterRefId(source.character().getId());
        target.setCharacterId(source.character().getCharacterId());
        target.setNotificationId(notificationId);
        target.setCreateUser(source.userId());
        target.setDeleted(0L);
        return target;
    }

    /** 应用国服通知字段并同时保留原始通知类型，便于后续补全分类规则。 */
    private static void apply(EveGameNotificationDO target,
                              SerenityGameNotificationResponse source,
                              LocalDateTime synchronizedAt,
                              LocalDateTime sourceExpiresAt) {
        target.setIsRead(Boolean.TRUE.equals(source.read()));
        target.setSenderId(source.senderId());
        target.setSenderType(source.senderType());
        target.setNotificationType(source.type());
        target.setCategory(categoryOf(source.type()));
        target.setContent(source.text());
        target.setSentAt(source.sentAt());
        target.setLastSeenAt(synchronizedAt);
        target.setSourceExpiresAt(sourceExpiresAt);
    }

    /** 依据国服稳定类型前缀归入产品分类，未覆盖的新类型始终保留并归入其他。 */
    static String categoryOf(String notificationType) {
        String value = notificationType == null ? "" : notificationType.toUpperCase();
        if (value.contains("WAR") || value.contains("SOVEREIGNTY") || value.contains("SOV")) {
            return "WAR_SOVEREIGNTY";
        }
        if (value.contains("STRUCTURE") || value.contains("ASSETSAFETY") || value.contains("ASSET_SAFETY")) {
            return "STRUCTURE_ASSET_SAFETY";
        }
        if (value.contains("MOON") || value.contains("MINING") || value.contains("INDUSTRY") || value
            .contains("MANUFACTUR") || value.contains("RESEARCH")) {
            return "MOON_INDUSTRY";
        }
        if (value.contains("CORP") || value.contains("CORPORATION") || value.contains("ALLIANCE") || value
            .contains("MEMBER") || value.contains("APPLICATION") || value.contains("ROLE")) {
            return "CORPORATION_MEMBER";
        }
        return "OTHER";
    }

    /** 按主键是否存在保存通知缓存。 */
    private void save(EveGameNotificationDO notification) {
        if (notification.getId() == null) {
            notificationMapper.insert(notification);
        } else {
            notificationMapper.updateById(notification);
        }
    }

    /** 解析通知发送方及正文引用的公开名称；名称服务不可用时不阻断通知阅读。 */
    private Map<Long, String> resolveEntityNames(Collection<EveGameNotificationDO> notifications) {
        List<Long> senderIds = notifications.stream()
            .filter(Objects::nonNull)
            .map(EveGameNotificationDO::getSenderId)
            .filter(Objects::nonNull)
            .filter(id -> id > 0 && id <= Integer.MAX_VALUE)
            .toList();
        List<Long> contentIds = notificationPresentationService.referencedEntityIds(notifications.stream()
            .map(EveGameNotificationDO::getContent)
            .toList());
        List<Long> ids = java.util.stream.Stream.concat(senderIds.stream(), contentIds.stream())
            .filter(id -> id > 0 && id <= Integer.MAX_VALUE)
            .distinct()
            .toList();
        Map<Long, String> names = new HashMap<>();
        for (int index = 0; index < ids.size(); index += 1000) {
            try {
                esiClient.resolveUniverseNames(ids.subList(index, Math.min(index + 1000, ids.size())))
                    .stream()
                    .filter(Objects::nonNull)
                    .filter(item -> item.id() != null && item.name() != null && !item.name().isBlank())
                    .forEach(item -> names.put(item.id(), item.name()));
            } catch (SerenityEsiClientException e) {
                log.warn("EVE 游戏通知发送方名称解析失败，failureCode={}", e.getFailureCode());
            }
        }
        return names;
    }

    /** 将通知缓存转换为不向用户暴露游戏内部数字 ID 的页面响应。 */
    private EveGameNotificationResp toResponse(EveGameNotificationDO source, Map<Long, String> entityNames) {
        String senderName = source.getSenderId() == null ? null : entityNames.get(source.getSenderId());
        EveGameNotificationPresentation presentation = notificationPresentationService.present(source
            .getNotificationType(), source.getContent(), entityNames);
        return new EveGameNotificationResp(source.getNotificationId(), source.getIsRead(), source.getCategory(), source
            .getNotificationType(), senderName, source.getSenderType(), source.getContent(), presentation
                .summary(), presentation.details(), source.getSentAt(), source.getLastSeenAt(), source
                    .getSourceExpiresAt());
    }

    /** 个人通知同步所需的角色、授权与隔离上下文。 */
    private record NotificationSource(Long tenantId, Long userId, EveCharacterDO character,
                                      EveAuthorizationDO authorization) {
    }
}
