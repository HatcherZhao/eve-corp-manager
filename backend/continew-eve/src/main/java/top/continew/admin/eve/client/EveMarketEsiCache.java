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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

/** 共享缓存公开市场响应，未过期不发请求，过期后以 ETag 条件校验减少重复传输。 */
@Component
@RequiredArgsConstructor
public class EveMarketEsiCache {

    private final RedissonClient redis;
    private final ObjectMapper objectMapper;

    /** 每个区域、物品或页码独立缓存；部分抓取或落库失败后也能复用已成功的页面。 */
    public ResponseEntity<String> get(String endpoint, Function<String, ResponseEntity<String>> fetch) {
        String key = "eve:market:esi-response:" + DigestUtils.md5DigestAsHex(endpoint.getBytes(StandardCharsets.UTF_8));
        RBucket<String> bucket = redis.getBucket(key);
        CachedResponse cached = read(bucket.get());
        if (cached != null && cached.expiresAt() > System.currentTimeMillis()) {
            return response(cached);
        }
        ResponseEntity<String> result = fetch.apply(cached == null ? null : cached.etag());
        boolean notModified = result.getStatusCode() == HttpStatus.NOT_MODIFIED;
        if (notModified && cached == null) {
            throw new IllegalStateException("官方市场返回 304 但缺少对应缓存");
        }
        String body = notModified ? cached.body() : result.getBody();
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("官方市场返回空响应");
        }
        long expiresAt = result.getHeaders().getExpires();
        long now = System.currentTimeMillis();
        if (expiresAt < 0) {
            expiresAt = now + Duration.ofMinutes(1).toMillis();
        }
        String etag = result.getHeaders().getETag();
        String pages = result.getHeaders().getFirst("X-Pages");
        CachedResponse refreshed = new CachedResponse(body, etag == null && notModified
            ? cached.etag()
            : etag, expiresAt, pages == null && notModified ? cached.pages() : pages);
        try {
            // 过期后仅额外保留一小时供 ETag 重验证，避免无限积累大型订单响应。
            bucket.set(objectMapper.writeValueAsString(refreshed), Duration.ofMillis(Math.max(0, expiresAt - now))
                .plusHours(1));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法保存官方市场响应缓存", e);
        }
        return response(refreshed);
    }

    /** 损坏缓存视为未命中，不能将无效内容继续发布为行情。 */
    private CachedResponse read(String value) {
        if (value == null) {
            return null;
        }
        try {
            CachedResponse cached = objectMapper.readValue(value, CachedResponse.class);
            return cached != null && cached.body() != null && !cached.body().isBlank() ? cached : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /** 恢复正文和官方缓存头，304 响应继续使用同一实体而不是发布空订单。 */
    private static ResponseEntity<String> response(CachedResponse cached) {
        HttpHeaders headers = new HttpHeaders();
        headers.setExpires(Instant.ofEpochMilli(cached.expiresAt()));
        if (cached.etag() != null) {
            headers.setETag(cached.etag());
        }
        if (cached.pages() != null) {
            headers.set("X-Pages", cached.pages());
        }
        return new ResponseEntity<>(cached.body(), headers, HttpStatus.OK);
    }

    /** Redis 中的可重验证市场实体；键仅来自固定的公开市场 URL。 */
    private record CachedResponse(String body, String etag, long expiresAt, String pages) {
    }
}
