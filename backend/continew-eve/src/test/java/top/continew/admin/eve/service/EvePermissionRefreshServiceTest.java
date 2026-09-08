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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.continew.admin.common.api.system.RoleApi;
import top.continew.admin.common.enums.DataScopeEnum;
import top.continew.admin.common.model.dto.EveSiteRoleDTO;
import top.continew.admin.eve.client.SerenityEsiClient;
import top.continew.admin.eve.client.SerenityEsiClientException;
import top.continew.admin.eve.client.OAuthFailureCode;
import top.continew.admin.eve.client.SerenityTokenClientException;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.mapper.EveAuthAuditMapper;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveCharacterRoleSnapshotMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.mapper.EveCorporationMemberMapper;
import top.continew.admin.eve.model.EvePermissionRefreshResp;
import top.continew.admin.eve.model.EveCapabilityResult;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveAuthAuditDO;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.entity.EveCorporationMemberDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.model.enums.EveCapability;
import top.continew.admin.eve.model.enums.EveCapabilityStatus;
import top.continew.admin.eve.model.enums.EvePermissionRefreshStatus;
import top.continew.admin.eve.model.serenity.SerenityCharacterResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationRolesResponse;
import top.continew.admin.eve.model.serenity.SerenityEsiResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 主动权限刷新状态机测试。
 *
 * @author zhaoyuqing
 */
class EvePermissionRefreshServiceTest {

    private EveCharacterMapper characterMapper;
    private EveCorporationMapper corporationMapper;
    private EveCorporationMemberMapper memberMapper;
    private EveAuthorizationMapper authorizationMapper;
    private EveCharacterRoleSnapshotMapper snapshotMapper;
    private EveAuthAuditMapper auditMapper;
    private SerenityEsiClient esiClient;
    private EveAuthorizationLifecycleService lifecycleService;
    private EveAuthorizationTokenService tokenService;
    private EveDerivedIdentityService derivedIdentityService;
    private EveCapabilityPolicy capabilityPolicy;
    private RoleApi roleApi;
    private RLock lock;
    private RBucket<LocalDateTime> cooldown;
    private EvePermissionRefreshService service;

    /** 初始化当前租户用户、分布式锁和基础身份事实。 */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws InterruptedException {
        characterMapper = mock(EveCharacterMapper.class);
        corporationMapper = mock(EveCorporationMapper.class);
        memberMapper = mock(EveCorporationMemberMapper.class);
        authorizationMapper = mock(EveAuthorizationMapper.class);
        snapshotMapper = mock(EveCharacterRoleSnapshotMapper.class);
        auditMapper = mock(EveAuthAuditMapper.class);
        esiClient = mock(SerenityEsiClient.class);
        lifecycleService = mock(EveAuthorizationLifecycleService.class);
        tokenService = mock(EveAuthorizationTokenService.class);
        derivedIdentityService = mock(EveDerivedIdentityService.class);
        capabilityPolicy = mock(EveCapabilityPolicy.class);
        roleApi = mock(RoleApi.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        lock = mock(RLock.class);
        cooldown = mock(RBucket.class);
        when(redissonClient.getLock("eve:serenity:permission-refresh:10:20")).thenReturn(lock);
        when(redissonClient.<LocalDateTime>getBucket("eve:serenity:permission-refresh-cooldown:10:20"))
            .thenReturn(cooldown);
        when(lock.tryLock(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        service = new EvePermissionRefreshService(characterMapper, corporationMapper, memberMapper, authorizationMapper, snapshotMapper, auditMapper, esiClient, lifecycleService, tokenService, derivedIdentityService, capabilityPolicy, roleApi, redissonClient, new SerenityProperties());
        when(roleApi.listEveSiteRoles(10L, 20L)).thenReturn(List.of());
        when(capabilityPolicy.evaluateAll(eq(10L), any())).thenReturn(List.of());
        stubBoundIdentity();
    }

    /** 普通成员升级为 Director 后应同步总监角色。 */
    @Test
    void shouldPromoteMemberToDirector() {
        stubUpstream(9001L, List.of("Director"), List.of("Station_Manager"), List.of("Accountant"), List.of("Auditor"));
        when(snapshotMapper.selectLatest(10L, 100L)).thenReturn(snapshot(false, List.of()));

        EvePermissionRefreshResp response = service.refresh(10L, 20L, false);

        assertThat(response.status()).isEqualTo(EvePermissionRefreshStatus.REFRESHED);
        verify(derivedIdentityService).synchronize(10L, 20L);
        ArgumentCaptor<EveCharacterRoleSnapshotDO> captor = ArgumentCaptor.forClass(EveCharacterRoleSnapshotDO.class);
        verify(snapshotMapper).insert(captor.capture());
        assertThat(captor.getValue().getRoles()).containsExactly("Director");
        assertThat(captor.getValue().getRolesAtHq()).containsExactly("Station_Manager");
        assertThat(captor.getValue().getRolesAtBase()).containsExactly("Accountant");
        assertThat(captor.getValue().getRolesAtOther()).containsExactly("Auditor");
    }

    /** 刷新应返回游戏角色、派生身份和模块能力的结构化差异。 */
    @Test
    void shouldDescribePermissionChanges() {
        stubUpstream(9001L, List.of("Director"), List.of(), List.of(), List.of());
        when(snapshotMapper.selectLatest(10L, 100L)).thenReturn(snapshot(false, List.of()));
        EveAuthorizationDO authorization = authorization();
        authorization.setScopes(List
            .of("esi-characters.read_corporation_roles.v1", "esi-assets.read_corporation_assets.v1"));
        when(authorizationMapper.selectByUserCharacter(10L, 20L, 100L)).thenReturn(List.of(authorization));
        when(lifecycleService.ensureAccessToken(authorization)).thenReturn("access-token");
        EveSiteRoleDTO memberRole = siteRole("corp_member", List.of("eve:assets:view"));
        EveSiteRoleDTO adminRole = siteRole("corp_admin", List.of("eve:assets:view"));
        when(roleApi.listEveSiteRoles(10L, 20L)).thenReturn(List.of(memberRole), List.of(memberRole), List
            .of(adminRole));
        when(capabilityPolicy.evaluateAll(eq(10L), any())).thenReturn(List.of(EveCapabilityResult
            .of(EveCapability.ASSETS, EveCapabilityStatus.MISSING_GAME_ROLE)), List.of(EveCapabilityResult
                .of(EveCapability.ASSETS, EveCapabilityStatus.AVAILABLE)));

        EvePermissionRefreshResp response = service.refresh(10L, 20L, false);

        assertThat(response.addedGameRoles())
            .containsExactly(new EvePermissionRefreshResp.GameRoleChange("roles", "Director"));
        assertThat(response.removedGameRoles()).isEmpty();
        assertThat(response.derivedIdentityChange())
            .isEqualTo(new EvePermissionRefreshResp.DerivedIdentityChange("MEMBER", "ADMIN"));
        assertThat(response.capabilityChanges())
            .containsExactly(new EvePermissionRefreshResp.CapabilityChange("assets", EveCapabilityStatus.MISSING_GAME_ROLE, EveCapabilityStatus.AVAILABLE));
    }

    /** Director 被撤权后应立即降为普通成员。 */
    @Test
    void shouldDemoteDirectorToMember() {
        stubUpstream(9001L, List.of(), List.of("Director"), List.of(), List.of());
        when(snapshotMapper.selectLatest(10L, 100L)).thenReturn(snapshot(false, List.of("Director")));

        service.refresh(10L, 20L, false);

        verify(derivedIdentityService).synchronize(10L, 20L);
    }

    /** 军团 CEO 变化到当前角色后应优先同步所有者身份。 */
    @Test
    void shouldPromoteCurrentCharacterWhenCeoChanges() {
        stubUpstream(8001L, List.of("Director"), List.of(), List.of(), List.of());
        when(snapshotMapper.selectLatest(10L, 100L)).thenReturn(snapshot(false, List.of("Director")));

        service.refresh(10L, 20L, false);

        verify(derivedIdentityService).synchronize(10L, 20L);
    }

    /** 连续检查无角色变化时沿用真实变化时间，仅延长原快照有效期。 */
    @Test
    void shouldPreserveLastRoleChangedAtWhenRolesRemainUnchanged() {
        stubUpstream(9001L, List.of("Director"), List.of(), List.of(), List.of());
        EveCharacterRoleSnapshotDO previous = snapshot(false, List.of("Director"));
        LocalDateTime lastChangedAt = previous.getCapturedAt();
        when(snapshotMapper.selectLatest(10L, 100L)).thenReturn(previous);

        EvePermissionRefreshResp first = service.refresh(10L, 20L, false);
        EvePermissionRefreshResp second = service.refresh(10L, 20L, false);

        assertThat(first.lastRoleChangedAt()).isEqualTo(lastChangedAt);
        assertThat(second.lastRoleChangedAt()).isEqualTo(lastChangedAt);
        verify(snapshotMapper, never()).insert(any(EveCharacterRoleSnapshotDO.class));
        verify(snapshotMapper, times(2)).updateById(previous);
    }

    /** 缺少角色读取 Scope 时不得伪造刷新或请求国服。 */
    @Test
    void shouldRequireReauthorizationWithoutRoleScope() {
        EveAuthorizationDO authorization = authorization();
        authorization.setScopes(List.of("esi-assets.read_corporation_assets.v1"));
        when(authorizationMapper.selectByUserCharacter(10L, 20L, 100L)).thenReturn(List.of(authorization));

        EvePermissionRefreshResp response = service.refresh(10L, 20L, false);

        assertThat(response.status()).isEqualTo(EvePermissionRefreshStatus.REAUTHORIZATION_REQUIRED);
        assertThat(response.missingScopes()).containsExactly("esi-characters.read_corporation_roles.v1");
        assertThat(response.reauthorizationPath()).isEqualTo("/eve/permissions/reauthorization/start");
        verify(lifecycleService, never()).ensureAccessToken(any());
        verify(esiClient, never()).getCharacter(anyLong());
    }

    /** 已获业务模块权限但缺少对应 Scope 时，应刷新身份事实并提示扩展授权。 */
    @Test
    void shouldRequireScopeExpansionForPermittedCapability() {
        stubUpstream(9001L, List.of("Director"), List.of(), List.of(), List.of());
        when(snapshotMapper.selectLatest(10L, 100L)).thenReturn(snapshot(false, List.of("Director")));
        when(roleApi.listEveSiteRoles(10L, 20L)).thenReturn(List.of(siteRole("asset_viewer", List
            .of("eve:assets:view"))));

        EvePermissionRefreshResp response = service.refresh(10L, 20L, false);

        assertThat(response.status()).isEqualTo(EvePermissionRefreshStatus.REAUTHORIZATION_REQUIRED);
        assertThat(response.missingScopes()).containsExactly("esi-assets.read_corporation_assets.v1");
        assertThat(response.reauthorizationPath()).isEqualTo("/eve/permissions/reauthorization/start");
        verify(tokenService).markVerified(eq(10L), eq(20L), eq(400L), any(LocalDateTime.class));
        verify(tokenService, never()).markReauthorizationRequired(any(), any(), any(), any());
    }

    /** 冷却期内重复请求不得触发任何上游调用。 */
    @Test
    void shouldReturnCooldownWithoutCallingUpstream() {
        when(cooldown.get()).thenReturn(LocalDateTime.now().plusMinutes(3));
        when(snapshotMapper.selectLatest(10L, 100L)).thenReturn(snapshot(false, List.of()));

        EvePermissionRefreshResp response = service.refresh(10L, 20L, false);

        assertThat(response.status()).isEqualTo(EvePermissionRefreshStatus.COOLDOWN);
        verify(lifecycleService, never()).ensureAccessToken(any());
    }

    /** 并发锁未获取时应快速返回且不得重复请求国服。 */
    @Test
    void shouldAvoidConcurrentUpstreamRefresh() throws InterruptedException {
        when(lock.tryLock(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(false);

        EvePermissionRefreshResp response = service.refresh(10L, 20L, false);

        assertThat(response.status()).isEqualTo(EvePermissionRefreshStatus.COOLDOWN);
        verify(lifecycleService, never()).ensureAccessToken(any());
        verify(esiClient, never()).getCharacter(anyLong());
    }

    /** 刷新成功时必须等事务提交后写冷却，并在事务结束后释放锁。 */
    @Test
    void shouldKeepLockAndCooldownAlignedWithTransactionCommit() {
        stubUpstream(9001L, List.of(), List.of(), List.of(), List.of());
        when(snapshotMapper.selectLatest(10L, 100L)).thenReturn(snapshot(false, List.of()));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.refresh(10L, 20L, false);

            verify(cooldown, never()).set(any(), any(java.time.Duration.class));
            verify(lock, never()).unlock();
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(cooldown).set(any(LocalDateTime.class), any(java.time.Duration.class));
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization
                    .afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            verify(lock, times(1)).unlock();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 刷新事务回滚时不得写入冷却，但仍必须在回滚完成后释放锁。 */
    @Test
    void shouldNotWriteCooldownWhenTransactionRollsBack() {
        stubUpstream(9001L, List.of(), List.of(), List.of(), List.of());
        when(snapshotMapper.selectLatest(10L, 100L)).thenReturn(snapshot(false, List.of()));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.refresh(10L, 20L, false);

            TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization
                    .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(cooldown, never()).set(any(), any(java.time.Duration.class));
            verify(lock, times(1)).unlock();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 永久令牌刷新失败应返回重新授权状态且不得继续调用 ESI。 */
    @Test
    void shouldRequireReauthorizationAfterPermanentTokenFailure() {
        when(lifecycleService.ensureAccessToken(any()))
            .thenThrow(new SerenityTokenClientException(OAuthFailureCode.INVALID_GRANT));

        EvePermissionRefreshResp response = service.refresh(10L, 20L, false);

        assertThat(response.status()).isEqualTo(EvePermissionRefreshStatus.REAUTHORIZATION_REQUIRED);
        assertThat(response.reauthorizationPath()).isEqualTo("/eve/permissions/reauthorization/start");
        verify(esiClient, never()).getCharacter(anyLong());
    }

    /** Token 上游临时失败应返回可重试状态且不永久停用授权。 */
    @Test
    void shouldReturnRetryableStatusAfterTransientTokenFailure() {
        when(lifecycleService.ensureAccessToken(any()))
            .thenThrow(new SerenityTokenClientException(OAuthFailureCode.TRANSIENT));

        EvePermissionRefreshResp response = service.refresh(10L, 20L, false);

        assertThat(response.status()).isEqualTo(EvePermissionRefreshStatus.UPSTREAM_UNAVAILABLE);
        assertThat(response.reauthorizationPath()).isNull();
        verify(esiClient, never()).getCharacter(anyLong());
        verify(tokenService, never()).markReauthorizationRequired(any(), any(), any(), any());
        verify(derivedIdentityService).synchronize(10L, 20L);
    }

    /** ESI 临时失败应落审计、保持授权可重试，并允许后续刷新成功。 */
    @Test
    void shouldRetrySuccessfullyAfterTransientEsiFailure() {
        when(esiClient.getCharacter(8001L)).thenThrow(new SerenityEsiClientException(OAuthFailureCode.TRANSIENT))
            .thenReturn(new SerenityCharacterResponse("Pilot", 9001L));
        when(esiClient.getCorporation(9001L))
            .thenReturn(new SerenityCorporationResponse("Corp", "CP", 9001L, null, 10, BigDecimal.ZERO));
        when(esiClient.getCorporationRolesWithMetadata(8001L, "access-token"))
            .thenReturn(new SerenityEsiResponse<>(new SerenityCorporationRolesResponse(List.of(), List.of(), List
                .of(), List.of()), LocalDateTime.now(ZoneOffset.UTC).plusMinutes(30), null));
        when(snapshotMapper.selectLatest(10L, 100L)).thenReturn(snapshot(false, List.of()));

        EvePermissionRefreshResp failed = service.refresh(10L, 20L, false);
        EvePermissionRefreshResp retried = service.refresh(10L, 20L, false);

        assertThat(failed.status()).isEqualTo(EvePermissionRefreshStatus.UPSTREAM_UNAVAILABLE);
        assertThat(retried.status()).isEqualTo(EvePermissionRefreshStatus.REFRESHED);
        verify(tokenService).recordFailure(10L, 20L, 400L, OAuthFailureCode.TRANSIENT);
        verify(auditMapper, org.mockito.Mockito.atLeastOnce()).insert(any(EveAuthAuditDO.class));
        verify(derivedIdentityService, times(2)).synchronize(10L, 20L);
    }

    /** 离团或换军团必须清除派生角色并强制注销会话。 */
    @Test
    void shouldInvalidateMembershipAndLogoutWhenCorporationChanges() {
        when(esiClient.getCharacter(8001L)).thenReturn(new SerenityCharacterResponse("Pilot", 9999L));
        EvePermissionRefreshResp response = service.refresh(10L, 20L, false);

        assertThat(response.status()).isEqualTo(EvePermissionRefreshStatus.MEMBERSHIP_INVALID);
        verify(tokenService).markReauthorizationRequired(10L, 20L, 400L, OAuthFailureCode.PERMANENT);
    }

    /** 建立站内已绑定角色、军团、成员和授权。 */
    private void stubBoundIdentity() {
        EveCharacterDO character = new EveCharacterDO();
        character.setId(100L);
        character.setTenantId(10L);
        character.setUserId(20L);
        character.setServer("serenity");
        character.setCharacterId(8001L);
        character.setCorporationId(9001L);
        when(characterMapper.selectActiveByUser(10L, 20L)).thenReturn(List.of(character));

        EveCorporationDO corporation = new EveCorporationDO();
        corporation.setId(200L);
        corporation.setTenantId(10L);
        corporation.setCorporationId(9001L);
        when(corporationMapper.selectByTenantAndCorporationId(10L, 9001L)).thenReturn(corporation);

        EveCorporationMemberDO member = new EveCorporationMemberDO();
        member.setId(300L);
        when(memberMapper.selectCurrent(10L, 20L, 100L)).thenReturn(member);

        EveAuthorizationDO authorization = authorization();
        when(authorizationMapper.selectByUserCharacter(10L, 20L, 100L)).thenReturn(List.of(authorization));
        when(lifecycleService.ensureAccessToken(authorization)).thenReturn("access-token");
    }

    /** 建立公开角色、军团与四范围角色响应。 */
    private void stubUpstream(Long ceoId,
                              List<String> roles,
                              List<String> rolesAtHq,
                              List<String> rolesAtBase,
                              List<String> rolesAtOther) {
        when(esiClient.getCharacter(8001L)).thenReturn(new SerenityCharacterResponse("Pilot", 9001L));
        when(esiClient.getCorporation(9001L))
            .thenReturn(new SerenityCorporationResponse("Corp", "CP", ceoId, null, 10, BigDecimal.ZERO));
        when(esiClient.getCorporationRolesWithMetadata(8001L, "access-token"))
            .thenReturn(new SerenityEsiResponse<>(new SerenityCorporationRolesResponse(roles, rolesAtHq, rolesAtBase, rolesAtOther), LocalDateTime
                .now(ZoneOffset.UTC)
                .plusMinutes(30), "role-etag"));
    }

    /** 创建有效角色读取授权。 */
    private static EveAuthorizationDO authorization() {
        EveAuthorizationDO authorization = new EveAuthorizationDO();
        authorization.setId(400L);
        authorization.setTenantId(10L);
        authorization.setUserId(20L);
        authorization.setCharacterRefId(100L);
        authorization.setStatus(EveAuthorizationStatus.ACTIVE);
        authorization.setScopes(List.of("esi-characters.read_corporation_roles.v1"));
        authorization.setAccessToken("access-token");
        authorization.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10));
        return authorization;
    }

    /** 创建上一版角色快照。 */
    private static EveCharacterRoleSnapshotDO snapshot(boolean ceo, List<String> roles) {
        EveCharacterRoleSnapshotDO snapshot = new EveCharacterRoleSnapshotDO();
        snapshot.setIsCeo(ceo);
        snapshot.setRoles(roles);
        snapshot.setRolesAtHq(List.of());
        snapshot.setRolesAtBase(List.of());
        snapshot.setRolesAtOther(List.of());
        snapshot.setCapturedAt(LocalDateTime.now().minusHours(1));
        snapshot.setSourceExpiresAt(LocalDateTime.now());
        return snapshot;
    }

    /** 构造用于权限差异计算的站内角色。 */
    private static EveSiteRoleDTO siteRole(String code, List<String> permissions) {
        return new EveSiteRoleDTO(1L, code, code, code, DataScopeEnum.ALL, true, permissions);
    }
}
