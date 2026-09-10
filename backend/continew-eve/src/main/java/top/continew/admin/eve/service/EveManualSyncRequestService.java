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
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.model.EveMeContextResp;
import top.continew.admin.eve.model.EveSyncRequestResp;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.enums.EveSyncModule;
import top.continew.admin.eve.model.enums.EveSyncTargetType;
import top.continew.starter.core.exception.BusinessException;

import java.util.List;

/**
 * 将页面的手动同步操作收敛为持久化队列请求。
 *
 * <p>页面只会提权当前用户可见的目标；实际国服请求始终由后台任务统一执行，避免手动请求绕开缓存、退避和限流。</p>
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

    /** 提升当前军团共享数据模块的同步任务。 */
    @Transactional(rollbackFor = Exception.class)
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
        return request(context.getTenantId(), EveSyncTargetType.CORPORATION, corporation.getId(), module);
    }

    /** 提升当前登录用户主角色的个人邮箱同步任务。 */
    @Transactional(rollbackFor = Exception.class)
    public EveSyncRequestResp requestCurrentMail() {
        UserContext context = UserContextHolder.getContext();
        EveCharacterDO character = characterMapper.selectActiveByUser(context.getTenantId(), context.getId())
            .stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException("当前账号未绑定有效 EVE 角色，请先完成授权"));
        return request(context.getTenantId(), EveSyncTargetType.CHARACTER, character.getId(), EveSyncModule.GAME_MAIL);
    }

    /** 提升当前登录用户主角色的游戏通知同步任务。 */
    @Transactional(rollbackFor = Exception.class)
    public EveSyncRequestResp requestCurrentNotifications() {
        UserContext context = UserContextHolder.getContext();
        EveCharacterDO character = characterMapper.selectActiveByUser(context.getTenantId(), context.getId())
            .stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException("当前账号未绑定有效 EVE 角色，请先完成授权"));
        return request(context.getTenantId(), EveSyncTargetType.CHARACTER, character
            .getId(), EveSyncModule.GAME_NOTIFICATIONS);
    }

    /** 创建缺失任务，或把等待、暂停任务提升到允许的最早执行时间。 */
    private EveSyncRequestResp request(Long tenantId,
                                       EveSyncTargetType targetType,
                                       Long targetRefId,
                                       EveSyncModule module) {
        syncJobService.ensureJobs(tenantId, targetType, targetRefId, List.of(module));
        boolean accepted = syncJobService.requestManual(tenantId, targetType, targetRefId, module);
        return new EveSyncRequestResp(accepted, accepted ? "已加入同步队列，系统将在上游缓存和限流允许后自动更新。" : "该数据正在同步中，当前任务已合并。");
    }
}
