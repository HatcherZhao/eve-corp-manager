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

package top.continew.admin.eve.auth;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import top.continew.admin.eve.security.OAuthSecurityUtils;

import java.time.Duration;
import java.util.Optional;

/**
 * 基于 Redis 原子 get-and-delete 的 OAuth 事务存储。
 *
 * @author zhaoyuqing
 */
@Component
@RequiredArgsConstructor
public class RedisOAuthTransactionStore implements OAuthTransactionStore {

    static final String KEY_PREFIX = "eve:serenity:oauth:txn:";

    private final RedissonClient redissonClient;

    /** {@inheritDoc} */
    @Override
    public void save(String state, OAuthTransaction transaction, Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("OAuth 事务有效期必须大于零");
        }
        bucket(state).set(transaction, ttl);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<OAuthTransaction> consume(String state) {
        return Optional.ofNullable(bucket(state).getAndDelete());
    }

    /** 根据 state 摘要获取 Redis Bucket。 */
    private RBucket<OAuthTransaction> bucket(String state) {
        String key = KEY_PREFIX + OAuthSecurityUtils.sha256Base64Url(state);
        return redissonClient.getBucket(key);
    }
}
