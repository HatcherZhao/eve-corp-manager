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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.client.OAuthFailureCode;
import top.continew.admin.eve.client.SerenityTokenClient;
import top.continew.admin.eve.client.SerenityTokenClientException;
import top.continew.admin.eve.mapper.EveAuthAuditMapper;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.model.EveAuthorizationRevocationResp;
import top.continew.admin.eve.model.entity.EveAuthAuditDO;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.enums.EveAuthAuditEventType;
import top.continew.admin.eve.model.enums.EveAuthAuditResult;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 处理当前用户主动撤销 EVE 授权；本站账号与军团身份独立保留。
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveAuthorizationRevocationService {

    private final EveAuthorizationMapper authorizationMapper;
    private final EveAuthorizationTokenService tokenService;
    private final SerenityTokenClient tokenClient;
    private final EveAuthAuditMapper auditMapper;
    private final RedissonClient redissonClient;

    /**
     * 按当前会话归属撤销指定授权；即使上游暂时不可用也保证本地立即失效。
     *
     * @param authorizationId 授权 ID
     * @return 本地已撤销及上游确认状态
     */
    @Transactional(rollbackFor = Exception.class)
    public EveAuthorizationRevocationResp revokeCurrent(Long authorizationId) {
        UserContext context = UserContextHolder.getContext();
        RLock lock = redissonClient
            .getLock(EveAuthorizationLifecycleService.AUTHORIZATION_LOCK_PREFIX + authorizationId);
        boolean locked = false;
        boolean unlockDeferred = false;
        try {
            locked = lock.tryLock(5, TimeUnit.SECONDS);
            if (!locked) {
                throw new IllegalStateException("EVE 授权正在变更，请稍后重试");
            }
            unlockDeferred = EveAuthorizationLifecycleService.deferUnlockUntilTransactionCompletion(lock);
            EveAuthorizationDO authorization = authorizationMapper.selectOwnedById(context.getTenantId(), context
                .getId(), authorizationId);
            if (authorization == null || !Objects.equals(context.getTenantId(), authorization.getTenantId()) || !Objects
                .equals(context.getId(), authorization.getUserId()) || !Objects.equals(authorizationId, authorization
                    .getId())) {
                throw new IllegalStateException("当前用户无权撤销该 EVE 授权");
            }
            OAuthFailureCode upstreamFailure = revokeUpstream(authorization);
            tokenService.clearRevokedTokens(context.getTenantId(), context.getId(), authorizationId);
            audit(authorization, upstreamFailure);
            return upstreamFailure == null
                ? EveAuthorizationRevocationResp.upstreamRevoked()
                : EveAuthorizationRevocationResp.upstreamUnconfirmed(upstreamFailure);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("EVE 授权正在变更，请稍后重试", e);
        } finally {
            if (locked && !unlockDeferred && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 尽力撤销两类上游令牌并仅返回脱敏失败分类。 */
    private OAuthFailureCode revokeUpstream(EveAuthorizationDO authorization) {
        OAuthFailureCode failure = revokeOne(authorization.getRefreshToken(), "refresh_token");
        OAuthFailureCode accessFailure = revokeOne(authorization.getAccessToken(), "access_token");
        return failure == null ? accessFailure : failure;
    }

    /** 撤销单个非空令牌。 */
    private OAuthFailureCode revokeOne(String token, String tokenType) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            tokenClient.revoke(token, tokenType);
            return null;
        } catch (SerenityTokenClientException e) {
            return e.getFailureCode();
        }
    }

    /** 写入不含令牌、响应正文和用户输入的撤销审计。 */
    private void audit(EveAuthorizationDO authorization, OAuthFailureCode upstreamFailure) {
        EveAuthAuditDO audit = new EveAuthAuditDO();
        audit.setTenantId(authorization.getTenantId());
        audit.setUserId(authorization.getUserId());
        audit.setCharacterRefId(authorization.getCharacterRefId());
        audit.setAuthorizationId(authorization.getId());
        audit.setEventType(EveAuthAuditEventType.AUTHORIZATION_REVOKED);
        audit.setResult(upstreamFailure == null ? EveAuthAuditResult.SUCCESS : EveAuthAuditResult.FAILURE);
        audit.setDetail(upstreamFailure == null ? "REVOKED" : "LOCAL_REVOKED_" + upstreamFailure.name());
        audit.setOccurredAt(LocalDateTime.now());
        audit.setCreateUser(1L);
        audit.setDeleted(0L);
        auditMapper.insert(audit);
    }
}
