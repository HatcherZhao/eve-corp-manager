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
import org.mockito.ArgumentCaptor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import top.continew.admin.eve.client.SerenityEsiClient;
import top.continew.admin.eve.config.EveMarketProperties;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.mapper.EveMarketRefreshBatchMapper;
import top.continew.admin.eve.mapper.EveMarketSnapshotMapper;
import top.continew.admin.eve.mapper.EveMarketRefreshQueueMapper;
import top.continew.admin.eve.mapper.EveStaticTypeReferenceMapper;
import top.continew.admin.eve.model.EveMarketDetailResp;
import top.continew.admin.eve.model.entity.EveMarketSnapshotDO;
import top.continew.admin.eve.model.entity.EveMarketRefreshBatchDO;
import top.continew.admin.eve.model.entity.EveMarketRefreshQueueDO;
import top.continew.admin.eve.model.entity.EveStaticLocationReferenceDO;
import top.continew.admin.eve.model.entity.EveStaticTypeReferenceDO;
import top.continew.admin.eve.model.serenity.SerenityEsiPagedResponse;
import top.continew.admin.eve.model.serenity.SerenityMarketOrderResponse;
import top.continew.admin.eve.model.serenity.SerenityEsiResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 吉他市场后台 ESI 同步与页面只读快照的回归测试。 */
class EveMarketServiceTest {

    /** 超过五分钟才能完成的大批次仍以固定起点收敛，不会把先完成的物品重新入队。 */
    @Test
    void shouldCompleteLargeBatchWithoutRequeueingEarlierSnapshots() {
        LocalDateTime batchStartedAt = LocalDateTime.now().minusMinutes(30);
        List<Integer> typeIds = IntStream.rangeClosed(1, 101).boxed().toList();
        List<EveMarketSnapshotDO> snapshots = typeIds.stream().map(typeId -> {
            EveMarketSnapshotDO snapshot = new EveMarketSnapshotDO();
            snapshot.setTypeId(typeId);
            snapshot.setQuoteSynchronizedAt(batchStartedAt.minusMinutes(1));
            snapshot.setQuoteNextRequestAt(batchStartedAt.plusMinutes(40));
            snapshot.setQuoteValidatedAt(batchStartedAt.plusSeconds(typeId));
            snapshot.setHistorySynchronizedAt(batchStartedAt.minusHours(1));
            snapshot.setHistoryNextRequestAt(batchStartedAt.plusDays(1));
            snapshot.setHistorySource("SERENITY_ESI_REGION_V1:10000002");
            return snapshot;
        }).toList();
        EveMarketSnapshotMapper snapshotMapper = mock(EveMarketSnapshotMapper.class);
        when(snapshotMapper.selectByIds(typeIds)).thenReturn(snapshots);
        EveMarketRefreshBatchMapper batchMapper = mock(EveMarketRefreshBatchMapper.class);
        EveMarketRefreshBatchDO batch = new EveMarketRefreshBatchDO();
        batch.setRequestedAt(batchStartedAt);
        when(batchMapper.selectBatch(anyString())).thenReturn(batch);
        EveMarketRefreshQueueMapper queueMapper = mock(EveMarketRefreshQueueMapper.class);
        EveMarketService service = service(new EveMarketProperties(), snapshotMapper, mock(EveStaticReferenceService.class), mock(SerenityEsiClient.class), mock(EveMarketSnapshotStore.class), queueMapper, batchMapper, redisson());

        var result = service.queueMarketTypes(typeIds, true);

        assertThat(result.refreshedTypeIds()).containsExactlyElementsOf(typeIds);
        assertThat(result.deferredTypeIds()).isEmpty();
        assertThat(result.failedTypeIds()).isEmpty();
        verify(queueMapper, never()).enqueue(anyInt(), anyBoolean(), any());
        verify(batchMapper).markCompleted(anyString(), eq(batchStartedAt), any());
    }

    /** 新批次可以复用批次开始时仍在官方有效期内的报价，避免不同页面重复查询同一物品。 */
    @Test
    void shouldReuseQuoteValidatedBeforeBatchWhileOfficialCacheIsValid() {
        LocalDateTime batchStartedAt = LocalDateTime.now();
        EveMarketSnapshotDO snapshot = new EveMarketSnapshotDO();
        snapshot.setTypeId(34);
        snapshot.setQuoteValidatedAt(batchStartedAt.minusMinutes(2));
        snapshot.setQuoteNextRequestAt(batchStartedAt.plusMinutes(3));
        EveMarketSnapshotMapper snapshotMapper = mock(EveMarketSnapshotMapper.class);
        when(snapshotMapper.selectByIds(List.of(34))).thenReturn(List.of(snapshot));
        EveMarketRefreshBatchMapper batchMapper = batchMapper(batchStartedAt);
        EveMarketRefreshQueueMapper queueMapper = mock(EveMarketRefreshQueueMapper.class);
        EveMarketService service = service(new EveMarketProperties(), snapshotMapper, mock(EveStaticReferenceService.class), mock(SerenityEsiClient.class), mock(EveMarketSnapshotStore.class), queueMapper, batchMapper, redisson());

        var result = service.queueMarketTypes(List.of(34), false);

        assertThat(result.refreshedTypeIds()).containsExactly(34);
        verify(queueMapper, never()).enqueue(anyInt(), anyBoolean(), any());
    }

    /** 已越过官方有效期的旧报价必须进入队列，不能仅凭近期验证时间复用。 */
    @Test
    void shouldQueueQuoteWhoseOfficialCacheExpiredBeforeBatch() {
        LocalDateTime batchStartedAt = LocalDateTime.now();
        EveMarketSnapshotDO snapshot = new EveMarketSnapshotDO();
        snapshot.setTypeId(34);
        snapshot.setQuoteValidatedAt(batchStartedAt.minusMinutes(2));
        snapshot.setQuoteNextRequestAt(batchStartedAt.minusSeconds(1));
        EveMarketSnapshotMapper snapshotMapper = mock(EveMarketSnapshotMapper.class);
        when(snapshotMapper.selectByIds(List.of(34))).thenReturn(List.of(snapshot));
        EveMarketRefreshBatchMapper batchMapper = batchMapper(batchStartedAt);
        EveMarketRefreshQueueMapper queueMapper = mock(EveMarketRefreshQueueMapper.class);
        EveMarketService service = service(new EveMarketProperties(), snapshotMapper, mock(EveStaticReferenceService.class), mock(SerenityEsiClient.class), mock(EveMarketSnapshotStore.class), queueMapper, batchMapper, redisson());

        var result = service.queueMarketTypes(List.of(34), false);

        assertThat(result.deferredTypeIds()).containsExactly(34);
        verify(queueMapper).enqueue(eq(34), eq(false), any());
    }

    /** 列表不复用已完成批次；官方有效期到达后必须立即恢复为 PENDING 并重新入队。 */
    @Test
    void shouldQueueExpiredQuoteForListEvenWhenStableBatchCompleted() {
        EveMarketSnapshotDO snapshot = new EveMarketSnapshotDO();
        snapshot.setTypeId(34);
        snapshot.setQuoteValidatedAt(LocalDateTime.now().minusMinutes(1));
        snapshot.setQuoteNextRequestAt(LocalDateTime.now().minusSeconds(1));
        EveMarketSnapshotMapper snapshotMapper = mock(EveMarketSnapshotMapper.class);
        when(snapshotMapper.selectByIds(List.of(34))).thenReturn(List.of(snapshot));
        EveMarketRefreshQueueMapper queueMapper = mock(EveMarketRefreshQueueMapper.class);
        EveMarketRefreshBatchMapper batchMapper = mock(EveMarketRefreshBatchMapper.class);
        EveMarketService service = service(new EveMarketProperties(), snapshotMapper, mock(EveStaticReferenceService.class), mock(SerenityEsiClient.class), mock(EveMarketSnapshotStore.class), queueMapper, batchMapper, redisson());

        var result = service.queueCurrentMarketTypes(List.of(34));

        assertThat(result.deferredTypeIds()).containsExactly(34);
        verify(queueMapper).enqueue(eq(34), eq(false), any());
        verifyNoInteractions(batchMapper);
    }

    /** 大集合轮询已存在同级任务时只批量读取队列，不重复执行数百次 upsert。 */
    @Test
    void shouldNotReenqueueExistingTasksDuringLargeBatchPolling() {
        List<Integer> typeIds = IntStream.rangeClosed(1, 500).boxed().toList();
        List<EveMarketRefreshQueueDO> queuedTasks = typeIds.stream().map(typeId -> {
            EveMarketRefreshQueueDO task = new EveMarketRefreshQueueDO();
            task.setTypeId(typeId);
            task.setIncludeHistory(false);
            return task;
        }).toList();
        EveMarketRefreshQueueMapper queueMapper = mock(EveMarketRefreshQueueMapper.class);
        when(queueMapper.selectQueued(typeIds)).thenReturn(queuedTasks);
        EveMarketRefreshBatchMapper batchMapper = batchMapper(LocalDateTime.now());
        EveMarketService service = service(new EveMarketProperties(), mock(EveMarketSnapshotMapper.class), mock(EveStaticReferenceService.class), mock(SerenityEsiClient.class), mock(EveMarketSnapshotStore.class), queueMapper, batchMapper, redisson());

        var result = service.queueMarketTypes(typeIds, false);

        assertThat(result.deferredTypeIds()).hasSize(500);
        verify(queueMapper, never()).enqueue(anyInt(), anyBoolean(), any());
    }

    /** 已有普通报价任务在工业需要历史时仍必须升级，不能因去重跳过。 */
    @Test
    void shouldUpgradeExistingQuoteTaskWhenHistoryIsRequired() {
        EveMarketRefreshQueueDO task = new EveMarketRefreshQueueDO();
        task.setTypeId(34);
        task.setIncludeHistory(false);
        EveMarketRefreshQueueMapper queueMapper = mock(EveMarketRefreshQueueMapper.class);
        when(queueMapper.selectQueued(List.of(34))).thenReturn(List.of(task));
        EveMarketService service = service(new EveMarketProperties(), mock(EveMarketSnapshotMapper.class), mock(EveStaticReferenceService.class), mock(SerenityEsiClient.class), mock(EveMarketSnapshotStore.class), queueMapper, batchMapper(LocalDateTime.now()), redisson());

        service.queueMarketTypes(List.of(34), true);

        verify(queueMapper).enqueue(eq(34), eq(true), any());
    }

    /** 单物品按需查询必须读取全部分页并保存完整订单簿，不能再截断为前一百条。 */
    @Test
    void shouldRefreshAllRequestedTypePagesWithoutDepthTruncation() throws Exception {
        SerenityEsiClient esiClient = mock(SerenityEsiClient.class);
        EveMarketSnapshotMapper mapper = mock(EveMarketSnapshotMapper.class);
        List<SerenityMarketOrderResponse> first = new java.util.ArrayList<>();
        for (int index = 0; index < 100; index++) {
            first.add(order(false, 9.00 + index, 1L, 30000142L, 60003760L));
        }
        SerenityMarketOrderResponse last = order(false, 250.00, 2L, 30000142L, 60003760L);
        LocalDateTime firstExpiry = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5);
        LocalDateTime lastExpiry = firstExpiry.minusMinutes(1);
        LocalDateTime firstFetchedAt = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(4);
        LocalDateTime lastFetchedAt = firstFetchedAt.plusSeconds(1);
        when(esiClient.getMarketOrdersWithMetadata(10000002L, 34, 1))
            .thenReturn(new SerenityEsiPagedResponse<>(first, firstExpiry, null, 2, firstFetchedAt));
        when(esiClient.getMarketOrdersWithMetadata(10000002L, 34, 2)).thenReturn(new SerenityEsiPagedResponse<>(List
            .of(last), lastExpiry, null, 2, lastFetchedAt));

        service(mapper, mock(EveStaticReferenceService.class), esiClient).refreshMarketType(34, false);

        ArgumentCaptor<List<EveMarketSnapshotDO>> snapshots = ArgumentCaptor.forClass(List.class);
        verify(mapper).upsertOrders(snapshots.capture());
        List<?> orders = new ObjectMapper().readValue(snapshots.getValue().get(0).getSellOrdersJson(), List.class);
        assertThat(orders).hasSize(101);
        assertThat(snapshots.getValue().get(0).getSellVolume()).isEqualTo(102L);
        assertThat(snapshots.getValue().get(0).getQuoteNextRequestAt()).isEqualTo(lastExpiry
            .atOffset(ZoneOffset.UTC)
            .atZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime());
        assertThat(snapshots.getValue().get(0).getQuoteSynchronizedAt()).isEqualTo(firstFetchedAt
            .atOffset(ZoneOffset.UTC)
            .atZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime());
        assertThat(snapshots.getValue().get(0).getQuoteValidatedAt()).isNotNull();
    }

    /** 官方缓存仍有效时直接复用快照，不能以本次访问时间延长报价有效期。 */
    @Test
    void shouldNotExtendQuoteFreshnessWhileOfficialCacheIsValid() {
        SerenityEsiClient esiClient = mock(SerenityEsiClient.class);
        EveMarketSnapshotMapper mapper = mock(EveMarketSnapshotMapper.class);
        EveMarketSnapshotDO snapshot = new EveMarketSnapshotDO();
        snapshot.setTypeId(34);
        snapshot.setQuoteSynchronizedAt(LocalDateTime.now().minusMinutes(4));
        snapshot.setQuoteNextRequestAt(LocalDateTime.now().plusSeconds(10));
        when(mapper.selectById(34)).thenReturn(snapshot);

        service(mapper, mock(EveStaticReferenceService.class), esiClient).refreshMarketType(34, false);

        verifyNoInteractions(esiClient);
        verify(mapper, never()).upsertOrders(any());
        verify(mapper).markQuoteValidated(eq(34), any());
    }

    /** 升级前缓存来源未知时不得借用已有快照时间，因为无法证明两份订单正文相同。 */
    @Test
    void shouldKeepSourceTimeUnknownForLegacyCachedResponse() {
        SerenityEsiClient esiClient = mock(SerenityEsiClient.class);
        EveMarketSnapshotMapper mapper = mock(EveMarketSnapshotMapper.class);
        LocalDateTime existingSourceTime = LocalDateTime.now().minusMinutes(4);
        EveMarketSnapshotDO existing = new EveMarketSnapshotDO();
        existing.setTypeId(34);
        existing.setQuoteSynchronizedAt(existingSourceTime);
        when(mapper.selectById(34)).thenReturn(existing);
        LocalDateTime expiry = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(10);
        when(esiClient.getMarketOrdersWithMetadata(10000002L, 34, 1)).thenReturn(new SerenityEsiPagedResponse<>(List
            .of(), expiry, null, 1));

        service(mapper, mock(EveStaticReferenceService.class), esiClient).refreshMarketType(34, false);

        ArgumentCaptor<List<EveMarketSnapshotDO>> snapshots = ArgumentCaptor.forClass(List.class);
        verify(mapper).upsertOrders(snapshots.capture());
        assertThat(snapshots.getValue().get(0).getSourceUpdatedAt()).isNull();
        assertThat(snapshots.getValue().get(0).getQuoteSynchronizedAt()).isNull();
        assertThat(snapshots.getValue().get(0).getQuoteNextRequestAt()).isAfter(LocalDateTime.now());
    }

    /** 旧版 Redis 缓存首次写快照时来源时间可未知，但数据库审计时间必须始终完整。 */
    @Test
    void shouldPersistAuditTimesWhenLegacyCacheCreatesFirstSnapshot() {
        SerenityEsiClient esiClient = mock(SerenityEsiClient.class);
        EveMarketSnapshotMapper mapper = mock(EveMarketSnapshotMapper.class);
        when(mapper.selectById(34)).thenReturn(null);
        when(esiClient.getMarketOrdersWithMetadata(10000002L, 34, 1)).thenReturn(new SerenityEsiPagedResponse<>(List
            .of(), LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1), null, 1));

        service(mapper, mock(EveStaticReferenceService.class), esiClient).refreshMarketType(34, false);

        ArgumentCaptor<List<EveMarketSnapshotDO>> snapshots = ArgumentCaptor.forClass(List.class);
        verify(mapper).upsertOrders(snapshots.capture());
        EveMarketSnapshotDO saved = snapshots.getValue().get(0);
        assertThat(saved.getSourceUpdatedAt()).isNull();
        assertThat(saved.getQuoteSynchronizedAt()).isNull();
        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(saved.getUpdateTime()).isNotNull();
        assertThat(saved.getQuoteValidatedAt()).isEqualTo(saved.getCreateTime());
    }

    /** 多页订单任一页缺少元数据时必须按整簿最保守口径发布。 */
    @Test
    void shouldUseConservativeMetadataWhenAnyOrderPageIsMissingIt() {
        SerenityEsiClient esiClient = mock(SerenityEsiClient.class);
        EveMarketSnapshotMapper mapper = mock(EveMarketSnapshotMapper.class);
        LocalDateTime existingSourceTime = LocalDateTime.now().minusMinutes(10);
        EveMarketSnapshotDO existing = new EveMarketSnapshotDO();
        existing.setTypeId(34);
        existing.setQuoteSynchronizedAt(existingSourceTime);
        when(mapper.selectById(34)).thenReturn(existing);
        LocalDateTime before = LocalDateTime.now();
        when(esiClient.getMarketOrdersWithMetadata(10000002L, 34, 1)).thenReturn(new SerenityEsiPagedResponse<>(List
            .of(), LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5), null, 2, LocalDateTime.now(ZoneOffset.UTC)));
        when(esiClient.getMarketOrdersWithMetadata(10000002L, 34, 2)).thenReturn(new SerenityEsiPagedResponse<>(List
            .of(), null, null, 2, null));

        service(mapper, mock(EveStaticReferenceService.class), esiClient).refreshMarketType(34, false);

        ArgumentCaptor<List<EveMarketSnapshotDO>> snapshots = ArgumentCaptor.forClass(List.class);
        verify(mapper).upsertOrders(snapshots.capture());
        EveMarketSnapshotDO saved = snapshots.getValue().get(0);
        assertThat(saved.getSourceUpdatedAt()).isNull();
        assertThat(saved.getQuoteSynchronizedAt()).isNull();
        assertThat(saved.getQuoteNextRequestAt()).isAfterOrEqualTo(before.plusSeconds(59));
        assertThat(saved.getQuoteNextRequestAt()).isBeforeOrEqualTo(before.plusMinutes(1).plusSeconds(2));
    }

    /** 任一分页失败时不得覆盖目标物品最近一次成功快照。 */
    @Test
    void shouldKeepRequestedTypeSnapshotWhenLaterPageFails() {
        SerenityEsiClient esiClient = mock(SerenityEsiClient.class);
        EveMarketSnapshotMapper mapper = mock(EveMarketSnapshotMapper.class);
        when(esiClient.getMarketOrdersWithMetadata(10000002L, 34, 1)).thenReturn(new SerenityEsiPagedResponse<>(List
            .of(order(false, 9.00, 1L, 30000142L, 60003760L)), null, null, 2));
        when(esiClient.getMarketOrdersWithMetadata(10000002L, 34, 2)).thenThrow(new IllegalStateException("upstream"));

        assertThatThrownBy(() -> service(mapper, mock(EveStaticReferenceService.class), esiClient)
            .refreshMarketType(34, false)).isInstanceOf(IllegalStateException.class);

        verify(mapper, never()).upsertOrders(any());
    }

    /** 列表请求只负责持久入队并立即返回 PENDING，不得在请求线程等待官方节流。 */
    @Test
    void shouldPersistPendingMarketTypesWithoutCallingEsi() {
        EveMarketRefreshQueueMapper queueMapper = mock(EveMarketRefreshQueueMapper.class);
        SerenityEsiClient esiClient = mock(SerenityEsiClient.class);
        EveMarketService service = service(mock(EveMarketSnapshotMapper.class), mock(EveStaticReferenceService.class), esiClient, mock(EveMarketSnapshotStore.class), queueMapper, redisson());

        var result = service.queueMarketTypes(List.of(34, 35), false);

        assertThat(result.deferredTypeIds()).containsExactly(34, 35);
        verify(queueMapper).enqueue(eq(34), eq(false), any());
        verify(queueMapper).enqueue(eq(35), eq(false), any());
        verifyNoInteractions(esiClient);
    }

    /** 消费时使用任务版本条件删除，处理中升级为历史请求的同类型任务不会被误删。 */
    @Test
    void shouldDeleteConsumedTaskByVersionAndRefreshHistoryWhenRequested() {
        EveMarketRefreshQueueMapper queueMapper = mock(EveMarketRefreshQueueMapper.class);
        EveMarketRefreshQueueDO task = new EveMarketRefreshQueueDO();
        task.setTypeId(34);
        task.setIncludeHistory(true);
        task.setTaskVersion(7L);
        task.setAttemptCount(0);
        when(queueMapper.selectDue(any(), eq(1))).thenReturn(List.of(task));
        SerenityEsiClient esiClient = mock(SerenityEsiClient.class);
        when(esiClient.getMarketOrdersWithMetadata(10000002L, 34, 1)).thenReturn(new SerenityEsiPagedResponse<>(List
            .of(), null, null, 1));
        when(esiClient.getMarketHistoryWithMetadata(10000002L, 34)).thenReturn(new SerenityEsiResponse<>(List
            .of(), null, null));
        EveMarketService service = service(mock(EveMarketSnapshotMapper.class), mock(EveStaticReferenceService.class), esiClient, mock(EveMarketSnapshotStore.class), queueMapper, redisson());

        assertThat(service.consumeMarketRefreshQueue(1)).isEqualTo(1);

        verify(queueMapper).deleteTask(34, 7L);
        verify(esiClient).getMarketHistoryWithMetadata(10000002L, 34);
    }

    /** 历史失败不能污染只需要报价的市场列表，但需要历史的计算必须明确失败。 */
    @Test
    void shouldIsolateOrderAndHistoryFailureStatus() {
        EveMarketSnapshotDO snapshot = new EveMarketSnapshotDO();
        snapshot.setTypeId(34);
        snapshot.setQuoteSynchronizedAt(LocalDateTime.now());
        snapshot.setQuoteNextRequestAt(LocalDateTime.now().plusMinutes(1));
        snapshot.setQuoteValidatedAt(LocalDateTime.now());
        snapshot.setHistorySynchronizedAt(LocalDateTime.now());
        snapshot.setHistorySource("SERENITY_ESI_REGION_V1:10000002");
        snapshot.setHistoryFailureMessage("历史暂时不可用");
        snapshot.setHistoryFailureAt(LocalDateTime.now());
        EveMarketSnapshotMapper mapper = mock(EveMarketSnapshotMapper.class);
        when(mapper.selectByIds(List.of(34))).thenReturn(List.of(snapshot));
        EveMarketRefreshQueueMapper queueMapper = mock(EveMarketRefreshQueueMapper.class);
        EveMarketService service = service(mapper, mock(EveStaticReferenceService.class), mock(SerenityEsiClient.class), mock(EveMarketSnapshotStore.class), queueMapper, redisson());

        var quoteOnly = service.queueMarketTypes(List.of(34), false);
        var withHistory = service.queueMarketTypes(List.of(34), true);

        assertThat(quoteOnly.refreshedTypeIds()).containsExactly(34);
        assertThat(quoteOnly.failedTypeIds()).isEmpty();
        assertThat(withHistory.failedTypeIds()).containsExactly(34);
        verify(queueMapper).enqueue(eq(34), eq(true), any());
        verify(queueMapper, never()).enqueue(eq(34), eq(false), any());
    }

    /** 历史请求失败只写历史失败字段，不能把已成功订单误标为失败。 */
    @Test
    void shouldRecordOnlyHistoryFailureWhenOrdersSucceeded() {
        SerenityEsiClient esiClient = mock(SerenityEsiClient.class);
        when(esiClient.getMarketOrdersWithMetadata(10000002L, 34, 1)).thenReturn(new SerenityEsiPagedResponse<>(List
            .of(), null, null, 1));
        when(esiClient.getMarketHistoryWithMetadata(10000002L, 34)).thenThrow(new IllegalStateException("history"));
        EveMarketSnapshotMapper mapper = mock(EveMarketSnapshotMapper.class);

        assertThatThrownBy(() -> service(mapper, mock(EveStaticReferenceService.class), esiClient)
            .refreshMarketType(34, true)).isInstanceOf(IllegalStateException.class);

        verify(mapper).recordHistoryFailure(eq(34), any(), any(), any(), any());
        verify(mapper, never()).recordMarketFailure(anyInt(), any(), any());
    }

    /** 详情历史失败时最新订单报价仍是 READY，不能把历史故障冒充为报价过期。 */
    @Test
    void shouldKeepFreshQuoteWhenOnlyHistoryFailed() {
        SerenityEsiClient esiClient = mock(SerenityEsiClient.class);
        EveMarketSnapshotMapper mapper = mock(EveMarketSnapshotMapper.class);
        EveStaticReferenceService references = mock(EveStaticReferenceService.class);
        when(references.findTypes(java.util.Set.of(34))).thenReturn(Map.of(34, type(34)));
        EveMarketSnapshotDO snapshot = new EveMarketSnapshotDO();
        snapshot.setTypeId(34);
        snapshot.setHighestBuyPrice(new BigDecimal("8.50"));
        snapshot.setQuoteSynchronizedAt(LocalDateTime.now().plusSeconds(1));
        snapshot.setQuoteNextRequestAt(LocalDateTime.now().plusMinutes(1));
        snapshot.setDetailSynchronizedAt(snapshot.getQuoteSynchronizedAt());
        when(mapper.selectById(34)).thenReturn(snapshot);
        when(esiClient.getMarketHistoryWithMetadata(10000002L, 34)).thenThrow(new IllegalStateException("history"));

        EveMarketDetailResp detail = service(mapper, references, esiClient).detail(34);

        assertThat(detail.quote().stale()).isFalse();
        assertThat(detail.upstreamMessage()).contains("市场历史暂时不可用");
        verify(mapper, never()).recordMarketFailure(anyInt(), any(), any());
        verify(mapper).recordHistoryFailure(eq(34), any(), any(), any(), any());
    }

    /** 升级后的旧第三方日线不能被当作官方区域日均价返回。 */
    @Test
    void shouldHideLegacyHistoryUntilOfficialHistoryIsAvailable() {
        SerenityEsiClient esiClient = mock(SerenityEsiClient.class);
        EveMarketSnapshotMapper snapshotMapper = mock(EveMarketSnapshotMapper.class);
        EveStaticReferenceService referenceService = mock(EveStaticReferenceService.class);
        when(referenceService.findTypes(java.util.Set.of(34))).thenReturn(Map.of(34, type(34)));
        EveMarketSnapshotDO snapshot = new EveMarketSnapshotDO();
        snapshot.setTypeId(34);
        snapshot.setHistoryJson("[{\"date\":\"2026-10-18\",\"closePrice\":99}]");
        snapshot.setHistorySynchronizedAt(LocalDateTime.now());
        when(snapshotMapper.selectById(34)).thenReturn(snapshot);

        EveMarketDetailResp detail = disabledService(snapshotMapper, referenceService, esiClient).detail(34);

        assertThat(detail.history()).isEmpty();
        assertThat(detail.historySynchronizedAt()).isNull();
        verifyNoInteractions(esiClient);
    }

    /** 市场功能关闭时详情只读取最近成功快照。 */
    @Test
    void shouldReadLocalSnapshotWithoutRequestingEsiForDetail() {
        SerenityEsiClient esiClient = mock(SerenityEsiClient.class);
        EveMarketSnapshotMapper snapshotMapper = mock(EveMarketSnapshotMapper.class);
        EveStaticReferenceService referenceService = mock(EveStaticReferenceService.class);
        EveStaticTypeReferenceDO type = type(34);
        when(referenceService.findTypes(Map.of(34, type).keySet())).thenReturn(Map.of(34, type));
        when(snapshotMapper.selectById(34)).thenReturn(null);

        EveMarketDetailResp detail = disabledService(snapshotMapper, referenceService, esiClient).detail(34);

        assertThat(detail.upstreamMessage()).contains("同步未启用");
        assertThat(detail.servedFromCache()).isTrue();
        verifyNoInteractions(esiClient);
    }

    /** 官方缓存截止时间已过时必须标记报价过期。 */
    @Test
    void shouldMarkQuoteOlderThanThirtyMinutesStale() {
        SerenityEsiClient esiClient = mock(SerenityEsiClient.class);
        EveMarketSnapshotMapper mapper = mock(EveMarketSnapshotMapper.class);
        EveStaticReferenceService references = mock(EveStaticReferenceService.class);
        when(references.findTypes(java.util.Set.of(34))).thenReturn(Map.of(34, type(34)));
        EveMarketSnapshotDO snapshot = new EveMarketSnapshotDO();
        snapshot.setTypeId(34);
        snapshot.setHighestBuyPrice(new BigDecimal("8.52"));
        snapshot.setLowestSellPrice(new BigDecimal("9.09"));
        snapshot.setQuoteSynchronizedAt(LocalDateTime.now().minusMinutes(31));
        snapshot.setQuoteNextRequestAt(LocalDateTime.now().minusSeconds(1));
        snapshot.setDetailSynchronizedAt(snapshot.getQuoteSynchronizedAt());
        when(mapper.selectById(34)).thenReturn(snapshot);

        EveMarketDetailResp detail = disabledService(mapper, references, esiClient).detail(34);

        assertThat(detail.quote().stale()).isTrue();
        assertThat(detail.quote().freshnessExpiresAt()).isEqualTo(snapshot.getQuoteNextRequestAt());
        assertThat(detail.servedFromCache()).isTrue();
        verifyNoInteractions(esiClient);
    }

    /** 完整区域订单读取后仅保留吉他星系订单，并从订单簿计算估值报价。 */
    @Test
    void shouldSynchronizeJitaOrdersThroughOfficialEsi() {
        SerenityEsiClient esiClient = mock(SerenityEsiClient.class);
        EveMarketSnapshotMapper snapshotMapper = mock(EveMarketSnapshotMapper.class);
        EveStaticReferenceService referenceService = mock(EveStaticReferenceService.class);
        EveMarketSnapshotStore store = mock(EveMarketSnapshotStore.class);
        SerenityMarketOrderResponse buy = order(true, 8.70, 20L, 30000142L, 60003760L);
        SerenityMarketOrderResponse sell = order(false, 9.30, 10L, 30000142L, 60003760L);
        SerenityMarketOrderResponse otherSystem = order(false, 1.00, 999L, 30000144L, 60003760L);
        LocalDateTime expiry = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5);
        EveStaticLocationReferenceDO station = new EveStaticLocationReferenceDO();
        station.setReferenceName("吉他 IV - 月面 4 - 加达里海军物流支援站");
        when(esiClient.getMarketOrdersWithMetadata(10000002L, 1)).thenReturn(new SerenityEsiPagedResponse<>(List
            .of(buy, sell, otherSystem), expiry, "\"orders-v1\"", 1));
        when(referenceService.findLocations(eq("NPC_STATION"), any())).thenReturn(Map.of(60003760L, station));
        service(snapshotMapper, referenceService, esiClient, store).synchronizeMarketOrders();

        ArgumentCaptor<List<EveMarketSnapshotDO>> snapshots = ArgumentCaptor.forClass(List.class);
        verify(store).replaceOrders(snapshots.capture(), any());
        assertThat(snapshots.getValue()).hasSize(1);
        EveMarketSnapshotDO snapshot = snapshots.getValue().get(0);

        assertThat(snapshot.getHighestBuyPrice()).isEqualByComparingTo("8.70");
        assertThat(snapshot.getLowestSellPrice()).isEqualByComparingTo("9.30");
        assertThat(snapshot.getBuyVolume()).isEqualTo(20L);
        assertThat(snapshot.getSellVolume()).isEqualTo(10L);
        assertThat(snapshot.getQuoteNextRequestAt()).isEqualTo(expiry
            .atOffset(ZoneOffset.UTC)
            .atZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime());
        assertThat(snapshot.getBuyOrdersJson()).contains("吉他 IV");
        assertThat(snapshot.getSellOrdersJson()).contains("9.3");
        verify(esiClient).getMarketOrdersWithMetadata(10000002L, 1);
        verify(snapshotMapper, never()).selectById(35);
    }

    /** 旧快照缺名时按真实资料分类就地补齐；保留已有名称和未知位置 ID，不触发上游或重写快照。 */
    @Test
    void shouldResolveCachedStationAndStructureNamesWithoutRequestingEsi() {
        SerenityEsiClient esiClient = mock(SerenityEsiClient.class);
        EveMarketSnapshotMapper mapper = mock(EveMarketSnapshotMapper.class);
        EveStaticReferenceService references = mock(EveStaticReferenceService.class);
        when(references.findTypes(java.util.Set.of(34))).thenReturn(Map.of(34, type(34)));
        EveStaticLocationReferenceDO station = new EveStaticLocationReferenceDO();
        station.setReferenceName("吉他 IV - 卫星 4 - 加达里海军 组装车间");
        EveStaticLocationReferenceDO structure = new EveStaticLocationReferenceDO();
        structure.setReferenceName("吉他公开贸易建筑");
        when(references.findLocations(eq("NPC_STATION"), any())).thenReturn(Map.of(60003760L, station));
        when(references.findLocations(eq("PUBLIC_STRUCTURE"), any())).thenReturn(Map.of(1020000000001L, structure));
        EveMarketSnapshotDO snapshot = new EveMarketSnapshotDO();
        snapshot.setTypeId(34);
        snapshot.setQuoteSynchronizedAt(LocalDateTime.now());
        snapshot.setBuyOrdersJson("""
            [{"side":"BUY","stationId":60003760,"stationName":null,"price":8.7,"volume":20},
             {"side":"BUY","stationId":1020000000001,"stationName":" ","price":8.6,"volume":10},
             {"side":"BUY","stationId":77,"stationName":"已缓存的空间站","price":8.5,"volume":5},
             {"side":"BUY","stationId":88,"stationName":null,"price":8.4,"volume":2}]
            """);
        snapshot.setSellOrdersJson("""
            [{"side":"SELL","stationId":60003760,"stationName":null,"price":9.3,"volume":10}]
            """);
        when(mapper.selectById(34)).thenReturn(snapshot);

        EveMarketDetailResp detail = disabledService(mapper, references, esiClient).detail(34);

        assertThat(detail.buyOrders()).extracting(order -> order.stationName())
            .containsExactly(station.getReferenceName(), structure.getReferenceName(), "已缓存的空间站", null);
        assertThat(detail.buyOrders().get(3).stationId()).isEqualTo(88L);
        assertThat(detail.sellOrders().get(0).stationName()).isEqualTo(station.getReferenceName());
        assertThat(detail.buyOrders().get(0).price()).isEqualByComparingTo("8.7");
        assertThat(snapshot.getBuyOrdersJson()).contains("\"stationName\":null");
        verifyNoInteractions(esiClient);
        verify(mapper, never()).updateById(any(EveMarketSnapshotDO.class));
    }

    /** 空市场也是成功的权威结果，必须交给事务发布器清理旧订单。 */
    @Test
    void shouldPublishEmptyOrderSnapshot() {
        SerenityEsiClient esiClient = mock(SerenityEsiClient.class);
        EveMarketSnapshotStore store = mock(EveMarketSnapshotStore.class);
        when(esiClient.getMarketOrdersWithMetadata(10000002L, 1)).thenReturn(new SerenityEsiPagedResponse<>(List
            .of(), null, null, 1));

        service(mock(EveMarketSnapshotMapper.class), mock(EveStaticReferenceService.class), esiClient, store)
            .synchronizeMarketOrders();

        verify(store).replaceOrders(eq(List.of()), any());
    }

    /** 后续分页失败时不能发布不完整结果，成功时则返回所有页最晚的缓存期限。 */
    @Test
    void shouldKeepPreviousSnapshotWhenAnyPageFails() {
        SerenityEsiClient esiClient = mock(SerenityEsiClient.class);
        EveMarketSnapshotStore store = mock(EveMarketSnapshotStore.class);
        when(esiClient.getMarketOrdersWithMetadata(10000002L, 1)).thenReturn(new SerenityEsiPagedResponse<>(List
            .of(), null, null, 2));
        when(esiClient.getMarketOrdersWithMetadata(10000002L, 2)).thenThrow(new IllegalStateException("upstream"));

        assertThatThrownBy(() -> service(mock(EveMarketSnapshotMapper.class), mock(EveStaticReferenceService.class), esiClient, store)
            .synchronizeMarketOrders()).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(store);
    }

    /** 调度不能在任一页官方缓存到期前重新抓取整个区域。 */
    @Test
    void shouldReturnLatestPageExpiry() {
        SerenityEsiClient esiClient = mock(SerenityEsiClient.class);
        LocalDateTime expiry = LocalDateTime.now(ZoneOffset.UTC).plusHours(2);
        when(esiClient.getMarketOrdersWithMetadata(10000002L, 1)).thenReturn(new SerenityEsiPagedResponse<>(List
            .of(), expiry.minusMinutes(1), null, 2));
        when(esiClient.getMarketOrdersWithMetadata(10000002L, 2)).thenReturn(new SerenityEsiPagedResponse<>(List
            .of(), expiry, null, 2));

        assertThat(service(mock(EveMarketSnapshotMapper.class), mock(EveStaticReferenceService.class), esiClient)
            .synchronizeMarketOrders()).isEqualTo(expiry);
    }

    /** 历史更新记录明确来源，并尊重比本地刷新周期更晚的 Expires。 */
    @Test
    void shouldSaveHistorySourceAndOfficialExpiryWithoutOverwritingOrders() {
        SerenityEsiClient esiClient = mock(SerenityEsiClient.class);
        EveMarketSnapshotMapper snapshotMapper = mock(EveMarketSnapshotMapper.class);
        LocalDateTime expires = LocalDateTime.now(ZoneOffset.UTC).plusDays(2);
        when(esiClient.getMarketHistoryWithMetadata(10000002L, 34)).thenReturn(new SerenityEsiResponse<>(List
            .of(), expires, "history-etag"));

        service(snapshotMapper, mock(EveStaticReferenceService.class), esiClient).refreshHistory(34, LocalDateTime
            .now());

        verify(snapshotMapper).upsertHistory(eq(34), eq("[]"), eq("SERENITY_ESI_REGION_V1:10000002"), any(), eq(expires
            .atOffset(ZoneOffset.UTC)
            .atZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime()));
        verify(snapshotMapper, never()).updateById(any(EveMarketSnapshotDO.class));
    }

    /** 当前区域官方历史能够正常展示，不能因为来源隔离而隐藏新数据。 */
    @Test
    void shouldShowOfficialHistoryForConfiguredRegion() {
        EveMarketSnapshotMapper mapper = mock(EveMarketSnapshotMapper.class);
        EveStaticReferenceService referenceService = mock(EveStaticReferenceService.class);
        when(referenceService.findTypes(java.util.Set.of(34))).thenReturn(Map.of(34, type(34)));
        EveMarketSnapshotDO snapshot = new EveMarketSnapshotDO();
        snapshot.setTypeId(34);
        snapshot.setHistorySource("SERENITY_ESI_REGION_V1:10000002");
        snapshot.setHistoryJson("[{\"date\":\"2026-10-18\",\"closePrice\":99}]");
        snapshot.setHistorySynchronizedAt(LocalDateTime.now());
        when(mapper.selectById(34)).thenReturn(snapshot);

        EveMarketDetailResp detail = disabledService(mapper, referenceService, mock(SerenityEsiClient.class))
            .detail(34);

        assertThat(detail.history()).hasSize(1);
        assertThat(detail.historySynchronizedAt()).isEqualTo(snapshot.getHistorySynchronizedAt());
        verify(mapper).touch(eq(34), any());
        verify(mapper, never()).updateById(any(EveMarketSnapshotDO.class));
    }

    /** 创建可控的服务实例，避免测试中装配无关的业务组件。 */
    private static EveMarketService service(EveMarketSnapshotMapper snapshotMapper,
                                            EveStaticReferenceService referenceService,
                                            SerenityEsiClient esiClient) {
        return service(new EveMarketProperties(), snapshotMapper, referenceService, esiClient, mock(EveMarketSnapshotStore.class));
    }

    /** 创建关闭上游访问的服务实例，用于验证缓存降级行为。 */
    private static EveMarketService disabledService(EveMarketSnapshotMapper snapshotMapper,
                                                    EveStaticReferenceService referenceService,
                                                    SerenityEsiClient esiClient) {
        EveMarketProperties properties = new EveMarketProperties();
        properties.setEnabled(false);
        return service(properties, snapshotMapper, referenceService, esiClient, mock(EveMarketSnapshotStore.class));
    }

    /** 注入可捕获事务发布边界的测试替身。 */
    private static EveMarketService service(EveMarketSnapshotMapper snapshotMapper,
                                            EveStaticReferenceService referenceService,
                                            SerenityEsiClient esiClient,
                                            EveMarketSnapshotStore store) {
        return service(new EveMarketProperties(), snapshotMapper, referenceService, esiClient, store);
    }

    /** 注入可控配置和跨实例锁。 */
    private static EveMarketService service(EveMarketProperties properties,
                                            EveMarketSnapshotMapper snapshotMapper,
                                            EveStaticReferenceService referenceService,
                                            SerenityEsiClient esiClient,
                                            EveMarketSnapshotStore store) {
        RedissonClient redissonClient = redisson();
        return service(properties, snapshotMapper, referenceService, esiClient, store, mock(EveMarketRefreshQueueMapper.class), batchMapper(), redissonClient);
    }

    /** 注入指定队列和锁替身。 */
    private static EveMarketService service(EveMarketSnapshotMapper snapshotMapper,
                                            EveStaticReferenceService referenceService,
                                            SerenityEsiClient esiClient,
                                            EveMarketSnapshotStore store,
                                            EveMarketRefreshQueueMapper queueMapper,
                                            RedissonClient redissonClient) {
        return service(new EveMarketProperties(), snapshotMapper, referenceService, esiClient, store, queueMapper, batchMapper(), redissonClient);
    }

    /** 注入完整可控依赖。 */
    private static EveMarketService service(EveMarketProperties properties,
                                            EveMarketSnapshotMapper snapshotMapper,
                                            EveStaticReferenceService referenceService,
                                            SerenityEsiClient esiClient,
                                            EveMarketSnapshotStore store,
                                            EveMarketRefreshQueueMapper queueMapper,
                                            EveMarketRefreshBatchMapper batchMapper,
                                            RedissonClient redissonClient) {
        return new EveMarketService(properties, mock(EveContextService.class), mock(EveCorporationMapper.class), snapshotMapper, mock(EveStaticTypeReferenceMapper.class), referenceService, esiClient, new ObjectMapper()
            .findAndRegisterModules(), store, redissonClient, queueMapper, batchMapper);
    }

    /** 创建已建立批次的 Mapper 替身。 */
    private static EveMarketRefreshBatchMapper batchMapper() {
        return batchMapper(LocalDateTime.now().minusSeconds(1));
    }

    /** 创建指定起点的批次 Mapper 替身。 */
    private static EveMarketRefreshBatchMapper batchMapper(LocalDateTime requestedAt) {
        EveMarketRefreshBatchMapper batchMapper = mock(EveMarketRefreshBatchMapper.class);
        EveMarketRefreshBatchDO batch = new EveMarketRefreshBatchDO();
        batch.setRequestedAt(requestedAt);
        when(batchMapper.selectBatch(anyString())).thenReturn(batch);
        return batchMapper;
    }

    /** 创建始终可取得的分布式锁替身。 */
    private static RedissonClient redisson() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        try {
            when(lock.tryLock(anyLong(), any())).thenReturn(true);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        return redissonClient;
    }

    /** 构造测试所需的最小物品资料。 */
    private static EveStaticTypeReferenceDO type(int typeId) {
        EveStaticTypeReferenceDO type = new EveStaticTypeReferenceDO();
        type.setTypeId(typeId);
        type.setTypeName("三钛合金");
        return type;
    }

    /** 构造一条官方 ESI 市场订单。 */
    private static SerenityMarketOrderResponse order(boolean buyOrder,
                                                     double price,
                                                     long volume,
                                                     long systemId,
                                                     long stationId) {
        return new SerenityMarketOrderResponse(buyOrder, Instant
            .parse("2026-10-19T00:00:00Z"), stationId, 1L, 10001L, BigDecimal
                .valueOf(price), "region", systemId, 34, volume, volume);
    }
}
