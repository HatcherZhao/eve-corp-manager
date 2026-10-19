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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.continew.admin.eve.client.SerenityEsiClient;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.config.EveMarketProperties;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.mapper.EveMarketSnapshotMapper;
import top.continew.admin.eve.mapper.EveStaticTypeReferenceMapper;
import top.continew.admin.eve.model.EveMarketDetailResp;
import top.continew.admin.eve.model.EveMarketHistoryResp;
import top.continew.admin.eve.model.EveMarketItemResp;
import top.continew.admin.eve.model.EveMarketOrderResp;
import top.continew.admin.eve.model.EveMarketQuoteResp;
import top.continew.admin.eve.model.EveStaticTypeReferenceResp;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.entity.EveMarketSnapshotDO;
import top.continew.admin.eve.model.entity.EveStaticTypeReferenceDO;
import top.continew.admin.eve.model.enums.EveMineralPriceCategory;
import top.continew.admin.eve.model.serenity.SerenityMarketHistoryResponse;
import top.continew.admin.eve.model.serenity.SerenityMarketOrderResponse;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提供以吉他贸易中心为固定口径的市场查询和服务器端缓存。
 *
 * <p>物品分类和名称来自本系统随应用交付的 evedata 基础信息；市场订单与区域成交历史只由后台经国服
 * 官方 ESI 同步。页面与手动操作严格只读取本地快照，上游故障时继续提供最后成功快照，避免用户访问
 * 形成对官方接口的突发请求。</p>
 *
 * @author zhaoyuqing
 */
@Service
@Slf4j
public class EveMarketService {

    /** 单个订单簿保留的最多订单，足以展示深度且防止详情响应过大。 */
    private static final int ORDER_LIMIT = 100;
    private static final int HISTORY_LIMIT = 366;

    private final EveMarketProperties properties;
    private final EveContextService contextService;
    private final EveCorporationMapper corporationMapper;
    private final EveMarketSnapshotMapper snapshotMapper;
    private final EveStaticTypeReferenceMapper typeReferenceMapper;
    private final EveStaticReferenceService staticReferenceService;
    private final SerenityEsiClient serenityEsiClient;
    private final ObjectMapper objectMapper;
    private final EveMarketSnapshotStore snapshotStore;

    /** 单实例内避免同一物品被多个后台历史补温任务重复处理。 */
    private final Set<Integer> refreshingTypes = ConcurrentHashMap.newKeySet();

    /**
     * 创建吉他市场服务；所有上游访问统一收敛到受全局节流器保护的国服 ESI 客户端。
     *
     * @param properties             市场同步配置
     * @param contextService         当前军团上下文服务
     * @param corporationMapper      军团绑定数据访问器
     * @param snapshotMapper         行情缓存数据访问器
     * @param typeReferenceMapper    静态物品资料访问器
     * @param staticReferenceService 基础信息查询服务
     * @param serenityEsiClient      国服 ESI 客户端
     * @param objectMapper           JSON 序列化器
     * @param snapshotStore          完整订单快照的事务发布器
     */
    public EveMarketService(EveMarketProperties properties,
                            EveContextService contextService,
                            EveCorporationMapper corporationMapper,
                            EveMarketSnapshotMapper snapshotMapper,
                            EveStaticTypeReferenceMapper typeReferenceMapper,
                            EveStaticReferenceService staticReferenceService,
                            SerenityEsiClient serenityEsiClient,
                            ObjectMapper objectMapper,
                            EveMarketSnapshotStore snapshotStore) {
        this.properties = properties;
        this.contextService = contextService;
        this.corporationMapper = corporationMapper;
        this.snapshotMapper = snapshotMapper;
        this.typeReferenceMapper = typeReferenceMapper;
        this.staticReferenceService = staticReferenceService;
        this.serenityEsiClient = serenityEsiClient;
        this.objectMapper = objectMapper;
        this.snapshotStore = snapshotStore;
    }

    /**
     * 按基础信息分类分页查看市场，只读取本地快照。
     */
    public PageResp<EveMarketItemResp> page(int page,
                                            int size,
                                            String keyword,
                                            List<String> categoryPath,
                                            boolean unclassified) {
        PageResp<EveStaticTypeReferenceResp> types = staticReferenceService
            .pageTypes(page, size, keyword, categoryPath, unclassified);
        return attachQuotes(types);
    }

    /**
     * 按月矿、普通矿物或冰矿目录读取吉他单价；军团月矿仅按已同步账本识别品类，不汇总任何开采量。
     */
    public PageResp<EveMarketItemResp> mineralPage(int page,
                                                   int size,
                                                   String keyword,
                                                   EveMineralPriceCategory category) {
        List<String> categoryPath = mineralCategoryPath(category);
        PageResp<EveStaticTypeReferenceResp> types;
        if (EveMineralPriceCategory.CORPORATION_MOON == category) {
            EveCorporationDO corporation = requireCurrentCorporation();
            List<Integer> typeIds = typeReferenceMapper.selectCorporationMoonOreTypeIds(UserContextHolder.getContext()
                .getTenantId(), corporation.getId());
            types = staticReferenceService.pageTypesByIds(page, size, keyword, categoryPath, typeIds);
        } else {
            types = staticReferenceService.pageTypes(page, size, keyword, categoryPath, false);
        }
        return attachQuotes(types);
    }

    /** 为静态目录中的每种矿物附加同一时点的吉他报价缓存。 */
    private PageResp<EveMarketItemResp> attachQuotes(PageResp<EveStaticTypeReferenceResp> types) {
        List<Integer> typeIds = types.getList().stream().map(EveStaticTypeReferenceResp::typeId).toList();
        Map<Integer, EveMarketSnapshotDO> snapshots = findSnapshots(typeIds);
        return new PageResp<>(types.getList()
            .stream()
            .map(type -> new EveMarketItemResp(type, toQuote(snapshots.get(type.typeId()))))
            .toList(), types.getTotal());
    }

    /** 将 evedata 中稳定的原材料分类映射为面向用户的四个价格目录。 */
    private static List<String> mineralCategoryPath(EveMineralPriceCategory category) {
        String mineralCategory = switch (category) {
            case CORPORATION_MOON, MOON -> "卫星矿石";
            case ORE -> "标准矿石";
            case ICE -> "冰矿";
        };
        return List.of("制造和研究", "材料", "原材料", mineralCategory);
    }

    /** 根据当前登录上下文确定正在浏览的军团，避免以任意账本数据越权识别月矿。 */
    private EveCorporationDO requireCurrentCorporation() {
        Long tenantId = UserContextHolder.getContext().getTenantId();
        var context = contextService.getCurrentContext();
        Long corporationId = context.corporation() == null ? null : context.corporation().corporationId();
        if (corporationId == null) {
            throw new BusinessException("请先选择所属军团后再查看军团月矿价格");
        }
        EveCorporationDO corporation = corporationMapper.selectByTenantAndCorporationId(tenantId, corporationId);
        if (corporation == null) {
            throw new BusinessException("当前所属军团不可用，请重新登录后重试");
        }
        return corporation;
    }

    /** 读取单物品完整行情；所有内容均来自后台同步的本地快照，不在用户请求中访问 ESI。 */
    public EveMarketDetailResp detail(int typeId) {
        EveStaticTypeReferenceResp type = requireType(typeId);
        EveMarketSnapshotDO snapshot = snapshotMapper.selectById(typeId);
        boolean stale = snapshot == null || needsQuoteRefresh(snapshot) || needsDetailRefresh(snapshot);
        boolean servedFromCache = stale;
        String message = null;
        if (!properties.isEnabled()) {
            message = snapshot == null ? "吉他市场同步未启用，暂无可用缓存" : "吉他市场同步已关闭，正在展示最近缓存";
        } else if (snapshot == null) {
            message = "吉他市场正在等待国服 ESI 的首轮后台同步";
        } else if (stale) {
            message = "行情正在等待下一轮国服 ESI 后台同步，当前展示最近缓存";
        }
        touch(typeId, snapshot);
        return new EveMarketDetailResp(type, toQuote(snapshot), readOrders(snapshot, "BUY"), readOrders(snapshot, "SELL"), readHistory(snapshot), hasOfficialHistory(snapshot)
            ? snapshot.getHistorySynchronizedAt()
            : null, servedFromCache, message);
    }

    /**
     * 为工业试算补齐有限数量的订单簿和历史数据。
     *
     * <p>全量工业扫描不能绕过后台调度访问外部市场。此方法只标记相关快照为近期使用，使其在后续
     * 官方 ESI 同步结果中优先被消费；缺少行情的机会仍会降级为可试算。</p>
     */
    public void prepareIndustrySnapshots(Collection<Integer> typeIds, int limit) {
        if (!properties.isEnabled() || typeIds == null || typeIds.isEmpty()) {
            return;
        }
        int processed = 0;
        for (Integer typeId : new LinkedHashSet<>(typeIds)) {
            if (processed >= Math.max(1, limit)) {
                break;
            }
            touch(typeId, snapshotMapper.selectById(typeId));
            processed++;
        }
    }

    /**
     * 低频补齐全站物品的价格历史。历史由公开接口直接提供，不依赖用户先点开每个物品。
     *
     * <p>每次只处理受限数量且只请求一个区域历史接口；订单簿仅由全量后台同步读取，避免形成
     * 按页面、按物品的突发抓取。</p>
     */
    public void warmPriceHistories() {
        if (!properties.isEnabled()) {
            return;
        }
        for (Integer typeId : typeReferenceMapper.selectHistoryWarmupTypeIds(properties
            .getHistoryWarmupLimit(), historySource())) {
            if (!refreshingTypes.add(typeId)) {
                continue;
            }
            try {
                refreshHistory(typeId);
            } catch (RuntimeException e) {
                recordFailure(typeId, "国服 ESI 市场历史暂时不可用");
                log.debug("国服 ESI 市场历史补温失败，typeId={}, errorType={}", typeId, e.getClass().getSimpleName());
            } finally {
                refreshingTypes.remove(typeId);
            }
        }
    }

    /**
     * 按官方 ESI 的区域订单快照更新全部吉他报价和订单簿。
     *
     * <p>该方法只由带分布式锁的后台任务调用。每一页请求均通过 {@link SerenityEsiClient} 的全局
     * 节流器串行执行，只有全部页面读取成功才开始写入本地快照，避免半次抓取覆盖最后成功数据。</p>
     */
    public LocalDateTime synchronizeMarketOrders() {
        if (!properties.isEnabled()) {
            return null;
        }
        MarketOrders orders = readJitaOrders();
        persistMarketOrders(orders.byType());
        log.info("国服 ESI 吉他市场订单同步完成，typeCount={}", orders.byType().size());
        return orders.expiresAt();
    }

    /**
     * 为月矿压缩估值标记少量高密度矿报价的使用状态，不在业务请求中访问官方接口。
     *
     * @param typeIds 需要估值的高密度月矿类型 ID
     */
    public void refreshQuotesForValuation(Collection<Integer> typeIds) {
        if (!properties.isEnabled() || typeIds == null || typeIds.isEmpty()) {
            return;
        }
        for (Integer typeId : typeIds.stream().filter(id -> id != null && id > 0).distinct().toList()) {
            touch(typeId, snapshotMapper.selectById(typeId));
        }
    }

    /** 读取一轮完整区域订单，只在所有页面成功后才返回可落库数据。 */
    private MarketOrders readJitaOrders() {
        var firstPage = serenityEsiClient.getMarketOrdersWithMetadata(properties.getRegionId(), 1);
        LocalDateTime expiresAt = firstPage.expiresAt();
        Map<Integer, List<SerenityMarketOrderResponse>> result = new HashMap<>();
        collectJitaOrders(result, firstPage.body());
        for (int page = 2; page <= firstPage.pageCount(); page++) {
            var nextPage = serenityEsiClient.getMarketOrdersWithMetadata(properties.getRegionId(), page);
            collectJitaOrders(result, nextPage.body());
            if (nextPage.expiresAt() != null && (expiresAt == null || nextPage.expiresAt().isAfter(expiresAt))) {
                expiresAt = nextPage.expiresAt();
            }
        }
        return new MarketOrders(result, expiresAt);
    }

    /** 将一页区域订单过滤为既定吉他口径；可选空间站配置会进一步收窄范围。 */
    private void collectJitaOrders(Map<Integer, List<SerenityMarketOrderResponse>> ordersByType,
                                   Collection<SerenityMarketOrderResponse> orders) {
        for (SerenityMarketOrderResponse order : orders) {
            if (order.typeId() == null || order.typeId() <= 0 || order.systemId() == null || order
                .locationId() == null || order.price() == null || order.volumeRemain() == null || order
                    .systemId() != properties.getSolarSystemId() || (properties.getStationId() != null && !properties
                        .getStationId()
                        .equals(order.locationId()))) {
                continue;
            }
            ordersByType.computeIfAbsent(order.typeId(), ignored -> new ArrayList<>()).add(order);
        }
    }

    /** 把一轮完整订单快照转换成报价和有限深度订单簿后落库。 */
    private void persistMarketOrders(Map<Integer, List<SerenityMarketOrderResponse>> ordersByType) {
        LocalDateTime now = LocalDateTime.now();
        Map<Long, String> stationNames = resolveStationNames(ordersByType.values()
            .stream()
            .flatMap(Collection::stream)
            .map(SerenityMarketOrderResponse::locationId)
            .distinct()
            .toList());
        List<EveMarketSnapshotDO> snapshots = new ArrayList<>();
        for (Map.Entry<Integer, List<SerenityMarketOrderResponse>> entry : ordersByType.entrySet()) {
            List<EveMarketOrderResp> buyOrders = toOrders(entry.getValue(), true, stationNames);
            List<EveMarketOrderResp> sellOrders = toOrders(entry.getValue(), false, stationNames);
            QuoteData quote = quote(buyOrders, sellOrders, entry.getValue());
            EveMarketSnapshotDO snapshot = new EveMarketSnapshotDO();
            snapshot.setTypeId(entry.getKey());
            snapshot.setCreateTime(now);
            snapshot.setHighestBuyPrice(quote.highestBuyPrice());
            snapshot.setLowestSellPrice(quote.lowestSellPrice());
            snapshot.setBuyVolume(quote.buyVolume());
            snapshot.setSellVolume(quote.sellVolume());
            snapshot.setSourceUpdatedAt(now);
            snapshot.setQuoteSynchronizedAt(now);
            snapshot.setDetailSynchronizedAt(now);
            snapshot.setBuyOrdersJson(writeJson(buyOrders));
            snapshot.setSellOrdersJson(writeJson(sellOrders));
            snapshot.setLastFailureMessage(null);
            snapshot.setUpdateTime(now);
            snapshots.add(snapshot);
        }
        snapshotStore.replaceOrders(snapshots, now);
    }

    /** 按静态资料的真实分类批量解析 NPC 空间站和公开建筑，不访问官方接口。 */
    private Map<Long, String> resolveStationNames(Collection<Long> stationIds) {
        if (stationIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (String referenceType : List.of("NPC_STATION", "PUBLIC_STRUCTURE")) {
            staticReferenceService.findLocations(referenceType, stationIds)
                .forEach((stationId, reference) -> names.put(stationId, reference.getReferenceName()));
        }
        return names;
    }

    /** 转换并按价格截取展示所需的订单深度。 */
    private static List<EveMarketOrderResp> toOrders(Collection<SerenityMarketOrderResponse> source,
                                                     boolean buyOrder,
                                                     Map<Long, String> stationNames) {
        List<EveMarketOrderResp> result = new ArrayList<>();
        for (SerenityMarketOrderResponse order : source) {
            if (order.buyOrder() != buyOrder) {
                continue;
            }
            result.add(new EveMarketOrderResp(buyOrder ? "BUY" : "SELL", order.locationId(), stationNames.get(order
                .locationId()), order.price(), order.volumeRemain(), localDate(order.issuedAt()), null, null));
        }
        Comparator<EveMarketOrderResp> price = Comparator.comparing(EveMarketOrderResp::price, Comparator
            .nullsLast(Comparator.naturalOrder()));
        result.sort(buyOrder ? price.reversed() : price);
        return result.size() <= ORDER_LIMIT ? List.copyOf(result) : List.copyOf(result.subList(0, ORDER_LIMIT));
    }

    /** 从完整订单集合计算估值使用的最高买价、最低卖价和总挂单量。 */
    private static QuoteData quote(List<EveMarketOrderResp> buyOrders,
                                   List<EveMarketOrderResp> sellOrders,
                                   Collection<SerenityMarketOrderResponse> allOrders) {
        long buyVolume = allOrders.stream()
            .filter(SerenityMarketOrderResponse::buyOrder)
            .map(SerenityMarketOrderResponse::volumeRemain)
            .filter(value -> value != null)
            .mapToLong(Long::longValue)
            .sum();
        long sellVolume = allOrders.stream()
            .filter(order -> !order.buyOrder())
            .map(SerenityMarketOrderResponse::volumeRemain)
            .filter(value -> value != null)
            .mapToLong(Long::longValue)
            .sum();
        return new QuoteData(buyOrders.isEmpty() ? null : buyOrders.get(0).price(), sellOrders.isEmpty()
            ? null
            : sellOrders.get(0).price(), buyVolume, sellVolume);
    }

    /** 仅补齐国服 ESI 的区域历史，不在用户请求中按物品拉取。 */
    protected void refreshHistory(int typeId) {
        var response = serenityEsiClient.getMarketHistoryWithMetadata(properties.getRegionId(), typeId);
        List<EveMarketHistoryResp> history = response.body()
            .stream()
            .map(EveMarketService::toHistory)
            .sorted(Comparator.comparing(EveMarketHistoryResp::date))
            .toList();
        if (history.size() > HISTORY_LIMIT) {
            history = List.copyOf(history.subList(history.size() - HISTORY_LIMIT, history.size()));
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRequestAt = now.plusDays(1);
        if (response.expiresAt() != null) {
            LocalDateTime localExpiresAt = response.expiresAt()
                .atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(java.time.ZoneId.systemDefault())
                .toLocalDateTime();
            if (localExpiresAt.isAfter(nextRequestAt)) {
                nextRequestAt = localExpiresAt;
            }
        }
        snapshotMapper.upsertHistory(typeId, writeJson(history), historySource(), now, nextRequestAt);
    }

    /** 将官方区域历史映射为现有图表字段；ESI 不提供开盘和收盘时统一以日均价展示。 */
    private static EveMarketHistoryResp toHistory(SerenityMarketHistoryResponse source) {
        return new EveMarketHistoryResp(source.date(), source.average(), source.average(), source.highest(), source
            .lowest(), source.volume());
    }

    /** 从本地快照恢复订单，损坏缓存仅降级为空集合而不影响市场页面。 */
    private List<EveMarketOrderResp> readOrders(EveMarketSnapshotDO snapshot, String side) {
        if (snapshot == null) {
            return List.of();
        }
        String json = "BUY".equals(side) ? snapshot.getBuyOrdersJson() : snapshot.getSellOrdersJson();
        List<EveMarketOrderResp> orders;
        try {
            orders = json == null || json.isBlank()
                ? List.of()
                : objectMapper.readerForListOf(EveMarketOrderResp.class).readValue(json);
        } catch (Exception e) {
            return List.of();
        }
        // 兼容修复前已落库的缺名订单，只补响应名称，不改行情时间、不写回陈旧快照。
        List<Long> missingNames = orders.stream()
            .filter(order -> order.stationName() == null || order.stationName().isBlank())
            .map(EveMarketOrderResp::stationId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        if (missingNames.isEmpty()) {
            return orders;
        }
        Map<Long, String> stationNames = resolveStationNames(missingNames);
        return orders.stream()
            .map(order -> order.stationName() != null && !order.stationName().isBlank()
                ? order
                : new EveMarketOrderResp(order.side(), order.stationId(), stationNames.get(order.stationId()), order
                    .price(), order.volume(), order.issuedOn(), order.reportedAt(), order.range()))
            .toList();
    }

    /** 从本地快照恢复历史日线。 */
    private List<EveMarketHistoryResp> readHistory(EveMarketSnapshotDO snapshot) {
        if (!hasOfficialHistory(snapshot) || snapshot.getHistoryJson() == null || snapshot.getHistoryJson().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readerForListOf(EveMarketHistoryResp.class).readValue(snapshot.getHistoryJson());
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 查询当前页所需的全站共享缓存快照。 */
    private Map<Integer, EveMarketSnapshotDO> findSnapshots(Collection<Integer> typeIds) {
        if (typeIds == null || typeIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, EveMarketSnapshotDO> result = new HashMap<>();
        snapshotMapper.selectByIds(typeIds).forEach(item -> result.put(item.getTypeId(), item));
        return result;
    }

    /** 查找资料库中的物品，禁止接受不存在的内部类型 ID。 */
    private EveStaticTypeReferenceResp requireType(int typeId) {
        EveStaticTypeReferenceDO type = staticReferenceService.findTypes(Set.of(typeId)).get(typeId);
        if (type == null) {
            throw new BusinessException("未找到该游戏物品资料");
        }
        return new EveStaticTypeReferenceResp(type.getTypeId(), type.getTypeName(), type.getTypeDescription(), type
            .getMarketCategoryL1(), type.getMarketCategoryL2(), type.getMarketCategoryL3(), type
                .getMarketCategoryL4(), type.getMarketCategoryL5(), type.getMarketCategoryL6(), type
                    .getSourceUpdatedAt());
    }

    /** 更新最后访问时间，用于识别实际使用过的本地市场快照。 */
    private void touch(int typeId, EveMarketSnapshotDO snapshot) {
        if (snapshot == null) {
            return;
        }
        snapshotMapper.touch(typeId, LocalDateTime.now());
    }

    /** 记录简短失败状态，不能覆盖已经成功保存的报价、订单或历史。 */
    private void recordFailure(int typeId, String message) {
        LocalDateTime now = LocalDateTime.now();
        snapshotMapper.recordHistoryFailure(typeId, message, historySource(), now, now.plusHours(1));
    }

    /** 历史来源包含区域，切换配置后也不能误用其他区域的缓存。 */
    private String historySource() {
        return "SERENITY_ESI_REGION_V1:" + properties.getRegionId();
    }

    /** 只对当前官方来源的历史开放展示，旧第三方缓存原样保留等待替换。 */
    private boolean hasOfficialHistory(EveMarketSnapshotDO snapshot) {
        return snapshot != null && historySource().equals(snapshot.getHistorySource());
    }

    private boolean needsQuoteRefresh(EveMarketSnapshotDO snapshot) {
        return snapshot == null || snapshot.getQuoteSynchronizedAt() == null || snapshot.getQuoteSynchronizedAt()
            .isBefore(LocalDateTime.now().minus(properties.getQuoteRefreshInterval()));
    }

    private boolean needsDetailRefresh(EveMarketSnapshotDO snapshot) {
        return snapshot == null || snapshot.getDetailSynchronizedAt() == null || snapshot.getDetailSynchronizedAt()
            .isBefore(LocalDateTime.now().minus(properties.getDetailRefreshInterval()));
    }

    /** 将缓存报价连同服务端计算的有效截止时间返回给页面。 */
    private EveMarketQuoteResp toQuote(EveMarketSnapshotDO snapshot) {
        if (snapshot == null) {
            return null;
        }
        LocalDateTime synchronizedAt = snapshot.getQuoteSynchronizedAt();
        LocalDateTime freshnessExpiresAt = synchronizedAt == null
            ? null
            : synchronizedAt.plus(properties.getQuoteRefreshInterval());
        return new EveMarketQuoteResp(snapshot.getHighestBuyPrice(), snapshot.getLowestSellPrice(), snapshot
            .getBuyVolume(), snapshot.getSellVolume(), snapshot.getSourceUpdatedAt(), snapshot
                .getQuoteSynchronizedAt(), freshnessExpiresAt, needsQuoteRefresh(snapshot));
    }

    private String writeJson(Object source) {
        try {
            return objectMapper.writeValueAsString(source);
        } catch (Exception e) {
            throw new BusinessException("无法保存吉他市场详情缓存");
        }
    }

    /** 将 ESI 的 UTC 订单时间转换为展示用日期。 */
    private static LocalDate localDate(Instant value) {
        return value == null ? null : value.atZone(ZoneOffset.UTC).toLocalDate();
    }

    /** 单个物品的市场统计值。 */
    private record QuoteData(BigDecimal highestBuyPrice, BigDecimal lowestSellPrice, Long buyVolume, Long sellVolume) {
    }

    /** 完整区域订单与最晚官方缓存期限，用于跨实例调度门禁。 */
    private record MarketOrders(Map<Integer, List<SerenityMarketOrderResponse>> byType, LocalDateTime expiresAt) {
    }
}
