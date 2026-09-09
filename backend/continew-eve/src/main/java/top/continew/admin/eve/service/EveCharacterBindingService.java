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
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.auth.OAuthTransaction;
import top.continew.admin.eve.auth.OAuthTransactionPurpose;
import top.continew.admin.eve.auth.OAuthTransactionStore;
import top.continew.admin.eve.auth.SerenityAuthorizationStart;
import top.continew.admin.eve.auth.SerenityAuthorizationStartService;
import top.continew.admin.eve.client.SerenityEsiClient;
import top.continew.admin.eve.client.SerenityTokenClient;
import top.continew.admin.eve.client.SerenityTokenResponse;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveCharacterRoleSnapshotMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.mapper.EveCorporationMemberMapper;
import top.continew.admin.eve.model.EveCharacterBindingResult;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.entity.EveCorporationMemberDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.model.enums.EveAuthorizationVerificationStatus;
import top.continew.admin.eve.model.enums.EveCharacterStatus;
import top.continew.admin.eve.model.enums.EveCorporationMemberStatus;
import top.continew.admin.eve.model.enums.EveCorporationStatus;
import top.continew.admin.eve.model.serenity.SerenityCharacterResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationRolesResponse;
import top.continew.admin.eve.security.SerenityCallback;
import top.continew.admin.eve.security.SerenityCallbackUrlParser;
import top.continew.admin.eve.security.SerenityJwtDecoderFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 为当前本站会话绑定同军团的 EVE 角色，并保存该角色独立的授权与权限快照。
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveCharacterBindingService {

    private static final Pattern CHARACTER_ID_SUFFIX = Pattern.compile("(?:^|:)([1-9][0-9]*)$");

    private final SerenityAuthorizationStartService authorizationStartService;
    private final OAuthTransactionStore transactionStore;
    private final SerenityCallbackUrlParser callbackParser;
    private final SerenityTokenClient tokenClient;
    private final SerenityJwtDecoderFactory jwtDecoderFactory;
    private final SerenityEsiClient esiClient;
    private final EveCharacterMapper characterMapper;
    private final EveCorporationMapper corporationMapper;
    private final EveCorporationMemberMapper memberMapper;
    private final EveAuthorizationMapper authorizationMapper;
    private final EveCharacterRoleSnapshotMapper roleSnapshotMapper;
    private final EveDerivedIdentityService derivedIdentityService;
    private final RedissonClient redissonClient;
    private final SerenityProperties properties;
    private final EveAuthorizationScopePolicy scopePolicy;

    /** 为当前会话创建绑定新角色的 OAuth 事务。 */
    public SerenityAuthorizationStart startCurrent(String browserBindingDigest) {
        UserContext context = UserContextHolder.getContext();
        return authorizationStartService.start(OAuthTransactionPurpose.BIND_CHARACTER, context.getTenantId(), context
            .getId(), null, null, browserBindingDigest, scopePolicy.plannedScopes());
    }

    /**
     * 消费一次性回调，校验当前会话、角色所属军团与全局唯一绑定后保存新角色。
     *
     * @param callbackUrl          完整固定回调地址
     * @param browserBindingDigest 当前浏览器绑定摘要
     * @return 新绑定角色的安全摘要
     */
    @Transactional(rollbackFor = Exception.class)
    public EveCharacterBindingResult bindCurrent(String callbackUrl, String browserBindingDigest) {
        UserContext context = UserContextHolder.getContext();
        SerenityCallback callback = callbackParser.parse(callbackUrl);
        String subject = context.getTenantId() + ":" + context.getId();
        RLock lock = redissonClient.getLock(EvePermissionRefreshService.USER_LOCK_PREFIX + subject);
        boolean locked = false;
        boolean unlockDeferred = false;
        try {
            locked = lock.tryLock(properties.getPermissionRefresh().getLockWait().toMillis(), TimeUnit.MILLISECONDS);
            if (!locked) {
                throw new IllegalStateException("EVE 角色绑定正在处理，请稍后重试");
            }
            unlockDeferred = deferUnlockUntilTransactionCompletion(lock);
            return bindLocked(context, callback, browserBindingDigest);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("EVE 角色绑定正在处理，请稍后重试", e);
        } finally {
            if (locked && !unlockDeferred && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 在租户用户级锁内消费回调并保存绑定，锁顺序与权限刷新保持一致。 */
    private EveCharacterBindingResult bindLocked(UserContext context,
                                                 SerenityCallback callback,
                                                 String browserBindingDigest) {
        OAuthTransaction transaction = transactionStore.consume(callback.state())
            .orElseThrow(() -> new IllegalStateException("EVE 角色绑定授权已失效，请重新发起"));
        validateTransaction(transaction, context, browserBindingDigest, callback);

        SerenityTokenResponse token = tokenClient.exchangeCode(callback.code(), transaction.getVerifier());
        Jwt jwt = jwtDecoderFactory.create().decode(token.accessToken());
        Long characterId = parseCharacterId(jwt.getSubject());
        List<String> scopes = readScopes(jwt.getClaim(properties.getSso().getScopeClaim()));
        if (!scopes.containsAll(transaction.getRequestedScopes())) {
            throw new IllegalStateException("国服授权未授予所需权限，请重新授权并勾选全部权限");
        }
        SerenityCharacterResponse characterFact = esiClient.getCharacter(characterId);
        if (characterFact.corporationId() == null || characterFact.corporationId() <= 0 || isBlank(characterFact
            .name())) {
            throw new IllegalStateException("未能读取有效的 EVE 角色资料，请重新授权");
        }
        EveCorporationDO corporation = corporationMapper.selectByTenantAndCorporationId(context
            .getTenantId(), characterFact.corporationId());
        if (corporation == null || !Objects.equals(context.getTenantId(), corporation
            .getTenantId()) || !EveCorporationStatus.ACTIVE.equals(corporation.getStatus())) {
            throw new IllegalStateException("该 EVE 角色不属于当前军团，不能绑定到此账号");
        }
        EveCharacterDO existing = characterMapper.selectByExternalId(properties.getEsi()
            .getDatasource()
            .getValue(), characterId);
        if (existing != null) {
            throw new IllegalStateException(Objects.equals(existing.getUserId(), context.getId())
                ? "该 EVE 角色已绑定到当前账号"
                : "该 EVE 角色已绑定其他本站账号");
        }
        SerenityCorporationResponse corporationFact = esiClient.getCorporation(characterFact.corporationId());
        SerenityCorporationRolesResponse roles = esiClient.getCorporationRoles(characterId, token.accessToken());
        boolean ceo = characterId.equals(corporationFact.ceoId());
        EveCharacterDO saved = persist(context, corporation, jwt
            .getClaimAsString("owner"), characterId, characterFact, roles, token, scopes, ceo);
        derivedIdentityService.synchronize(context.getTenantId(), context.getId());
        return new EveCharacterBindingResult(saved.getCharacterId(), saved.getName());
    }

    /** 将用户锁保持到事务提交或回滚完成，避免并发绑定观察到未提交的主角色状态。 */
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

    /** 校验回调事务只能被原登录用户、租户与浏览器消费。 */
    private static void validateTransaction(OAuthTransaction transaction,
                                            UserContext context,
                                            String browserBindingDigest,
                                            SerenityCallback callback) {
        boolean sameBrowser = browserBindingDigest != null && transaction
            .getBrowserBindingDigest() != null && MessageDigest.isEqual(transaction.getBrowserBindingDigest()
                .getBytes(StandardCharsets.US_ASCII), browserBindingDigest.getBytes(StandardCharsets.US_ASCII));
        if (transaction.getPurpose() != OAuthTransactionPurpose.BIND_CHARACTER || transaction
            .getExpiresAt() == null || transaction.getExpiresAt().isBefore(Instant.now()) || callback
                .hasError() || !sameBrowser || !Objects.equals(context.getId(), transaction
                    .getBoundUserId()) || !Objects.equals(context.getTenantId(), transaction.getBoundTenantId())) {
            throw new IllegalStateException("EVE 角色绑定授权校验失败，请重新发起");
        }
    }

    /** 将已核验的角色、成员关系、令牌与权限快照写入当前租户。 */
    private EveCharacterDO persist(UserContext context,
                                   EveCorporationDO corporation,
                                   String ownerHash,
                                   Long characterId,
                                   SerenityCharacterResponse characterFact,
                                   SerenityCorporationRolesResponse roles,
                                   SerenityTokenResponse token,
                                   List<String> scopes,
                                   boolean ceo) {
        if (isBlank(ownerHash)) {
            throw new IllegalStateException("国服角色所有者声明缺失，请重新授权");
        }
        if (token.refreshToken() == null || token.refreshToken().isBlank()) {
            throw new IllegalStateException("国服授权未返回可续期令牌，请重新授权");
        }
        LocalDateTime now = LocalDateTime.now();
        characterMapper.update(null, new UpdateWrapper<EveCharacterDO>().set("is_primary", false)
            .eq("tenant_id", context.getTenantId())
            .eq("user_id", context.getId()));
        EveCharacterDO character = new EveCharacterDO();
        character.setTenantId(context.getTenantId());
        character.setUserId(context.getId());
        character.setServer(properties.getEsi().getDatasource().getValue());
        character.setCharacterId(characterId);
        character.setCorporationId(characterFact.corporationId());
        character.setOwnerHash(ownerHash);
        character.setName(characterFact.name());
        character.setStatus(EveCharacterStatus.ACTIVE);
        character.setIsPrimary(true);
        character.setJoinedAt(now);
        character.setLastVerifiedAt(now);
        prepareInsert(character);
        characterMapper.insert(character);

        EveCorporationMemberDO member = new EveCorporationMemberDO();
        member.setTenantId(context.getTenantId());
        member.setCorporationRefId(corporation.getId());
        member.setCharacterRefId(character.getId());
        member.setUserId(context.getId());
        member.setStatus(EveCorporationMemberStatus.ACTIVE);
        member.setJoinedAt(now);
        member.setLastVerifiedAt(now);
        prepareInsert(member);
        memberMapper.insert(member);

        EveAuthorizationDO authorization = new EveAuthorizationDO();
        authorization.setTenantId(context.getTenantId());
        authorization.setCharacterRefId(character.getId());
        authorization.setUserId(context.getId());
        authorization.setServer(character.getServer());
        authorization.setScopes(scopes);
        authorization.setAccessToken(token.accessToken());
        authorization.setRefreshToken(token.refreshToken());
        authorization.setTokenType(token.tokenType());
        authorization.setExpiresAt(LocalDateTime.ofInstant(Instant.now()
            .plusSeconds(token.expiresIn()), ZoneOffset.UTC));
        authorization.setStatus(EveAuthorizationStatus.ACTIVE);
        authorization.setFailureCount(0);
        authorization.setLastVerificationStatus(EveAuthorizationVerificationStatus.VALID);
        authorization.setLastVerifiedAt(now);
        prepareInsert(authorization);
        authorizationMapper.insert(authorization);

        EveCharacterRoleSnapshotDO snapshot = new EveCharacterRoleSnapshotDO();
        snapshot.setTenantId(context.getTenantId());
        snapshot.setCharacterRefId(character.getId());
        snapshot.setServer(character.getServer());
        snapshot.setCharacterId(characterId);
        snapshot.setIsCeo(ceo);
        snapshot.setRoles(safeList(roles.roles()));
        snapshot.setRolesAtHq(safeList(roles.rolesAtHq()));
        snapshot.setRolesAtBase(safeList(roles.rolesAtBase()));
        snapshot.setRolesAtOther(safeList(roles.rolesAtOther()));
        snapshot.setCapturedAt(now);
        snapshot.setSourceExpiresAt(now.plus(properties.getPermissionRefresh().getRoleCacheTtl()));
        prepareInsert(snapshot);
        roleSnapshotMapper.insert(snapshot);
        return character;
    }

    /** 从已验签 subject 提取角色 ID。 */
    private static Long parseCharacterId(String subject) {
        Matcher matcher = CHARACTER_ID_SUFFIX.matcher(subject == null ? "" : subject);
        if (!matcher.find()) {
            throw new IllegalStateException("国服角色标识无效");
        }
        return Long.valueOf(matcher.group(1));
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

    /** 归一国服可空角色集合。 */
    private static List<String> safeList(List<String> values) {
        return values == null
            ? List.of()
            : values.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
    }

    /** 填充未登录或服务端写入所需审计字段。 */
    private static void prepareInsert(top.continew.admin.common.base.model.entity.BaseDO entity) {
        entity.setCreateUser(1L);
        entity.setDeleted(0L);
    }

    /** 判断文本是否为空。 */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
