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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.continew.admin.eve.client.OAuthFailureCode;
import top.continew.admin.eve.client.SerenityTokenClient;
import top.continew.admin.eve.client.SerenityTokenClientException;
import top.continew.admin.eve.client.SerenityTokenResponse;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveAuthAuditMapper;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.model.entity.EveAuthAuditDO;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.enums.EveAuthAuditEventType;
import top.continew.admin.eve.model.enums.EveAuthAuditResult;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.security.SerenityJwtDecoderFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 协调单条 EVE 授权的访问令牌检查与安全轮换。
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveAuthorizationLifecycleService {

    static final String AUTHORIZATION_LOCK_PREFIX = "eve:serenity:authorization-refresh:";
    private static final Pattern CHARACTER_ID_SUFFIX = Pattern.compile("(?:^|:)([1-9][0-9]*)$");

    private final EveAuthorizationMapper authorizationMapper;
    private final EveCharacterMapper characterMapper;
    private final EveAuthAuditMapper auditMapper;
    private final EveAuthorizationTokenService tokenService;
    private final SerenityTokenClient tokenClient;
    private final SerenityJwtDecoderFactory jwtDecoderFactory;
    private final RedissonClient redissonClient;
    private final SerenityProperties properties;

    /**
     * 返回可用访问令牌；临近过期时在授权级锁内原子轮换。
     *
     * @param authorization 当前授权
     * @return 可用访问令牌
     */
    public String ensureAccessToken(EveAuthorizationDO authorization) {
        requireActiveAuthorization(authorization);
        LocalDateTime refreshBefore = LocalDateTime.now(ZoneOffset.UTC)
            .plus(properties.getPermissionRefresh().getTokenRefreshSkew());
        if (authorization.getExpiresAt() != null && authorization.getExpiresAt().isAfter(refreshBefore)) {
            return authorization.getAccessToken();
        }
        RLock lock = redissonClient.getLock(AUTHORIZATION_LOCK_PREFIX + authorization.getId());
        boolean locked = false;
        boolean unlockDeferred = false;
        try {
            locked = lock.tryLock(properties.getPermissionRefresh().getLockWait().toMillis(), TimeUnit.MILLISECONDS);
            if (!locked) {
                throw new IllegalStateException("授权令牌正在刷新，请稍后重试");
            }
            unlockDeferred = deferUnlockUntilTransactionCompletion(lock);
            EveAuthorizationDO latest = authorizationMapper.selectOwnedById(authorization.getTenantId(), authorization
                .getUserId(), authorization.getId());
            requireActiveAuthorization(latest);
            refreshBefore = LocalDateTime.now(ZoneOffset.UTC)
                .plus(properties.getPermissionRefresh().getTokenRefreshSkew());
            if (latest.getExpiresAt() != null && latest.getExpiresAt().isAfter(refreshBefore)) {
                return latest.getAccessToken();
            }
            if (latest.getRefreshToken() == null || latest.getRefreshToken().isBlank()) {
                throw new SerenityTokenClientException(OAuthFailureCode.INVALID_GRANT);
            }
            SerenityTokenResponse response = tokenClient.refresh(latest.getRefreshToken());
            SerenityTokenResponse persisted = validateAndNormalize(latest, response);
            tokenService.saveTokenResponse(latest.getTenantId(), latest.getUserId(), latest.getId(), persisted);
            audit(latest, EveAuthAuditResult.SUCCESS, "TOKEN_ROTATED");
            return response.accessToken();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("授权令牌正在刷新，请稍后重试", e);
        } catch (SerenityTokenClientException e) {
            tokenService.recordFailure(authorization.getTenantId(), authorization.getUserId(), authorization.getId(), e
                .getFailureCode());
            audit(authorization, EveAuthAuditResult.FAILURE, e.getFailureCode().name());
            throw e;
        } catch (JwtException | TokenIdentityValidationException e) {
            tokenService.recordFailure(authorization.getTenantId(), authorization.getUserId(), authorization
                .getId(), OAuthFailureCode.PERMANENT);
            audit(authorization, EveAuthAuditResult.FAILURE, OAuthFailureCode.PERMANENT.name());
            throw new SerenityTokenClientException(OAuthFailureCode.PERMANENT);
        } finally {
            if (locked && !unlockDeferred && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 将授权锁保持到当前事务完成，避免未提交状态被并发覆盖。 */
    static boolean deferUnlockUntilTransactionCompletion(RLock lock) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        });
        return true;
    }

    /** 对刷新响应重新验签并严格绑定原服务器、角色、所有者与 Scope。 */
    private SerenityTokenResponse validateAndNormalize(EveAuthorizationDO authorization,
                                                       SerenityTokenResponse response) {
        EveCharacterDO character = characterMapper.selectById(authorization.getCharacterRefId());
        Jwt jwt = jwtDecoderFactory.create().decode(response.accessToken());
        List<String> jwtScopes = readScopes(jwt.getClaim(properties.getSso().getScopeClaim()));
        List<String> oldScopes = authorization.getScopes() == null ? List.of() : authorization.getScopes();
        List<String> responseScopes = response.scope() == null || response.scope().isBlank()
            ? oldScopes
            : parseScopes(response.scope());
        boolean ownedCharacter = isOwnedCharacter(authorization, character);
        boolean expectedServer = ownedCharacter && isExpectedServer(authorization, character);
        boolean expectedSubject = ownedCharacter && isExpectedSubject(character, jwt);
        boolean expectedOwner = ownedCharacter && sameOwner(character.getOwnerHash(), jwt.getClaimAsString("owner"));
        boolean consistentScopes = areScopesConsistent(oldScopes, responseScopes, jwtScopes, response.scope());
        if (!ownedCharacter || !expectedServer || !expectedSubject || !expectedOwner || !consistentScopes) {
            throw new TokenIdentityValidationException();
        }
        return new SerenityTokenResponse(response.accessToken(), response.refreshToken() == null || response
            .refreshToken()
            .isBlank() ? authorization.getRefreshToken() : response.refreshToken(), response.tokenType(), response
                .expiresIn(), String.join(" ", responseScopes));
    }

    /** 校验角色绑定严格属于当前授权的租户、用户和角色记录。 */
    private static boolean isOwnedCharacter(EveAuthorizationDO authorization, EveCharacterDO character) {
        return character != null && Objects.equals(authorization.getTenantId(), character.getTenantId()) && Objects
            .equals(authorization.getUserId(), character.getUserId()) && Objects.equals(authorization
                .getCharacterRefId(), character.getId());
    }

    /** 校验授权服务器、角色服务器和当前数据源完全一致。 */
    private boolean isExpectedServer(EveAuthorizationDO authorization, EveCharacterDO character) {
        return Objects.equals(authorization.getServer(), character.getServer()) && Objects.equals(character
            .getServer(), properties.getEsi().getDatasource().getValue());
    }

    /** 校验已验签 JWT 的 subject 仍指向原绑定角色。 */
    private static boolean isExpectedSubject(EveCharacterDO character, Jwt jwt) {
        return Objects.equals(character.getCharacterId(), parseCharacterId(jwt.getSubject()));
    }

    /** 校验刷新前、响应和 JWT 三方 Scope 不发生静默缩减或替换。 */
    private static boolean areScopesConsistent(List<String> oldScopes,
                                               List<String> responseScopes,
                                               List<String> jwtScopes,
                                               String responseScopeText) {
        LinkedHashSet<String> jwtScopeSet = new LinkedHashSet<>(jwtScopes);
        if (!jwtScopeSet.containsAll(responseScopes) || !responseScopes.containsAll(oldScopes)) {
            return false;
        }
        return responseScopeText == null || responseScopeText.isBlank() || jwtScopeSet
            .equals(new LinkedHashSet<>(responseScopes));
    }

    /** 从已验签 JWT subject 提取角色 ID。 */
    private static Long parseCharacterId(String subject) {
        Matcher matcher = CHARACTER_ID_SUFFIX.matcher(subject == null ? "" : subject);
        if (!matcher.find()) {
            throw new TokenIdentityValidationException();
        }
        return Long.valueOf(matcher.group(1));
    }

    /** 恒定时间比较角色所有者声明。 */
    private static boolean sameOwner(String expected, String actual) {
        return expected != null && !expected.isBlank() && actual != null && !actual.isBlank() && MessageDigest
            .isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    /** 读取字符串或字符串集合形式的 Scope。 */
    private static List<String> readScopes(Object claim) {
        if (claim instanceof String value) {
            return parseScopes(value);
        }
        if (claim instanceof Collection<?> values && values.stream().allMatch(String.class::isInstance)) {
            return values.stream().map(String.class::cast).filter(value -> !value.isBlank()).distinct().toList();
        }
        throw new TokenIdentityValidationException();
    }

    /** 解析空格分隔的 Scope。 */
    private static List<String> parseScopes(String scope) {
        return scope == null || scope.isBlank()
            ? List.of()
            : Arrays.stream(scope.trim().split("\\s+")).distinct().toList();
    }

    /** 仅表示刷新令牌已验签但身份绑定不满足本地不变量。 */
    private static final class TokenIdentityValidationException extends RuntimeException {
    }

    /** 校验授权状态和本地令牌。 */
    private static void requireActiveAuthorization(EveAuthorizationDO authorization) {
        if (authorization == null || !EveAuthorizationStatus.ACTIVE.equals(authorization.getStatus()) || authorization
            .getAccessToken() == null || authorization.getAccessToken().isBlank()) {
            throw new IllegalStateException("EVE 授权不可用");
        }
    }

    /** 写入只包含固定分类的令牌刷新审计。 */
    private void audit(EveAuthorizationDO authorization, EveAuthAuditResult result, String detail) {
        EveAuthAuditDO audit = new EveAuthAuditDO();
        audit.setTenantId(authorization.getTenantId());
        audit.setUserId(authorization.getUserId());
        audit.setCharacterRefId(authorization.getCharacterRefId());
        audit.setAuthorizationId(authorization.getId());
        audit.setEventType(EveAuthAuditEventType.TOKEN_REFRESHED);
        audit.setResult(result);
        audit.setDetail(detail);
        audit.setOccurredAt(LocalDateTime.now());
        audit.setCreateUser(1L);
        audit.setDeleted(0L);
        auditMapper.insert(audit);
    }
}
