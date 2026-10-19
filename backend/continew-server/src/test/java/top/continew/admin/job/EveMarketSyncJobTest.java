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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import top.continew.admin.eve.config.EveMarketProperties;
import top.continew.admin.eve.service.EveMarketService;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 市场后台锁与共享到期门禁的跨实例回归测试。 */
class EveMarketSyncJobTest {

    private final Map<String, AtomicReference<Long>> values = new HashMap<>();
    private RedissonClient redis;
    private EveMarketService service;
    private EveMarketSyncJob first;
    private EveMarketSyncJob second;

    /** 两个任务实例共享 Redis，但不共享进程内调度状态。 */
    @BeforeEach
    void setUp() {
        redis = mock(RedissonClient.class);
        service = mock(EveMarketService.class);
        RLock lock = mock(RLock.class);
        when(redis.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        Map<String, RBucket<Long>> buckets = new HashMap<>();
        when(redis.<Long>getBucket(anyString())).thenAnswer(invocation -> buckets.computeIfAbsent(invocation
            .getArgument(0), key -> {
                AtomicReference<Long> value = values.computeIfAbsent(key, ignored -> new AtomicReference<>());
                RBucket<Long> bucket = mock(RBucket.class);
                when(bucket.get()).thenAnswer(ignored -> value.get());
                doAnswer(call -> {
                    value.set(call.getArgument(0));
                    return null;
                }).when(bucket).set(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
                return bucket;
            }));
        first = new EveMarketSyncJob(service, redis, new EveMarketProperties());
        second = new EveMarketSyncJob(service, redis, new EveMarketProperties());
    }

    /** 错峰实例即使依次拿到锁，也不能重复抓取同一轮历史。 */
    @Test
    void shouldWarmHistoryOnlyOnceAcrossInstances() {
        first.warmPriceHistories();
        second.warmPriceHistories();

        verify(service, times(1)).warmPriceHistories();
    }

    /** 官方 Expires 比配置间隔更晚时，以官方截止时间为准。 */
    @Test
    void shouldRespectOfficialOrderExpiryAcrossInstances() {
        LocalDateTime expires = LocalDateTime.now(ZoneOffset.UTC).plusHours(2);
        when(service.synchronizeMarketOrders()).thenReturn(expires);

        first.synchronizeMarketOrders();
        second.synchronizeMarketOrders();

        verify(service, times(1)).synchronizeMarketOrders();
        assertThat(values.get("eve:market:order-sync:next-at").get()).isEqualTo(expires.toInstant(ZoneOffset.UTC)
            .toEpochMilli());
    }

    /** 抓取失败也要共享退避，不能让其他实例立即重复抓取。 */
    @Test
    void shouldShareFailureBackoff() {
        when(service.synchronizeMarketOrders()).thenThrow(new IllegalStateException("failed"));

        first.synchronizeMarketOrders();
        second.synchronizeMarketOrders();

        verify(service, times(1)).synchronizeMarketOrders();
        assertThat(values.get("eve:market:order-sync:next-at").get()).isGreaterThan(System
            .currentTimeMillis() + 290_000);
    }
}
