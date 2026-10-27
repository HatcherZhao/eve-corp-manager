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
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.client.SerenityEsiClient;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveGameNotificationMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.mapper.EveCorporationStructureMapper;
import top.continew.admin.eve.model.EveGameNotificationPresentation;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.entity.EveCorporationStructureDO;
import top.continew.admin.eve.model.entity.EveGameNotificationDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.model.serenity.SerenityEsiResponse;
import top.continew.starter.core.exception.BusinessException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 游戏通知类型分类测试。
 *
 * @author zhaoyuqing
 */
class EveGameNotificationServiceTest {

    /** 已知关键类型应进入稳定菜单分类，未知类型必须保留在其他。 */
    @Test
    void shouldClassifyNotificationTypesWithoutDroppingUnknownTypes() {
        assertThat(EveGameNotificationService.categoryOf("CorpAppNewMsg")).isEqualTo("CORPORATION_MEMBER");
        assertThat(EveGameNotificationService.categoryOf("StructureFuelAlert")).isEqualTo("STRUCTURE_ASSET_SAFETY");
        assertThat(EveGameNotificationService.categoryOf("WarDeclaredMsg")).isEqualTo("WAR_SOVEREIGNTY");
        assertThat(EveGameNotificationService.categoryOf("MoonMiningExtractionStarted")).isEqualTo("MOON_INDUSTRY");
        assertThat(EveGameNotificationService.categoryOf("FutureGameMessage")).isEqualTo("OTHER");
        for (String type : List.of("CharAppAcceptMsg", "CharAppWithdrawMsg", "CharTerminationMsg")) {
            assertThat(EveGameNotificationService.categoryOf(type)).isEqualTo("CORPORATION_MEMBER");
        }
        for (String type : List
            .of("OwnershipTransferred", "unknown notification type (6040)", "unknown notification type (6041)")) {
            assertThat(EveGameNotificationService.categoryOf(type)).isEqualTo("STRUCTURE_ASSET_SAFETY");
        }
        for (String type : List.of("EntosisCaptureStarted", "AcceptedSurrender", "WarAdopted ")) {
            assertThat(EveGameNotificationService.categoryOf(type)).isEqualTo("WAR_SOVEREIGNTY");
        }
        assertThat(EveGameNotificationService.categoryOf("NPCStandingsLost")).isEqualTo("OTHER");
    }

    /** 建筑名称只允许从当前租户、当前角色军团的快照中补全。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldResolveStructureNameInsideCurrentTenantAndCorporation() throws Exception {
        TableInfoHelper
            .initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "notification-test"), EveCorporationStructureDO.class);
        Fixture f = new Fixture();
        long structureId = 1_030_000_000_000L;
        EveGameNotificationDO notification = new EveGameNotificationDO();
        notification.setNotificationId(7001L);
        notification.setNotificationType("StructureFuelAlert");
        notification.setContent("structureID: &id001 " + structureId);
        notification.setCategory("OTHER");
        notification.setDeleted(0L);
        when(f.notifications.selectPage(any(Page.class), any())).thenReturn(new Page<EveGameNotificationDO>(1, 20, 1)
            .setRecords(List.of(notification)));
        when(f.presentation.referencedEntityIds(any())).thenReturn(List.of());
        when(f.presentation.referencedStructureIds(any())).thenReturn(List.of(structureId));
        when(f.presentation.present(anyString(), anyString(), any()))
            .thenReturn(new EveGameNotificationPresentation("燃料告警", "燃料不足", List.of()));
        EveCorporationDO corporation = new EveCorporationDO();
        corporation.setId(400L);
        when(f.corporations.selectByTenantAndCorporationId(10L, 300L)).thenReturn(corporation);
        EveCorporationStructureDO structure = new EveCorporationStructureDO();
        structure.setStructureId(structureId);
        structure.setStructureName("本军团建筑");
        when(f.structures.selectList(any())).thenReturn(List.of(structure));

        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(f.context);
            f.service.page(1, 20, "ALL", null);
        }

        verify(f.corporations).selectByTenantAndCorporationId(10L, 300L);
        ArgumentCaptor<LambdaQueryWrapper<EveCorporationStructureDO>> wrapper = ArgumentCaptor
            .forClass(LambdaQueryWrapper.class);
        verify(f.structures).selectList(wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment())
            .contains("tenant_id", "corporation_ref_id", "deleted", "structure_id");
        assertThat(wrapper.getValue().getParamNameValuePairs().values()).contains(10L, 400L, 0L, structureId);
        ArgumentCaptor<Map<Long, String>> names = ArgumentCaptor.forClass(Map.class);
        verify(f.presentation).present(eq("StructureFuelAlert"), eq(notification.getContent()), names.capture());
        assertThat(names.getValue()).containsEntry(structureId, "本军团建筑");
        verifyNoInteractions(f.esi);
    }

    /** 上游缓存未过期时，重复点击刷新只使用本人缓存，不能重复请求通知接口。 */
    @Test
    void shouldReuseUnexpiredNotificationCache() throws Exception {
        Fixture f = new Fixture();
        EveGameNotificationDO cached = new EveGameNotificationDO();
        cached.setSourceExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        when(f.notifications.selectOne(any())).thenReturn(cached);
        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(f.context);
            f.service.syncCurrentUserForMoonProfile(300L);
        }
        verifyNoInteractions(f.esi, f.lifecycle);
        verify(f.lock).unlock();
    }

    /** 空通知响应也要记录上游有效期，避免没有通知时每次刷新都重复外呼。 */
    @Test
    void shouldCacheEmptyNotificationResponse() throws Exception {
        Fixture f = new Fixture();
        when(f.lifecycle.ensureAccessToken(f.authorization)).thenReturn("test-token");
        when(f.esi.getGameNotificationsWithMetadata(200L, "test-token")).thenReturn(new SerenityEsiResponse<>(List
            .of(), LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10), "etag"));
        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(f.context);
            f.service.syncCurrentUserForMoonProfile(300L);
            f.service.syncCurrentUserForMoonProfile(300L);
        }
        verify(f.esi, times(1)).getGameNotificationsWithMetadata(200L, "test-token");
        verify(f.profiles).initializeMissingForCharacter(10L, 20L, 100L);
        assertThat(f.expiresAt.get()).isGreaterThan(System.currentTimeMillis());
    }

    /** 上游失败后必须退避，连续手动刷新不能绕过冷却反复请求。 */
    @Test
    void shouldBackOffAfterUpstreamFailure() throws Exception {
        Fixture f = new Fixture();
        when(f.lifecycle.ensureAccessToken(f.authorization)).thenReturn("test-token");
        when(f.esi.getGameNotificationsWithMetadata(200L, "test-token")).thenThrow(new BusinessException("国服暂时不可用"));
        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(f.context);
            assertThatThrownBy(() -> f.service.syncCurrentUserForMoonProfile(300L)).hasMessage("国服暂时不可用");
            assertThatThrownBy(() -> f.service.syncCurrentUserForMoonProfile(300L)).hasMessageContaining("已有矿种档案已保留");
        }
        verify(f.esi, times(1)).getGameNotificationsWithMetadata(200L, "test-token");
        verifyNoInteractions(f.profiles);
    }

    /** 本人角色已离开目标军团时，不能为了刷新旧军团矿堡读取通知。 */
    @Test
    void shouldRejectAnotherCorporationBeforeReadingNotifications() throws Exception {
        Fixture f = new Fixture();
        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(f.context);
            assertThatThrownBy(() -> f.service.syncCurrentUserForMoonProfile(301L)).hasMessage("当前授权角色不属于该月矿堡所在军团");
        }
        verifyNoInteractions(f.notifications, f.esi, f.lifecycle, f.profiles);
    }

    /** 数据库提交前不发布成功门禁，角色锁必须持续到事务结束。 */
    @Test
    void shouldPublishCacheOnlyAfterCommitAndKeepLockUntilCompletion() throws Exception {
        Fixture f = new Fixture();
        when(f.lifecycle.ensureAccessToken(f.authorization)).thenReturn("test-token");
        when(f.esi.getGameNotificationsWithMetadata(200L, "test-token")).thenReturn(new SerenityEsiResponse<>(List
            .of(), LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10), "etag"));
        TransactionSynchronizationManager.initSynchronization();
        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(f.context);
            f.service.syncCurrentUserForMoonProfile(300L);
            assertThat(f.expiresAt.get()).isNull();
            verify(f.lock, never()).unlock();
            List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
            callbacks.forEach(TransactionSynchronization::afterCommit);
            assertThat(f.expiresAt.get()).isGreaterThan(System.currentTimeMillis());
            callbacks.forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            verify(f.lock).unlock();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 提交失败时不能把未持久化的通知快照标记为成功缓存。 */
    @Test
    void shouldNotAdvanceCacheWhenTransactionRollsBack() throws Exception {
        Fixture f = new Fixture();
        when(f.lifecycle.ensureAccessToken(f.authorization)).thenReturn("test-token");
        when(f.esi.getGameNotificationsWithMetadata(200L, "test-token")).thenReturn(new SerenityEsiResponse<>(List
            .of(), LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10), "etag"));
        TransactionSynchronizationManager.initSynchronization();
        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(f.context);
            f.service.syncCurrentUserForMoonProfile(300L);
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertThat(f.expiresAt.get()).isNull();
            verify(f.lock).unlock();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 其他线程已持有角色锁时不能额外请求国服接口。 */
    @Test
    void shouldRejectConcurrentNotificationReadBeforeCallingUpstream() throws Exception {
        Fixture f = new Fixture();
        when(f.lock.tryLock(1, TimeUnit.SECONDS)).thenReturn(false);
        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(f.context);
            assertThatThrownBy(() -> f.service.syncCurrentUserForMoonProfile(300L)).hasMessageContaining("正在同步");
        }
        verifyNoInteractions(f.notifications, f.esi, f.lifecycle, f.profiles);
        verify(f.lock, never()).unlock();
    }

    /** 构造隔离的本人通知、分布式锁及缓存，测试不连接真实国服或 Redis。 */
    private static final class Fixture {
        private final UserContext context = new UserContext();
        private final EveCharacterMapper characters = mock(EveCharacterMapper.class);
        private final EveAuthorizationMapper authorizations = mock(EveAuthorizationMapper.class);
        private final EveGameNotificationMapper notifications = mock(EveGameNotificationMapper.class);
        private final EveAuthorizationLifecycleService lifecycle = mock(EveAuthorizationLifecycleService.class);
        private final EveGameNotificationPresentationService presentation = mock(EveGameNotificationPresentationService.class);
        private final SerenityEsiClient esi = mock(SerenityEsiClient.class);
        private final EveMoonOreProfileService profiles = mock(EveMoonOreProfileService.class);
        private final EveCorporationMapper corporations = mock(EveCorporationMapper.class);
        private final EveCorporationStructureMapper structures = mock(EveCorporationStructureMapper.class);
        private final RedissonClient redis = mock(RedissonClient.class);
        private final RLock lock = mock(RLock.class);
        private final EveAuthorizationDO authorization = new EveAuthorizationDO();
        private final AtomicReference<Long> expiresAt = new AtomicReference<>();
        private final EveGameNotificationService service;

        /** 为当前用户和军团建立有效授权及可重复使用的缓存假对象。 */
        private Fixture() throws Exception {
            context.setTenantId(10L);
            context.setId(20L);
            EveCharacterDO character = new EveCharacterDO();
            character.setId(100L);
            character.setCharacterId(200L);
            character.setCorporationId(300L);
            when(characters.selectActiveByUser(10L, 20L)).thenReturn(List.of(character));
            when(characters.selectActiveByTenantUserAndRefId(10L, 20L, 100L)).thenReturn(character);
            authorization.setStatus(EveAuthorizationStatus.ACTIVE);
            authorization.setScopes(List.of("esi-characters.read_notifications.v1"));
            when(authorizations.selectByUserCharacter(10L, 20L, 100L)).thenReturn(List.of(authorization));
            when(redis.getLock(anyString())).thenReturn(lock);
            when(lock.tryLock(1, TimeUnit.SECONDS)).thenReturn(true);
            when(lock.isHeldByCurrentThread()).thenReturn(true);
            RBucket<Long> expiry = bucket(expiresAt);
            RBucket<Long> retry = bucket(new AtomicReference<>());
            doReturn(expiry).when(redis).getBucket("eve:serenity:notifications:10:20:100:expires");
            doReturn(retry).when(redis).getBucket("eve:serenity:notifications:10:20:100:retry");
            service = new EveGameNotificationService(characters, authorizations, notifications, lifecycle, presentation, esi, redis, profiles, corporations, structures);
        }

        /** 以原子引用模拟带有效期的缓存值，检查后续调用是否遵守缓存门槛。 */
        @SuppressWarnings("unchecked")
        private static RBucket<Long> bucket(AtomicReference<Long> value) {
            RBucket<Long> bucket = mock(RBucket.class);
            when(bucket.get()).thenAnswer(invocation -> value.get());
            doAnswer(invocation -> {
                value.set(invocation.getArgument(0));
                return null;
            }).when(bucket).set(any(Long.class), any(Duration.class));
            return bucket;
        }
    }
}
