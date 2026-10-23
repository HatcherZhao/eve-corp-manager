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
import org.redisson.api.RedissonClient;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.client.SerenityEsiClient;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterRoleSnapshotMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.mapper.EveCorporationStructureMapper;
import top.continew.admin.eve.mapper.EveMoonExtractionMapper;
import top.continew.admin.eve.mapper.EveMoonStructureNoteMapper;
import top.continew.admin.eve.model.EveMeContextResp;
import top.continew.admin.eve.model.EveMoonOreProfileResp;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.entity.EveMoonExtractionDO;
import top.continew.admin.eve.model.entity.EveMoonStructureNoteDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;

/**
 * 月矿同步数据源选择测试。
 *
 * @author zhaoyuqing
 */
class EveMoonExtractionServiceTest {

    /** 角色快照过期时，刷新后出现的空间站管理员必须可以成为月矿同步来源。 */
    @Test
    void shouldRefreshExpiredRoleSnapshotBeforeSelectingMoonExtractionSource() throws Exception {
        EveAuthorizationMapper authorizationMapper = mock(EveAuthorizationMapper.class);
        EveCharacterRoleSnapshotMapper roleSnapshotMapper = mock(EveCharacterRoleSnapshotMapper.class);
        EvePermissionRefreshService permissionRefreshService = mock(EvePermissionRefreshService.class);
        EveMoonExtractionService service = new EveMoonExtractionService(mock(EveContextService.class), mock(EveCorporationMapper.class), mock(EveCorporationStructureMapper.class), mock(EveMoonExtractionMapper.class), mock(EveMoonStructureNoteMapper.class), authorizationMapper, roleSnapshotMapper, mock(EveAuthorizationLifecycleService.class), permissionRefreshService, mock(EveStaticReferenceService.class), mock(SerenityEsiClient.class), mock(RedissonClient.class), mock(EveDataFreshnessService.class), mock(EveMoonOreProfileService.class), mock(EveGameNotificationService.class));
        EveAuthorizationDO authorization = authorization();
        when(authorizationMapper.selectTenantCandidates(10L)).thenReturn(List.of(authorization));
        when(roleSnapshotMapper.selectLatest(10L, 100L)).thenReturn(snapshot(LocalDateTime.now(ZoneOffset.UTC)
            .minusMinutes(1)), snapshot(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5)));

        Method selectSource = EveMoonExtractionService.class.getDeclaredMethod("selectSource", Long.class);
        selectSource.setAccessible(true);
        EveAuthorizationDO result = (EveAuthorizationDO)selectSource.invoke(service, 10L);

        assertThat(result).isSameAs(authorization);
        verify(permissionRefreshService).reviewAuthorization(authorization);
    }

    /** 月矿堡备注必须按建筑保存，并清理首尾空白。 */
    @Test
    void shouldSaveNoteOnActiveMoonExtraction() {
        EveContextService contextService = mock(EveContextService.class);
        EveCorporationMapper corporationMapper = mock(EveCorporationMapper.class);
        EveMoonExtractionMapper extractionMapper = mock(EveMoonExtractionMapper.class);
        EveMoonStructureNoteMapper structureNoteMapper = mock(EveMoonStructureNoteMapper.class);
        EveMoonExtractionService service = new EveMoonExtractionService(contextService, corporationMapper, mock(EveCorporationStructureMapper.class), extractionMapper, structureNoteMapper, mock(EveAuthorizationMapper.class), mock(EveCharacterRoleSnapshotMapper.class), mock(EveAuthorizationLifecycleService.class), mock(EvePermissionRefreshService.class), mock(EveStaticReferenceService.class), mock(SerenityEsiClient.class), mock(RedissonClient.class), mock(EveDataFreshnessService.class), mock(EveMoonOreProfileService.class), mock(EveGameNotificationService.class));
        UserContext context = new UserContext();
        context.setId(20L);
        context.setTenantId(10L);
        EveCorporationDO corporation = new EveCorporationDO();
        corporation.setId(30L);
        corporation.setTenantId(10L);
        EveMoonExtractionDO extraction = new EveMoonExtractionDO();
        extraction.setId(1L);
        extraction.setTenantId(10L);
        extraction.setCorporationRefId(30L);
        extraction.setStructureId(40L);
        extraction.setStatus("ACTIVE");
        extraction.setDeleted(0L);
        when(contextService.getCurrentContext())
            .thenReturn(new EveMeContextResp(10L, null, new EveMeContextResp.CorporationInfo(300L, "测试军团", "TEST"), null, null, List
                .of(), null, List.of()));
        when(corporationMapper.selectByTenantAndCorporationId(10L, 300L)).thenReturn(corporation);
        when(extractionMapper.selectById(1L)).thenReturn(extraction);

        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(context);
            assertThat(service.saveNote(1L, "  今晚优先高价值矿种  ").structureNote()).isEqualTo("今晚优先高价值矿种");
        }

        verify(structureNoteMapper).insert(argThat((EveMoonStructureNoteDO item) -> item.getTenantId()
            .equals(10L) && item.getCorporationRefId().equals(30L) && item.getStructureId().equals(40L) && "今晚优先高价值矿种"
                .equals(item.getNote()) && item.getCreateUser().equals(20L)));
        verify(extractionMapper, never()).updateById(extraction);
    }

    /** 维护备注时必须拒绝其他租户的月矿快照。 */
    @Test
    void shouldRejectSavingNoteForAnotherTenantExtraction() {
        EveContextService contextService = mock(EveContextService.class);
        EveCorporationMapper corporationMapper = mock(EveCorporationMapper.class);
        EveMoonExtractionMapper extractionMapper = mock(EveMoonExtractionMapper.class);
        EveMoonStructureNoteMapper structureNoteMapper = mock(EveMoonStructureNoteMapper.class);
        EveMoonExtractionService service = new EveMoonExtractionService(contextService, corporationMapper, mock(EveCorporationStructureMapper.class), extractionMapper, structureNoteMapper, mock(EveAuthorizationMapper.class), mock(EveCharacterRoleSnapshotMapper.class), mock(EveAuthorizationLifecycleService.class), mock(EvePermissionRefreshService.class), mock(EveStaticReferenceService.class), mock(SerenityEsiClient.class), mock(RedissonClient.class), mock(EveDataFreshnessService.class), mock(EveMoonOreProfileService.class), mock(EveGameNotificationService.class));
        UserContext context = new UserContext();
        context.setId(20L);
        context.setTenantId(10L);
        EveCorporationDO corporation = new EveCorporationDO();
        corporation.setId(30L);
        corporation.setTenantId(10L);
        EveMoonExtractionDO extraction = new EveMoonExtractionDO();
        extraction.setId(1L);
        extraction.setTenantId(11L);
        extraction.setCorporationRefId(30L);
        extraction.setStatus("ACTIVE");
        extraction.setDeleted(0L);
        when(contextService.getCurrentContext())
            .thenReturn(new EveMeContextResp(10L, null, new EveMeContextResp.CorporationInfo(300L, "测试军团", "TEST"), null, null, List
                .of(), null, List.of()));
        when(corporationMapper.selectByTenantAndCorporationId(10L, 300L)).thenReturn(corporation);
        when(extractionMapper.selectById(1L)).thenReturn(extraction);

        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(context);
            assertThatThrownBy(() -> service.saveNote(1L, "越权修改")).hasMessage("月矿时间线不存在或已失效");
        }

        verify(extractionMapper, never()).updateById(extraction);
        verify(structureNoteMapper, never()).insert(org.mockito.ArgumentMatchers.any(EveMoonStructureNoteDO.class));
    }

    /** 第一次按矿种搜索前先补全本地档案，整个列表查询不得额外读取国服通知。 */
    @Test
    void shouldInitializeProfilesBeforeMineralSearchWithoutUpstreamCalls() {
        ProfileFixture f = new ProfileFixture();
        EveMoonOreProfileResp profile = new EveMoonOreProfileResp("PENDING", List.of(), null, null);
        when(f.profiles.initializeMissing(f.corporation, List.of(f.extraction), 20L, 100L)).thenReturn(Map
            .of(40L, profile));
        when(f.extractions.selectList(any())).thenReturn(List.of(f.extraction));
        when(f.extractions.selectPage(any(Page.class), any())).thenReturn(new Page<EveMoonExtractionDO>(1, 20, 1)
            .setRecords(List.of(f.extraction)));
        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(f.context);
            var result = f.service.page(1, 20, "沥青", null);
            assertThat(result.getList()).hasSize(1);
            assertThat(result.getList().get(0).oreProfile()).isSameAs(profile);
        }
        var ordered = inOrder(f.profiles, f.extractions);
        ordered.verify(f.extractions).selectList(any());
        ordered.verify(f.profiles).initializeMissing(f.corporation, List.of(f.extraction), 20L, 100L);
        ordered.verify(f.extractions).selectPage(any(Page.class), any());
        verifyNoInteractions(f.notifications, f.esi);
    }

    /** 手动刷新必须先拒绝其他租户目标，不能触发本人通知同步或档案更新。 */
    @Test
    void shouldRejectForeignProfileRefreshBeforeAnyNotificationRead() {
        ProfileFixture f = new ProfileFixture();
        f.extraction.setTenantId(99L);
        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(f.context);
            assertThatThrownBy(() -> f.service.refreshOreProfile(1L)).hasMessage("月矿时间线不存在或已失效");
        }
        verifyNoInteractions(f.notifications, f.profiles, f.esi);
    }

    /** 合法手动刷新使用当前军团及本人角色，并在通知校验完成后更新档案。 */
    @Test
    void shouldRefreshOnlyCurrentCorporationProfile() {
        ProfileFixture f = new ProfileFixture();
        EveMoonOreProfileResp profile = new EveMoonOreProfileResp("READY", List.of(), null, null);
        when(f.profiles.refresh(f.corporation, f.extraction, 20L, 100L)).thenReturn(profile);
        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(f.context);
            assertThat(f.service.refreshOreProfile(1L).oreProfile()).isSameAs(profile);
        }
        var ordered = inOrder(f.notifications, f.profiles);
        ordered.verify(f.notifications).syncCurrentUserForMoonProfile(300L);
        ordered.verify(f.profiles).refresh(f.corporation, f.extraction, 20L, 100L);
    }

    /** 构造本军团时间线与当前用户上下文，所有外部依赖均为测试替身。 */
    private static final class ProfileFixture {
        private final UserContext context = new UserContext();
        private final EveContextService contextService = mock(EveContextService.class);
        private final EveCorporationMapper corporations = mock(EveCorporationMapper.class);
        private final EveMoonExtractionMapper extractions = mock(EveMoonExtractionMapper.class);
        private final EveMoonOreProfileService profiles = mock(EveMoonOreProfileService.class);
        private final EveGameNotificationService notifications = mock(EveGameNotificationService.class);
        private final SerenityEsiClient esi = mock(SerenityEsiClient.class);
        private final EveCorporationDO corporation = new EveCorporationDO();
        private final EveMoonExtractionDO extraction = new EveMoonExtractionDO();
        private final EveMoonExtractionService service;

        /** 绑定明确的租户、用户、角色和军团，避免测试误用真实授权。 */
        private ProfileFixture() {
            context.setId(20L);
            context.setTenantId(10L);
            corporation.setId(30L);
            corporation.setTenantId(10L);
            corporation.setCorporationId(300L);
            extraction.setId(1L);
            extraction.setTenantId(10L);
            extraction.setCorporationRefId(30L);
            extraction.setStructureId(40L);
            extraction.setMoonId(50L);
            extraction.setStatus("ACTIVE");
            extraction.setDeleted(0L);
            when(contextService.getCurrentContext())
                .thenReturn(new EveMeContextResp(10L, new EveMeContextResp.CharacterInfo(100L, 200L, "测试角色"), new EveMeContextResp.CorporationInfo(300L, "测试军团", "TEST"), null, null, List
                    .of(), null, List.of()));
            when(corporations.selectByTenantAndCorporationId(10L, 300L)).thenReturn(corporation);
            when(extractions.selectById(1L)).thenReturn(extraction);
            service = new EveMoonExtractionService(contextService, corporations, mock(EveCorporationStructureMapper.class), extractions, mock(EveMoonStructureNoteMapper.class), mock(EveAuthorizationMapper.class), mock(EveCharacterRoleSnapshotMapper.class), mock(EveAuthorizationLifecycleService.class), mock(EvePermissionRefreshService.class), mock(EveStaticReferenceService.class), esi, mock(RedissonClient.class), mock(EveDataFreshnessService.class), profiles, notifications);
        }
    }

    /** 构造最小可用的月矿读取授权。 */
    private static EveAuthorizationDO authorization() {
        EveAuthorizationDO authorization = new EveAuthorizationDO();
        authorization.setTenantId(10L);
        authorization.setCharacterRefId(100L);
        authorization.setStatus(EveAuthorizationStatus.ACTIVE);
        authorization.setScopes(List.of("esi-industry.read_corporation_mining.v1"));
        return authorization;
    }

    /** 构造空间站管理员角色快照，并由调用方指定国服缓存到期时间。 */
    private static EveCharacterRoleSnapshotDO snapshot(LocalDateTime expiresAt) {
        EveCharacterRoleSnapshotDO snapshot = new EveCharacterRoleSnapshotDO();
        snapshot.setTenantId(10L);
        snapshot.setCharacterRefId(100L);
        snapshot.setRoles(List.of("Station_Manager"));
        snapshot.setSourceExpiresAt(expiresAt);
        return snapshot;
    }
}
