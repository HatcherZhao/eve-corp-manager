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

package top.continew.admin.eve.recovery;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import top.continew.admin.eve.security.OAuthSecurityUtils;

import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Optional;

/**
 * 使用 Redis 原子消费的 EVE 密码重置凭证存储。
 *
 * @author zhaoyuqing
 */
@Component
public class RedisPasswordRecoveryCredentialStore implements PasswordRecoveryCredentialStore {

    static final String KEY_PREFIX = "eve:serenity:password-recovery:";

    private final RedissonClient redissonClient;

    /** 创建密码重置凭证存储。 */
    public RedisPasswordRecoveryCredentialStore(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /** {@inheritDoc} */
    @Override
    public String issue(String browserBindingDigest, Long tenantId, Long userId, Duration ttl) {
        if (isBlank(browserBindingDigest) || tenantId == null || userId == null || ttl == null || ttl
            .isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("密码重置凭证签发参数无效");
        }
        String credential = OAuthSecurityUtils.generateState();
        bucket(credential)
            .set(new StoredCredential(browserBindingDigest, new PasswordRecoveryIdentity(tenantId, userId)), ttl);
        return credential;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<PasswordRecoveryIdentity> consume(String credential, String browserBindingDigest) {
        if (isBlank(credential) || isBlank(browserBindingDigest)) {
            return Optional.empty();
        }
        StoredCredential stored = bucket(credential).getAndDelete();
        if (stored == null || !MessageDigest.isEqual(stored.browserBindingDigest
            .getBytes(StandardCharsets.US_ASCII), browserBindingDigest.getBytes(StandardCharsets.US_ASCII))) {
            return Optional.empty();
        }
        return Optional.of(stored.identity);
    }

    /** 按凭证摘要定位 Redis Bucket，避免将原凭证作为 key 保存。 */
    private RBucket<StoredCredential> bucket(String credential) {
        return redissonClient.getBucket(KEY_PREFIX + OAuthSecurityUtils.sha256Base64Url(credential));
    }

    /** 判断文本是否为空。 */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Redis 内部保存对象，不允许输出重置凭证或浏览器绑定。 */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    private static class StoredCredential implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String browserBindingDigest;
        private PasswordRecoveryIdentity identity;

        /** 防止日志输出身份和浏览器绑定摘要。 */
        @Override
        public String toString() {
            return "StoredCredential[redacted]";
        }
    }
}
