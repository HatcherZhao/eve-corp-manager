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

import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import top.continew.admin.eve.security.OAuthSecurityUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis OAuth 事务存储测试。
 *
 * @author zhaoyuqing
 */
class RedisOAuthTransactionStoreTest {

    /** 验证 state 仅以摘要作 key 且事务通过原子操作只能消费一次。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldHashKeyAndConsumeOnce() {
        RedissonClient client = mock(RedissonClient.class);
        RBucket<OAuthTransaction> bucket = mock(RBucket.class);
        String state = "state-value-that-must-not-be-a-key";
        String expectedKey = RedisOAuthTransactionStore.KEY_PREFIX + OAuthSecurityUtils.sha256Base64Url(state);
        OAuthTransaction transaction = OAuthTransaction.builder()
            .browserBindingDigest("browser-digest")
            .purpose(OAuthTransactionPurpose.REGISTER)
            .redirectUri("/login/eve/result")
            .requestedScopes(Set.of("scope-a"))
            .verifier(OAuthSecurityUtils.generateCodeVerifier())
            .expiresAt(Instant.now().plusSeconds(600))
            .build();
        when(client.<OAuthTransaction>getBucket(expectedKey)).thenReturn(bucket);
        AtomicInteger calls = new AtomicInteger();
        when(bucket.getAndDelete()).thenAnswer(invocation -> calls.getAndIncrement() == 0 ? transaction : null);
        RedisOAuthTransactionStore store = new RedisOAuthTransactionStore(client);

        store.save(state, transaction, Duration.ofMinutes(10));
        assertThat(store.consume(state)).containsSame(transaction);
        assertThat(store.consume(state)).isEmpty();

        verify(bucket).set(transaction, Duration.ofMinutes(10));
        assertThat(expectedKey).doesNotContain(state);
    }
}
