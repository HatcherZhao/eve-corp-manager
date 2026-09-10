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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.continew.admin.eve.service.EveStaticReferenceRemoteSyncService;

/**
 * 每日从公开来源更新 EVE 静态资料。
 *
 * <p>多实例以 Redis 锁互斥；下载、解析或入库任一步失败都会保留上一版数据库快照。</p>
 *
 * @author zhaoyuqing
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "eve.reference.remote-sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EveStaticReferenceRemoteSyncJob {

    private static final String DISPATCH_LOCK = "eve:static-reference:remote-sync";

    private final RedissonClient redissonClient;
    private final EveStaticReferenceRemoteSyncService remoteSyncService;

    /** 每日按配置时间下载公开 evedata.xlsx；相同文件哈希不会重复写库。 */
    @Scheduled(cron = "${eve.reference.remote-sync.cron:0 15 4 * * *}", zone = "${eve.reference.remote-sync.zone:Asia/Shanghai}")
    public void synchronize() {
        RLock lock = redissonClient.getLock(DISPATCH_LOCK);
        if (!lock.tryLock()) {
            return;
        }
        try {
            var result = remoteSyncService.synchronize();
            log.info("EVE 公开静态资料同步完成，imported={}, types={}, locations={}, sourceUpdatedAt={}", result.imported(), result
                .typeCount(), result.locationCount(), result.sourceUpdatedAt());
        } catch (RuntimeException e) {
            log.warn("EVE 公开静态资料同步失败，已保留现有资料快照", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
