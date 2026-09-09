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
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveCharacterRoleSnapshotMapper;
import top.continew.admin.eve.model.EveCapabilityResult;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.model.enums.EveCapability;
import top.continew.admin.eve.model.enums.EveCapabilityStatus;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 集中校验站内权限、租户、授权 Scope 与真实游戏角色的能力策略。
 *
 * <p>每次只在同一授权、其绑定角色及该角色最新快照内判断，禁止跨授权拼接条件。</p>
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveCapabilityPolicy {

    private final EveCharacterMapper characterMapper;
    private final EveAuthorizationMapper authorizationMapper;
    private final EveCharacterRoleSnapshotMapper roleSnapshotMapper;

    /** 判断当前租户用户是否具备指定模块能力。 */
    public EveCapabilityResult evaluate(EveCapability capability, Long tenantId, Set<String> sitePermissions) {
        return evaluateCapabilities(List.of(capability), tenantId, sitePermissions).get(capability);
    }

    /** 一次读取租户授权与相关最新快照并计算全部模块能力。 */
    public List<EveCapabilityResult> evaluateAll(Long tenantId, Set<String> sitePermissions) {
        Map<EveCapability, EveCapabilityResult> results = evaluateCapabilities(Arrays.asList(EveCapability
            .values()), tenantId, sitePermissions);
        return Arrays.stream(EveCapability.values()).map(results::get).toList();
    }

    /** 使用同一批候选授权和快照计算指定能力，避免按能力重复查询。 */
    private Map<EveCapability, EveCapabilityResult> evaluateCapabilities(List<EveCapability> capabilities,
                                                                         Long tenantId,
                                                                         Set<String> sitePermissions) {
        Map<EveCapability, EveCapabilityResult> results = new EnumMap<>(EveCapability.class);
        List<EveCapability> permittedCapabilities = capabilities.stream()
            .filter(capability -> sitePermissions != null && sitePermissions.contains(capability.getSitePermission()))
            .toList();
        capabilities.stream()
            .filter(capability -> !permittedCapabilities.contains(capability))
            .forEach(capability -> results.put(capability, EveCapabilityResult
                .of(capability, EveCapabilityStatus.MISSING_SITE_PERMISSION)));
        if (permittedCapabilities.isEmpty()) {
            return results;
        }
        List<EveAuthorizationDO> authorizations = authorizationMapper.selectTenantCandidates(tenantId);
        Set<String> relevantScopes = permittedCapabilities.stream()
            .map(EveCapability::getScope)
            .collect(Collectors.toSet());
        List<Long> characterRefIds = authorizations.stream()
            .filter(authorization -> Objects.equals(tenantId, authorization.getTenantId()) && isActive(authorization))
            .filter(authorization -> safeScopes(authorization).stream().anyMatch(relevantScopes::contains))
            .map(EveAuthorizationDO::getCharacterRefId)
            .filter(Objects::nonNull)
            .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), List::copyOf));
        Map<Long, EveCharacterRoleSnapshotDO> snapshots = characterRefIds.isEmpty()
            ? Map.of()
            : roleSnapshotMapper.selectLatestByCharacterRefs(tenantId, characterRefIds)
                .stream()
                .filter(snapshot -> snapshot.getCharacterRefId() != null)
                .collect(Collectors.toMap(EveCharacterRoleSnapshotDO::getCharacterRefId, Function.identity(), (left,
                                                                                                               right) -> left));
        permittedCapabilities.forEach(capability -> results
            .put(capability, evaluateLoaded(capability, tenantId, authorizations, snapshots)));
        return results;
    }

    /** 在已加载的同租户候选数据内判断单项能力。 */
    private EveCapabilityResult evaluateLoaded(EveCapability capability,
                                               Long tenantId,
                                               List<EveAuthorizationDO> authorizations,
                                               Map<Long, EveCharacterRoleSnapshotDO> snapshots) {
        boolean authorizationSeen = false;
        boolean validAuthorizationSeen = false;
        boolean scopeSeen = false;
        boolean snapshotSeen = false;
        for (EveAuthorizationDO authorization : authorizations) {
            if (!Objects.equals(tenantId, authorization.getTenantId())) {
                continue;
            }
            authorizationSeen = true;
            if (!isActive(authorization)) {
                continue;
            }
            validAuthorizationSeen = true;
            List<String> scopes = safeScopes(authorization);
            if (!scopes.contains(capability.getScope())) {
                continue;
            }
            scopeSeen = true;
            EveCharacterRoleSnapshotDO snapshot = snapshots.get(authorization.getCharacterRefId());
            if (!isUsableSnapshot(snapshot, tenantId, authorization.getCharacterRefId())) {
                continue;
            }
            snapshotSeen = true;
            List<String> globalRoles = snapshot.getRoles() == null ? Collections.emptyList() : snapshot.getRoles();
            if (capability.getGameRole() == null || Boolean.TRUE.equals(snapshot.getIsCeo()) || globalRoles
                .contains("Director") || globalRoles.contains(capability.getGameRole())) {
                return EveCapabilityResult.of(capability, EveCapabilityStatus.AVAILABLE);
            }
        }
        return EveCapabilityResult
            .of(capability, resolveUnavailableStatus(authorizationSeen, validAuthorizationSeen, scopeSeen, snapshotSeen));
    }

    /** 按数据源、授权和 Scope 的既定优先级返回不可用原因。 */
    private static EveCapabilityStatus resolveUnavailableStatus(boolean authorizationSeen,
                                                                boolean validAuthorizationSeen,
                                                                boolean scopeSeen,
                                                                boolean snapshotSeen) {
        if (!authorizationSeen || validAuthorizationSeen && scopeSeen && !snapshotSeen) {
            return EveCapabilityStatus.NO_DATA_SOURCE;
        }
        if (!validAuthorizationSeen) {
            return EveCapabilityStatus.AUTH_EXPIRED;
        }
        return scopeSeen ? EveCapabilityStatus.MISSING_GAME_ROLE : EveCapabilityStatus.MISSING_SCOPE;
    }

    /** 安全读取授权 Scope 集合。 */
    private static List<String> safeScopes(EveAuthorizationDO authorization) {
        return authorization.getScopes() == null ? Collections.emptyList() : authorization.getScopes();
    }

    /** 汇总当前用户授权健康状态，不受具体模块站内权限影响。 */
    public EveCapabilityStatus authorizationStatus(Long tenantId, Long userId) {
        List<EveCharacterDO> characters = characterMapper.selectActiveByUser(tenantId, userId);
        if (characters.isEmpty()) {
            return EveCapabilityStatus.NO_DATA_SOURCE;
        }
        boolean authorizationSeen = false;
        for (EveCharacterDO character : characters) {
            if (!Objects.equals(tenantId, character.getTenantId()) || !Objects.equals(userId, character.getUserId())) {
                continue;
            }
            List<EveAuthorizationDO> authorizations = authorizationMapper
                .selectByUserCharacter(tenantId, userId, character.getId());
            List<EveAuthorizationDO> matched = authorizations.stream()
                .filter(authorization -> Objects.equals(tenantId, authorization.getTenantId()) && Objects
                    .equals(userId, authorization.getUserId()) && Objects.equals(character.getId(), authorization
                        .getCharacterRefId()))
                .toList();
            authorizationSeen |= !matched.isEmpty();
            if (matched.stream().anyMatch(EveCapabilityPolicy::isActive)) {
                return EveCapabilityStatus.AVAILABLE;
            }
        }
        return authorizationSeen ? EveCapabilityStatus.AUTH_EXPIRED : EveCapabilityStatus.NO_DATA_SOURCE;
    }

    /** ACTIVE 且访问令牌可用或仍可刷新时才是能力判断的有效授权。 */
    private static boolean isActive(EveAuthorizationDO authorization) {
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

    /** 快照必须属于当前角色且仍处于上游事实缓存有效期内。 */
    private static boolean isUsableSnapshot(EveCharacterRoleSnapshotDO snapshot, Long tenantId, Long characterRefId) {
        return snapshot != null && Objects.equals(tenantId, snapshot.getTenantId()) && Objects
            .equals(characterRefId, snapshot.getCharacterRefId()) && snapshot.getSourceExpiresAt() != null && snapshot
                .getSourceExpiresAt()
                .isAfter(LocalDateTime.now(ZoneOffset.UTC));
    }
}
