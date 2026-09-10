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

import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import top.continew.admin.eve.client.OAuthFailureCode;
import top.continew.admin.eve.client.SerenityEsiClientException;
import top.continew.admin.eve.config.EveAutoSyncProperties;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 为全部本系统实例串行国服 ESI 请求，避免分页快照在单个任务内形成突发流量。
 *
 * <p>该限制器位于 ESI 客户端边界，自动任务、手动刷新和页面名称解析都会遵守同一请求间隔。
 * Redis 锁保障多实例共用一条节流时间线；无法取得节流锁时按可恢复上游失败交由任务退避。</p>
 *
 * @author zhaoyuqing
 */
@Component
@RequiredArgsConstructor
public class EveUpstreamRequestPacer {

    private static final String LOCK_NAME = "eve:serenity:upstream-request-pacer";
    private static final String LAST_REQUEST_KEY = "eve:serenity:upstream-request-pacer:last-at";

    private final RedissonClient redissonClient;
    private final EveAutoSyncProperties properties;

    /** 等待全局请求间隔后发放一个国服 ESI 请求许可。 */
    public void awaitPermit() {
        Duration interval = properties.getUpstreamRequestInterval();
        if (interval == null || interval.isZero() || interval.isNegative()) {
            return;
        }
        RLock lock = redissonClient.getLock(LOCK_NAME);
        boolean locked = false;
        try {
            locked = lock.tryLock(5, TimeUnit.SECONDS);
            if (!locked) {
                throw new SerenityEsiClientException(OAuthFailureCode.TRANSIENT);
            }
            RBucket<Long> lastRequestAt = redissonClient.getBucket(LAST_REQUEST_KEY);
            Long previous = lastRequestAt.get();
            long now = System.currentTimeMillis();
            long waitMillis = previous == null ? 0 : Math.max(0, interval.toMillis() - (now - previous));
            if (waitMillis > 0) {
                Thread.sleep(waitMillis);
            }
            lastRequestAt.set(System.currentTimeMillis(), Math.max(interval
                .toMillis() * 2, 1000), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SerenityEsiClientException(OAuthFailureCode.TRANSIENT);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
