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

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.eve.client.SerenityTokenResponse;
import top.continew.admin.eve.client.OAuthFailureCode;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.model.enums.EveAuthorizationVerificationStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * EVE 授权令牌持久化服务，与本站 Sa-Token 会话完全独立。
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveAuthorizationTokenService {

    private final EveAuthorizationMapper authorizationMapper;
    private final Clock clock = Clock.systemUTC();

    /**
     * 保存国服返回的加密令牌与有效期。
     *
     * @param tenantId        租户 ID
     * @param userId          用户 ID
     * @param authorizationId 授权记录 ID
     * @param response        国服 Token 响应
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveTokenResponse(Long tenantId, Long userId, Long authorizationId, SerenityTokenResponse response) {
        saveTokenResponse(tenantId, userId, authorizationId, response, EveAuthorizationStatus.ACTIVE);
    }

    /** 重新授权时仅在记录仍处于读取到的原状态时替换令牌，撤销状态不可恢复。 */
    @Transactional(rollbackFor = Exception.class)
    public void replaceTokenResponse(Long tenantId,
                                     Long userId,
                                     Long authorizationId,
                                     SerenityTokenResponse response,
                                     EveAuthorizationStatus expectedStatus) {
        if (expectedStatus == null || EveAuthorizationStatus.REVOKED.equals(expectedStatus)) {
            throw new IllegalStateException("已撤销的 EVE 授权不能直接恢复");
        }
        saveTokenResponse(tenantId, userId, authorizationId, response, expectedStatus);
    }

    /** 校验并按预期状态原子保存国服令牌。 */
    private void saveTokenResponse(Long tenantId,
                                   Long userId,
                                   Long authorizationId,
                                   SerenityTokenResponse response,
                                   EveAuthorizationStatus expectedStatus) {
        if (authorizationId == null || response == null || response.accessToken() == null || response.accessToken()
            .isBlank() || response.refreshToken() == null || response.refreshToken().isBlank() || response
                .expiresIn() <= 0 || !"Bearer".equalsIgnoreCase(response.tokenType())) {
            throw new IllegalArgumentException("国服 Token 响应无效");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        EveAuthorizationDO update = new EveAuthorizationDO();
        update.setAccessToken(response.accessToken());
        update.setRefreshToken(response.refreshToken());
        update.setTokenType(response.tokenType());
        update.setExpiresAt(now.plusSeconds(response.expiresIn()));
        update.setScopes(parseScopes(response.scope()));
        update.setStatus(EveAuthorizationStatus.ACTIVE);
        update.setFailureCount(0);
        update.setLastVerificationStatus(EveAuthorizationVerificationStatus.PENDING);
        updateOwned(tenantId, userId, authorizationId, update, Wrappers.<EveAuthorizationDO>update()
            .set("failure_code", null)
            .eq("status", expectedStatus));
    }

    /**
     * 撤销后原子清空两类密文并记录撤销状态；本站账号和最近已验证的军团身份不受影响。
     *
     * @param authorizationId 授权记录 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void clearRevokedTokens(Long tenantId, Long userId, Long authorizationId) {
        updateOwned(tenantId, userId, authorizationId, Wrappers.<EveAuthorizationDO>update()
            .set("access_token", null)
            .set("refresh_token", null)
            .set("status", EveAuthorizationStatus.REVOKED)
            .set("revoked_at", LocalDateTime.now(clock)));
    }

    /**
     * 记录脱敏失败分类，不保存国服响应正文。
     *
     * @param authorizationId 授权记录 ID
     * @param failureCode     安全失败分类
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordFailure(Long tenantId, Long userId, Long authorizationId, OAuthFailureCode failureCode) {
        EveAuthorizationDO current = authorizationMapper.selectOwnedById(tenantId, userId, authorizationId);
        if (current == null) {
            throw new IllegalStateException("当前用户无权更新该 EVE 授权");
        }
        int failureCount = current.getFailureCount() == null ? 1 : current.getFailureCount() + 1;
        boolean permanent = isPermanent(failureCode);
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<EveAuthorizationDO> wrapper = Wrappers
            .<EveAuthorizationDO>update()
            .set("failure_count", failureCount)
            .set("failure_code", failureCode.name())
            .set("last_verification_status", permanent
                ? EveAuthorizationVerificationStatus.INVALID
                : EveAuthorizationVerificationStatus.FAILED)
            .set("status", permanent ? EveAuthorizationStatus.REAUTH_REQUIRED : EveAuthorizationStatus.ACTIVE);
        if (permanent) {
            wrapper.set("access_token", null)
                .set("refresh_token", null)
                .set("last_verified_at", LocalDateTime.now(clock));
        }
        updateOwned(tenantId, userId, authorizationId, wrapper.eq("status", EveAuthorizationStatus.ACTIVE));
    }

    /** 标记授权已通过最新上游事实验证。 */
    @Transactional(rollbackFor = Exception.class)
    public void markVerified(Long tenantId, Long userId, Long authorizationId, LocalDateTime verifiedAt) {
        updateOwned(tenantId, userId, authorizationId, Wrappers.<EveAuthorizationDO>update()
            .set("status", EveAuthorizationStatus.ACTIVE)
            .set("last_verification_status", EveAuthorizationVerificationStatus.VALID)
            .set("last_verified_at", verifiedAt)
            .set("failure_count", 0)
            .set("failure_code", null)
            .eq("status", EveAuthorizationStatus.ACTIVE));
    }

    /** 标记授权必须由用户重新授权，不增加临时失败计数。 */
    @Transactional(rollbackFor = Exception.class)
    public void markReauthorizationRequired(Long tenantId,
                                            Long userId,
                                            Long authorizationId,
                                            OAuthFailureCode failureCode) {
        updateOwned(tenantId, userId, authorizationId, Wrappers.<EveAuthorizationDO>update()
            .set("access_token", null)
            .set("refresh_token", null)
            .set("status", EveAuthorizationStatus.REAUTH_REQUIRED)
            .set("last_verification_status", EveAuthorizationVerificationStatus.INVALID)
            .set("last_verified_at", LocalDateTime.now(clock))
            .set("failure_code", failureCode.name())
            .eq("status", EveAuthorizationStatus.ACTIVE));
    }

    /** 为全部更新附加租户和所有者条件，并要求精确更新一行。 */
    private void updateOwned(Long tenantId,
                             Long userId,
                             Long authorizationId,
                             com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<EveAuthorizationDO> wrapper) {
        updateOwned(tenantId, userId, authorizationId, null, wrapper);
    }

    /** 为实体更新附加租户和所有者条件，确保 TypeHandler 与字段加密拦截器生效。 */
    private void updateOwned(Long tenantId,
                             Long userId,
                             Long authorizationId,
                             EveAuthorizationDO update,
                             com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<EveAuthorizationDO> wrapper) {
        if (tenantId == null || userId == null || authorizationId == null) {
            throw new IllegalArgumentException("EVE 授权归属参数无效");
        }
        int affected = authorizationMapper.update(update, wrapper.eq("tenant_id", tenantId)
            .eq("user_id", userId)
            .eq("id", authorizationId)
            .eq("deleted", 0));
        if (affected != 1) {
            throw new IllegalStateException("当前用户无权更新该 EVE 授权");
        }
    }

    /** 判断失败是否要求用户重新授权。 */
    private static boolean isPermanent(OAuthFailureCode failureCode) {
        return failureCode == OAuthFailureCode.INVALID_GRANT || failureCode == OAuthFailureCode.UNAUTHORIZED || failureCode == OAuthFailureCode.FORBIDDEN || failureCode == OAuthFailureCode.PERMANENT;
    }

    /** 将空格分隔 Scope 转换为不可变列表。 */
    private static List<String> parseScopes(String scope) {
        if (scope == null || scope.isBlank()) {
            return List.of();
        }
        return Arrays.stream(scope.trim().split("\\s+")).distinct().toList();
    }
}
