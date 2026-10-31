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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import top.continew.admin.eve.client.SerenityEsiClient;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.config.EveMarketProperties;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.mapper.EveMarketRefreshBatchMapper;
import top.continew.admin.eve.mapper.EveMarketSnapshotMapper;
import top.continew.admin.eve.mapper.EveMarketRefreshQueueMapper;
import top.continew.admin.eve.mapper.EveStaticTypeReferenceMapper;
import top.continew.admin.eve.model.EveMarketDetailResp;
import top.continew.admin.eve.model.EveMarketHistoryResp;
import top.continew.admin.eve.model.EveMarketItemResp;
import top.continew.admin.eve.model.EveMarketOrderResp;
import top.continew.admin.eve.model.EveMarketQuoteResp;
import top.continew.admin.eve.model.EveMarketRefreshResult;
import top.continew.admin.eve.model.EveStaticTypeReferenceResp;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.entity.EveMarketRefreshBatchDO;
import top.continew.admin.eve.model.entity.EveMarketSnapshotDO;
import top.continew.admin.eve.model.entity.EveMarketRefreshQueueDO;
import top.continew.admin.eve.model.entity.EveStaticTypeReferenceDO;
import top.continew.admin.eve.model.enums.EveMineralPriceCategory;
import top.continew.admin.eve.model.serenity.SerenityMarketHistoryResponse;
import top.continew.admin.eve.model.serenity.SerenityMarketOrderResponse;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 提供以吉他贸易中心为固定口径的市场查询和服务器端缓存。
 *
 * <p>业务只对实际使用的物品按需读取国服官方 ESI；本地快照用于跨页面复用和上游异常时明确降级，
 * 不再要求用户等待全市场定时扫描。</p>
 *
 * @author zhaoyuqing
 */
@Service
@Slf4j
public class EveMarketService {

    private static final int HISTORY_LIMIT = 366;
    /** 页面和估值可直接复用的按需订单窗口；官方 HTTP 缓存仍由客户端独立遵守。 */
    private static final Duration ON_DEMAND_QUOTE_WINDOW = Duration.ofMinutes(5);
    /** 官方成交历史按日汇总，一天内的成功结果可直接复用。 */
    private static final Duration ON_DEMAND_HISTORY_WINDOW = Duration.ofDays(1);

    private final EveMarketProperties properties;
    private final EveContextService contextService;
    private final EveCorporationMapper corporationMapper;
    private final EveMarketSnapshotMapper snapshotMapper;
    private final EveStaticTypeReferenceMapper typeReferenceMapper;
    private final EveStaticReferenceService staticReferenceService;
    private final SerenityEsiClient serenityEsiClient;
    private final ObjectMapper objectMapper;
    private final EveMarketSnapshotStore snapshotStore;
    private final RedissonClient redissonClient;
    private final EveMarketRefreshQueueMapper refreshQueueMapper;
    private final EveMarketRefreshBatchMapper refreshBatchMapper;

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
     * @param redissonClient         跨实例按物品请求锁
     * @param refreshQueueMapper     持久化按需刷新队列
     * @param refreshBatchMapper     持久化按需刷新批次
     */
    public EveMarketService(EveMarketProperties properties,
                            EveContextService contextService,
                            EveCorporationMapper corporationMapper,
                            EveMarketSnapshotMapper snapshotMapper,
                            EveStaticTypeReferenceMapper typeReferenceMapper,
                            EveStaticReferenceService staticReferenceService,
                            SerenityEsiClient serenityEsiClient,
                            ObjectMapper objectMapper,
                            EveMarketSnapshotStore snapshotStore,
                            RedissonClient redissonClient,
                            EveMarketRefreshQueueMapper refreshQueueMapper,
                            EveMarketRefreshBatchMapper refreshBatchMapper) {
        this.properties = properties;
        this.contextService = contextService;
        this.corporationMapper = corporationMapper;
        this.snapshotMapper = snapshotMapper;
        this.typeReferenceMapper = typeReferenceMapper;
        this.staticReferenceService = staticReferenceService;
        this.serenityEsiClient = serenityEsiClient;
        this.objectMapper = objectMapper;
        this.snapshotStore = snapshotStore;
        this.redissonClient = redissonClient;
        this.refreshQueueMapper = refreshQueueMapper;
        this.refreshBatchMapper = refreshBatchMapper;
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

    /** 为当前页物品按需取得官方行情，并明确标记本轮失败或延期的旧快照。 */
    private PageResp<EveMarketItemResp> attachQuotes(PageResp<EveStaticTypeReferenceResp> types) {
        List<Integer> typeIds = types.getList().stream().map(EveStaticTypeReferenceResp::typeId).toList();
        EveMarketRefreshResult refresh = queueCurrentMarketTypes(typeIds);
        Set<Integer> unavailable = new java.util.HashSet<>(refresh.failedTypeIds());
        unavailable.addAll(refresh.deferredTypeIds());
        Set<Integer> failed = Set.copyOf(refresh.failedTypeIds());
        Set<Integer> pending = Set.copyOf(refresh.deferredTypeIds());
        Map<Integer, EveMarketSnapshotDO> snapshots = findSnapshots(typeIds);
        return new PageResp<>(types.getList()
            .stream()
            .map(type -> new EveMarketItemResp(type, toQuote(snapshots.get(type.typeId()), unavailable
                .contains(type.typeId())), failed.contains(type.typeId())
                    ? "FAILED"
                    : pending.contains(type.typeId()) ? "PENDING" : "READY"))
            .toList(), types.getTotal());
    }

    /**
     * 列表按每个物品当前官方有效期判断；报价一旦到期便立即重新入队，不复用大批次完成窗口。
     */
    EveMarketRefreshResult queueCurrentMarketTypes(Collection<Integer> typeIds) {
        if (!properties.isEnabled() || typeIds == null || typeIds.isEmpty()) {
            return new EveMarketRefreshResult(List.of(), List.of(), typeIds == null ? List.of() : typeIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList());
        }
        List<Integer> requested = typeIds.stream().filter(id -> id != null && id > 0).distinct().sorted().toList();
        Map<Integer, EveMarketSnapshotDO> snapshots = findSnapshots(requested);
        Map<Integer, EveMarketRefreshQueueDO> queuedTasks = findQueuedTasks(requested);
        List<Integer> refreshed = new ArrayList<>();
        List<Integer> failed = new ArrayList<>();
        List<Integer> deferred = new ArrayList<>();
        for (Integer typeId : requested) {
            EveMarketSnapshotDO snapshot = snapshots.get(typeId);
            if (hasRecentFailure(snapshot, false)) {
                failed.add(typeId);
                enqueueRefresh(typeId, false, deferred, failed, queuedTasks.get(typeId));
            } else if (isOnDemandFresh(snapshot, false)) {
                refreshed.add(typeId);
            } else {
                deferred.add(typeId);
                enqueueRefresh(typeId, false, deferred, failed, queuedTasks.get(typeId));
            }
        }
        return new EveMarketRefreshResult(refreshed, failed, deferred);
    }

    /**
     * 把列表中实际出现的物品加入持久化刷新队列，避免请求线程串行等待全局 ESI 节流。
     *
     * <p>任务按类型 ID 去重，进程重启后仍会保留；后台消费者完成后页面轮询即可看到新行情。</p>
     */
    public EveMarketRefreshResult queueMarketTypes(Collection<Integer> typeIds, boolean includeHistory) {
        if (!properties.isEnabled() || typeIds == null || typeIds.isEmpty()) {
            return new EveMarketRefreshResult(List.of(), List.of(), typeIds == null ? List.of() : typeIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList());
        }
        List<Integer> requested = typeIds.stream().filter(id -> id != null && id > 0).distinct().sorted().toList();
        if (requested.isEmpty()) {
            return new EveMarketRefreshResult(List.of(), List.of(), List.of());
        }
        LocalDateTime now = LocalDateTime.now();
        String batchKey = marketBatchKey(requested, includeHistory);
        refreshBatchMapper.startOrReuse(batchKey, includeHistory, requested.size(), now, now
            .minus(ON_DEMAND_QUOTE_WINDOW));
        EveMarketRefreshBatchDO batch = refreshBatchMapper.selectBatch(batchKey);
        if (batch == null || batch.getRequestedAt() == null) {
            throw new IllegalStateException("无法建立市场行情刷新批次");
        }
        LocalDateTime requestedAt = batch.getRequestedAt();
        Map<Integer, EveMarketSnapshotDO> snapshots = findSnapshots(requested);
        Map<Integer, EveMarketRefreshQueueDO> queuedTasks = findQueuedTasks(requested);
        List<Integer> refreshed = new ArrayList<>();
        List<Integer> failed = new ArrayList<>();
        List<Integer> deferred = new ArrayList<>();
        for (Integer typeId : requested) {
            EveMarketSnapshotDO snapshot = snapshots.get(typeId);
            if (hasBatchFailure(snapshot, includeHistory, requestedAt)) {
                failed.add(typeId);
                enqueueRefresh(typeId, includeHistory, deferred, failed, queuedTasks.get(typeId));
                continue;
            }
            if (isBatchReady(snapshot, includeHistory, requestedAt)) {
                refreshed.add(typeId);
                continue;
            }
            deferred.add(typeId);
            enqueueRefresh(typeId, includeHistory, deferred, failed, queuedTasks.get(typeId));
        }
        if (failed.isEmpty() && deferred.isEmpty()) {
            refreshBatchMapper.markCompleted(batchKey, requestedAt, LocalDateTime.now());
        }
        return new EveMarketRefreshResult(refreshed, failed, deferred);
    }

    /** 同一物品集合复用稳定批次键，调用顺序或租户不会影响公开行情批次。 */
    private static String marketBatchKey(List<Integer> typeIds, boolean includeHistory) {
        String source = (includeHistory ? "H:" : "Q:") + typeIds.stream()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
        return DigestUtils.md5DigestAsHex(source.getBytes(StandardCharsets.UTF_8));
    }

    /** 批次只要求数据不早于固定起点，处理超过五分钟也不会把已完成项重新入队。 */
    private boolean isBatchReady(EveMarketSnapshotDO snapshot,
                                 boolean includeHistory,
                                 LocalDateTime requestedAt) {
        if (snapshot == null || snapshot.getQuoteValidatedAt() == null || snapshot.getQuoteNextRequestAt() == null) {
            return false;
        }
        boolean quoteValidatedForBatch = !snapshot.getQuoteValidatedAt().isBefore(requestedAt) || (!snapshot
            .getQuoteValidatedAt()
            .isBefore(requestedAt.minus(ON_DEMAND_QUOTE_WINDOW)) && snapshot.getQuoteNextRequestAt()
                .isAfter(requestedAt));
        if (!quoteValidatedForBatch) {
            return false;
        }
        return !includeHistory || hasOfficialHistory(snapshot) && snapshot.getHistoryNextRequestAt() != null && snapshot
            .getHistoryNextRequestAt()
            .isAfter(requestedAt);
    }

    /** 只把批次开始后的失败计入本轮状态，旧故障不能污染新批次。 */
    private static boolean hasBatchFailure(EveMarketSnapshotDO snapshot,
                                           boolean includeHistory,
                                           LocalDateTime requestedAt) {
        if (snapshot == null) {
            return false;
        }
        boolean orderFailed = snapshot.getOrderFailureMessage() != null && !snapshot.getOrderFailureMessage()
            .isBlank() && snapshot.getOrderFailureAt() != null && !snapshot.getOrderFailureAt().isBefore(requestedAt);
        boolean historyFailed = includeHistory && snapshot.getHistoryFailureMessage() != null && !snapshot
            .getHistoryFailureMessage()
            .isBlank() && snapshot.getHistoryFailureAt() != null && !snapshot.getHistoryFailureAt()
                .isBefore(requestedAt);
        return orderFailed || historyFailed;
    }

    /** 持久入队失败时从 PENDING 切换为 FAILED。 */
    private void enqueueRefresh(int typeId,
                                boolean includeHistory,
                                List<Integer> deferred,
                                List<Integer> failed,
                                EveMarketRefreshQueueDO queuedTask) {
        if (queuedTask != null && (!includeHistory || Boolean.TRUE.equals(queuedTask.getIncludeHistory()))) {
            return;
        }
        try {
            refreshQueueMapper.enqueue(typeId, includeHistory, LocalDateTime.now());
        } catch (RuntimeException e) {
            deferred.remove(Integer.valueOf(typeId));
            if (!failed.contains(typeId)) {
                failed.add(typeId);
            }
            log.debug("提交市场刷新任务失败，typeId={}, errorType={}", typeId, e.getClass().getSimpleName());
        }
    }

    /** 批量读取已入队物品；消费竞态导致的短暂缺失会在下一次轮询重新提交。 */
    private Map<Integer, EveMarketRefreshQueueDO> findQueuedTasks(List<Integer> typeIds) {
        if (typeIds.isEmpty()) {
            return Map.of();
        }
        return refreshQueueMapper.selectQueued(typeIds).stream()
            .collect(Collectors.toMap(EveMarketRefreshQueueDO::getTypeId, task -> task));
    }

    /**
     * 消费持久化按需任务；全局锁保证多实例不会重复处理，失败任务按分钟级指数退避。
     *
     * @param limit 单轮最多处理的物品数
     * @return 本轮成功处理数量
     */
    public int consumeMarketRefreshQueue(int limit) {
        if (!properties.isEnabled() || limit <= 0) {
            return 0;
        }
        RLock lock = redissonClient.getLock("eve:market:on-demand:queue-consumer");
        if (!lock.tryLock()) {
            return 0;
        }
        try {
            int succeeded = 0;
            for (EveMarketRefreshQueueDO task : refreshQueueMapper.selectDue(LocalDateTime.now(), limit)) {
                try {
                    refreshMarketType(task.getTypeId(), Boolean.TRUE.equals(task.getIncludeHistory()));
                    refreshQueueMapper.deleteTask(task.getTypeId(), task.getTaskVersion() == null
                        ? 0L
                        : task.getTaskVersion());
                    succeeded++;
                } catch (RuntimeException e) {
                    int attempts = task.getAttemptCount() == null ? 0 : task.getAttemptCount();
                    long delayMinutes = Math.min(60, 1L << Math.min(6, attempts));
                    LocalDateTime now = LocalDateTime.now();
                    refreshQueueMapper.recordFailure(task.getTypeId(), task.getTaskVersion() == null
                        ? 0L
                        : task.getTaskVersion(), "国服市场行情暂时不可用", now.plusMinutes(delayMinutes), now);
                    log.debug("消费市场刷新队列失败，typeId={}, attempt={}, errorType={}", task.getTypeId(), attempts
                        + 1, e.getClass().getSimpleName());
                }
            }
            return succeeded;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 判断订单及可选历史是否已在按需复用窗口内成功更新。 */
    private boolean isOnDemandFresh(EveMarketSnapshotDO snapshot, boolean includeHistory) {
        if (snapshot == null || snapshot.getQuoteNextRequestAt() == null || !snapshot.getQuoteNextRequestAt()
            .isAfter(LocalDateTime.now())) {
            return false;
        }
        return !includeHistory || hasOfficialHistory(snapshot) && snapshot.getHistorySynchronizedAt() != null && !snapshot
            .getHistorySynchronizedAt()
            .isBefore(LocalDateTime.now().minus(ON_DEMAND_HISTORY_WINDOW));
    }

    /** 最近失败的物品在响应中明确标记失败，同时仍允许后台任务继续重试。 */
    private static boolean hasRecentFailure(EveMarketSnapshotDO snapshot, boolean includeHistory) {
        if (snapshot == null) {
            return false;
        }
        boolean orderFailed = snapshot.getOrderFailureMessage() != null && !snapshot.getOrderFailureMessage()
            .isBlank() && snapshot.getOrderFailureAt() != null && !snapshot.getOrderFailureAt()
                .isBefore(LocalDateTime.now().minus(ON_DEMAND_QUOTE_WINDOW));
        boolean historyFailed = includeHistory && snapshot.getHistoryFailureMessage() != null && !snapshot
            .getHistoryFailureMessage()
            .isBlank() && snapshot.getHistoryFailureAt() != null && !snapshot.getHistoryFailureAt()
                .isBefore(LocalDateTime.now().minus(ON_DEMAND_HISTORY_WINDOW));
        return orderFailed || historyFailed;
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

    /** 读取单物品完整行情；本轮直接按需取得官方订单与历史，失败时明确返回最近成功快照。 */
    public EveMarketDetailResp detail(int typeId) {
        EveStaticTypeReferenceResp type = requireType(typeId);
        String failure = null;
        boolean orderFailed = false;
        if (properties.isEnabled()) {
            try {
                refreshMarketOrders(typeId);
            } catch (RuntimeException e) {
                orderFailed = true;
                failure = "国服市场订单暂时不可用，正在展示最近成功数据";
                recordMarketFailureSafely(typeId, failure);
                log.debug("按需读取国服市场订单失败，typeId={}, errorType={}", typeId, e.getClass().getSimpleName());
            }
            try {
                refreshMarketHistory(typeId);
            } catch (RuntimeException e) {
                String historyFailure = "国服市场历史暂时不可用，正在展示最近成功数据";
                failure = failure == null ? historyFailure : failure + "；市场历史也暂时不可用";
                recordHistoryFailureSafely(typeId, historyFailure);
                log.debug("按需读取国服市场历史失败，typeId={}, errorType={}", typeId, e.getClass().getSimpleName());
            }
        }
        EveMarketSnapshotDO snapshot = snapshotMapper.selectById(typeId);
        boolean stale = snapshot == null || needsQuoteRefresh(snapshot) || needsDetailRefresh(snapshot);
        boolean servedFromCache = stale || failure != null;
        String message = failure;
        if (!properties.isEnabled()) {
            message = snapshot == null ? "吉他市场同步未启用，暂无可用缓存" : "吉他市场同步已关闭，正在展示最近缓存";
        } else if (message == null && snapshot == null) {
            message = "官方接口暂未返回该物品行情";
        } else if (message == null && stale) {
            message = "行情已超过本地新鲜度窗口，当前展示最近成功数据";
        }
        touch(typeId, snapshot);
        return new EveMarketDetailResp(type, toQuote(snapshot, orderFailed), readOrders(snapshot, "BUY"), readOrders(snapshot, "SELL"), readHistory(snapshot), hasOfficialHistory(snapshot)
            ? snapshot.getHistorySynchronizedAt()
            : null, servedFromCache, message);
    }

    /**
     * 按更新时间从旧到新刷新一批实际使用的物品，确保重复调用时延期项能够继续推进。
     *
     * @param typeIds        实际使用的物品类型
     * @param limit          本轮最多处理的物品数
     * @param includeHistory 是否同时读取官方区域历史
     * @return 已刷新、失败和因本轮预算延期的物品
     */
    public EveMarketRefreshResult refreshMarketTypes(Collection<Integer> typeIds,
                                                     int limit,
                                                     boolean includeHistory) {
        if (!properties.isEnabled() || typeIds == null || typeIds.isEmpty()) {
            return new EveMarketRefreshResult(List.of(), List.of(), typeIds == null ? List.of() : typeIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList());
        }
        List<Integer> requested = typeIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        Map<Integer, EveMarketSnapshotDO> snapshots = findSnapshots(requested);
        List<Integer> ordered = requested.stream()
            .sorted(Comparator.comparing(id -> {
                EveMarketSnapshotDO snapshot = snapshots.get(id);
                return snapshot == null || snapshot.getUpdateTime() == null ? LocalDateTime.MIN : snapshot
                    .getUpdateTime();
            }))
            .toList();
        int budget = Math.max(0, limit);
        List<Integer> refreshed = new ArrayList<>();
        List<Integer> failed = new ArrayList<>();
        List<Integer> deferred = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            Integer typeId = ordered.get(index);
            if (index >= budget) {
                deferred.add(typeId);
                continue;
            }
            try {
                refreshMarketType(typeId, includeHistory);
                refreshed.add(typeId);
            } catch (RuntimeException e) {
                failed.add(typeId);
                log.debug("批量按需读取国服市场失败，typeId={}, errorType={}", typeId, e.getClass().getSimpleName());
            }
        }
        return new EveMarketRefreshResult(refreshed, failed, deferred);
    }

    /** 按需读取一个物品的订单，并按需补充官方区域历史。 */
    public void refreshMarketType(int typeId, boolean includeHistory) {
        if (!properties.isEnabled() || typeId <= 0) {
            return;
        }
        try {
            refreshMarketOrders(typeId);
        } catch (RuntimeException e) {
            recordMarketFailureSafely(typeId, "国服市场订单暂时不可用");
            throw e;
        }
        if (includeHistory) {
            try {
                refreshMarketHistory(typeId);
            } catch (RuntimeException e) {
                recordHistoryFailureSafely(typeId, "国服市场历史暂时不可用");
                throw e;
            }
        }
    }

    /** 合并跨实例同物品请求，全部分页成功后才覆盖该物品订单快照。 */
    private void refreshMarketOrders(int typeId) {
        RLock lock = redissonClient.getLock("eve:market:on-demand:orders:" + properties.getRegionId() + ":" + typeId);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(30, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("市场订单请求繁忙");
            }
            EveMarketSnapshotDO existing = snapshotMapper.selectById(typeId);
            if (existing != null && existing.getQuoteNextRequestAt() != null && existing.getQuoteNextRequestAt()
                .isAfter(LocalDateTime.now())) {
                snapshotMapper.markQuoteValidated(typeId, LocalDateTime.now());
                return;
            }
            TypeMarketOrders orders = readMarketOrders(typeId);
            persistMarketType(typeId, orders.orders(), orders.sourceUpdatedAt(), orders.nextRequestAt());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("市场订单请求被中断", e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 完整读取单物品的全部订单页，任一页失败都不产生半份快照。 */
    private TypeMarketOrders readMarketOrders(int typeId) {
        var first = serenityEsiClient.getMarketOrdersWithMetadata(properties.getRegionId(), typeId, 1);
        List<SerenityMarketOrderResponse> result = new ArrayList<>();
        LocalDateTime fallbackNextRequestAt = LocalDateTime.now().plusMinutes(1);
        LocalDateTime nextRequestAt = localExpiry(first.expiresAt());
        if (nextRequestAt == null) {
            nextRequestAt = fallbackNextRequestAt;
        }
        LocalDateTime sourceUpdatedAt = localExpiry(first.fetchedAt());
        boolean sourceTimeComplete = sourceUpdatedAt != null;
        collectMarketTypeOrders(result, typeId, first.body());
        for (int page = 2; page <= first.pageCount(); page++) {
            var response = serenityEsiClient.getMarketOrdersWithMetadata(properties.getRegionId(), typeId, page);
            collectMarketTypeOrders(result, typeId, response.body());
            LocalDateTime pageExpiry = localExpiry(response.expiresAt());
            if (pageExpiry == null) {
                pageExpiry = fallbackNextRequestAt;
            }
            if (pageExpiry.isBefore(nextRequestAt)) {
                nextRequestAt = pageExpiry;
            }
            LocalDateTime pageFetchedAt = localExpiry(response.fetchedAt());
            sourceTimeComplete &= pageFetchedAt != null;
            if (pageFetchedAt != null && (sourceUpdatedAt == null || pageFetchedAt.isBefore(sourceUpdatedAt))) {
                sourceUpdatedAt = pageFetchedAt;
            }
        }
        if (!sourceTimeComplete) {
            sourceUpdatedAt = null;
        }
        return new TypeMarketOrders(List.copyOf(result), nextRequestAt, sourceUpdatedAt);
    }

    /** 只接收目标物品且符合既定吉他星系或空间站范围的订单。 */
    private void collectMarketTypeOrders(List<SerenityMarketOrderResponse> target,
                                         int typeId,
                                         Collection<SerenityMarketOrderResponse> source) {
        for (SerenityMarketOrderResponse order : source) {
            if (order.typeId() == null || order.typeId() != typeId || order.systemId() == null || order
                .locationId() == null || order.price() == null || order.volumeRemain() == null || order
                    .systemId() != properties.getSolarSystemId() || (properties.getStationId() != null && !properties
                        .getStationId()
                        .equals(order.locationId()))) {
                continue;
            }
            target.add(order);
        }
    }

    /** 原子保存单物品的完整订单簿；空集合也会明确清空该物品旧报价。 */
    private void persistMarketType(int typeId,
                                   List<SerenityMarketOrderResponse> orders,
                                   LocalDateTime synchronizedAt,
                                   LocalDateTime nextRequestAt) {
        Map<Long, String> stationNames = resolveStationNames(orders.stream()
            .map(SerenityMarketOrderResponse::locationId)
            .distinct()
            .toList());
        List<EveMarketOrderResp> buyOrders = toOrders(orders, true, stationNames);
        List<EveMarketOrderResp> sellOrders = toOrders(orders, false, stationNames);
        QuoteData quote = quote(buyOrders, sellOrders, orders);
        LocalDateTime persistedAt = LocalDateTime.now();
        EveMarketSnapshotDO snapshot = new EveMarketSnapshotDO();
        snapshot.setTypeId(typeId);
        snapshot.setHighestBuyPrice(quote.highestBuyPrice());
        snapshot.setLowestSellPrice(quote.lowestSellPrice());
        snapshot.setBuyVolume(quote.buyVolume());
        snapshot.setSellVolume(quote.sellVolume());
        snapshot.setSourceUpdatedAt(synchronizedAt);
        snapshot.setQuoteSynchronizedAt(synchronizedAt);
        snapshot.setQuoteNextRequestAt(nextRequestAt);
        snapshot.setQuoteValidatedAt(persistedAt);
        snapshot.setDetailSynchronizedAt(synchronizedAt);
        snapshot.setBuyOrdersJson(writeJson(buyOrders));
        snapshot.setSellOrdersJson(writeJson(sellOrders));
        snapshot.setCreateTime(persistedAt);
        snapshot.setUpdateTime(persistedAt);
        snapshotMapper.upsertOrders(List.of(snapshot));
    }

    /** 合并跨实例同物品历史请求，成功后原子替换该物品官方历史。 */
    private void refreshMarketHistory(int typeId) {
        LocalDateTime requestedAt = LocalDateTime.now();
        RLock lock = redissonClient.getLock("eve:market:on-demand:history:" + properties.getRegionId() + ":" + typeId);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(30, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("市场历史请求繁忙");
            }
            EveMarketSnapshotDO existing = snapshotMapper.selectById(typeId);
            if (existing != null && hasOfficialHistory(existing) && existing.getHistorySynchronizedAt() != null && !existing
                .getHistorySynchronizedAt()
                .isBefore(requestedAt)) {
                return;
            }
            refreshHistory(typeId, requestedAt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("市场历史请求被中断", e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 为工业试算按需补齐受预算约束的订单簿和官方历史。
     */
    public EveMarketRefreshResult prepareIndustrySnapshots(Collection<Integer> typeIds, int limit) {
        // 按需任务已移到后台执行器，不能再用旧的同步请求上限永久遗漏后续材料。
        return queueMarketTypes(typeIds, true);
    }

    /**
     * 兼容旧调度入口，低频补齐最近使用物品的价格历史。
     *
     * <p>新业务入口应直接调用按需刷新；待旧任务移除后此方法可一并删除。</p>
     */
    public void warmPriceHistories() {
        if (!properties.isEnabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Integer typeId : typeReferenceMapper.selectHistoryWarmupTypeIds(properties
            .getHistoryWarmupLimit(), historySource(), now)) {
            if (!refreshingTypes.add(typeId)) {
                continue;
            }
            try {
                refreshHistory(typeId, now);
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
        persistMarketOrders(orders.byType(), localExpiry(orders.expiresAt()));
        log.info("国服 ESI 吉他市场订单同步完成，typeCount={}", orders.byType().size());
        return orders.expiresAt();
    }

    /**
     * 为月矿压缩估值标记少量高密度矿报价的使用状态，不在业务请求中访问官方接口。
     *
     * @param typeIds 需要估值的高密度月矿类型 ID
     */
    public EveMarketRefreshResult refreshQuotesForValuation(Collection<Integer> typeIds) {
        return queueMarketTypes(typeIds, false);
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
    private void persistMarketOrders(Map<Integer, List<SerenityMarketOrderResponse>> ordersByType,
                                     LocalDateTime nextRequestAt) {
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
            snapshot.setQuoteNextRequestAt(nextRequestAt);
            snapshot.setQuoteValidatedAt(now);
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

    /** 转换完整订单簿并按价格排序，工业深度计算与页面展示共用同一份权威数据。 */
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
        return List.copyOf(result);
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

    /** 读取并保存国服 ESI 直接提供的区域每日成交历史。 */
    protected void refreshHistory(int typeId, LocalDateTime now) {
        var response = serenityEsiClient.getMarketHistoryWithMetadata(properties.getRegionId(), typeId);
        List<EveMarketHistoryResp> history = response.body()
            .stream()
            .map(EveMarketService::toHistory)
            .sorted(Comparator.comparing(EveMarketHistoryResp::date))
            .toList();
        if (history.size() > HISTORY_LIMIT) {
            history = List.copyOf(history.subList(history.size() - HISTORY_LIMIT, history.size()));
        }
        LocalDateTime synchronizedAt = LocalDateTime.now();
        LocalDateTime nextRequestAt = synchronizedAt.plusDays(1);
        if (response.expiresAt() != null) {
            LocalDateTime localExpiresAt = response.expiresAt()
                .atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(java.time.ZoneId.systemDefault())
                .toLocalDateTime();
            if (localExpiresAt.isAfter(nextRequestAt)) {
                nextRequestAt = localExpiresAt;
            }
        }
        snapshotMapper.upsertHistory(typeId, writeJson(history), historySource(), synchronizedAt, nextRequestAt);
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

    /** 保存订单失败状态时不得让本地数据库异常覆盖原始上游异常。 */
    private void recordMarketFailureSafely(int typeId, String message) {
        try {
            snapshotMapper.recordMarketFailure(typeId, message, LocalDateTime.now());
        } catch (RuntimeException e) {
            log.debug("保存市场订单失败状态失败，typeId={}, errorType={}", typeId, e.getClass().getSimpleName());
        }
    }

    /** 保存历史失败状态时不得让本地数据库异常覆盖原始上游异常。 */
    private void recordHistoryFailureSafely(int typeId, String message) {
        try {
            recordFailure(typeId, message);
        } catch (RuntimeException e) {
            log.debug("保存市场历史失败状态失败，typeId={}, errorType={}", typeId, e.getClass().getSimpleName());
        }
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
        return snapshot == null || snapshot.getQuoteNextRequestAt() == null || !snapshot.getQuoteNextRequestAt()
            .isAfter(LocalDateTime.now());
    }

    private boolean needsDetailRefresh(EveMarketSnapshotDO snapshot) {
        return snapshot == null || snapshot.getDetailSynchronizedAt() == null || snapshot.getDetailSynchronizedAt()
            .isBefore(LocalDateTime.now().minus(properties.getDetailRefreshInterval()));
    }

    /** 将缓存报价连同服务端计算的有效截止时间返回给页面。 */
    private EveMarketQuoteResp toQuote(EveMarketSnapshotDO snapshot) {
        return toQuote(snapshot, false);
    }

    /** 失败或延期的当前请求必须强制显示旧数据状态。 */
    private EveMarketQuoteResp toQuote(EveMarketSnapshotDO snapshot, boolean forcedStale) {
        if (snapshot == null) {
            return null;
        }
        LocalDateTime synchronizedAt = snapshot.getQuoteSynchronizedAt();
        LocalDateTime freshnessExpiresAt = snapshot.getQuoteNextRequestAt();
        return new EveMarketQuoteResp(snapshot.getHighestBuyPrice(), snapshot.getLowestSellPrice(), snapshot
            .getBuyVolume(), snapshot.getSellVolume(), snapshot.getSourceUpdatedAt(), snapshot
                .getQuoteSynchronizedAt(), freshnessExpiresAt, forcedStale || needsQuoteRefresh(snapshot));
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

    /** 将客户端以 UTC 表示的官方缓存截止时间转换为应用本地时间。 */
    private static LocalDateTime localExpiry(LocalDateTime utcExpiry) {
        return utcExpiry == null
            ? null
            : utcExpiry.atOffset(ZoneOffset.UTC).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime();
    }

    /** 单个物品的市场统计值。 */
    private record QuoteData(BigDecimal highestBuyPrice, BigDecimal lowestSellPrice, Long buyVolume, Long sellVolume) {
    }

    /** 单物品完整订单及所有分页中最早的官方缓存截止时间。 */
    private record TypeMarketOrders(List<SerenityMarketOrderResponse> orders,
                                    LocalDateTime nextRequestAt,
                                    LocalDateTime sourceUpdatedAt) {
    }

    /** 完整区域订单与最晚官方缓存期限，用于跨实例调度门禁。 */
    private record MarketOrders(Map<Integer, List<SerenityMarketOrderResponse>> byType, LocalDateTime expiresAt) {
    }
}
