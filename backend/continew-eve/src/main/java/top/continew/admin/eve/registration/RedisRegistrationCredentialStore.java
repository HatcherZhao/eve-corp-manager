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

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.continew.admin.eve.security.OAuthSecurityUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 使用 Redis 原子消费的一次性注册凭证存储。
 *
 * @author zhaoyuqing
 */
@Component
@Slf4j
public class RedisRegistrationCredentialStore implements RegistrationCredentialStore {

    static final String KEY_PREFIX = "eve:serenity:registration:";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int NONCE_LENGTH = 12;
    private static final int AUTH_TAG_LENGTH_BITS = 128;
    private static final Duration PROCESSING_RECOVERY_TTL = Duration.ofMinutes(5);

    private final RedissonClient redissonClient;
    private final SecretKeySpec encryptionKey;
    private final SecureRandom secureRandom = new SecureRandom();

    /** 创建 Redis 注册凭证存储。 */
    public RedisRegistrationCredentialStore(RedissonClient redissonClient,
                                            @Value("${continew-starter.encrypt.field.password}") String encryptionKey) {
        this.redissonClient = redissonClient;
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalArgumentException("注册凭证加密密钥不能为空");
        }
        this.encryptionKey = new SecretKeySpec(deriveEncryptionKey(encryptionKey), "AES");
    }

    /** {@inheritDoc} */
    @Override
    public String issue(String browserBindingDigest, VerifiedRegistrationIdentity identity, Duration ttl) {
        if (browserBindingDigest == null || browserBindingDigest.isBlank() || identity == null || ttl == null || ttl
            .isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("注册凭证签发参数无效");
        }
        String credential = OAuthSecurityUtils.generateState();
        Instant credentialExpiresAt = Instant.now().plus(ttl);
        RegistrationCredentialSnapshot snapshot = new RegistrationCredentialSnapshot(identity, null, credentialExpiresAt, null);
        bucket(credential).set(encrypt(browserBindingDigest, snapshot), ttl.plus(PROCESSING_RECOVERY_TTL));
        return credential;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<RegistrationCredentialSnapshot> find(String credential, String browserBindingDigest) {
        RBucket<StoredCredential> credentialBucket = bucket(credential);
        StoredCredential stored = credentialBucket.get();
        Optional<RegistrationCredentialSnapshot> found = decryptAndValidateBrowser(stored, browserBindingDigest);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RegistrationCredentialSnapshot snapshot = found.get();
        Instant now = Instant.now();
        if (snapshot.activation() != null || snapshot.processingExpiresAt() != null && snapshot.processingExpiresAt()
            .isAfter(now)) {
            return Optional.of(snapshot);
        }
        if (snapshot.credentialExpiresAt() == null || !snapshot.credentialExpiresAt().isAfter(now)) {
            return Optional.empty();
        }
        Instant processingExpiresAt = now.plus(PROCESSING_RECOVERY_TTL);
        RegistrationCredentialSnapshot processing = new RegistrationCredentialSnapshot(snapshot
            .identity(), null, snapshot.credentialExpiresAt(), processingExpiresAt);
        credentialBucket.set(encrypt(browserBindingDigest, processing), PROCESSING_RECOVERY_TTL);
        return Optional.of(processing);
    }

    /** {@inheritDoc} */
    @Override
    public boolean markActivated(String credential, String browserBindingDigest, RegistrationActivation activation) {
        if (activation == null) {
            throw new IllegalArgumentException("注册激活结果不能为空");
        }
        RBucket<StoredCredential> credentialBucket = bucket(credential);
        StoredCredential stored = credentialBucket.get();
        Optional<RegistrationCredentialSnapshot> snapshot = decryptAndValidateBrowser(stored, browserBindingDigest);
        long remainingTtl = credentialBucket.remainTimeToLive();
        if (snapshot.isEmpty() || remainingTtl <= 0) {
            return false;
        }
        StoredCredential activated = encrypt(browserBindingDigest, new RegistrationCredentialSnapshot(snapshot.get()
            .identity(), activation, snapshot.get().credentialExpiresAt(), snapshot.get().processingExpiresAt()));
        credentialBucket.set(activated, Duration.ofMillis(remainingTtl));
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<RegistrationCredentialSnapshot> consume(String credential, String browserBindingDigest) {
        RBucket<StoredCredential> credentialBucket = bucket(credential);
        StoredCredential stored = credentialBucket.get();
        Optional<RegistrationCredentialSnapshot> snapshot = decryptAndValidateBrowser(stored, browserBindingDigest);
        if (snapshot.isEmpty() || !credentialBucket.compareAndSet(stored, null)) {
            return Optional.empty();
        }
        return snapshot;
    }

    /** 解密凭证并校验与原浏览器绑定一致。 */
    private Optional<RegistrationCredentialSnapshot> decryptAndValidateBrowser(StoredCredential stored,
                                                                               String browserBindingDigest) {
        if (stored == null || stored.browserBindingDigest == null || stored.nonce == null || stored.nonce.length != NONCE_LENGTH || stored.encryptedIdentity == null || browserBindingDigest == null || !MessageDigest
            .isEqual(stored.browserBindingDigest.getBytes(StandardCharsets.US_ASCII), browserBindingDigest
                .getBytes(StandardCharsets.US_ASCII))) {
            return Optional.empty();
        }
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(AUTH_TAG_LENGTH_BITS, stored.nonce));
            cipher.updateAAD(stored.browserBindingDigest.getBytes(StandardCharsets.US_ASCII));
            byte[] serialized = cipher.doFinal(stored.encryptedIdentity);
            return deserializeSafely(serialized);
        } catch (GeneralSecurityException e) {
            log.warn("EVE 注册凭证校验失败，failureType=DECRYPTION_OR_AUTHENTICATION_FAILED");
            return Optional.empty();
        }
    }

    /** 反序列化失败时保持关闭并仅记录固定失败分类。 */
    private static Optional<RegistrationCredentialSnapshot> deserializeSafely(byte[] serialized) {
        try {
            return Optional.of(deserialize(serialized));
        } catch (IOException | ClassNotFoundException e) {
            log.warn("EVE 注册凭证校验失败，failureType=DESERIALIZATION_FAILED");
            return Optional.empty();
        }
    }

    /** 使用 AES-GCM 对注册中间态执行认证加密。 */
    private StoredCredential encrypt(String browserBindingDigest, RegistrationCredentialSnapshot snapshot) {
        byte[] nonce = new byte[NONCE_LENGTH];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(AUTH_TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(browserBindingDigest.getBytes(StandardCharsets.US_ASCII));
            return new StoredCredential(browserBindingDigest, nonce, cipher.doFinal(serialize(snapshot)));
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("注册凭证加密失败", e);
        }
    }

    /** 将注册身份序列化为仅供认证加密使用的字节流。 */
    private static byte[] serialize(RegistrationCredentialSnapshot snapshot) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
            ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(snapshot);
            return output.toByteArray();
        }
    }

    /** 从已认证的明文字节流恢复注册身份。 */
    private static RegistrationCredentialSnapshot deserialize(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object value = input.readObject();
            if (!(value instanceof RegistrationCredentialSnapshot snapshot)) {
                throw new IOException("注册凭证内容类型无效");
            }
            return snapshot;
        }
    }

    /** 从项目字段加密环境密钥派生固定长度的 AES-256 密钥。 */
    private static byte[] deriveEncryptionKey(String encryptionKey) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("当前 JRE 不支持 SHA-256", e);
        }
    }

    /** 根据凭证摘要定位 Redis Bucket。 */
    private RBucket<StoredCredential> bucket(String credential) {
        return redissonClient.getBucket(KEY_PREFIX + OAuthSecurityUtils.sha256Base64Url(credential));
    }

    /** Redis 内部保存对象，不允许输出敏感身份。 */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    private static class StoredCredential implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String browserBindingDigest;
        private byte[] nonce;
        private byte[] encryptedIdentity;

        /** 防止日志输出浏览器摘要和令牌。 */
        @Override
        public String toString() {
            return "StoredCredential[redacted]";
        }
    }
}
