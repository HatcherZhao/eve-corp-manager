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
import top.continew.admin.eve.client.SerenityEsiClient;
import top.continew.admin.eve.config.EveMarketProperties;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.mapper.EveMarketSnapshotMapper;
import top.continew.admin.eve.mapper.EveStaticTypeReferenceMapper;
import top.continew.admin.eve.model.EveMarketDetailResp;
import top.continew.admin.eve.model.entity.EveMarketSnapshotDO;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 吉他市场后台 ESI 同步与页面只读快照的回归测试。 */
class EveMarketServiceTest {

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

        EveMarketDetailResp detail = service(snapshotMapper, referenceService, esiClient).detail(34);

        assertThat(detail.history()).isEmpty();
        assertThat(detail.historySynchronizedAt()).isNull();
        verifyNoInteractions(esiClient);
    }

    /** 打开详情必须只读取本地缓存，不能因用户浏览直接请求官方 ESI。 */
    @Test
    void shouldReadLocalSnapshotWithoutRequestingEsiForDetail() {
        SerenityEsiClient esiClient = mock(SerenityEsiClient.class);
        EveMarketSnapshotMapper snapshotMapper = mock(EveMarketSnapshotMapper.class);
        EveStaticReferenceService referenceService = mock(EveStaticReferenceService.class);
        EveStaticTypeReferenceDO type = type(34);
        when(referenceService.findTypes(Map.of(34, type).keySet())).thenReturn(Map.of(34, type));
        when(snapshotMapper.selectById(34)).thenReturn(null);

        EveMarketDetailResp detail = service(snapshotMapper, referenceService, esiClient).detail(34);

        assertThat(detail.upstreamMessage()).contains("首轮后台同步");
        assertThat(detail.servedFromCache()).isTrue();
        verifyNoInteractions(esiClient);
    }

    /** 报价超过三十分钟必须标记过期，页面读取仍不能绕过后台同步访问官方接口。 */
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
        snapshot.setDetailSynchronizedAt(snapshot.getQuoteSynchronizedAt());
        when(mapper.selectById(34)).thenReturn(snapshot);

        EveMarketDetailResp detail = service(mapper, references, esiClient).detail(34);

        assertThat(detail.quote().stale()).isTrue();
        assertThat(detail.quote().freshnessExpiresAt()).isEqualTo(snapshot.getQuoteSynchronizedAt().plusMinutes(30));
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
        EveStaticLocationReferenceDO station = new EveStaticLocationReferenceDO();
        station.setReferenceName("吉他 IV - 月面 4 - 加达里海军物流支援站");
        when(esiClient.getMarketOrdersWithMetadata(10000002L, 1)).thenReturn(new SerenityEsiPagedResponse<>(List
            .of(buy, sell, otherSystem), LocalDateTime.now(), "\"orders-v1\"", 1));
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

        EveMarketDetailResp detail = service(mapper, references, esiClient).detail(34);

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

        EveMarketDetailResp detail = service(mapper, referenceService, mock(SerenityEsiClient.class)).detail(34);

        assertThat(detail.history()).hasSize(1);
        assertThat(detail.historySynchronizedAt()).isEqualTo(snapshot.getHistorySynchronizedAt());
        verify(mapper).touch(eq(34), any());
        verify(mapper, never()).updateById(any(EveMarketSnapshotDO.class));
    }

    /** 创建可控的服务实例，避免测试中装配无关的业务组件。 */
    private static EveMarketService service(EveMarketSnapshotMapper snapshotMapper,
                                            EveStaticReferenceService referenceService,
                                            SerenityEsiClient esiClient) {
        return service(snapshotMapper, referenceService, esiClient, mock(EveMarketSnapshotStore.class));
    }

    /** 注入可捕获事务发布边界的测试替身。 */
    private static EveMarketService service(EveMarketSnapshotMapper snapshotMapper,
                                            EveStaticReferenceService referenceService,
                                            SerenityEsiClient esiClient,
                                            EveMarketSnapshotStore store) {
        return new EveMarketService(new EveMarketProperties(), mock(EveContextService.class), mock(EveCorporationMapper.class), snapshotMapper, mock(EveStaticTypeReferenceMapper.class), referenceService, esiClient, new ObjectMapper()
            .findAndRegisterModules(), store);
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
