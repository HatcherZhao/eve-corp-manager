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
import org.redisson.api.RedissonClient;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.client.SerenityEsiClient;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterRoleSnapshotMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.mapper.EveCorporationStructureMapper;
import top.continew.admin.eve.mapper.EveMiningLedgerMapper;
import top.continew.admin.eve.mapper.EveMiningObserverMapper;
import top.continew.admin.eve.mapper.EveMiningSyncRunMapper;
import top.continew.admin.eve.model.EveMeContextResp;
import top.continew.admin.eve.model.EveMiningAnalyticsResp;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采矿账本数据源选择测试。
 *
 * @author zhaoyuqing
 */
class EveMiningLedgerServiceTest {

    /** MySQL 聚合列会返回 JDBC 日期类型，页面汇总必须能稳定转换。 */
    @Test
    void shouldConvertJdbcAggregateDates() {
        LocalDate date = LocalDate.of(2026, 9, 9);
        LocalDateTime dateTime = LocalDateTime.of(2026, 9, 9, 16, 59, 57);

        assertThat(EveMiningLedgerService.toLocalDate(java.sql.Date.valueOf(date))).isEqualTo(date);
        assertThat(EveMiningLedgerService.toLocalDateTime(java.sql.Timestamp.valueOf(dateTime))).isEqualTo(dateTime);
    }

    /** 图表聚合必须限定当前租户军团，并且只返回业务名称而不暴露游戏内部 ID。 */
    @Test
    void shouldBuildCurrentCorporationAnalyticsWithoutInternalIds() {
        EveContextService contextService = mock(EveContextService.class);
        EveCorporationMapper corporationMapper = mock(EveCorporationMapper.class);
        EveMiningLedgerMapper ledgerMapper = mock(EveMiningLedgerMapper.class);
        EveMiningLedgerService service = new EveMiningLedgerService(contextService, corporationMapper, mock(EveCorporationStructureMapper.class), mock(EveMiningObserverMapper.class), ledgerMapper, mock(EveMiningSyncRunMapper.class), mock(EveAuthorizationMapper.class), mock(EveCharacterRoleSnapshotMapper.class), mock(EveAuthorizationLifecycleService.class), mock(EvePermissionRefreshService.class), mock(EveStaticReferenceService.class), mock(SerenityEsiClient.class), mock(RedissonClient.class), mock(EveDataFreshnessService.class));
        UserContext userContext = new UserContext();
        userContext.setTenantId(10L);
        EveCorporationDO corporation = new EveCorporationDO();
        corporation.setId(20L);
        corporation.setTenantId(10L);
        corporation.setCorporationId(30L);
        corporation.setName("测试军团");
        corporation.setTicker("TEST");
        LocalDate fromDate = LocalDate.of(2026, 9, 1);
        LocalDate toDate = LocalDate.of(2026, 9, 12);
        when(contextService.getCurrentContext())
            .thenReturn(new EveMeContextResp(10L, null, new EveMeContextResp.CorporationInfo(30L, "测试军团", "TEST"), null, null, List
                .of(), null, List.of()));
        when(corporationMapper.selectByTenantAndCorporationId(10L, 30L)).thenReturn(corporation);
        when(ledgerMapper.summarize(10L, 20L, null, null, null, fromDate, toDate, "铁")).thenReturn(Map
            .of("quantity", 1200L, "entryCount", 12L, "observerCount", 2L, "characterCount", 3L, "mineralTypeCount", 4L));
        when(ledgerMapper.summarizeTimeline(10L, 20L, fromDate, toDate, "铁")).thenReturn(List.of(Map
            .of("recordedAt", java.sql.Date.valueOf(fromDate), "quantity", 800L, "entryCount", 8L)));
        when(ledgerMapper.summarizeObservers(10L, 20L, fromDate, toDate, "铁", 10)).thenReturn(List.of(Map
            .of("observerName", "月矿堡", "quantity", 900L, "entryCount", 9L)));
        when(ledgerMapper.summarizeCharacters(10L, 20L, fromDate, toDate, "铁", 10)).thenReturn(List.of(Map
            .of("characterName", "矿工甲", "quantity", 700L, "entryCount", 7L)));

        UserContextHolder.setContext(userContext, false);
        try {
            EveMiningAnalyticsResp result = service.analytics(fromDate, toDate, " 铁 ");

            assertThat(result.corporation().name()).isEqualTo("测试军团");
            assertThat(result.corporation().quantity()).isEqualTo(1200L);
            assertThat(result.timeline()).containsExactly(new EveMiningAnalyticsResp.TimelinePoint(fromDate, 800L, 8L));
            assertThat(result.observers()).containsExactly(new EveMiningAnalyticsResp.ObserverRanking("月矿堡", 900L, 9L));
            assertThat(result.characters())
                .containsExactly(new EveMiningAnalyticsResp.CharacterRanking("矿工甲", 700L, 7L));
        } finally {
            UserContextHolder.clearContext();
        }
    }

    /** 角色缓存过期时应主动复核，且只有 Accountant 能成为观察者账本数据源。 */
    @Test
    void shouldRefreshExpiredAccountantSnapshotBeforeSelectingMiningSource() throws Exception {
        EveAuthorizationMapper authorizationMapper = mock(EveAuthorizationMapper.class);
        EveCharacterRoleSnapshotMapper roleSnapshotMapper = mock(EveCharacterRoleSnapshotMapper.class);
        EvePermissionRefreshService permissionRefreshService = mock(EvePermissionRefreshService.class);
        EveMiningLedgerService service = new EveMiningLedgerService(mock(EveContextService.class), mock(EveCorporationMapper.class), mock(EveCorporationStructureMapper.class), mock(EveMiningObserverMapper.class), mock(EveMiningLedgerMapper.class), mock(EveMiningSyncRunMapper.class), authorizationMapper, roleSnapshotMapper, mock(EveAuthorizationLifecycleService.class), permissionRefreshService, mock(EveStaticReferenceService.class), mock(SerenityEsiClient.class), mock(RedissonClient.class), mock(EveDataFreshnessService.class));
        EveAuthorizationDO authorization = authorization();
        when(authorizationMapper.selectTenantCandidates(10L)).thenReturn(List.of(authorization));
        when(roleSnapshotMapper.selectLatest(10L, 100L)).thenReturn(snapshot(List.of("Accountant"), LocalDateTime
            .now(ZoneOffset.UTC)
            .minusMinutes(1)), snapshot(List.of("Accountant"), LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5)));

        Method selectSource = EveMiningLedgerService.class.getDeclaredMethod("selectSource", Long.class);
        selectSource.setAccessible(true);
        EveAuthorizationDO result = (EveAuthorizationDO)selectSource.invoke(service, 10L);

        assertThat(result).isSameAs(authorization);
        verify(permissionRefreshService).reviewAuthorization(authorization);
    }

    /** 不能用总监权限猜测会计权限，国服契约要求的 Accountant 必须真实存在。 */
    @Test
    void shouldRejectDirectorWithoutAccountantRole() throws Exception {
        EveAuthorizationMapper authorizationMapper = mock(EveAuthorizationMapper.class);
        EveCharacterRoleSnapshotMapper roleSnapshotMapper = mock(EveCharacterRoleSnapshotMapper.class);
        EvePermissionRefreshService permissionRefreshService = mock(EvePermissionRefreshService.class);
        EveMiningLedgerService service = new EveMiningLedgerService(mock(EveContextService.class), mock(EveCorporationMapper.class), mock(EveCorporationStructureMapper.class), mock(EveMiningObserverMapper.class), mock(EveMiningLedgerMapper.class), mock(EveMiningSyncRunMapper.class), authorizationMapper, roleSnapshotMapper, mock(EveAuthorizationLifecycleService.class), permissionRefreshService, mock(EveStaticReferenceService.class), mock(SerenityEsiClient.class), mock(RedissonClient.class), mock(EveDataFreshnessService.class));
        EveAuthorizationDO authorization = authorization();
        when(authorizationMapper.selectTenantCandidates(10L)).thenReturn(List.of(authorization));
        when(roleSnapshotMapper.selectLatest(10L, 100L)).thenReturn(snapshot(List.of("Director"), LocalDateTime
            .now(ZoneOffset.UTC)
            .plusMinutes(5)));

        Method selectSource = EveMiningLedgerService.class.getDeclaredMethod("selectSource", Long.class);
        selectSource.setAccessible(true);

        assertThat(selectSource.invoke(service, 10L)).isNull();
        verify(permissionRefreshService).reviewAuthorization(authorization);
    }

    /** 构造最小可用的采矿 Scope 授权。 */
    private static EveAuthorizationDO authorization() {
        EveAuthorizationDO authorization = new EveAuthorizationDO();
        authorization.setTenantId(10L);
        authorization.setCharacterRefId(100L);
        authorization.setStatus(EveAuthorizationStatus.ACTIVE);
        authorization.setScopes(List.of("esi-industry.read_corporation_mining.v1"));
        return authorization;
    }

    /** 构造包含指定游戏角色的快照。 */
    private static EveCharacterRoleSnapshotDO snapshot(List<String> roles, LocalDateTime expiresAt) {
        EveCharacterRoleSnapshotDO snapshot = new EveCharacterRoleSnapshotDO();
        snapshot.setTenantId(10L);
        snapshot.setCharacterRefId(100L);
        snapshot.setRoles(roles);
        snapshot.setSourceExpiresAt(expiresAt);
        return snapshot;
    }
}
