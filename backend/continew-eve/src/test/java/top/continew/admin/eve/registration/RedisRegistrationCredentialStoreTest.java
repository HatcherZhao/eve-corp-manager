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

package top.continew.admin.eve.registration;

import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Redis 一次性注册凭证存储测试。
 *
 * @author zhaoyuqing
 */
class RedisRegistrationCredentialStoreTest {

    /** 验证凭证只保存摘要索引、仅消费一次且绑定原浏览器。 */
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldConsumeCredentialOnlyOnceForBoundBrowser() {
        RedissonClient client = mock(RedissonClient.class);
        RBucket bucket = mock(RBucket.class);
        AtomicReference<Object> stored = new AtomicReference<>();
        when(client.getBucket(anyString())).thenReturn(bucket);
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return null;
        }).when(bucket).set(any(), any(Duration.class));
        when(bucket.get()).thenAnswer(invocation -> stored.get());
        when(bucket.remainTimeToLive()).thenReturn(Duration.ofMinutes(10).toMillis());
        when(bucket.compareAndSet(any(), any())).thenAnswer(invocation -> stored.compareAndSet(invocation
            .getArgument(0), invocation.getArgument(1)));
        RedisRegistrationCredentialStore store = new RedisRegistrationCredentialStore(client, "test-field-key");
        VerifiedRegistrationIdentity identity = identity();

        String credential = store.issue("browser-a", identity, Duration.ofMinutes(10));

        assertThat(new String(serialize(stored.get()), StandardCharsets.ISO_8859_1))
            .doesNotContain("access-token", "refresh-token");
        assertThat(store.find(credential, "browser-a")).get()
            .extracting(RegistrationCredentialSnapshot::identity)
            .isEqualTo(identity);
        assertThat(store.find(credential, "browser-b")).isEmpty();
        RegistrationActivation activation = new RegistrationActivation(10L, 20L, true);
        assertThat(store.markActivated(credential, "browser-a", activation)).isTrue();
        assertThat(store.find(credential, "browser-a")).get()
            .extracting(RegistrationCredentialSnapshot::activation)
            .isEqualTo(activation);
        assertThat(store.consume(credential, "browser-a")).get()
            .extracting(RegistrationCredentialSnapshot::identity, RegistrationCredentialSnapshot::activation)
            .containsExactly(identity, activation);
        assertThat(store.consume(credential, "browser-a")).isEmpty();
        String crossBrowserCredential = store.issue("browser-a", identity, Duration.ofMinutes(10));
        assertThat(store.consume(crossBrowserCredential, "browser-b")).isEmpty();
        String tamperedCredential = store.issue("browser-a", identity, Duration.ofMinutes(10));
        byte[] encryptedIdentity = (byte[])ReflectionTestUtils.getField(stored.get(), "encryptedIdentity");
        encryptedIdentity[0] ^= 1;
        assertThat(store.find(tamperedCredential, "browser-a")).isEmpty();
        assertThat(credential).hasSize(43);
        assertThat(identity.toString()).doesNotContain("access-token", "refresh-token", "owner-hash");
    }

    /** 原始有效期跨界后，已开始处理的凭证仍可在有限恢复窗口内重试。 */
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldKeepStartedCredentialAcrossOriginalTtlBoundary() throws InterruptedException {
        RedissonClient client = mock(RedissonClient.class);
        RBucket bucket = mock(RBucket.class);
        AtomicReference<Object> stored = new AtomicReference<>();
        when(client.getBucket(anyString())).thenReturn(bucket);
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return null;
        }).when(bucket).set(any(), any(Duration.class));
        when(bucket.get()).thenAnswer(invocation -> stored.get());
        RedisRegistrationCredentialStore store = new RedisRegistrationCredentialStore(client, "test-field-key");

        String credential = store.issue("browser-a", identity(), Duration.ofSeconds(1));
        RegistrationCredentialSnapshot processing = store.find(credential, "browser-a").orElseThrow();
        Thread.sleep(1100);

        assertThat(processing.processingExpiresAt()).isAfter(processing.credentialExpiresAt());
        assertThat(store.find(credential, "browser-a")).contains(processing);
    }

    /** 已通过解密但无法反序列化的内容必须保持关闭并返回空结果。 */
    @Test
    void shouldFailClosedWhenDecryptedPayloadCannotBeDeserialized() {
        Optional<?> result = ReflectionTestUtils
            .invokeMethod(RedisRegistrationCredentialStore.class, "deserializeSafely", new byte[] {1, 2, 3});

        assertThat(result).isEmpty();
    }

    /** 将 Redis 实际保存对象序列化，模拟检查底层原始值。 */
    private static byte[] serialize(Object value) {
        try (java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            java.io.ObjectOutputStream objectOutput = new java.io.ObjectOutputStream(output)) {
            objectOutput.writeObject(value);
            return output.toByteArray();
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    /** 创建测试身份。 */
    private static VerifiedRegistrationIdentity identity() {
        return VerifiedRegistrationIdentity.builder()
            .server("serenity")
            .characterId(100L)
            .corporationId(200L)
            .ownerHash("owner-hash")
            .roles(List.of())
            .rolesAtHq(List.of())
            .rolesAtBase(List.of())
            .rolesAtOther(List.of())
            .scopes(List.of("scope"))
            .accessToken("access-token")
            .refreshToken("refresh-token")
            .tokenType("Bearer")
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
    }
}
