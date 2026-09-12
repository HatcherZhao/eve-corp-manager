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
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.config.EveAutoSyncProperties;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.model.EveMeContextResp;
import top.continew.admin.eve.model.EveSyncRequestResp;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.enums.EveSyncModule;
import top.continew.admin.eve.model.enums.EveSyncTargetType;
import top.continew.starter.core.exception.BusinessException;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 执行页面发起的即时同步，并重排对应的后续自动任务。
 *
 * <p>手动同步不经过自动队列的冷却和失败退避，但仍沿用各业务服务的分布式锁，防止同一资源被
 * 并发重复拉取。</p>
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveManualSyncRequestService {

    private final EveContextService contextService;
    private final EveCorporationMapper corporationMapper;
    private final EveCharacterMapper characterMapper;
    private final EveSyncJobService syncJobService;
    private final EveAutoSyncProperties autoSyncProperties;
    private final EveCorporationAssetService assetService;
    private final EveCorporationStructureService structureService;
    private final EveCorporationMemberService memberService;
    private final EveMoonExtractionService moonExtractionService;
    private final EveMiningLedgerService miningLedgerService;
    private final EveGameMailService gameMailService;
    private final EveGameNotificationService gameNotificationService;

    /** 立即同步当前军团的共享数据模块。 */
    public EveSyncRequestResp requestCurrentCorporation(EveSyncModule module) {
        UserContext context = UserContextHolder.getContext();
        EveMeContextResp eveContext = contextService.getCurrentContext();
        if (eveContext.corporation() == null) {
            throw new BusinessException("当前账号尚未加入已认领军团");
        }
        EveCorporationDO corporation = corporationMapper.selectByTenantAndCorporationId(context
            .getTenantId(), eveContext.corporation().corporationId());
        if (corporation == null) {
            throw new BusinessException("当前军团租户数据不可用");
        }
        LocalDateTime sourceExpiresAt = switch (module) {
            case ASSETS -> assetService.syncCurrentTenant().sourceExpiresAt();
            case STRUCTURES -> structureService.syncCurrentTenant().sourceExpiresAt();
            case MEMBER_ROSTER, MEMBER_TRACKING -> memberService.syncCurrentTenant().rosterSourceExpiresAt();
            case MOON_EXTRACTIONS -> moonExtractionService.syncCurrentTenant().sourceExpiresAt();
            case MINING_LEDGER -> miningLedgerService.syncCurrentTenant().sourceExpiresAt();
            case GAME_MAIL, GAME_NOTIFICATIONS -> throw new IllegalArgumentException("军团模块不能同步个人通信数据");
        };
        scheduleNextAutoSync(context.getTenantId(), EveSyncTargetType.CORPORATION, corporation
            .getId(), module, sourceExpiresAt);
        return new EveSyncRequestResp(true, "同步完成。");
    }

    /** 立即同步当前登录用户主角色的个人邮箱。 */
    public EveSyncRequestResp requestCurrentMail() {
        UserContext context = UserContextHolder.getContext();
        EveCharacterDO character = requireCurrentCharacter(context);
        LocalDateTime sourceExpiresAt = gameMailService.syncCurrentUser().sourceExpiresAt();
        scheduleNextAutoSync(context.getTenantId(), EveSyncTargetType.CHARACTER, character
            .getId(), EveSyncModule.GAME_MAIL, sourceExpiresAt);
        return new EveSyncRequestResp(true, "同步完成。");
    }

    /** 立即同步当前登录用户主角色的游戏通知。 */
    public EveSyncRequestResp requestCurrentNotifications() {
        UserContext context = UserContextHolder.getContext();
        EveCharacterDO character = requireCurrentCharacter(context);
        LocalDateTime sourceExpiresAt = gameNotificationService.syncCurrentUser().sourceExpiresAt();
        scheduleNextAutoSync(context.getTenantId(), EveSyncTargetType.CHARACTER, character
            .getId(), EveSyncModule.GAME_NOTIFICATIONS, sourceExpiresAt);
        return new EveSyncRequestResp(true, "同步完成。");
    }

    /** 读取当前登录用户的有效主角色，确保自动任务与本次授权角色一致。 */
    private EveCharacterDO requireCurrentCharacter(UserContext context) {
        return characterMapper.selectActiveByUser(context.getTenantId(), context.getId())
            .stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException("当前账号未绑定有效 EVE 角色，请先完成授权"));
    }

    /** 同步成功后依据自动同步配置延后下一轮后台拉取。 */
    private void scheduleNextAutoSync(Long tenantId,
                                      EveSyncTargetType targetType,
                                      Long targetRefId,
                                      EveSyncModule module,
                                      LocalDateTime sourceExpiresAt) {
        syncJobService
            .scheduleAfterManualSuccess(tenantId, targetType, targetRefId, module, intervalFor(module), sourceExpiresAt);
    }

    /** 返回模块当前生效的自动同步间隔。 */
    private Duration intervalFor(EveSyncModule module) {
        return switch (module) {
            case ASSETS -> autoSyncProperties.getAssetsInterval();
            case STRUCTURES -> autoSyncProperties.getStructuresInterval();
            case MEMBER_ROSTER, MEMBER_TRACKING -> autoSyncProperties.getMembersInterval();
            case MOON_EXTRACTIONS -> autoSyncProperties.getMoonExtractionsInterval();
            case MINING_LEDGER -> autoSyncProperties.getMiningLedgerInterval();
            case GAME_MAIL -> autoSyncProperties.getMailInterval();
            case GAME_NOTIFICATIONS -> autoSyncProperties.getNotificationsInterval();
        };
    }
}
