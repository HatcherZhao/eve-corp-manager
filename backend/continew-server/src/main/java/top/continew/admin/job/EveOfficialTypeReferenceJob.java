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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.continew.admin.eve.service.EveOfficialTypeReferenceService;

import java.time.LocalDateTime;

/** 以跨实例锁和有限请求预算持续构建晨曦官方物品目录。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EveOfficialTypeReferenceJob {

    private static final String LOCK_NAME = "eve:reference:official-types";

    private final EveOfficialTypeReferenceService service;
    private final RedissonClient redissonClient;

    @Value("${eve.reference.official-type-sync-enabled:true}")
    private boolean enabled;

    /** 每分钟使用有限预算推进官方目录，应用重启后从数据库阶段继续。 */
    @Scheduled(initialDelayString = "${eve.reference.official-type-sync-initial-delay:PT2M}", fixedDelayString = "${eve.reference.official-type-sync-interval:PT1M}")
    public void synchronize() {
        if (!enabled) {
            return;
        }
        RLock lock = redissonClient.getLock(LOCK_NAME);
        if (!lock.tryLock()) {
            return;
        }
        try {
            var current = service.status();
            if ("READY".equals(current.phase()) && current.completedAt() != null && current.completedAt()
                .isBefore(LocalDateTime.now().minusDays(7))) {
                service.restart();
            }
            // 四次请求约占共享三秒节流时间线的五分之一，给市场、资产等交互请求保留余量。
            service.advance(4);
        } catch (RuntimeException e) {
            log.warn("晨曦官方物品目录同步本轮失败，将从持久进度继续，errorType={}", e.getClass().getSimpleName());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
