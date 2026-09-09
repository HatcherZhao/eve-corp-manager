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
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.continew.admin.common.api.system.RoleApi;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.common.model.dto.EveSiteRoleDTO;
import top.continew.admin.eve.client.OAuthFailureCode;
import top.continew.admin.eve.client.SerenityEsiClient;
import top.continew.admin.eve.client.SerenityEsiClientException;
import top.continew.admin.eve.client.SerenityTokenClientException;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.mapper.EveAuthAuditMapper;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveCharacterRoleSnapshotMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.mapper.EveCorporationMemberMapper;
import top.continew.admin.eve.model.EvePermissionRefreshResp;
import top.continew.admin.eve.model.entity.EveAuthAuditDO;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.entity.EveCorporationMemberDO;
import top.continew.admin.eve.model.enums.EveAuthAuditEventType;
import top.continew.admin.eve.model.enums.EveAuthAuditResult;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.model.enums.EveCharacterStatus;
import top.continew.admin.eve.model.enums.EveCapabilityStatus;
import top.continew.admin.eve.model.enums.EveCorporationMemberStatus;
import top.continew.admin.eve.model.enums.EvePermissionRefreshStatus;
import top.continew.admin.eve.model.serenity.SerenityCharacterResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationRolesResponse;
import top.continew.admin.eve.model.serenity.SerenityEsiResponse;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 从当前服务端会话定位授权并刷新 EVE 游戏权限事实。
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EvePermissionRefreshService {

    static final String USER_LOCK_PREFIX = "eve:serenity:permission-refresh:";
    private static final String COOLDOWN_PREFIX = "eve:serenity:permission-refresh-cooldown:";
    private static final String REAUTHORIZATION_PATH = "/eve/permissions/reauthorization/start";

    private final EveCharacterMapper characterMapper;
    private final EveCorporationMapper corporationMapper;
    private final EveCorporationMemberMapper memberMapper;
    private final EveAuthorizationMapper authorizationMapper;
    private final EveCharacterRoleSnapshotMapper roleSnapshotMapper;
    private final EveAuthAuditMapper auditMapper;
    private final SerenityEsiClient esiClient;
    private final EveAuthorizationLifecycleService lifecycleService;
    private final EveAuthorizationTokenService tokenService;
    private final EveDerivedIdentityService derivedIdentityService;
    private final EveCapabilityPolicy capabilityPolicy;
    private final RoleApi roleApi;
    private final RedissonClient redissonClient;
    private final SerenityProperties properties;
    private final EveAuthorizationScopePolicy scopePolicy;

    /** 从当前登录会话安全刷新，不接受客户端角色或军团参数。 */
    @Transactional(rollbackFor = Exception.class)
    public EvePermissionRefreshResp refreshCurrent() {
        UserContext context = UserContextHolder.getContext();
        return refresh(context.getTenantId(), context.getId(), false);
    }

    /**
     * 按服务端确认的租户和用户执行复核。
     *
     * @param tenantId       租户 ID
     * @param userId         用户 ID
     * @param bypassCooldown 是否跳过用户主动刷新冷却；上游缓存仍由调用方约束
     * @return 刷新结果
     */
    @Transactional(rollbackFor = Exception.class)
    public EvePermissionRefreshResp refresh(Long tenantId, Long userId, boolean bypassCooldown) {
        return refresh(tenantId, userId, bypassCooldown, null, null);
    }

    /**
     * 后台按授权逐条复核，避免同一用户的次要角色长期得不到验证。
     *
     * @param authorization 待复核授权
     * @return 刷新结果
     */
    @Transactional(rollbackFor = Exception.class)
    public EvePermissionRefreshResp reviewAuthorization(EveAuthorizationDO authorization) {
        if (authorization == null) {
            throw new IllegalArgumentException("待复核 EVE 授权不能为空");
        }
        return refresh(authorization.getTenantId(), authorization.getUserId(), true, authorization
            .getCharacterRefId(), authorization.getId());
    }

    /** 在可选的角色和授权约束下执行刷新。 */
    private EvePermissionRefreshResp refresh(Long tenantId,
                                             Long userId,
                                             boolean bypassCooldown,
                                             Long characterRefId,
                                             Long authorizationId) {
        LocalDateTime requestedAt = LocalDateTime.now();
        String subject = tenantId + ":" + userId;
        RLock lock = redissonClient.getLock(USER_LOCK_PREFIX + subject);
        boolean locked = false;
        boolean unlockDeferred = false;
        try {
            locked = lock.tryLock(properties.getPermissionRefresh().getLockWait().toMillis(), TimeUnit.MILLISECONDS);
            if (!locked) {
                return cooldownResponse(tenantId, userId, requestedAt);
            }
            unlockDeferred = deferUnlockUntilTransactionCompletion(lock);
            RBucket<String> cooldown = redissonClient.getBucket(COOLDOWN_PREFIX + subject);
            LocalDateTime nextAllowedAt = readCooldown(cooldown.get());
            if (!bypassCooldown && nextAllowedAt != null && nextAllowedAt.isAfter(requestedAt)) {
                return responseFromLatest(tenantId, userId, EvePermissionRefreshStatus.COOLDOWN, requestedAt, nextAllowedAt, List
                    .of(), null);
            }
            EvePermissionRefreshResp response = refreshLocked(tenantId, userId, characterRefId, authorizationId, requestedAt);
            LocalDateTime nextRefresh = requestedAt.plus(properties.getPermissionRefresh().getCooldown());
            setCooldownAfterCommit(cooldown, nextRefresh);
            return withNextRefresh(response, nextRefresh);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("游戏权限正在刷新，请稍后重试", e);
        } finally {
            if (locked && !unlockDeferred && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 将刷新锁保持到数据库事务提交或回滚完成，避免并发请求读取未提交状态。 */
    private static boolean deferUnlockUntilTransactionCompletion(RLock lock) {
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

    /** 仅在事务成功提交后写入冷却；无事务调用时立即写入。 */
    private void setCooldownAfterCommit(RBucket<String> cooldown, LocalDateTime nextRefresh) {
        Runnable writeCooldown = () -> cooldown.set(nextRefresh.toString(), properties.getPermissionRefresh()
            .getCooldown());
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            writeCooldown.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                writeCooldown.run();
            }
        });
    }

    /**
     * 解析 Redis 中的冷却时间。
     *
     * <p>Redisson 的 JSON 编解码器会将 {@link LocalDateTime} 还原为字符串；统一采用 ISO-8601 字符串
     * 存储，避免登录链路发生类型转换异常。历史无效缓存视为不存在并由后续刷新覆盖。</p>
     *
     * @param value Redis 缓存值
     * @return 解析后的时间，缺失或无效时返回 {@code null}
     */
    private static LocalDateTime readCooldown(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** 在用户级锁内执行上游检查和数据库同步。 */
    private EvePermissionRefreshResp refreshLocked(Long tenantId,
                                                   Long userId,
                                                   Long characterRefId,
                                                   Long authorizationId,
                                                   LocalDateTime requestedAt) {
        EveCharacterDO character = characterRefId == null
            ? primaryCharacter(tenantId, userId)
            : character(tenantId, userId, characterRefId);
        if (character == null) {
            return emptyResponse(EvePermissionRefreshStatus.MEMBERSHIP_INVALID, requestedAt);
        }
        EveAuthorizationDO authorization = authorizationId == null
            ? latestAuthorization(tenantId, userId, character.getId())
            : ownedAuthorization(tenantId, userId, character.getId(), authorizationId);
        if (authorization == null) {
            return emptyResponse(EvePermissionRefreshStatus.AUTHORIZATION_DISABLED, requestedAt);
        }
        if (EveAuthorizationStatus.REAUTH_REQUIRED.equals(authorization.getStatus())) {
            return responseFromLatest(tenantId, userId, EvePermissionRefreshStatus.REAUTHORIZATION_REQUIRED, requestedAt, null, List
                .of(), REAUTHORIZATION_PATH);
        }
        if (!EveAuthorizationStatus.ACTIVE.equals(authorization.getStatus())) {
            return emptyResponse(EvePermissionRefreshStatus.AUTHORIZATION_DISABLED, requestedAt);
        }
        List<String> missingPlannedScopes = scopePolicy.missingPlannedScopes(authorization.getScopes());
        if (!missingPlannedScopes.isEmpty()) {
            tokenService.markReauthorizationRequired(tenantId, userId, authorization
                .getId(), OAuthFailureCode.PERMANENT);
            audit(tenantId, userId, character.getId(), authorization
                .getId(), EveAuthAuditResult.FAILURE, "MISSING_SCOPES");
            return responseFromLatest(tenantId, userId, EvePermissionRefreshStatus.REAUTHORIZATION_REQUIRED, requestedAt, null, missingPlannedScopes, REAUTHORIZATION_PATH);
        }
        String accessToken;
        try {
            accessToken = lifecycleService.ensureAccessToken(authorization);
        } catch (SerenityTokenClientException e) {
            if (isPermanent(e.getFailureCode())) {
                return responseFromLatest(tenantId, userId, EvePermissionRefreshStatus.REAUTHORIZATION_REQUIRED, requestedAt, null, List
                    .of(), REAUTHORIZATION_PATH);
            }
            reconcileDerivedIdentityAfterTransientFailure(tenantId, userId);
            return responseFromLatest(tenantId, userId, EvePermissionRefreshStatus.UPSTREAM_UNAVAILABLE, requestedAt, null, List
                .of(), null);
        }
        try {
            SerenityCharacterResponse characterResponse = esiClient.getCharacter(character.getCharacterId());
            EveCorporationDO corporation = corporationMapper.selectByTenantAndCorporationId(tenantId, character
                .getCorporationId());
            if (corporation == null || !Objects.equals(characterResponse.corporationId(), corporation
                .getCorporationId())) {
                invalidateMembership(tenantId, userId, character, authorization);
                audit(tenantId, userId, character.getId(), authorization
                    .getId(), EveAuthAuditResult.FAILURE, "MEMBERSHIP_CHANGED");
                return emptyResponse(EvePermissionRefreshStatus.MEMBERSHIP_INVALID, requestedAt);
            }
            SerenityCorporationResponse corporationResponse = esiClient.getCorporation(corporation.getCorporationId());
            SerenityEsiResponse<SerenityCorporationRolesResponse> rolesResponse = esiClient
                .getCorporationRolesWithMetadata(character.getCharacterId(), accessToken);
            LocalDateTime checkedAt = LocalDateTime.now();
            EvePermissionRefreshResp refreshed = persistFacts(tenantId, userId, character, corporation, authorization, characterResponse, corporationResponse, rolesResponse
                .body(), requestedAt, checkedAt, rolesResponse.expiresAt());
            return refreshed;
        } catch (SerenityEsiClientException e) {
            if (isPermanent(e.getFailureCode())) {
                tokenService.markReauthorizationRequired(tenantId, userId, authorization.getId(), e.getFailureCode());
                audit(tenantId, userId, character.getId(), authorization.getId(), EveAuthAuditResult.FAILURE, e
                    .getFailureCode()
                    .name());
                return responseFromLatest(tenantId, userId, EvePermissionRefreshStatus.REAUTHORIZATION_REQUIRED, requestedAt, null, List
                    .of(), REAUTHORIZATION_PATH);
            }
            tokenService.recordFailure(tenantId, userId, authorization.getId(), e.getFailureCode());
            audit(tenantId, userId, character.getId(), authorization.getId(), EveAuthAuditResult.FAILURE, e
                .getFailureCode()
                .name());
            reconcileDerivedIdentityAfterTransientFailure(tenantId, userId);
            return responseFromLatest(tenantId, userId, EvePermissionRefreshStatus.UPSTREAM_UNAVAILABLE, requestedAt, null, List
                .of(), null);
        }
    }

    /** 临时上游失败时仍按本地快照租约收敛身份，过期管理员不得继续保留站内权限。 */
    private void reconcileDerivedIdentityAfterTransientFailure(Long tenantId, Long userId) {
        derivedIdentityService.synchronize(tenantId, userId);
    }

    /** 原子写入公开资料、四范围角色快照和派生站内角色。 */
    private EvePermissionRefreshResp persistFacts(Long tenantId,
                                                  Long userId,
                                                  EveCharacterDO character,
                                                  EveCorporationDO corporation,
                                                  EveAuthorizationDO authorization,
                                                  SerenityCharacterResponse characterResponse,
                                                  SerenityCorporationResponse corporationResponse,
                                                  SerenityCorporationRolesResponse rolesResponse,
                                                  LocalDateTime requestedAt,
                                                  LocalDateTime checkedAt,
                                                  LocalDateTime upstreamExpiresAt) {
        List<String> roles = safeList(rolesResponse.roles());
        List<String> rolesAtHq = safeList(rolesResponse.rolesAtHq());
        List<String> rolesAtBase = safeList(rolesResponse.rolesAtBase());
        List<String> rolesAtOther = safeList(rolesResponse.rolesAtOther());
        boolean ceo = Objects.equals(character.getCharacterId(), corporationResponse.ceoId());
        EveCharacterRoleSnapshotDO previous = roleSnapshotMapper.selectLatest(tenantId, character.getId());
        PermissionState beforeState = permissionState(tenantId, userId, previous);
        boolean changed = previous == null || !Objects.equals(previous.getIsCeo(), ceo) || !Objects.equals(previous
            .getRoles(), roles) || !Objects.equals(previous.getRolesAtHq(), rolesAtHq) || !Objects.equals(previous
                .getRolesAtBase(), rolesAtBase) || !Objects.equals(previous.getRolesAtOther(), rolesAtOther);
        boolean sourceExpiryEstimated = upstreamExpiresAt == null;
        LocalDateTime sourceExpiresAt = sourceExpiryEstimated
            ? checkedAt.plus(properties.getPermissionRefresh().getRoleCacheTtl())
            : upstreamExpiresAt;

        character.setName(characterResponse.name());
        character.setLastVerifiedAt(checkedAt);
        character.setStatus(EveCharacterStatus.ACTIVE);
        characterMapper.updateById(character);

        corporation.setName(corporationResponse.name());
        corporation.setTicker(corporationResponse.ticker());
        corporation.setCeoCharacterId(corporationResponse.ceoId());
        corporation.setAllianceId(corporationResponse.allianceId());
        corporation.setMemberCount(corporationResponse.memberCount());
        corporation.setTaxRate(corporationResponse.taxRate());
        corporation.setLastSyncedAt(checkedAt);
        corporationMapper.updateById(corporation);

        EveCorporationMemberDO member = memberMapper.selectCurrent(tenantId, userId, character.getId());
        if (member != null) {
            member.setStatus(EveCorporationMemberStatus.ACTIVE);
            member.setLeftAt(null);
            member.setLastVerifiedAt(checkedAt);
            memberMapper.updateById(member);
        }

        if (changed) {
            EveCharacterRoleSnapshotDO snapshot = new EveCharacterRoleSnapshotDO();
            snapshot.setTenantId(tenantId);
            snapshot.setCharacterRefId(character.getId());
            snapshot.setServer(character.getServer());
            snapshot.setCharacterId(character.getCharacterId());
            snapshot.setIsCeo(ceo);
            snapshot.setRoles(roles);
            snapshot.setRolesAtHq(rolesAtHq);
            snapshot.setRolesAtBase(rolesAtBase);
            snapshot.setRolesAtOther(rolesAtOther);
            snapshot.setCapturedAt(checkedAt);
            snapshot.setSourceExpiresAt(sourceExpiresAt);
            snapshot.setSourceExpiryEstimated(sourceExpiryEstimated);
            snapshot.setCreateUser(1L);
            snapshot.setDeleted(0L);
            roleSnapshotMapper.insert(snapshot);
        } else {
            previous.setSourceExpiresAt(sourceExpiresAt);
            previous.setSourceExpiryEstimated(sourceExpiryEstimated);
            roleSnapshotMapper.updateById(previous);
        }

        tokenService.markVerified(tenantId, userId, authorization.getId(), checkedAt);
        derivedIdentityService.synchronize(tenantId, userId);
        PermissionState afterState = permissionState(tenantId, userId, ceo, roles, rolesAtHq, rolesAtBase, rolesAtOther);
        audit(tenantId, userId, character.getId(), authorization.getId(), EveAuthAuditResult.SUCCESS, changed
            ? "ROLES_CHANGED"
            : "ROLES_UNCHANGED");
        LocalDateTime lastChangedAt = changed ? checkedAt : previous.getCapturedAt();
        return new EvePermissionRefreshResp(EvePermissionRefreshStatus.REFRESHED, requestedAt, checkedAt, sourceExpiresAt, sourceExpiryEstimated, lastChangedAt, null, List
            .of(), null, difference(afterState.gameRoles(), beforeState.gameRoles()), difference(beforeState
                .gameRoles(), afterState.gameRoles()), identityChange(beforeState.derivedIdentity(), afterState
                    .derivedIdentity()), capabilityChanges(beforeState.capabilities(), afterState.capabilities()));
    }

    /** 离团或换军团时停用成员身份并收回军团权限，本站账号仍可登录。 */
    private void invalidateMembership(Long tenantId,
                                      Long userId,
                                      EveCharacterDO character,
                                      EveAuthorizationDO authorization) {
        LocalDateTime now = LocalDateTime.now();
        character.setStatus(EveCharacterStatus.LEFT_CORPORATION);
        character.setLastVerifiedAt(now);
        characterMapper.updateById(character);
        EveCorporationMemberDO member = memberMapper.selectCurrent(tenantId, userId, character.getId());
        if (member != null) {
            member.setStatus(EveCorporationMemberStatus.LEFT);
            member.setLeftAt(now);
            member.setLastVerifiedAt(now);
            memberMapper.updateById(member);
        }
        tokenService.markReauthorizationRequired(tenantId, userId, authorization.getId(), OAuthFailureCode.PERMANENT);
        derivedIdentityService.synchronize(tenantId, userId);
    }

    /** 读取指定的有效角色绑定。 */
    private EveCharacterDO character(Long tenantId, Long userId, Long characterRefId) {
        return characterMapper.selectActiveByUser(tenantId, userId)
            .stream()
            .filter(character -> Objects.equals(tenantId, character.getTenantId()) && Objects.equals(userId, character
                .getUserId()) && Objects.equals(characterRefId, character.getId()))
            .findFirst()
            .orElse(null);
    }

    /** 读取当前主角色。 */
    private EveCharacterDO primaryCharacter(Long tenantId, Long userId) {
        List<EveCharacterDO> characters = characterMapper.selectActiveByUser(tenantId, userId);
        return characters.stream()
            .filter(character -> Objects.equals(tenantId, character.getTenantId()) && Objects.equals(userId, character
                .getUserId()))
            .findFirst()
            .orElse(null);
    }

    /** 读取角色最新授权记录。 */
    private EveAuthorizationDO latestAuthorization(Long tenantId, Long userId, Long characterRefId) {
        List<EveAuthorizationDO> authorizations = authorizationMapper
            .selectByUserCharacter(tenantId, userId, characterRefId);
        return authorizations.stream()
            .filter(authorization -> Objects.equals(tenantId, authorization.getTenantId()) && Objects
                .equals(userId, authorization.getUserId()) && Objects.equals(characterRefId, authorization
                    .getCharacterRefId()))
            .findFirst()
            .orElse(null);
    }

    /** 按归属读取后台指定授权并校验其角色绑定。 */
    private EveAuthorizationDO ownedAuthorization(Long tenantId,
                                                  Long userId,
                                                  Long characterRefId,
                                                  Long authorizationId) {
        EveAuthorizationDO authorization = authorizationMapper.selectOwnedById(tenantId, userId, authorizationId);
        return authorization != null && Objects.equals(characterRefId, authorization.getCharacterRefId())
            ? authorization
            : null;
    }

    /** 将上游可空数组归一为空列表并去重。 */
    private static List<String> safeList(List<String> values) {
        return values == null
            ? List.of()
            : values.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
    }

    /** 判断上游失败是否只能通过重新授权恢复。 */
    private static boolean isPermanent(OAuthFailureCode code) {
        return code == OAuthFailureCode.INVALID_GRANT || code == OAuthFailureCode.UNAUTHORIZED || code == OAuthFailureCode.FORBIDDEN || code == OAuthFailureCode.PERMANENT;
    }

    /** 写入只包含固定分类的脱敏审计。 */
    private void audit(Long tenantId,
                       Long userId,
                       Long characterRefId,
                       Long authorizationId,
                       EveAuthAuditResult result,
                       String detail) {
        EveAuthAuditDO audit = new EveAuthAuditDO();
        audit.setTenantId(tenantId);
        audit.setUserId(userId);
        audit.setCharacterRefId(characterRefId);
        audit.setAuthorizationId(authorizationId);
        audit.setEventType(EveAuthAuditEventType.ROLES_REFRESHED);
        audit.setResult(result);
        audit.setDetail(detail);
        audit.setOccurredAt(LocalDateTime.now());
        audit.setCreateUser(1L);
        audit.setDeleted(0L);
        auditMapper.insert(audit);
    }

    /** 锁竞争时返回与冷却相同的安全结果。 */
    private EvePermissionRefreshResp cooldownResponse(Long tenantId, Long userId, LocalDateTime requestedAt) {
        return responseFromLatest(tenantId, userId, EvePermissionRefreshStatus.COOLDOWN, requestedAt, requestedAt
            .plus(properties.getPermissionRefresh().getCooldown()), List.of(), null);
    }

    /** 使用最近快照补充未请求上游时的时间信息。 */
    private EvePermissionRefreshResp responseFromLatest(Long tenantId,
                                                        Long userId,
                                                        EvePermissionRefreshStatus status,
                                                        LocalDateTime requestedAt,
                                                        LocalDateTime nextRefresh,
                                                        List<String> missingScopes,
                                                        String reauthorizationPath) {
        EveCharacterDO character = primaryCharacter(tenantId, userId);
        EveCharacterRoleSnapshotDO latest = character == null
            ? null
            : roleSnapshotMapper.selectLatest(tenantId, character.getId());
        return new EvePermissionRefreshResp(status, requestedAt, character == null
            ? null
            : character.getLastVerifiedAt(), latest == null
                ? null
                : latest.getSourceExpiresAt(), latest != null && Boolean.TRUE.equals(latest
                    .getSourceExpiryEstimated()), latest == null
                        ? null
                        : latest.getCapturedAt(), nextRefresh, missingScopes, reauthorizationPath, List.of(), List
                            .of(), null, List.of());
    }

    /** 创建无角色上下文的安全结果。 */
    private static EvePermissionRefreshResp emptyResponse(EvePermissionRefreshStatus status,
                                                          LocalDateTime requestedAt) {
        return new EvePermissionRefreshResp(status, requestedAt, null, null, false, null, null, List.of(), null, List
            .of(), List.of(), null, List.of());
    }

    /** 写入统一的下一建议刷新时间。 */
    private static EvePermissionRefreshResp withNextRefresh(EvePermissionRefreshResp response,
                                                            LocalDateTime nextRefresh) {
        return new EvePermissionRefreshResp(response.status(), response.requestedAt(), response
            .upstreamCheckedAt(), response.sourceExpiresAt(), response.sourceExpiryEstimated(), response
                .lastRoleChangedAt(), nextRefresh, response.missingScopes(), response.reauthorizationPath(), response
                    .addedGameRoles(), response.removedGameRoles(), response.derivedIdentityChange(), response
                        .capabilityChanges());
    }

    /** 读取刷新前已持久化的游戏事实及当前站内权限状态。 */
    private PermissionState permissionState(Long tenantId, Long userId, EveCharacterRoleSnapshotDO snapshot) {
        if (snapshot == null) {
            return permissionState(tenantId, userId, false, List.of(), List.of(), List.of(), List.of());
        }
        return permissionState(tenantId, userId, Boolean.TRUE.equals(snapshot.getIsCeo()), safeList(snapshot
            .getRoles()), safeList(snapshot.getRolesAtHq()), safeList(snapshot.getRolesAtBase()), safeList(snapshot
                .getRolesAtOther()));
    }

    /** 基于指定游戏事实与当前站内角色构建可比较的权限状态。 */
    private PermissionState permissionState(Long tenantId,
                                            Long userId,
                                            boolean ceo,
                                            List<String> roles,
                                            List<String> rolesAtHq,
                                            List<String> rolesAtBase,
                                            List<String> rolesAtOther) {
        if (userId == null) {
            return new PermissionState(gameRoles(ceo, roles, rolesAtHq, rolesAtBase, rolesAtOther), null, Map.of());
        }
        List<EveSiteRoleDTO> siteRoles = roleApi.listEveSiteRoles(tenantId, userId);
        Set<String> permissions = siteRoles.stream()
            .flatMap(role -> role.permissions().stream())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<String, EveCapabilityStatus> capabilities = new LinkedHashMap<>();
        capabilityPolicy.evaluateAll(tenantId, permissions)
            .forEach(capability -> capabilities.put(capability.key(), capability.status()));
        return new PermissionState(gameRoles(ceo, roles, rolesAtHq, rolesAtBase, rolesAtOther), derivedIdentity(siteRoles), capabilities);
    }

    /** 将 CEO 与四类角色范围转换为稳定的结构化差异项。 */
    private static Set<EvePermissionRefreshResp.GameRoleChange> gameRoles(boolean ceo,
                                                                          List<String> roles,
                                                                          List<String> rolesAtHq,
                                                                          List<String> rolesAtBase,
                                                                          List<String> rolesAtOther) {
        Set<EvePermissionRefreshResp.GameRoleChange> result = new LinkedHashSet<>();
        if (ceo) {
            result.add(new EvePermissionRefreshResp.GameRoleChange("ceo", "CEO"));
        }
        addGameRoles(result, "roles", roles);
        addGameRoles(result, "roles_at_hq", rolesAtHq);
        addGameRoles(result, "roles_at_base", rolesAtBase);
        addGameRoles(result, "roles_at_other", rolesAtOther);
        return Set.copyOf(result);
    }

    /** 追加单个范围的游戏角色。 */
    private static void addGameRoles(Set<EvePermissionRefreshResp.GameRoleChange> target,
                                     String sourceScope,
                                     List<String> roles) {
        roles.forEach(role -> target.add(new EvePermissionRefreshResp.GameRoleChange(sourceScope, role)));
    }

    /** 计算有序集合差异。 */
    private static List<EvePermissionRefreshResp.GameRoleChange> difference(Set<EvePermissionRefreshResp.GameRoleChange> left,
                                                                            Set<EvePermissionRefreshResp.GameRoleChange> right) {
        return left.stream().filter(item -> !right.contains(item)).toList();
    }

    /** 解析站内派生身份，与前端展示优先级保持一致。 */
    private static String derivedIdentity(List<EveSiteRoleDTO> roles) {
        Set<String> codes = roles.stream().map(EveSiteRoleDTO::code).collect(java.util.stream.Collectors.toSet());
        if (codes.contains("corp_owner")) {
            return "OWNER";
        }
        if (codes.contains("corp_admin")) {
            return "ADMIN";
        }
        return codes.contains("corp_member") ? "MEMBER" : null;
    }

    /** 仅在派生身份真正变化时返回差异。 */
    private static EvePermissionRefreshResp.DerivedIdentityChange identityChange(String before, String after) {
        return Objects.equals(before, after) ? null : new EvePermissionRefreshResp.DerivedIdentityChange(before, after);
    }

    /** 计算刷新前后模块能力状态变化。 */
    private static List<EvePermissionRefreshResp.CapabilityChange> capabilityChanges(Map<String, EveCapabilityStatus> before,
                                                                                     Map<String, EveCapabilityStatus> after) {
        Set<String> keys = new LinkedHashSet<>(before.keySet());
        keys.addAll(after.keySet());
        return keys.stream()
            .filter(key -> !Objects.equals(before.get(key), after.get(key)))
            .map(key -> new EvePermissionRefreshResp.CapabilityChange(key, before.get(key), after.get(key)))
            .toList();
    }

    /** 用于计算刷新前后差异的瞬时状态。 */
    private record PermissionState(Set<EvePermissionRefreshResp.GameRoleChange> gameRoles, String derivedIdentity,
                                   Map<String, EveCapabilityStatus> capabilities) {
    }
}
