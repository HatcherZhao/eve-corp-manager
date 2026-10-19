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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import top.continew.admin.eve.client.SerenityEsiClientException;
import top.continew.admin.eve.config.EveAutoSyncProperties;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 官方限流反馈必须形成跨实例共享的冷却，且不能被后续响应缩短。 */
class EveUpstreamRequestPacerTest {

    private final Map<String, AtomicReference<Long>> values = new HashMap<>();
    private RedissonClient redis;
    private EveAutoSyncProperties properties;
    private EveUpstreamRequestPacer pacer;

    /** 使用共享存储模拟多个 JVM 访问同一 Redis，关闭普通请求间隔以避免测试休眠。 */
    @BeforeEach
    void setUp() throws InterruptedException {
        redis = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redis.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(5, TimeUnit.SECONDS)).thenReturn(true);
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
        properties = new EveAutoSyncProperties();
        properties.setUpstreamRequestInterval(Duration.ZERO);
        pacer = new EveUpstreamRequestPacer(redis, properties);
    }

    /** 一个实例收到 429 后，另一个实例必须立即停止发放请求许可。 */
    @Test
    void shouldShareRetryAfterCooldownAcrossInstances() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "120");
        pacer.observeResponse(HttpStatusCode.valueOf(429), headers);

        EveUpstreamRequestPacer other = new EveUpstreamRequestPacer(redis, properties);
        assertThatThrownBy(other::awaitPermit).isInstanceOf(SerenityEsiClientException.class);
        assertThat(cooldown()).isGreaterThan(System.currentTimeMillis() + 110_000);
    }

    /** 错误预算接近耗尽时，即使当前 HTTP 成功也要等待错误窗口重置。 */
    @Test
    void shouldPauseWhenSuccessfulResponseReportsExhaustedBudget() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Esi-Error-Limit-Remain", "0");
        headers.set("X-Esi-Error-Limit-Reset", "90");
        pacer.observeResponse(HttpStatusCode.valueOf(200), headers);

        assertThatThrownBy(pacer::awaitPermit).isInstanceOf(SerenityEsiClientException.class);
        assertThat(cooldown()).isGreaterThan(System.currentTimeMillis() + 80_000);
    }

    /** 支持 HTTP 日期形式的 Retry-After，较短的后续窗口不能覆盖长窗口。 */
    @Test
    void shouldHonorHttpDateAndNeverShortenExistingCooldown() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, ZonedDateTime.now(java.time.ZoneOffset.UTC)
            .plusMinutes(10)
            .format(DateTimeFormatter.RFC_1123_DATE_TIME));
        pacer.observeResponse(HttpStatusCode.valueOf(420), headers);
        long previous = cooldown();
        pacer.observeResponse(HttpStatusCode.valueOf(429), new HttpHeaders());
        pacer.observeResponse(HttpStatusCode.valueOf(200), new HttpHeaders());

        assertThat(cooldown()).isEqualTo(previous).isGreaterThan(System.currentTimeMillis() + 590_000);
    }

    /** 限流响应缺失或损坏提示时仍采用默认退避，普通成功则不建立冷却。 */
    @Test
    void shouldUseConservativeFallbackForInvalidRetryAfter() {
        assertThatCode(pacer::awaitPermit).doesNotThrowAnyException();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "not-a-date");
        pacer.observeResponse(HttpStatusCode.valueOf(429), headers);

        assertThat(cooldown()).isGreaterThan(System.currentTimeMillis() + 50_000);
        assertThatThrownBy(pacer::awaitPermit).isInstanceOf(SerenityEsiClientException.class);
    }

    /** 读取测试共享的冷却截止时间。 */
    private Long cooldown() {
        return values.get("eve:serenity:upstream-request-pacer:not-before").get();
    }
}
