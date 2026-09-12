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
import org.mockito.MockedStatic;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.config.EveAutoSyncProperties;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.model.EveAssetSyncResp;
import top.continew.admin.eve.model.EveGameMailSyncResp;
import top.continew.admin.eve.model.EveGameNotificationSyncResp;
import top.continew.admin.eve.model.EveMeContextResp;
import top.continew.admin.eve.model.EveMemberSyncResp;
import top.continew.admin.eve.model.EveMiningSyncResp;
import top.continew.admin.eve.model.EveMoonExtractionSyncResp;
import top.continew.admin.eve.model.EveStructureSyncResp;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.enums.EveSyncModule;
import top.continew.admin.eve.model.enums.EveSyncTargetType;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 页面手动同步必须直接调用业务服务的路由测试。 */
class EveManualSyncRequestServiceTest {

    /** 全部军团模块均应在当前请求内同步，并重排下一轮自动任务。 */
    @Test
    void shouldSynchronizeCorporationModulesImmediately() {
        Fixture fixture = new Fixture();
        fixture.prepareCorporation();
        LocalDateTime sourceExpiresAt = LocalDateTime.of(2026, 9, 12, 12, 0);
        when(fixture.assetService.syncCurrentTenant())
            .thenReturn(new EveAssetSyncResp(1, 1, sourceExpiresAt, sourceExpiresAt));
        when(fixture.structureService.syncCurrentTenant())
            .thenReturn(new EveStructureSyncResp(1, 1, sourceExpiresAt, sourceExpiresAt));
        when(fixture.memberService.syncCurrentTenant())
            .thenReturn(new EveMemberSyncResp(1, 1, true, null, sourceExpiresAt, sourceExpiresAt, sourceExpiresAt));
        when(fixture.moonExtractionService.syncCurrentTenant())
            .thenReturn(new EveMoonExtractionSyncResp(1, 1, sourceExpiresAt, sourceExpiresAt));
        when(fixture.miningLedgerService.syncCurrentTenant())
            .thenReturn(new EveMiningSyncResp(1, 1, 1, sourceExpiresAt, sourceExpiresAt));

        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(context());
            for (EveSyncModule module : List
                .of(EveSyncModule.ASSETS, EveSyncModule.STRUCTURES, EveSyncModule.MEMBER_ROSTER, EveSyncModule.MEMBER_TRACKING, EveSyncModule.MOON_EXTRACTIONS, EveSyncModule.MINING_LEDGER)) {
                assertThat(fixture.service.requestCurrentCorporation(module).accepted()).isTrue();
            }
        }

        verify(fixture.assetService).syncCurrentTenant();
        verify(fixture.structureService).syncCurrentTenant();
        verify(fixture.memberService, times(2)).syncCurrentTenant();
        verify(fixture.moonExtractionService).syncCurrentTenant();
        verify(fixture.miningLedgerService).syncCurrentTenant();
        verify(fixture.syncJobService, times(6))
            .scheduleAfterManualSuccess(eq(10L), eq(EveSyncTargetType.CORPORATION), eq(30L), any(), any(), eq(sourceExpiresAt));
    }

    /** 邮件和通知应直接同步当前授权角色，不等待自动队列。 */
    @Test
    void shouldSynchronizePersonalCommunicationImmediately() {
        Fixture fixture = new Fixture();
        LocalDateTime sourceExpiresAt = LocalDateTime.of(2026, 9, 12, 12, 0);
        EveCharacterDO character = new EveCharacterDO();
        character.setId(40L);
        when(fixture.characterMapper.selectActiveByUser(10L, 20L)).thenReturn(List.of(character));
        when(fixture.gameMailService.syncCurrentUser())
            .thenReturn(new EveGameMailSyncResp(1, sourceExpiresAt, sourceExpiresAt));
        when(fixture.gameNotificationService.syncCurrentUser())
            .thenReturn(new EveGameNotificationSyncResp(1, sourceExpiresAt, sourceExpiresAt));

        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(context());
            assertThat(fixture.service.requestCurrentMail().accepted()).isTrue();
            assertThat(fixture.service.requestCurrentNotifications().accepted()).isTrue();
        }

        verify(fixture.gameMailService).syncCurrentUser();
        verify(fixture.gameNotificationService).syncCurrentUser();
        verify(fixture.syncJobService)
            .scheduleAfterManualSuccess(10L, EveSyncTargetType.CHARACTER, 40L, EveSyncModule.GAME_MAIL, fixture.properties
                .getMailInterval(), sourceExpiresAt);
        verify(fixture.syncJobService)
            .scheduleAfterManualSuccess(10L, EveSyncTargetType.CHARACTER, 40L, EveSyncModule.GAME_NOTIFICATIONS, fixture.properties
                .getNotificationsInterval(), sourceExpiresAt);
    }

    /** 构造隔离的手动同步服务及其依赖。 */
    private static final class Fixture {

        private final EveContextService contextService = mock(EveContextService.class);
        private final EveCorporationMapper corporationMapper = mock(EveCorporationMapper.class);
        private final EveCharacterMapper characterMapper = mock(EveCharacterMapper.class);
        private final EveSyncJobService syncJobService = mock(EveSyncJobService.class);
        private final EveAutoSyncProperties properties = new EveAutoSyncProperties();
        private final EveCorporationAssetService assetService = mock(EveCorporationAssetService.class);
        private final EveCorporationStructureService structureService = mock(EveCorporationStructureService.class);
        private final EveCorporationMemberService memberService = mock(EveCorporationMemberService.class);
        private final EveMoonExtractionService moonExtractionService = mock(EveMoonExtractionService.class);
        private final EveMiningLedgerService miningLedgerService = mock(EveMiningLedgerService.class);
        private final EveGameMailService gameMailService = mock(EveGameMailService.class);
        private final EveGameNotificationService gameNotificationService = mock(EveGameNotificationService.class);
        private final EveManualSyncRequestService service = new EveManualSyncRequestService(contextService, corporationMapper, characterMapper, syncJobService, properties, assetService, structureService, memberService, moonExtractionService, miningLedgerService, gameMailService, gameNotificationService);

        /** 准备当前用户可访问的军团及其持久化目标。 */
        private void prepareCorporation() {
            EveCorporationDO corporation = new EveCorporationDO();
            corporation.setId(30L);
            when(contextService.getCurrentContext())
                .thenReturn(new EveMeContextResp(10L, null, new EveMeContextResp.CorporationInfo(300L, "测试军团", "TEST"), null, null, List
                    .of(), null, List.of()));
            when(corporationMapper.selectByTenantAndCorporationId(10L, 300L)).thenReturn(corporation);
        }
    }

    /** 构造当前租户及用户上下文。 */
    private static UserContext context() {
        UserContext context = new UserContext();
        context.setTenantId(10L);
        context.setId(20L);
        return context;
    }
}
