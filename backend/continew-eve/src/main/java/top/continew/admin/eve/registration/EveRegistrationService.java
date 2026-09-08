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

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import top.continew.admin.common.api.system.EveAccountApi;
import top.continew.admin.common.api.system.EveDerivedRoleApi;
import top.continew.admin.common.api.tenant.TenantApi;
import top.continew.admin.common.model.dto.EveAccountCreateDTO;
import top.continew.admin.common.model.dto.EveLoginSessionDTO;
import top.continew.admin.common.model.dto.EveTenantCreateDTO;
import top.continew.admin.common.model.dto.EveTenantCreateResultDTO;
import top.continew.admin.common.enums.EveDerivedIdentity;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveCharacterRoleSnapshotMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.mapper.EveCorporationMemberMapper;
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
import top.continew.admin.eve.security.OAuthSecurityUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 一次性消费注册凭证并原子完成军团认领或成员加入。
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveRegistrationService {

    private static final String CLAIM_LOCK_PREFIX = "eve:serenity:corporation-claim:";
    private static final String CREDENTIAL_LOCK_PREFIX = "eve:serenity:registration-credential:";

    private final RegistrationCredentialStore credentialStore;
    private final SerenityRegistrationFactVerifier factVerifier;
    private final RedissonClient redissonClient;
    private final TenantApi tenantApi;
    private final EveAccountApi accountApi;
    private final EveDerivedRoleApi derivedRoleApi;
    private final EveCorporationMapper corporationMapper;
    private final EveCharacterMapper characterMapper;
    private final EveCorporationMemberMapper memberMapper;
    private final EveAuthorizationMapper authorizationMapper;
    private final EveCharacterRoleSnapshotMapper roleSnapshotMapper;
    private final SerenityProperties properties;
    private final TransactionTemplate transactionTemplate;

    /**
     * 激活本站账号；相同军团的并发请求由分布式锁和数据库唯一索引共同约束。
     *
     * @param command 激活命令
     * @return 已建立的本站会话
     */
    public EveRegistrationCompleteResult complete(EveRegistrationCompleteCommand command) {
        validateCommand(command);
        RLock credentialLock = redissonClient.getLock(CREDENTIAL_LOCK_PREFIX + OAuthSecurityUtils
            .sha256Base64Url(command.credential()));
        boolean credentialLocked = false;
        try {
            credentialLocked = credentialLock.tryLock(5, TimeUnit.SECONDS);
            if (!credentialLocked) {
                throw new IllegalStateException("注册凭证正在处理中，请稍后重试");
            }
            RegistrationCredentialSnapshot snapshot = credentialStore.find(command.credential(), command
                .browserBindingDigest()).orElseThrow(() -> new IllegalStateException("注册凭证已失效"));
            if (snapshot.activation() != null && isCommittedActivation(snapshot)) {
                return deliverSession(command, snapshot.activation());
            }
            return completeWithCredentialLock(command, snapshot.identity());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("注册凭证正在处理中，请稍后重试", e);
        } finally {
            if (credentialLocked && credentialLock.isHeldByCurrentThread()) {
                credentialLock.unlock();
            }
        }
    }

    /** 在凭证级锁保护下完成军团串行写入，并仅在事务成功后消费凭证。 */
    private EveRegistrationCompleteResult completeWithCredentialLock(EveRegistrationCompleteCommand command,
                                                                     VerifiedRegistrationIdentity identity) throws InterruptedException {
        RLock corporationLock = redissonClient.getLock(CLAIM_LOCK_PREFIX + OAuthSecurityUtils.sha256Base64Url(identity
            .server() + ":" + identity.corporationId()));
        boolean corporationLocked = false;
        try {
            corporationLocked = corporationLock.tryLock(5, TimeUnit.SECONDS);
            if (!corporationLocked) {
                throw new IllegalStateException("军团认领处理中，请稍后重试");
            }
            VerifiedRegistrationIdentity currentIdentity;
            try {
                currentIdentity = factVerifier.refresh(identity);
            } catch (RegistrationQualificationChangedException e) {
                invalidateChangedCredential(command);
                throw e;
            }
            RegistrationActivation activation = transactionTemplate.execute(status -> {
                RegistrationActivation created = completeLocked(command, currentIdentity);
                if (!credentialStore.markActivated(command.credential(), command.browserBindingDigest(), created)) {
                    throw new IllegalStateException("注册结果暂存失败，请保留当前页面后重试");
                }
                return created;
            });
            if (activation == null) {
                throw new IllegalStateException("EVE 账号激活事务未完成");
            }
            return deliverSession(command, activation);
        } finally {
            if (corporationLocked && corporationLock.isHeldByCurrentThread()) {
                corporationLock.unlock();
            }
        }
    }

    /** 在已持有军团锁时执行数据库写入。 */
    private RegistrationActivation completeLocked(EveRegistrationCompleteCommand command,
                                                  VerifiedRegistrationIdentity identity) {
        if (identity.refreshToken() == null || identity.refreshToken().isBlank()) {
            throw new IllegalStateException("国服授权未返回可续期令牌，请重新授权");
        }
        if (characterMapper.selectByExternalId(identity.server(), identity.characterId()) != null) {
            throw new IllegalStateException("该 EVE 角色已绑定其他本站账号");
        }
        EveCorporationDO corporation = corporationMapper.selectByExternalId(identity.server(), identity
            .corporationId());
        boolean tenantCreated = corporation == null;
        Long tenantId;
        Long userId;
        if (tenantCreated) {
            if (!identity.ceo() && !identity.director()) {
                throw new IllegalStateException("军团尚未入驻，请联系 CEO 或总监");
            }
            EveTenantCreateResultDTO created = tenantApi.createEveTenant(new EveTenantCreateDTO(identity
                .corporationName(), command.username(), command.encryptedPassword(), identity
                    .characterName(), properties.getRegistration().getTenantPackageId()));
            tenantId = created.tenantId();
            userId = created.userId();
            corporation = insertCorporation(tenantId, identity);
        } else {
            if (corporation.getTenantId() == null || !EveCorporationStatus.ACTIVE.equals(corporation.getStatus())) {
                throw new IllegalStateException("军团租户当前不可加入");
            }
            tenantId = corporation.getTenantId();
            userId = accountApi.createMember(new EveAccountCreateDTO(tenantId, command.username(), command
                .encryptedPassword(), identity.characterName()));
        }
        persistIdentity(tenantId, userId, corporation, identity);
        derivedRoleApi.synchronize(tenantId, userId, EveDerivedIdentity.from(identity.ceo(), identity.director()));
        return new RegistrationActivation(tenantId, userId, tenantCreated);
    }

    /** 会话建立成功后再原子消费凭证，消费未确认时不向调用方交付令牌。 */
    private EveRegistrationCompleteResult deliverSession(EveRegistrationCompleteCommand command,
                                                         RegistrationActivation activation) {
        EveLoginSessionDTO session = accountApi.openSession(activation.tenantId(), activation.userId(), command
            .clientId());
        RegistrationCredentialSnapshot consumed = credentialStore.consume(command.credential(), command
            .browserBindingDigest()).orElseThrow(() -> new IllegalStateException("注册会话交付未确认，请重试"));
        if (!activation.equals(consumed.activation())) {
            throw new IllegalStateException("注册会话交付状态不一致，请重新授权");
        }
        return new EveRegistrationCompleteResult(session.token(), activation.tenantId(), activation.userId(), activation
            .tenantCreated());
    }

    /** 资格变化时必须确认原浏览器绑定凭证已经失效。 */
    private void invalidateChangedCredential(EveRegistrationCompleteCommand command) {
        if (credentialStore.consume(command.credential(), command.browserBindingDigest()).isEmpty()) {
            throw new IllegalStateException("注册凭证失效确认失败，请重新授权");
        }
    }

    /** 确认 Redis 激活结果已真实提交，拒绝使用数据库回滚后残留的跨系统状态。 */
    private boolean isCommittedActivation(RegistrationCredentialSnapshot snapshot) {
        EveCharacterDO character = characterMapper.selectByExternalId(snapshot.identity().server(), snapshot.identity()
            .characterId());
        RegistrationActivation activation = snapshot.activation();
        return character != null && Objects.equals(character.getTenantId(), activation.tenantId()) && Objects
            .equals(character.getUserId(), activation.userId());
    }

    /** 写入军团唯一租户绑定。 */
    private EveCorporationDO insertCorporation(Long tenantId, VerifiedRegistrationIdentity identity) {
        EveCorporationDO corporation = new EveCorporationDO();
        corporation.setTenantId(tenantId);
        corporation.setServer(identity.server());
        corporation.setCorporationId(identity.corporationId());
        corporation.setName(identity.corporationName());
        corporation.setTicker(identity.corporationTicker());
        corporation.setCeoCharacterId(identity.ceoCharacterId());
        corporation.setAllianceId(identity.allianceId());
        corporation.setMemberCount(identity.memberCount());
        corporation.setTaxRate(identity.taxRate());
        corporation.setStatus(EveCorporationStatus.ACTIVE);
        corporation.setLastSyncedAt(LocalDateTime.now());
        prepareInsert(corporation);
        corporationMapper.insert(corporation);
        return corporation;
    }

    /** 原子保存角色、成员、授权和四范围角色快照。 */
    private void persistIdentity(Long tenantId,
                                 Long userId,
                                 EveCorporationDO corporation,
                                 VerifiedRegistrationIdentity identity) {
        LocalDateTime now = LocalDateTime.now();
        EveCharacterDO character = new EveCharacterDO();
        character.setTenantId(tenantId);
        character.setUserId(userId);
        character.setServer(identity.server());
        character.setCharacterId(identity.characterId());
        character.setCorporationId(identity.corporationId());
        character.setOwnerHash(identity.ownerHash());
        character.setName(identity.characterName());
        character.setStatus(EveCharacterStatus.ACTIVE);
        character.setIsPrimary(true);
        character.setJoinedAt(now);
        character.setLastVerifiedAt(now);
        prepareInsert(character);
        characterMapper.insert(character);

        EveCorporationMemberDO member = new EveCorporationMemberDO();
        member.setTenantId(tenantId);
        member.setCorporationRefId(corporation.getId());
        member.setCharacterRefId(character.getId());
        member.setUserId(userId);
        member.setStatus(EveCorporationMemberStatus.ACTIVE);
        member.setJoinedAt(now);
        member.setLastVerifiedAt(now);
        prepareInsert(member);
        memberMapper.insert(member);

        EveAuthorizationDO authorization = new EveAuthorizationDO();
        authorization.setTenantId(tenantId);
        authorization.setCharacterRefId(character.getId());
        authorization.setUserId(userId);
        authorization.setServer(identity.server());
        authorization.setScopes(identity.scopes());
        authorization.setAccessToken(identity.accessToken());
        authorization.setRefreshToken(identity.refreshToken());
        authorization.setTokenType(identity.tokenType());
        authorization.setExpiresAt(LocalDateTime.ofInstant(identity.expiresAt(), ZoneOffset.UTC));
        authorization.setStatus(EveAuthorizationStatus.ACTIVE);
        authorization.setFailureCount(0);
        authorization.setLastVerificationStatus(EveAuthorizationVerificationStatus.VALID);
        authorization.setLastVerifiedAt(now);
        prepareInsert(authorization);
        authorizationMapper.insert(authorization);

        EveCharacterRoleSnapshotDO snapshot = new EveCharacterRoleSnapshotDO();
        snapshot.setTenantId(tenantId);
        snapshot.setCharacterRefId(character.getId());
        snapshot.setServer(identity.server());
        snapshot.setCharacterId(identity.characterId());
        snapshot.setIsCeo(identity.ceo());
        snapshot.setRoles(identity.roles());
        snapshot.setRolesAtHq(identity.rolesAtHq());
        snapshot.setRolesAtBase(identity.rolesAtBase());
        snapshot.setRolesAtOther(identity.rolesAtOther());
        snapshot.setCapturedAt(now);
        boolean sourceExpiryEstimated = identity.roleSourceExpiresAt() == null;
        snapshot.setSourceExpiresAt(sourceExpiryEstimated
            ? now.plus(properties.getPermissionRefresh().getRoleCacheTtl())
            : identity.roleSourceExpiresAt());
        snapshot.setSourceExpiryEstimated(sourceExpiryEstimated);
        prepareInsert(snapshot);
        roleSnapshotMapper.insert(snapshot);
    }

    /** 填充未登录注册写入所需的系统审计字段。 */
    private static void prepareInsert(top.continew.admin.common.base.model.entity.BaseDO entity) {
        entity.setCreateUser(1L);
        entity.setDeleted(0L);
    }

    /** 校验本站账号激活参数。 */
    private static void validateCommand(EveRegistrationCompleteCommand command) {
        if (command == null || isBlank(command.credential()) || isBlank(command.browserBindingDigest()) || command
            .username() == null || !isValidUsername(command.username()) || isBlank(command
                .encryptedPassword()) || isBlank(command.clientId())) {
            throw new IllegalArgumentException("本站账号激活参数无效");
        }
    }

    /** 允许游戏角色名、邮箱等常用登录账号，仅排除边界空格、控制字符及异常长度。 */
    private static boolean isValidUsername(String username) {
        return username.length() >= 2 && username.length() <= 64 && username.equals(username.trim()) && username.chars()
            .noneMatch(Character::isISOControl);
    }

    /** 判断文本是否为空。 */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
