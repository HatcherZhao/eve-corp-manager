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
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.client.SerenityEsiClient;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterRoleSnapshotMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.mapper.EveCorporationStructureMapper;
import top.continew.admin.eve.mapper.EveMoonExtractionMapper;
import top.continew.admin.eve.model.EveMeContextResp;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.entity.EveMoonExtractionDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        EveMoonExtractionService service = new EveMoonExtractionService(mock(EveContextService.class), mock(EveCorporationMapper.class), mock(EveCorporationStructureMapper.class), mock(EveMoonExtractionMapper.class), authorizationMapper, roleSnapshotMapper, mock(EveAuthorizationLifecycleService.class), permissionRefreshService, mock(EveStaticReferenceService.class), mock(SerenityEsiClient.class), mock(RedissonClient.class), mock(EveDataFreshnessService.class));
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

    /** 月矿备注必须直接保存到快照记录，并清理首尾空白。 */
    @Test
    void shouldSaveNoteOnActiveMoonExtraction() {
        EveContextService contextService = mock(EveContextService.class);
        EveCorporationMapper corporationMapper = mock(EveCorporationMapper.class);
        EveMoonExtractionMapper extractionMapper = mock(EveMoonExtractionMapper.class);
        EveMoonExtractionService service = new EveMoonExtractionService(contextService, corporationMapper, mock(EveCorporationStructureMapper.class), extractionMapper, mock(EveAuthorizationMapper.class), mock(EveCharacterRoleSnapshotMapper.class), mock(EveAuthorizationLifecycleService.class), mock(EvePermissionRefreshService.class), mock(EveStaticReferenceService.class), mock(SerenityEsiClient.class), mock(RedissonClient.class), mock(EveDataFreshnessService.class));
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
        extraction.setStatus("ACTIVE");
        extraction.setDeleted(0L);
        when(contextService.getCurrentContext())
            .thenReturn(new EveMeContextResp(10L, null, new EveMeContextResp.CorporationInfo(300L, "测试军团", "TEST"), null, null, List
                .of(), null, List.of()));
        when(corporationMapper.selectByTenantAndCorporationId(10L, 300L)).thenReturn(corporation);
        when(extractionMapper.selectById(1L)).thenReturn(extraction);

        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(context);
            assertThat(service.saveNote(1L, "  今晚优先高价值矿种  ").note()).isEqualTo("今晚优先高价值矿种");
        }

        assertThat(extraction.getNote()).isEqualTo("今晚优先高价值矿种");
        assertThat(extraction.getUpdateUser()).isEqualTo(20L);
        verify(extractionMapper).updateById(extraction);
    }

    /** 维护备注时必须拒绝其他租户的月矿快照。 */
    @Test
    void shouldRejectSavingNoteForAnotherTenantExtraction() {
        EveContextService contextService = mock(EveContextService.class);
        EveCorporationMapper corporationMapper = mock(EveCorporationMapper.class);
        EveMoonExtractionMapper extractionMapper = mock(EveMoonExtractionMapper.class);
        EveMoonExtractionService service = new EveMoonExtractionService(contextService, corporationMapper, mock(EveCorporationStructureMapper.class), extractionMapper, mock(EveAuthorizationMapper.class), mock(EveCharacterRoleSnapshotMapper.class), mock(EveAuthorizationLifecycleService.class), mock(EvePermissionRefreshService.class), mock(EveStaticReferenceService.class), mock(SerenityEsiClient.class), mock(RedissonClient.class), mock(EveDataFreshnessService.class));
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
