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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.auth.OAuthTransaction;
import top.continew.admin.eve.auth.OAuthTransactionPurpose;
import top.continew.admin.eve.auth.OAuthTransactionStore;
import top.continew.admin.eve.auth.SerenityAuthorizationStart;
import top.continew.admin.eve.auth.SerenityAuthorizationStartService;
import top.continew.admin.eve.client.SerenityTokenClient;
import top.continew.admin.eve.client.SerenityTokenResponse;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.mapper.EveAuthAuditMapper;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.model.EvePermissionRefreshResp;
import top.continew.admin.eve.model.entity.EveAuthAuditDO;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.model.enums.EveAuthAuditEventType;
import top.continew.admin.eve.model.enums.EveAuthAuditResult;
import top.continew.admin.eve.security.SerenityCallback;
import top.continew.admin.eve.security.SerenityCallbackUrlParser;
import top.continew.admin.eve.security.SerenityJwtDecoderFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;

/**
 * 为已登录用户执行严格绑定原角色的 Scope 扩展授权。
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EvePermissionReauthorizationService {

    private static final Pattern CHARACTER_ID_SUFFIX = Pattern.compile("(?:^|:)([1-9][0-9]*)$");
    private final EveCharacterMapper characterMapper;
    private final EveAuthorizationMapper authorizationMapper;
    private final EveAuthAuditMapper auditMapper;
    private final SerenityAuthorizationStartService authorizationStartService;
    private final OAuthTransactionStore transactionStore;
    private final SerenityCallbackUrlParser callbackParser;
    private final SerenityTokenClient tokenClient;
    private final SerenityJwtDecoderFactory jwtDecoderFactory;
    private final EveAuthorizationTokenService tokenService;
    private final EvePermissionRefreshService permissionRefreshService;
    private final SerenityProperties properties;
    private final RedissonClient redissonClient;
    private final EveAuthorizationScopePolicy scopePolicy;

    /** 发起绑定当前租户、用户、服务器和原游戏角色的重新授权。 */
    public SerenityAuthorizationStart startCurrent(String browserBindingDigest) {
        UserContext context = UserContextHolder.getContext();
        EveCharacterDO character = primaryCharacter(context.getTenantId(), context.getId());
        latestAuthorization(context.getTenantId(), context.getId(), character);
        return authorizationStartService.start(OAuthTransactionPurpose.EXPAND_SCOPES, context.getTenantId(), context
            .getId(), character.getServer(), character.getCharacterId(), browserBindingDigest, scopePolicy
                .plannedScopes());
    }

    /**
     * 消费重新授权回调，严格匹配原角色并原子更新令牌和 Scope，随后立即刷新权限。
     *
     * @param callbackUrl          完整回调 URL
     * @param browserBindingDigest 浏览器绑定摘要
     * @return 权限刷新结果
     */
    @Transactional(rollbackFor = Exception.class)
    public EvePermissionRefreshResp handleCurrent(String callbackUrl, String browserBindingDigest) {
        UserContext context = UserContextHolder.getContext();
        SerenityCallback callback = callbackParser.parse(callbackUrl);
        OAuthTransaction transaction = transactionStore.consume(callback.state())
            .orElseThrow(() -> new IllegalStateException("国服重新授权事务已失效"));
        validateTransaction(transaction, context, browserBindingDigest, callback);
        EveCharacterDO character = primaryCharacter(context.getTenantId(), context.getId());
        if (!transaction.getBoundCharacterId().equals(character.getCharacterId()) || !transaction.getBoundServer()
            .equals(character.getServer()) || !properties.getEsi()
                .getDatasource()
                .getValue()
                .equals(character.getServer())) {
            throw new IllegalStateException("重新授权角色与原绑定不一致");
        }
        EveAuthorizationDO authorization = latestAuthorization(context.getTenantId(), context.getId(), character);
        RLock userLock = redissonClient.getLock(EvePermissionRefreshService.USER_LOCK_PREFIX + context
            .getTenantId() + ":" + context.getId());
        RLock authorizationLock = redissonClient
            .getLock(EveAuthorizationLifecycleService.AUTHORIZATION_LOCK_PREFIX + authorization.getId());
        boolean userLocked = false;
        boolean authorizationLocked = false;
        boolean userUnlockDeferred = false;
        boolean authorizationUnlockDeferred = false;
        try {
            userLocked = userLock.tryLock(properties.getPermissionRefresh()
                .getLockWait()
                .toMillis(), TimeUnit.MILLISECONDS);
            if (!userLocked) {
                throw new IllegalStateException("EVE 授权正在变更，请稍后重试");
            }
            userUnlockDeferred = EveAuthorizationLifecycleService.deferUnlockUntilTransactionCompletion(userLock);
            authorizationLocked = authorizationLock.tryLock(properties.getPermissionRefresh()
                .getLockWait()
                .toMillis(), TimeUnit.MILLISECONDS);
            if (!authorizationLocked) {
                throw new IllegalStateException("EVE 授权正在变更，请稍后重试");
            }
            authorizationUnlockDeferred = EveAuthorizationLifecycleService
                .deferUnlockUntilTransactionCompletion(authorizationLock);
            EveAuthorizationDO latest = authorizationMapper.selectOwnedById(context.getTenantId(), context
                .getId(), authorization.getId());
            if (latest == null || EveAuthorizationStatus.REVOKED.equals(latest.getStatus())) {
                throw new IllegalStateException("已撤销的 EVE 授权不能直接恢复");
            }
            SerenityTokenResponse token = tokenClient.exchangeCode(callback.code(), transaction.getVerifier());
            Jwt jwt = jwtDecoderFactory.create().decode(token.accessToken());
            Long characterId = parseCharacterId(jwt.getSubject());
            List<String> scopes = readScopes(jwt.getClaim(properties.getSso().getScopeClaim()));
            if (!character.getCharacterId().equals(characterId) || !sameOwner(character.getOwnerHash(), jwt
                .getClaimAsString("owner")) || !scopes.containsAll(transaction.getRequestedScopes())) {
                throw new IllegalStateException("重新授权角色或 Scope 与原事务不一致");
            }
            SerenityTokenResponse normalized = new SerenityTokenResponse(token.accessToken(), token
                .refreshToken(), token.tokenType(), token.expiresIn(), String.join(" ", scopes));
            tokenService.replaceTokenResponse(context.getTenantId(), context.getId(), authorization
                .getId(), normalized, latest.getStatus());
            EvePermissionRefreshResp response = permissionRefreshService.refresh(context.getTenantId(), context
                .getId(), true);
            audit(context.getTenantId(), context.getId(), character.getId(), authorization.getId());
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("EVE 授权正在变更，请稍后重试", e);
        } finally {
            if (authorizationLocked && !authorizationUnlockDeferred && authorizationLock.isHeldByCurrentThread()) {
                authorizationLock.unlock();
            }
            if (userLocked && !userUnlockDeferred && userLock.isHeldByCurrentThread()) {
                userLock.unlock();
            }
        }
    }

    /** 校验一次性事务与当前站内会话严格一致。 */
    private static void validateTransaction(OAuthTransaction transaction,
                                            UserContext context,
                                            String browserBindingDigest,
                                            SerenityCallback callback) {
        boolean sameBrowser = browserBindingDigest != null && transaction
            .getBrowserBindingDigest() != null && MessageDigest.isEqual(transaction.getBrowserBindingDigest()
                .getBytes(StandardCharsets.US_ASCII), browserBindingDigest.getBytes(StandardCharsets.US_ASCII));
        if (transaction.getPurpose() != OAuthTransactionPurpose.EXPAND_SCOPES || transaction
            .getExpiresAt() == null || transaction.getExpiresAt().isBefore(Instant.now()) || !sameBrowser || callback
                .hasError() || !context.getTenantId().equals(transaction.getBoundTenantId()) || !context.getId()
                    .equals(transaction.getBoundUserId())) {
            throw new IllegalStateException("国服重新授权事务校验失败");
        }
    }

    /** 读取当前主角色，不允许通过客户端指定。 */
    private EveCharacterDO primaryCharacter(Long tenantId, Long userId) {
        List<EveCharacterDO> characters = characterMapper.selectActiveByUser(tenantId, userId);
        return characters.stream()
            .filter(character -> Objects.equals(tenantId, character.getTenantId()) && Objects.equals(userId, character
                .getUserId()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("当前用户没有可重新授权的 EVE 角色"));
    }

    /** 读取原角色的最新授权。 */
    private EveAuthorizationDO latestAuthorization(Long tenantId, Long userId, EveCharacterDO character) {
        List<EveAuthorizationDO> authorizations = authorizationMapper.selectByUserCharacter(tenantId, userId, character
            .getId());
        return authorizations.stream()
            .filter(authorization -> Objects.equals(tenantId, authorization.getTenantId()) && Objects
                .equals(userId, authorization.getUserId()) && Objects.equals(character.getId(), authorization
                    .getCharacterRefId()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("当前用户没有可重新授权的 EVE 授权"));
    }

    /** 从已校验 JWT subject 尾部提取角色 ID。 */
    private static Long parseCharacterId(String subject) {
        Matcher matcher = CHARACTER_ID_SUFFIX.matcher(subject == null ? "" : subject);
        if (!matcher.find()) {
            throw new IllegalStateException("国服角色标识无效");
        }
        return Long.valueOf(matcher.group(1));
    }

    /** 使用恒定时间比较已验签 owner 与历史角色所有者标识。 */
    private static boolean sameOwner(String expectedOwner, String actualOwner) {
        return expectedOwner != null && !expectedOwner.isBlank() && actualOwner != null && !actualOwner
            .isBlank() && MessageDigest.isEqual(expectedOwner.getBytes(StandardCharsets.UTF_8), actualOwner
                .getBytes(StandardCharsets.UTF_8));
    }

    /** 读取字符串或字符串集合形式的 Scope。 */
    private static List<String> readScopes(Object claim) {
        if (claim instanceof String value) {
            return value.isBlank() ? List.of() : List.of(value.trim().split("\\s+"));
        }
        if (claim instanceof Collection<?> values && values.stream().allMatch(String.class::isInstance)) {
            return values.stream().map(String.class::cast).distinct().toList();
        }
        throw new IllegalStateException("国服授权 Scope 无效");
    }

    /** 写入不包含令牌、授权码或回调地址的重新授权审计。 */
    private void audit(Long tenantId, Long userId, Long characterRefId, Long authorizationId) {
        EveAuthAuditDO audit = new EveAuthAuditDO();
        audit.setTenantId(tenantId);
        audit.setUserId(userId);
        audit.setCharacterRefId(characterRefId);
        audit.setAuthorizationId(authorizationId);
        audit.setEventType(EveAuthAuditEventType.REAUTHORIZED);
        audit.setResult(EveAuthAuditResult.SUCCESS);
        audit.setDetail("SCOPES_REPLACED");
        audit.setOccurredAt(LocalDateTime.now());
        audit.setCreateUser(1L);
        audit.setDeleted(0L);
        auditMapper.insert(audit);
    }
}
