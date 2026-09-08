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
import org.springframework.stereotype.Service;
import top.continew.admin.common.api.system.EveDerivedRoleApi;
import top.continew.admin.common.enums.EveDerivedIdentity;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveCharacterRoleSnapshotMapper;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/**
 * 基于同租户用户的全部有效角色事实聚合唯一站内派生身份。
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveDerivedIdentityService {

    private final EveCharacterMapper characterMapper;
    private final EveAuthorizationMapper authorizationMapper;
    private final EveCharacterRoleSnapshotMapper roleSnapshotMapper;
    private final EveDerivedRoleApi derivedRoleApi;

    /**
     * 聚合全部有效授权角色，按 CEO、总监、普通成员的优先级同步站内身份。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     */
    public void synchronize(Long tenantId, Long userId) {
        boolean member = false;
        boolean director = false;
        for (EveCharacterDO character : characterMapper.selectActiveByUser(tenantId, userId)) {
            if (!ownedBy(character, tenantId, userId) || !hasActiveAuthorization(tenantId, userId, character.getId())) {
                continue;
            }
            EveCharacterRoleSnapshotDO snapshot = roleSnapshotMapper.selectLatest(tenantId, character.getId());
            if (!isFreshOwnedSnapshot(snapshot, tenantId, character.getId())) {
                continue;
            }
            member = true;
            if (Boolean.TRUE.equals(snapshot.getIsCeo())) {
                derivedRoleApi.synchronize(tenantId, userId, EveDerivedIdentity.OWNER);
                return;
            }
            director |= safeRoles(snapshot.getRoles()).contains("Director");
        }
        derivedRoleApi.synchronize(tenantId, userId, director
            ? EveDerivedIdentity.ADMIN
            : member ? EveDerivedIdentity.MEMBER : EveDerivedIdentity.NONE);
    }

    /** 判断角色是否严格属于当前租户用户。 */
    private static boolean ownedBy(EveCharacterDO character, Long tenantId, Long userId) {
        return Objects.equals(tenantId, character.getTenantId()) && Objects.equals(userId, character.getUserId());
    }

    /** 判断角色是否至少存在一条有效授权。 */
    private boolean hasActiveAuthorization(Long tenantId, Long userId, Long characterRefId) {
        return authorizationMapper.selectByUserCharacter(tenantId, userId, characterRefId)
            .stream()
            .anyMatch(authorization -> ownedBy(authorization, tenantId, userId, characterRefId) && isUsableAuthorization(authorization));
    }

    /** 授权必须处于 ACTIVE，且访问令牌仍有效或刷新令牌可用于续期。 */
    private static boolean isUsableAuthorization(EveAuthorizationDO authorization) {
        if (!EveAuthorizationStatus.ACTIVE.equals(authorization.getStatus())) {
            return false;
        }
        boolean accessTokenAvailable = authorization.getAccessToken() != null && !authorization.getAccessToken()
            .isBlank() && authorization.getExpiresAt() != null && authorization.getExpiresAt()
                .isAfter(LocalDateTime.now(ZoneOffset.UTC));
        boolean refreshTokenAvailable = authorization.getRefreshToken() != null && !authorization.getRefreshToken()
            .isBlank();
        return accessTokenAvailable || refreshTokenAvailable;
    }

    /** 快照必须属于当前租户角色，且上游事实缓存仍在有效期内。 */
    private static boolean isFreshOwnedSnapshot(EveCharacterRoleSnapshotDO snapshot,
                                                Long tenantId,
                                                Long characterRefId) {
        return snapshot != null && Objects.equals(tenantId, snapshot.getTenantId()) && Objects
            .equals(characterRefId, snapshot.getCharacterRefId()) && snapshot.getSourceExpiresAt() != null && snapshot
                .getSourceExpiresAt()
                .isAfter(LocalDateTime.now());
    }

    /** 判断授权是否严格属于当前租户用户和角色。 */
    private static boolean ownedBy(EveAuthorizationDO authorization, Long tenantId, Long userId, Long characterRefId) {
        return Objects.equals(tenantId, authorization.getTenantId()) && Objects.equals(userId, authorization
            .getUserId()) && Objects.equals(characterRefId, authorization.getCharacterRefId());
    }

    /** 归一可空角色集合。 */
    private static List<String> safeRoles(List<String> roles) {
        return roles == null ? List.of() : roles;
    }
}
