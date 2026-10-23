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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.mapper.EveMoonOreProfileMapper;
import top.continew.admin.eve.model.EveMoonOreItemResp;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.entity.EveGameNotificationDO;
import top.continew.admin.eve.model.entity.EveMoonExtractionDO;
import top.continew.admin.eve.model.entity.EveMoonOreProfileDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.model.enums.EveCorporationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 月矿堡长期矿种档案初始化与刷新测试。
 *
 * @author zhaoyuqing
 */
class EveMoonOreProfileServiceTest {

    /** READY 档案自动初始化时不得读取或解析个人通知缓存。 */
    @Test
    void shouldSkipNotificationCacheForReadyProfile() throws Exception {
        Fixture fixture = fixture();
        EveMoonOreProfileDO ready = profile("READY", "[{\"typeId\":45492,\"typeName\":\"沥青\",\"proportion\":1}]");
        when(fixture.profileMapper.selectProfiles(eq(10L), eq(30L), anyCollection())).thenReturn(List.of(ready));

        assertThat(fixture.service.initializeMissing(fixture.corporation, List.of(fixture.extraction), 20L, 40L)
            .get(50L)
            .ores()).containsExactly(new EveMoonOreItemResp(45492, "沥青", BigDecimal.ONE));

        verify(fixture.profileMapper, never()).selectMoonNotifications(any(), any(), any());
        verify(fixture.profileMapper, never()).updateReadyIfPending(any(), any(), any());
        verify(fixture.authorizationMapper, never()).selectByUserCharacter(any(), any(), any());
    }

    /** 普通查看用户没有个人通知授权时仍可看到 PENDING，占位不会令月矿列表失败。 */
    @Test
    void shouldKeepPendingReadableWithoutNotificationAuthorization() {
        Fixture fixture = fixture();
        EveMoonOreProfileDO pending = profile("PENDING", null);
        when(fixture.profileMapper.selectProfiles(eq(10L), eq(30L), anyCollection())).thenReturn(List.of(pending));
        when(fixture.authorizationMapper.selectByUserCharacter(10L, 20L, 40L)).thenReturn(List.of());

        assertThat(fixture.service.initializeMissing(fixture.corporation, List.of(fixture.extraction), 20L, 40L)
            .get(50L)
            .status()).isEqualTo("PENDING");

        verify(fixture.profileMapper, never()).selectMoonNotifications(any(), any(), any());
    }

    /** 旧月球 READY 档案不能作为当前月球档案返回或触发通知读取。 */
    @Test
    void shouldHideProfileFromPreviousMoon() {
        Fixture fixture = fixture();
        EveMoonOreProfileDO oldMoon = profile("READY", "[{\"typeId\":45492,\"typeName\":\"沥青\",\"proportion\":1}]");
        oldMoon.setMoonId(61L);
        when(fixture.profileMapper.selectProfiles(eq(10L), eq(30L), anyCollection())).thenReturn(List.of(oldMoon));

        assertThat(fixture.service.initializeMissing(fixture.corporation, List.of(fixture.extraction), 20L, 40L)
            .get(50L)
            .status()).isEqualTo("PENDING");

        verify(fixture.profileMapper, never()).selectMoonNotifications(any(), any(), any());
    }

    /** 无建筑 ID 的通知仅在完整 ACTIVE 时间线中月球对应唯一矿堡时自动补齐。 */
    @Test
    void shouldInitializePendingProfileFromUniqueMoonNotification() {
        Fixture fixture = fixture();
        EveMoonOreProfileDO pending = profile("PENDING", null);
        EveMoonOreProfileDO ready = profile("READY", "[{\"typeId\":45492,\"typeName\":\"沥青\",\"proportion\":1.0000000000}]");
        when(fixture.profileMapper.selectProfiles(eq(10L), eq(30L), anyCollection())).thenReturn(List.of(pending), List
            .of(ready));
        when(fixture.profileMapper.selectMoonNotifications(10L, 20L, 40L)).thenReturn(List.of(notification("""
            moonID: 60
            oreVolumeByType:
              45492: 100
            """)));

        fixture.service.initializeMissing(fixture.corporation, List.of(fixture.extraction), 20L, 40L);

        verify(fixture.profileMapper)
            .updateReadyIfPending(any(EveMoonOreProfileDO.class), eq(20L), any(LocalDateTime.class));
    }

    /** 无建筑 ID 的通知在同一月球存在多个 ACTIVE 矿堡时必须拒绝猜测。 */
    @Test
    void shouldRejectMoonOnlyNotificationWhenMoonHasMultipleStructures() {
        Fixture fixture = fixture();
        EveMoonExtractionDO second = extraction(71L, 51L, 60L);
        EveMoonOreProfileDO firstPending = profile("PENDING", null);
        EveMoonOreProfileDO secondPending = profile("PENDING", null);
        secondPending.setStructureId(51L);
        when(fixture.profileMapper.selectProfiles(eq(10L), eq(30L), anyCollection())).thenReturn(List
            .of(firstPending, secondPending));
        when(fixture.profileMapper.selectMoonNotifications(10L, 20L, 40L)).thenReturn(List.of(notification("""
            moonID: 60
            oreVolumeByType:
              45492: 100
            """)));

        fixture.service.initializeMissing(fixture.corporation, List.of(fixture.extraction, second), 20L, 40L);

        verify(fixture.profileMapper, never()).updateReadyIfPending(any(), any(), any());
    }

    /** 手动刷新遇到畸形通知时必须保留 READY 档案，不能清空或降级。 */
    @Test
    void shouldKeepReadyProfileWhenManualRefreshHasNoValidNotification() {
        Fixture fixture = fixture();
        EveMoonOreProfileDO ready = profile("READY", "[{\"typeId\":45492,\"typeName\":\"沥青\",\"proportion\":1}]");
        when(fixture.profileMapper.selectActiveExtractions(10L, 30L)).thenReturn(List.of(fixture.extraction));
        when(fixture.profileMapper.selectProfiles(eq(10L), eq(30L), anyCollection())).thenReturn(List.of(ready));
        when(fixture.profileMapper.selectMoonNotifications(10L, 20L, 40L)).thenReturn(List.of(notification("""
            moonID: 60
            oreVolumeByType:
              45492: NaN
            """)));

        assertThat(fixture.service.refresh(fixture.corporation, fixture.extraction, 20L, 40L).status())
            .isEqualTo("READY");

        verify(fixture.profileMapper)
            .resetForMoonChange(eq(10L), eq(30L), eq(50L), eq(60L), eq(20L), any(LocalDateTime.class));
        verify(fixture.profileMapper, never()).forceUpdateReady(any(), any(), any());
    }

    /** 新通知落库后应立即补齐，即使普通页面刚检查过空缓存也不能再等十分钟。 */
    @Test
    void shouldCompletePendingImmediatelyAfterNewNotifications() {
        Fixture fixture = fixture();
        EveMoonOreProfileDO pending = profile("PENDING", null);
        pending.setLastCacheCheckedAt(LocalDateTime.now(ZoneOffset.UTC));
        when(fixture.profileMapper.selectActiveExtractions(10L, 30L)).thenReturn(List.of(fixture.extraction));
        when(fixture.profileMapper.selectProfiles(eq(10L), eq(30L), anyCollection())).thenReturn(List.of(pending));
        when(fixture.profileMapper.selectMoonNotifications(10L, 20L, 40L)).thenReturn(List.of(notification("""
            moonID: 60
            structureID: 50
            oreVolumeByType:
              45492: 100
            """)));

        fixture.service.initializeMissingForCharacter(10L, 20L, 40L);

        verify(fixture.profileMapper).selectMoonNotifications(10L, 20L, 40L);
        verify(fixture.profileMapper).updateReadyIfPending(any(), eq(20L), any(LocalDateTime.class));
    }

    /** 个人通知同步不依赖军团已认领，未认领时 hook 安全跳过。 */
    @Test
    void shouldSkipNotificationHookForUnclaimedCorporation() {
        Fixture fixture = fixture();
        when(fixture.corporationMapper.selectByTenantAndCorporationId(10L, 300L)).thenReturn(null);

        fixture.service.initializeMissingForCharacter(10L, 20L, 40L);

        verify(fixture.profileMapper, never()).selectActiveExtractions(any(), any());
        verify(fixture.profileMapper, never()).selectMoonNotifications(any(), any(), any());
    }

    /** 档案写入 SQL 必须以数据库当前 ACTIVE 月球为最终门禁。 */
    @Test
    void shouldGuardProfileWritesWithCurrentActiveMoon() throws Exception {
        String insertSql = String.join("\n", EveMoonOreProfileMapper.class
            .getMethod("insertPending", Long.class, Long.class, Long.class, Long.class, Long.class, LocalDateTime.class)
            .getAnnotation(Insert.class)
            .value());
        String resetSql = String.join("\n", EveMoonOreProfileMapper.class
            .getMethod("resetForMoonChange", Long.class, Long.class, Long.class, Long.class, Long.class, LocalDateTime.class)
            .getAnnotation(Update.class)
            .value());
        String autoUpdateSql = String.join("\n", EveMoonOreProfileMapper.class
            .getMethod("updateReadyIfPending", EveMoonOreProfileDO.class, Long.class, LocalDateTime.class)
            .getAnnotation(Update.class)
            .value());
        String forceUpdateSql = String.join("\n", EveMoonOreProfileMapper.class
            .getMethod("forceUpdateReady", EveMoonOreProfileDO.class, Long.class, LocalDateTime.class)
            .getAnnotation(Update.class)
            .value());
        String touchSql = String.join("\n", EveMoonOreProfileMapper.class
            .getMethod("touchPendingChecked", Long.class, Long.class, Long.class, Long.class, Long.class, LocalDateTime.class)
            .getAnnotation(Update.class)
            .value());

        assertThat(insertSql)
            .contains("WHERE EXISTS", "active_extraction.moon_id = #{moonId}", "active_extraction.status = 'ACTIVE'");
        assertThat(resetSql)
            .contains("AND EXISTS", "active_extraction.moon_id = #{moonId}", "active_extraction.status = 'ACTIVE'");
        assertThat(autoUpdateSql)
            .contains("moon_id = #{profile.moonId}", "active_extraction.moon_id = #{profile.moonId}", "active_extraction.status = 'ACTIVE'");
        assertThat(forceUpdateSql)
            .contains("moon_id = #{profile.moonId}", "active_extraction.moon_id = #{profile.moonId}", "active_extraction.status = 'ACTIVE'");
        assertThat(touchSql).contains("moon_id = #{moonId}");
    }

    /** 创建具备严格本人、角色、军团及通知 Scope 链路的测试夹具。 */
    private static Fixture fixture() {
        EveMoonOreProfileMapper profileMapper = mock(EveMoonOreProfileMapper.class);
        EveCharacterMapper characterMapper = mock(EveCharacterMapper.class);
        EveAuthorizationMapper authorizationMapper = mock(EveAuthorizationMapper.class);
        EveCorporationMapper corporationMapper = mock(EveCorporationMapper.class);
        EveCorporationDO corporation = new EveCorporationDO();
        corporation.setId(30L);
        corporation.setTenantId(10L);
        corporation.setCorporationId(300L);
        corporation.setServer("serenity");
        corporation.setStatus(EveCorporationStatus.ACTIVE);
        EveCharacterDO character = new EveCharacterDO();
        character.setId(40L);
        character.setTenantId(10L);
        character.setUserId(20L);
        character.setCorporationId(300L);
        character.setServer("serenity");
        EveAuthorizationDO authorization = new EveAuthorizationDO();
        authorization.setStatus(EveAuthorizationStatus.ACTIVE);
        authorization.setScopes(List.of("esi-characters.read_notifications.v1"));
        EveMoonExtractionDO extraction = extraction(70L, 50L, 60L);
        when(characterMapper.selectActiveByTenantUserAndRefId(10L, 20L, 40L)).thenReturn(character);
        when(authorizationMapper.selectByUserCharacter(10L, 20L, 40L)).thenReturn(List.of(authorization));
        when(corporationMapper.selectByTenantAndCorporationId(10L, 300L)).thenReturn(corporation);
        EveMoonOreProfileService service = new EveMoonOreProfileService(profileMapper, characterMapper, authorizationMapper, corporationMapper, new EveGameNotificationPresentationService(new EveStaticNameReference()), new ObjectMapper());
        return new Fixture(service, profileMapper, authorizationMapper, corporationMapper, corporation, extraction);
    }

    /** 创建指定状态的档案实体。 */
    private static EveMoonOreProfileDO profile(String status, String oresJson) {
        EveMoonOreProfileDO profile = new EveMoonOreProfileDO();
        profile.setTenantId(10L);
        profile.setCorporationRefId(30L);
        profile.setStructureId(50L);
        profile.setMoonId(60L);
        profile.setStatus(status);
        profile.setOresJson(oresJson);
        profile.setDeleted(0L);
        return profile;
    }

    /** 创建本军团 ACTIVE 月矿时间线。 */
    private static EveMoonExtractionDO extraction(Long id, Long structureId, Long moonId) {
        EveMoonExtractionDO extraction = new EveMoonExtractionDO();
        extraction.setId(id);
        extraction.setTenantId(10L);
        extraction.setCorporationRefId(30L);
        extraction.setStructureId(structureId);
        extraction.setMoonId(moonId);
        extraction.setStatus("ACTIVE");
        extraction.setDeleted(0L);
        return extraction;
    }

    /** 创建当前角色缓存中的月矿通知。 */
    private static EveGameNotificationDO notification(String content) {
        EveGameNotificationDO notification = new EveGameNotificationDO();
        notification.setNotificationId(80L);
        notification.setNotificationType("MoonminingExtractionStarted");
        notification.setContent(content);
        notification.setSentAt(LocalDateTime.of(2026, 10, 24, 4, 0));
        return notification;
    }

    /** 测试所需依赖与领域对象。 */
    private record Fixture(EveMoonOreProfileService service, EveMoonOreProfileMapper profileMapper,
                           EveAuthorizationMapper authorizationMapper, EveCorporationMapper corporationMapper,
                           EveCorporationDO corporation, EveMoonExtractionDO extraction) {
    }
}
