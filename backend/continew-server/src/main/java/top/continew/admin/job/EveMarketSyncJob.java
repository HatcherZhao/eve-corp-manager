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
 * 保留供维护操作使用的国服市场同步入口。
 *
 * <p>日常行情由实际业务按需查询；维护入口不再自动扫描市场或补齐全部物品历史。</p>
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

    /** 持续处理实际业务提交的物品，空队列不会向官方发出请求。 */
    @Scheduled(initialDelayString = "${eve.market.demand-sync-initial-delay:PT5S}", fixedDelayString = "${eve.market.demand-sync-interval:PT2S}")
    public void consumeDemandQueue() {
        try {
            marketService.consumeMarketRefreshQueue(1);
        } catch (RuntimeException e) {
            log.warn("按需市场行情队列处理失败，errorType={}", e.getClass().getSimpleName());
        }
    }

    /** 按需调用时同步完整区域订单。 */
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
            // 先预留共享周期；写入失败则不抓取，完成写入瞬断时也不在下一分钟重复整轮工作。
            // 更长的官方 Expires 仍由逐页响应缓存独立保护。
            nextRun.set(System.currentTimeMillis() + interval.toMillis(), interval.plusHours(1)
                .toMillis(), TimeUnit.MILLISECONDS);
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

    /** 按需调用时补齐少量官方区域历史。 */
    public void warmPriceHistories() {
        runGuarded(HISTORY_SYNC_LOCK, properties.getHistoryWarmupInterval(), () -> {
            marketService.warmPriceHistories();
            return null;
        });
    }
}
