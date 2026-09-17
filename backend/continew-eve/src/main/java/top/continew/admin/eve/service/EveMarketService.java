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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
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
import top.continew.admin.eve.model.EveMarketSyncResp;
import top.continew.admin.eve.model.EveStaticTypeReferenceResp;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.entity.EveMarketSnapshotDO;
import top.continew.admin.eve.model.entity.EveStaticTypeReferenceDO;
import top.continew.admin.eve.model.enums.EveMineralPriceCategory;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.resp.PageResp;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提供以吉他贸易中心为固定口径的市场查询和服务器端缓存。
 *
 * <p>物品分类和名称来自本系统的 evedata 基础信息；市场数据由 CEVE Market 的公开国服接口读取。
 * 页面不会直接请求第三方：列表按页批量刷新报价，详情缓存订单簿与每日历史，上游故障时继续提供最后
 * 成功快照。</p>
 *
 * @author zhaoyuqing
 */
@Service
@Slf4j
public class EveMarketService {

    /** 吉他星系 ID，市场估值和报价均以此为唯一默认口径。 */
    private static final long JITA_SOLAR_SYSTEM_ID = 30000142L;
    /** CEVE Market 文档约定的单次 marketstat 最大物品数。 */
    private static final int MARKETSTAT_BATCH_SIZE = 20;
    /** 单个订单簿保留的最多订单，足以展示深度且防止详情响应过大。 */
    private static final int ORDER_LIMIT = 100;
    private static final int HISTORY_LIMIT = 366;

    private final EveMarketProperties properties;
    private final EveContextService contextService;
    private final EveCorporationMapper corporationMapper;
    private final EveMarketSnapshotMapper snapshotMapper;
    private final EveStaticTypeReferenceMapper typeReferenceMapper;
    private final EveStaticReferenceService staticReferenceService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    /** 单实例内按物品合并并发详情刷新，避免同一物品被多个用户重复拉取。 */
    private final Set<Integer> refreshingTypes = ConcurrentHashMap.newKeySet();

    /**
     * 创建吉他市场服务，并明确注入独立的市场 HTTP 客户端。
     *
     * @param properties             市场同步配置
     * @param contextService         当前军团上下文服务
     * @param corporationMapper      军团绑定数据访问器
     * @param snapshotMapper         行情缓存数据访问器
     * @param typeReferenceMapper    静态物品资料访问器
     * @param staticReferenceService 基础信息查询服务
     * @param objectMapper           JSON 解析器
     * @param restClient             吉他公开市场 HTTP 客户端
     */
    public EveMarketService(EveMarketProperties properties,
                            EveContextService contextService,
                            EveCorporationMapper corporationMapper,
                            EveMarketSnapshotMapper snapshotMapper,
                            EveStaticTypeReferenceMapper typeReferenceMapper,
                            EveStaticReferenceService staticReferenceService,
                            ObjectMapper objectMapper,
                            @Qualifier("eveMarketRestClient") RestClient restClient) {
        this.properties = properties;
        this.contextService = contextService;
        this.corporationMapper = corporationMapper;
        this.snapshotMapper = snapshotMapper;
        this.typeReferenceMapper = typeReferenceMapper;
        this.staticReferenceService = staticReferenceService;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    /**
     * 按基础信息分类分页查看市场。报价未命中或过期时，当前页最多二十项会合并为一个上游请求。
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
            List<Integer> typeIds = typeReferenceMapper.selectCorporationMoonOreTypeIds(UserContextHolder
                .getContext()
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
        if (properties.isEnabled()) {
            refreshQuotesIfNecessary(typeIds);
        }
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
        Long corporationId = context.corporation() == null
            ? null
            : context.corporation().corporationId();
        if (corporationId == null) {
            throw new BusinessException("请先选择所属军团后再查看军团月矿价格");
        }
        EveCorporationDO corporation = corporationMapper.selectByTenantAndCorporationId(tenantId, corporationId);
        if (corporation == null) {
            throw new BusinessException("当前所属军团不可用，请重新登录后重试");
        }
        return corporation;
    }

    /** 读取单物品完整行情；用户查看仅按需更新报价和订单，历史日线由后台独立维护。 */
    public EveMarketDetailResp detail(int typeId) {
        EveStaticTypeReferenceResp type = requireType(typeId);
        EveMarketSnapshotDO before = snapshotMapper.selectById(typeId);
        boolean shouldRefresh = needsDetailRefresh(before);
        boolean servedFromCache = false;
        String message = null;
        if (properties.isEnabled() && shouldRefresh) {
            try {
                refreshDetail(typeId, false);
            } catch (RuntimeException e) {
                servedFromCache = before != null;
                message = before == null ? "暂时无法读取吉他行情，请稍后重试" : "上游暂时不可用，正在展示最近缓存";
                log.warn("吉他市场详情刷新失败，typeId={}, errorType={}", typeId, e.getClass().getSimpleName());
            }
        } else if (!properties.isEnabled()) {
            servedFromCache = before != null;
            message = "吉他市场同步已关闭，正在展示最近缓存";
        }
        EveMarketSnapshotDO snapshot = snapshotMapper.selectById(typeId);
        if (snapshot == null && !properties.isEnabled()) {
            throw new BusinessException("吉他市场同步未启用，暂无可用缓存");
        }
        touch(typeId, snapshot);
        return new EveMarketDetailResp(type, toQuote(snapshot), readOrders(snapshot, "BUY"), readOrders(snapshot,
            "SELL"), readHistory(snapshot), snapshot == null ? null : snapshot.getHistorySynchronizedAt(),
            servedFromCache, message);
    }

    /** CEO 或总监可主动强制更新某个物品的完整买卖盘与日线。 */
    public EveMarketSyncResp synchronize(int typeId) {
        requireType(typeId);
        if (!properties.isEnabled()) {
            return new EveMarketSyncResp(false, "吉他市场同步未启用", LocalDateTime.now());
        }
        try {
            refreshDetail(typeId, true);
            return new EveMarketSyncResp(true, "已更新吉他行情、订单与历史", LocalDateTime.now());
        } catch (RuntimeException e) {
            log.warn("手动刷新吉他市场失败，typeId={}, errorType={}", typeId, e.getClass().getSimpleName());
            recordFailure(typeId, "上游暂时不可用");
            return new EveMarketSyncResp(false, "暂时无法读取吉他行情，已保留最近缓存", LocalDateTime.now());
        }
    }

    /** 定时低频刷新近期真正被查看过的物品，避免全量扫描两万余个市场类型。 */
    public void refreshRecentSnapshots() {
        if (!properties.isEnabled()) {
            return;
        }
        LocalDateTime accessThreshold = LocalDateTime.now().minusDays(7);
        List<EveMarketSnapshotDO> candidates = snapshotMapper.selectList(new LambdaQueryWrapper<EveMarketSnapshotDO>()
            .gt(EveMarketSnapshotDO::getLastAccessedAt, accessThreshold)
            .orderByAsc(EveMarketSnapshotDO::getDetailSynchronizedAt)
            .last("LIMIT " + properties.getRecentRefreshLimit()));
        for (EveMarketSnapshotDO candidate : candidates) {
            if (!needsDetailRefresh(candidate) || !refreshingTypes.add(candidate.getTypeId())) {
                continue;
            }
            try {
                refreshDetailInternal(candidate.getTypeId(), false);
            } catch (RuntimeException e) {
                recordFailure(candidate.getTypeId(), "上游暂时不可用");
                log.debug("近期吉他市场缓存刷新失败，typeId={}, errorType={}", candidate.getTypeId(), e.getClass().getSimpleName());
            } finally {
                refreshingTypes.remove(candidate.getTypeId());
            }
        }
    }

    /**
     * 低频补齐全站物品的价格历史。历史由公开接口直接提供，不依赖用户先点开每个物品。
     *
     * <p>每次只处理受限数量且只请求历史接口；订单簿仍在用户查看详情时才读取，避免对上游造成
     * 不必要的三倍请求量。</p>
     */
    public void warmPriceHistories() {
        if (!properties.isEnabled()) {
            return;
        }
        for (Integer typeId : typeReferenceMapper.selectHistoryWarmupTypeIds(properties.getHistoryWarmupLimit())) {
            if (!refreshingTypes.add(typeId)) {
                continue;
            }
            try {
                refreshHistory(typeId);
            } catch (RuntimeException e) {
                recordFailure(typeId, "上游暂时不可用");
                log.debug("吉他市场价格历史补温失败，typeId={}, errorType={}", typeId, e.getClass().getSimpleName());
            } finally {
                refreshingTypes.remove(typeId);
            }
        }
    }

    /**
     * 轮转刷新全部可交易物品的吉他报价，数据维护不依赖任何用户页面访问。
     *
     * <p>公开报价接口可一次读取二十个物品，因此该任务以批量方式运行；用户查看列表时的按页刷新
     * 仅用于缩短当前页面的等待时间，不是行情数据存在的前提。</p>
     */
    public void warmMarketQuotes() {
        if (!properties.isEnabled()) {
            return;
        }
        List<Integer> typeIds = typeReferenceMapper.selectQuoteSyncTypeIds(properties.getQuoteWarmupLimit());
        if (typeIds.isEmpty()) {
            return;
        }
        try {
            refreshQuotes(typeIds);
        } catch (RuntimeException e) {
            typeIds.forEach(typeId -> recordFailure(typeId, "上游暂时不可用"));
            log.debug("吉他市场全量报价补温失败，count={}, errorType={}", typeIds.size(), e.getClass().getSimpleName());
        }
    }

    /**
     * 为月矿压缩估值按需补齐少量高密度矿的报价，仍复用报价缓存时效。
     *
     * @param typeIds 需要估值的高密度月矿类型 ID
     */
    public void refreshQuotesForValuation(Collection<Integer> typeIds) {
        if (!properties.isEnabled() || typeIds == null || typeIds.isEmpty()) {
            return;
        }
        List<Integer> distinctIds = typeIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        for (int start = 0; start < distinctIds.size(); start += MARKETSTAT_BATCH_SIZE) {
            refreshQuotesIfNecessary(distinctIds.subList(start, Math.min(start + MARKETSTAT_BATCH_SIZE, distinctIds
                .size())));
        }
    }

    /** 批量刷新当前分页的报价；未过期项保留本地快照，避免翻页频繁请求公开上游。 */
    private void refreshQuotesIfNecessary(List<Integer> typeIds) {
        if (typeIds.isEmpty()) {
            return;
        }
        Map<Integer, EveMarketSnapshotDO> existing = findSnapshots(typeIds);
        List<Integer> staleIds = typeIds.stream().filter(typeId -> needsQuoteRefresh(existing.get(typeId))).toList();
        if (staleIds.isEmpty()) {
            return;
        }
        try {
            refreshQuotes(staleIds);
        } catch (RuntimeException e) {
            staleIds.forEach(typeId -> recordFailure(typeId, "上游暂时不可用"));
            log.debug("吉他市场分页报价刷新失败，count={}, errorType={}", staleIds.size(), e.getClass().getSimpleName());
        }
    }

    /** 调用公开 marketstat 批量接口并将价格基线写入本地。 */
    @Transactional(rollbackFor = Exception.class)
    protected void refreshQuotes(Collection<Integer> typeIds) {
        List<Integer> distinctIds = typeIds.stream()
            .filter(id -> id != null && id > 0)
            .distinct()
            .limit(MARKETSTAT_BATCH_SIZE)
            .toList();
        if (distinctIds.isEmpty()) {
            return;
        }
        Map<Integer, QuoteData> quotes = requestQuotes(distinctIds);
        LocalDateTime now = LocalDateTime.now();
        for (Integer typeId : distinctIds) {
            QuoteData quote = quotes.get(typeId);
            if (quote == null) {
                continue;
            }
            EveMarketSnapshotDO snapshot = snapshotMapper.selectById(typeId);
            if (snapshot == null) {
                snapshot = new EveMarketSnapshotDO();
                snapshot.setTypeId(typeId);
                snapshot.setCreateTime(now);
                snapshotMapper.insert(snapshot);
            }
            snapshot.setHighestBuyPrice(quote.highestBuyPrice());
            snapshot.setLowestSellPrice(quote.lowestSellPrice());
            snapshot.setBuyVolume(quote.buyVolume());
            snapshot.setSellVolume(quote.sellVolume());
            snapshot.setSourceUpdatedAt(now);
            snapshot.setQuoteSynchronizedAt(now);
            snapshot.setLastFailureMessage(null);
            snapshot.setUpdateTime(now);
            snapshotMapper.updateById(snapshot);
        }
    }

    /** 刷新一个物品的报价和订单簿；仅管理员主动刷新时同时更新日线。 */
    private void refreshDetail(int typeId, boolean includeHistory) {
        if (!refreshingTypes.add(typeId)) {
            return;
        }
        try {
            refreshDetailInternal(typeId, includeHistory);
        } finally {
            refreshingTypes.remove(typeId);
        }
    }

    /** 刷新详情缓存；历史日线只由后台补温或管理员手动刷新请求。 */
    @Transactional(rollbackFor = Exception.class)
    protected void refreshDetailInternal(int typeId, boolean includeHistory) {
        QuoteData quote = requestQuotes(List.of(typeId)).get(typeId);
        if (quote == null) {
            throw new BusinessException("上游未返回该物品的吉他报价");
        }
        JsonNode sellPayload = requestJson("/order/?typeid=" + typeId + "&regionid=0&orders=sell&server=cn");
        JsonNode buyPayload = requestJson("/order/?typeid=" + typeId + "&regionid=0&orders=buy&server=cn");
        List<EveMarketOrderResp> sellOrders = parseOrders(sellPayload.path("sell"), "SELL");
        List<EveMarketOrderResp> buyOrders = parseOrders(buyPayload.path("buy"), "BUY");
        List<EveMarketHistoryResp> history = includeHistory
            ? parseHistory(requestJson("/query_history/?typeid=" + typeId + "&regionid=0&server=cn"))
            : null;
        LocalDateTime now = LocalDateTime.now();
        EveMarketSnapshotDO snapshot = snapshotMapper.selectById(typeId);
        if (snapshot == null) {
            snapshot = new EveMarketSnapshotDO();
            snapshot.setTypeId(typeId);
            snapshot.setCreateTime(now);
            snapshotMapper.insert(snapshot);
        }
        snapshot.setHighestBuyPrice(quote.highestBuyPrice());
        snapshot.setLowestSellPrice(quote.lowestSellPrice());
        snapshot.setBuyVolume(quote.buyVolume());
        snapshot.setSellVolume(quote.sellVolume());
        snapshot.setSourceUpdatedAt(now);
        snapshot.setQuoteSynchronizedAt(now);
        snapshot.setDetailSynchronizedAt(now);
        snapshot.setBuyOrdersJson(writeJson(buyOrders));
        snapshot.setSellOrdersJson(writeJson(sellOrders));
        if (includeHistory) {
            snapshot.setHistorySynchronizedAt(now);
            snapshot.setHistoryJson(writeJson(history));
        }
        snapshot.setLastFailureMessage(null);
        snapshot.setUpdateTime(now);
        snapshotMapper.updateById(snapshot);
    }

    /** 仅补齐价格历史，不提前请求用户尚未查看的买卖订单。 */
    @Transactional(rollbackFor = Exception.class)
    protected void refreshHistory(int typeId) {
        JsonNode historyPayload = requestJson("/query_history/?typeid=" + typeId + "&regionid=0&server=cn");
        List<EveMarketHistoryResp> history = parseHistory(historyPayload);
        LocalDateTime now = LocalDateTime.now();
        EveMarketSnapshotDO snapshot = snapshotMapper.selectById(typeId);
        if (snapshot == null) {
            snapshot = new EveMarketSnapshotDO();
            snapshot.setTypeId(typeId);
            snapshot.setCreateTime(now);
            snapshotMapper.insert(snapshot);
        }
        snapshot.setHistoryJson(writeJson(history));
        snapshot.setHistorySynchronizedAt(now);
        snapshot.setLastFailureMessage(null);
        snapshot.setUpdateTime(now);
        snapshotMapper.updateById(snapshot);
    }

    /** 从公开 marketstat XML 解析当前最高求购、最低卖出和总量。 */
    private Map<Integer, QuoteData> requestQuotes(List<Integer> typeIds) {
        StringBuilder endpoint = new StringBuilder("/api/marketstat?");
        for (Integer typeId : typeIds) {
            endpoint.append("typeid=").append(typeId).append('&');
        }
        endpoint.append("usesystem=").append(JITA_SOLAR_SYSTEM_ID);
        String xml = restClient.get().uri(properties.getBaseUrl() + endpoint).retrieve().body(String.class);
        if (xml == null || xml.isBlank()) {
            throw new BusinessException("市场上游未返回报价数据");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            Map<Integer, QuoteData> result = new HashMap<>();
            NodeList nodes = document.getElementsByTagName("type");
            for (int index = 0; index < nodes.getLength(); index++) {
                Element type = (Element)nodes.item(index);
                int typeId = Integer.parseInt(type.getAttribute("id"));
                Element buy = child(type, "buy");
                Element sell = child(type, "sell");
                result
                    .put(typeId, new QuoteData(decimal(childText(buy, "max")), decimal(childText(sell, "min")), longValue(childText(buy, "volume")), longValue(childText(sell, "volume"))));
            }
            return result;
        } catch (Exception e) {
            throw new BusinessException("无法解析吉他市场报价");
        }
    }

    /** 请求网站详情页面同源使用的公开 JSON 接口；请求只在服务器缓存刷新时发起。 */
    private JsonNode requestJson(String endpoint) {
        String body = restClient.get()
            .uri(properties.getBaseUrl() + endpoint)
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .retrieve()
            .body(String.class);
        if (body == null || body.isBlank()) {
            throw new BusinessException("市场上游未返回详情数据");
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new BusinessException("无法解析吉他市场详情");
        }
    }

    /** 解析并限制订单深度，卖单由低到高、买单由高到低呈现。 */
    private static List<EveMarketOrderResp> parseOrders(JsonNode source, String side) {
        if (!source.isArray()) {
            return List.of();
        }
        List<EveMarketOrderResp> result = new ArrayList<>();
        for (JsonNode item : source) {
            result.add(new EveMarketOrderResp(side, longValue(item.path("stationid").asText()), item.path("stationname")
                .asText(null), decimal(item.path("price").asText()), longValue(item.path("vol")
                    .asText()), localDate(item.path("issued").asText(null)), localDateTime(item.path("reported")
                        .asText(null)), intValue(item.path("range").asText())));
        }
        Comparator<EveMarketOrderResp> price = Comparator.comparing(EveMarketOrderResp::price, Comparator
            .nullsLast(Comparator.naturalOrder()));
        result.sort("BUY".equals(side) ? price.reversed() : price);
        return result.size() <= ORDER_LIMIT ? List.copyOf(result) : List.copyOf(result.subList(0, ORDER_LIMIT));
    }

    /** 解析最近一年的每日价格走势；最新日期排在最后，便于直接绘制时间轴。 */
    private static List<EveMarketHistoryResp> parseHistory(JsonNode source) {
        if (!source.isArray()) {
            return List.of();
        }
        List<EveMarketHistoryResp> result = new ArrayList<>();
        for (JsonNode item : source) {
            LocalDate date = localDate(item.path("date").asText(null));
            if (date != null) {
                result.add(new EveMarketHistoryResp(date, decimal(item.path("open").asText()), decimal(item
                    .path("close")
                    .asText()), decimal(item.path("high").asText()), decimal(item.path("low").asText()), longValue(item
                        .path("volume")
                        .asText())));
            }
        }
        result.sort(Comparator.comparing(EveMarketHistoryResp::date));
        return result.size() <= HISTORY_LIMIT
            ? List.copyOf(result)
            : List.copyOf(result.subList(result.size() - HISTORY_LIMIT, result.size()));
    }

    /** 从本地快照恢复订单，损坏缓存仅降级为空集合而不影响市场页面。 */
    private List<EveMarketOrderResp> readOrders(EveMarketSnapshotDO snapshot, String side) {
        if (snapshot == null) {
            return List.of();
        }
        String json = "BUY".equals(side) ? snapshot.getBuyOrdersJson() : snapshot.getSellOrdersJson();
        try {
            return json == null || json.isBlank()
                ? List.of()
                : objectMapper.readerForListOf(EveMarketOrderResp.class).readValue(json);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 从本地快照恢复历史日线。 */
    private List<EveMarketHistoryResp> readHistory(EveMarketSnapshotDO snapshot) {
        if (snapshot == null || snapshot.getHistoryJson() == null || snapshot.getHistoryJson().isBlank()) {
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
        snapshotMapper.selectList(new LambdaQueryWrapper<EveMarketSnapshotDO>()
            .in(EveMarketSnapshotDO::getTypeId, typeIds)).forEach(item -> result.put(item.getTypeId(), item));
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

    /** 更新最后访问时间，供后台仅刷新实际被使用过的市场缓存。 */
    private void touch(int typeId, EveMarketSnapshotDO snapshot) {
        if (snapshot == null) {
            return;
        }
        snapshot.setLastAccessedAt(LocalDateTime.now());
        snapshotMapper.updateById(snapshot);
    }

    /** 记录简短失败状态，不能覆盖已经成功保存的报价、订单或历史。 */
    private void recordFailure(int typeId, String message) {
        EveMarketSnapshotDO snapshot = snapshotMapper.selectById(typeId);
        if (snapshot == null) {
            return;
        }
        snapshot.setLastFailureMessage(message);
        snapshot.setUpdateTime(LocalDateTime.now());
        snapshotMapper.updateById(snapshot);
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

    private static Element child(Element source, String name) {
        if (source == null) {
            return null;
        }
        NodeList nodes = source.getChildNodes();
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node instanceof Element element && name.equals(element.getTagName())) {
                return element;
            }
        }
        return null;
    }

    private static String childText(Element source, String name) {
        Element child = child(source, name);
        return child == null ? null : child.getTextContent();
    }

    private static BigDecimal decimal(String value) {
        try {
            return value == null || value.isBlank() ? null : new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long longValue(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer intValue(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate localDate(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static LocalDateTime localDateTime(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDateTime.parse(value.replace(' ', 'T'));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 单个物品的市场统计值。 */
    private record QuoteData(BigDecimal highestBuyPrice, BigDecimal lowestSellPrice, Long buyVolume, Long sellVolume) {
    }
}
