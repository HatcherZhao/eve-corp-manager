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

package top.continew.admin.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.api.RBucket;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.continew.admin.eve.service.EveMarketService;
import top.continew.admin.eve.config.EveMarketProperties;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 以保守节流同步国服 ESI 吉他市场缓存。
 *
 * <p>订单簿同步按区域分页执行，具体请求由 ESI 客户端全局节流；此任务再以 Redis 锁保证多实例
 * 不会并发抓取同一市场。任何用户请求都只读取本地快照。</p>
 *
 * @author zhaoyuqing
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EveMarketSyncJob {

    private static final String ORDER_SYNC_LOCK = "eve:market:order-sync";
    private static final String HISTORY_SYNC_LOCK = "eve:market:history-sync";

    private final EveMarketService marketService;
    private final RedissonClient redissonClient;
    private final EveMarketProperties properties;

    /** 默认每三十分钟完整同步一次 The Forge 订单目录。 */
    @Scheduled(initialDelayString = "${eve.market.order-sync-initial-delay:PT90S}", fixedDelayString = "${eve.market.order-sync-interval:PT30M}")
    public void synchronizeMarketOrders() {
        runGuarded(ORDER_SYNC_LOCK, properties.getOrderSyncInterval(), marketService::synchronizeMarketOrders);
    }

    /** 锁内复查共享到期时间；锁防并发，时间门禁防错峰实例重复抓取。 */
    private void runGuarded(String key, Duration interval, Supplier<LocalDateTime> synchronize) {
        if (!properties.isEnabled()) {
            return;
        }
        RLock lock = redissonClient.getLock(key);
        if (!lock.tryLock()) {
            return;
        }
        try {
            RBucket<Long> nextRun = redissonClient.getBucket(key + ":next-at");
            Long nextAt = nextRun.get();
            if (nextAt != null && nextAt > System.currentTimeMillis()) {
                return;
            }
            long dueAt;
            try {
                LocalDateTime sourceExpiresAt = synchronize.get();
                dueAt = System.currentTimeMillis() + interval.toMillis();
                if (sourceExpiresAt != null) {
                    dueAt = Math.max(dueAt, sourceExpiresAt.toInstant(ZoneOffset.UTC).toEpochMilli());
                }
            } catch (RuntimeException e) {
                // 失败也写入共享重试时间，避免错峰实例立即再抓整轮订单。
                dueAt = System.currentTimeMillis() + Math.min(interval.toMillis(), Duration.ofMinutes(5).toMillis());
                log.warn("国服 ESI 市场后台同步失败，最近完整快照已保留，task={}, errorType={}", key, e.getClass().getSimpleName());
            }
            nextRun.set(dueAt, Math.max(1000, dueAt - System.currentTimeMillis()) + Duration.ofHours(1)
                .toMillis(), TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            log.warn("国服 ESI 市场共享调度异常，task={}, errorType={}", key, e.getClass().getSimpleName());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 低频补齐官方区域历史；每轮限量请求，不能因补温产生高频扫描。 */
    @Scheduled(initialDelayString = "${eve.market.history-warmup-initial-delay:PT5M}", fixedDelayString = "${eve.market.history-warmup-interval:PT5M}")
    public void warmPriceHistories() {
        runGuarded(HISTORY_SYNC_LOCK, properties.getHistoryWarmupInterval(), () -> {
            marketService.warmPriceHistories();
            return null;
        });
    }
}
