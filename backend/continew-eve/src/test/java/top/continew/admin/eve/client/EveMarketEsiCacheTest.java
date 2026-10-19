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

package top.continew.admin.eve.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 市场原始响应缓存必须遵守 Expires，并正确复用 304 的正文和分页元数据。 */
class EveMarketEsiCacheTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, AtomicReference<String>> values = new HashMap<>();
    private RedissonClient redis;
    private EveMarketEsiCache cache;

    /** 多个缓存实例通过 Redis 中的同一响应实体复用官方数据。 */
    @BeforeEach
    void setUp() {
        redis = mock(RedissonClient.class);
        Map<String, RBucket<String>> buckets = new HashMap<>();
        when(redis.<String>getBucket(anyString())).thenAnswer(invocation -> buckets.computeIfAbsent(invocation
            .getArgument(0), key -> {
                AtomicReference<String> value = values.computeIfAbsent(key, ignored -> new AtomicReference<>());
                RBucket<String> bucket = mock(RBucket.class);
                when(bucket.get()).thenAnswer(ignored -> value.get());
                doAnswer(call -> {
                    value.set(call.getArgument(0));
                    return null;
                }).when(bucket).set(anyString(), any(Duration.class));
                return bucket;
            }));
        cache = new EveMarketEsiCache(redis, objectMapper);
    }

    /** 官方缓存未过期时另一个实例也不能再发请求，包括上一轮部分抓取失败后的重入。 */
    @Test
    void shouldReuseUnexpiredResponseAcrossInstances() {
        AtomicInteger requests = new AtomicInteger();
        var fetch = (java.util.function.Function<String, ResponseEntity<String>>)etag -> {
            requests.incrementAndGet();
            return success("[]", Instant.now().plusSeconds(600));
        };
        cache.get("orders/region-1/page-1", fetch);
        new EveMarketEsiCache(redis, objectMapper).get("orders/region-1/page-1", fetch);
        cache.get("orders/region-1/page-2", fetch);

        assertThat(requests.get()).isEqualTo(2);
    }

    /** 到期后传递 ETag，304 不能丢失已缓存正文及 X-Pages。 */
    @Test
    void shouldRevalidateExpiredResponseAndPreserveBodyOn304() {
        cache.get("orders/region-1/page-1", etag -> success("[{\"order_id\":1}]", Instant.now().plusSeconds(600)));
        expireAll();
        ResponseEntity<String> result = cache.get("orders/region-1/page-1", etag -> {
            assertThat(etag).isEqualTo("\"orders-v1\"");
            HttpHeaders headers = new HttpHeaders();
            headers.setExpires(Instant.now().plusSeconds(600));
            return new ResponseEntity<>(null, headers, HttpStatus.NOT_MODIFIED);
        });

        assertThat(result.getBody()).isEqualTo("[{\"order_id\":1}]");
        assertThat(result.getHeaders().getFirst("X-Pages")).isEqualTo("132");
        assertThat(result.getHeaders().getETag()).isEqualTo("\"orders-v1\"");
    }

    /** 缓存损坏时必须重新抓取，不能发送没有实体可复用的条件请求。 */
    @Test
    void shouldRecoverFromCorruptCache() {
        cache.get("history/region-1/type-34", etag -> success("[]", Instant.now().plusSeconds(600)));
        values.values().forEach(value -> value.set("broken-json"));

        assertThat(cache.get("history/region-1/type-34", etag -> {
            assertThat(etag).isNull();
            return success("[]", Instant.now().plusSeconds(600));
        }).getBody()).isEqualTo("[]");
    }

    /** 模拟时间推进而不在测试中等待真实缓存期限。 */
    private void expireAll() {
        values.values().forEach(value -> {
            try {
                ObjectNode json = (ObjectNode)objectMapper.readTree(value.get());
                json.put("expiresAt", System.currentTimeMillis() - 1000);
                value.set(objectMapper.writeValueAsString(json));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /** 构造带标准缓存和分页头的官方成功响应。 */
    private static ResponseEntity<String> success(String body, Instant expiresAt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setExpires(expiresAt);
        headers.setETag("\"orders-v1\"");
        headers.set("X-Pages", "132");
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }
}
