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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import top.continew.admin.common.api.system.EveAccountApi;
import top.continew.admin.common.api.system.EveDerivedRoleApi;
import top.continew.admin.common.api.tenant.TenantApi;
import top.continew.admin.common.enums.EveDerivedIdentity;
import top.continew.admin.common.model.dto.EveLoginSessionDTO;
import top.continew.admin.common.model.dto.EveTenantCreateResultDTO;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveCharacterRoleSnapshotMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.mapper.EveCorporationMemberMapper;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.enums.EveCorporationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 本站账号激活与军团认领服务测试。
 *
 * @author zhaoyuqing
 */
class EveRegistrationServiceTest {

    private RegistrationCredentialStore credentialStore;
    private SerenityRegistrationFactVerifier factVerifier;
    private TenantApi tenantApi;
    private EveAccountApi accountApi;
    private EveDerivedRoleApi derivedRoleApi;
    private EveCorporationMapper corporationMapper;
    private EveCharacterMapper characterMapper;
    private EveCorporationMemberMapper memberMapper;
    private EveAuthorizationMapper authorizationMapper;
    private EveCharacterRoleSnapshotMapper snapshotMapper;
    private EveRegistrationService service;
    private RLock credentialLock;
    private RLock corporationLock;
    private TransactionTemplate transactionTemplate;

    /** 初始化军团锁和持久层测试替身。 */
    @BeforeEach
    void setUp() throws InterruptedException {
        credentialStore = mock(RegistrationCredentialStore.class);
        factVerifier = mock(SerenityRegistrationFactVerifier.class);
        tenantApi = mock(TenantApi.class);
        accountApi = mock(EveAccountApi.class);
        derivedRoleApi = mock(EveDerivedRoleApi.class);
        corporationMapper = mock(EveCorporationMapper.class);
        characterMapper = mock(EveCharacterMapper.class);
        memberMapper = mock(EveCorporationMemberMapper.class);
        authorizationMapper = mock(EveAuthorizationMapper.class);
        snapshotMapper = mock(EveCharacterRoleSnapshotMapper.class);
        transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(invocation -> ((TransactionCallback<?>)invocation.getArgument(0)).doInTransaction(null))
            .when(transactionTemplate)
            .execute(any());
        RedissonClient redisson = mock(RedissonClient.class);
        credentialLock = mock(RLock.class);
        corporationLock = mock(RLock.class);
        when(redisson.getLock(startsWith("eve:serenity:registration-credential:"))).thenReturn(credentialLock);
        when(redisson.getLock(startsWith("eve:serenity:corporation-claim:"))).thenReturn(corporationLock);
        when(credentialLock.tryLock(5, TimeUnit.SECONDS)).thenReturn(true);
        when(corporationLock.tryLock(5, TimeUnit.SECONDS)).thenReturn(true);
        when(credentialLock.isHeldByCurrentThread()).thenReturn(true);
        when(corporationLock.isHeldByCurrentThread()).thenReturn(true);
        doAnswer(invocation -> {
            ((EveCorporationDO)invocation.getArgument(0)).setId(300L);
            return 1;
        }).when(corporationMapper).insert(any(EveCorporationDO.class));
        doAnswer(invocation -> {
            ((EveCharacterDO)invocation.getArgument(0)).setId(400L);
            return 1;
        }).when(characterMapper).insert(any(EveCharacterDO.class));
        AtomicReference<RegistrationActivation> activation = new AtomicReference<>();
        when(credentialStore.markActivated(any(), any(), any())).thenAnswer(invocation -> {
            activation.set(invocation.getArgument(2));
            return true;
        });
        when(credentialStore.consume(any(), any())).thenAnswer(invocation -> Optional
            .of(new RegistrationCredentialSnapshot(identity(true, false), activation.get())));
        when(factVerifier.refresh(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new EveRegistrationService(credentialStore, factVerifier, redisson, tenantApi, accountApi, derivedRoleApi, corporationMapper, characterMapper, memberMapper, authorizationMapper, snapshotMapper, new SerenityProperties(), transactionTemplate);
    }

    /** 普通成员不得创建尚未入驻的军团租户。 */
    @Test
    void shouldRejectOrdinaryMemberClaimingNewCorporation() {
        when(credentialStore.find("credential", "browser")).thenReturn(snapshot(identity(false, false)));

        assertThatThrownBy(() -> service.complete(command())).isInstanceOf(IllegalStateException.class)
            .hasMessage("军团尚未入驻，请联系 CEO 或总监");

        verify(tenantApi, never()).createEveTenant(any());
        verify(accountApi, never()).openSession(any(), any(), any());
    }

    /** CEO 可创建唯一军团租户，并保存角色、成员、授权和四范围快照。 */
    @Test
    void shouldCreateTenantForCeoAndPersistIdentity() {
        when(credentialStore.find("credential", "browser")).thenReturn(snapshot(identity(true, false)));
        when(tenantApi.createEveTenant(any())).thenReturn(new EveTenantCreateResultDTO(10L, 20L));
        when(accountApi.openSession(10L, 20L, "client")).thenReturn(new EveLoginSessionDTO("token", 10L));

        EveRegistrationCompleteResult result = service.complete(command());

        assertThat(result.tenantCreated()).isTrue();
        assertThat(result.token()).isEqualTo("token");
        verify(memberMapper).insert(any(top.continew.admin.eve.model.entity.EveCorporationMemberDO.class));
        ArgumentCaptor<top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO> snapshot = ArgumentCaptor
            .forClass(top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO.class);
        verify(snapshotMapper).insert(snapshot.capture());
        assertThat(snapshot.getValue().getSourceExpiresAt()).isAfter(snapshot.getValue().getCapturedAt());
        ArgumentCaptor<EveAuthorizationDO> authorization = ArgumentCaptor.forClass(EveAuthorizationDO.class);
        verify(authorizationMapper).insert(authorization.capture());
        assertThat(authorization.getValue().getAccessToken()).isEqualTo("access-token");
        assertThat(authorization.getValue().getRefreshToken()).isEqualTo("refresh-token");
        verify(derivedRoleApi).synchronize(10L, 20L, EveDerivedIdentity.OWNER);
        verify(credentialStore).consume("credential", "browser");
        verify(corporationLock).unlock();
        verify(credentialLock).unlock();
    }

    /** 总监在军团未入驻时也可发起首次认领。 */
    @Test
    void shouldCreateTenantForDirector() {
        when(credentialStore.find("credential", "browser")).thenReturn(snapshot(identity(false, true)));
        when(tenantApi.createEveTenant(any())).thenReturn(new EveTenantCreateResultDTO(10L, 20L));
        when(accountApi.openSession(10L, 20L, "client")).thenReturn(new EveLoginSessionDTO("token", 10L));

        EveRegistrationCompleteResult result = service.complete(command());

        assertThat(result.tenantCreated()).isTrue();
        verify(tenantApi).createEveTenant(any());
        verify(derivedRoleApi).synchronize(10L, 20L, EveDerivedIdentity.ADMIN);
    }

    /** 已入驻军团允许真实普通成员创建本站账号并加入。 */
    @Test
    void shouldJoinExistingCorporationAsMember() {
        when(credentialStore.find("credential", "browser")).thenReturn(snapshot(identity(false, false)));
        EveCorporationDO corporation = new EveCorporationDO();
        corporation.setId(300L);
        corporation.setTenantId(10L);
        corporation.setStatus(EveCorporationStatus.ACTIVE);
        when(corporationMapper.selectByExternalId("serenity", 200L)).thenReturn(corporation);
        when(accountApi.createMember(any())).thenReturn(21L);
        when(accountApi.openSession(10L, 21L, "client")).thenReturn(new EveLoginSessionDTO("member-token", 10L));

        EveRegistrationCompleteResult result = service.complete(command());

        assertThat(result.tenantCreated()).isFalse();
        assertThat(result.userId()).isEqualTo(21L);
        verify(tenantApi, never()).createEveTenant(any());
        verify(accountApi).createMember(any());
        verify(derivedRoleApi).synchronize(10L, 21L, EveDerivedIdentity.MEMBER);
    }

    /** 已暂停或禁用的军团租户不得继续创建成员账号。 */
    @Test
    void shouldRejectJoiningInactiveCorporation() {
        when(credentialStore.find("credential", "browser")).thenReturn(snapshot(identity(false, false)));
        EveCorporationDO corporation = new EveCorporationDO();
        corporation.setId(300L);
        corporation.setTenantId(10L);
        corporation.setStatus(EveCorporationStatus.SUSPENDED);
        when(corporationMapper.selectByExternalId("serenity", 200L)).thenReturn(corporation);

        assertThatThrownBy(() -> service.complete(command())).isInstanceOf(IllegalStateException.class)
            .hasMessage("军团租户当前不可加入");

        verify(accountApi, never()).createMember(any());
        verify(accountApi, never()).openSession(any(), any(), any());
    }

    /** 同一 EVE 角色不得绑定第二个本站用户。 */
    @Test
    void shouldRejectCharacterAlreadyBound() {
        when(credentialStore.find("credential", "browser")).thenReturn(snapshot(identity(true, false)));
        when(characterMapper.selectByExternalId("serenity", 100L)).thenReturn(new EveCharacterDO());

        assertThatThrownBy(() -> service.complete(command())).isInstanceOf(IllegalStateException.class)
            .hasMessage("该 EVE 角色已绑定其他本站账号");

        verify(tenantApi, never()).createEveTenant(any());
    }

    /** 注册凭证缺少刷新令牌时不得创建账号或 ACTIVE 授权。 */
    @Test
    void shouldRejectRegistrationWithoutRefreshToken() {
        when(credentialStore.find("credential", "browser")).thenReturn(snapshot(identity(true, false, null)));

        assertThatThrownBy(() -> service.complete(command())).isInstanceOf(IllegalStateException.class)
            .hasMessage("国服授权未返回可续期令牌，请重新授权");

        verify(tenantApi, never()).createEveTenant(any());
        verify(authorizationMapper, never()).insert(any(EveAuthorizationDO.class));
    }

    /** 数据库事务失败时不得创建幽灵会话，军团锁仍必须释放。 */
    @Test
    void shouldNotOpenSessionWhenActivationTransactionFails() {
        when(credentialStore.find("credential", "browser")).thenReturn(snapshot(identity(true, false)));
        doAnswer(invocation -> {
            throw new IllegalStateException("transaction rolled back");
        }).when(transactionTemplate).execute(any());

        assertThatThrownBy(() -> service.complete(command())).isInstanceOf(IllegalStateException.class)
            .hasMessage("transaction rolled back");

        verify(accountApi, never()).openSession(any(), any(), any());
        verify(credentialStore, never()).consume(any(), any());
        verify(corporationLock, times(1)).unlock();
        verify(credentialLock, times(1)).unlock();
    }

    /** 凭证锁竞争失败不得读取或消费凭证，后续重试仍可成功。 */
    @Test
    void shouldKeepCredentialWhenConcurrentRequestOwnsCredentialLock() throws InterruptedException {
        when(credentialLock.tryLock(5, TimeUnit.SECONDS)).thenReturn(false, true);
        when(credentialStore.find("credential", "browser")).thenReturn(snapshot(identity(true, false)));
        when(tenantApi.createEveTenant(any())).thenReturn(new EveTenantCreateResultDTO(10L, 20L));
        when(accountApi.openSession(10L, 20L, "client")).thenReturn(new EveLoginSessionDTO("token", 10L));

        assertThatThrownBy(() -> service.complete(command())).isInstanceOf(IllegalStateException.class)
            .hasMessage("注册凭证正在处理中，请稍后重试");
        EveRegistrationCompleteResult result = service.complete(command());

        assertThat(result.token()).isEqualTo("token");
        verify(credentialStore, times(1)).find("credential", "browser");
        verify(credentialStore, times(1)).consume("credential", "browser");
    }

    /** 用户名冲突等事务失败不得消费凭证，修正参数后可以再次提交。 */
    @Test
    void shouldKeepCredentialWhenRecoverableActivationFails() {
        when(credentialStore.find("credential", "browser")).thenReturn(snapshot(identity(true, false)));
        doAnswer(invocation -> {
            throw new IllegalStateException("用户名已存在");
        }).doAnswer(invocation -> ((TransactionCallback<?>)invocation.getArgument(0)).doInTransaction(null))
            .when(transactionTemplate)
            .execute(any());
        when(tenantApi.createEveTenant(any())).thenReturn(new EveTenantCreateResultDTO(10L, 20L));
        when(accountApi.openSession(10L, 20L, "client")).thenReturn(new EveLoginSessionDTO("token", 10L));

        assertThatThrownBy(() -> service.complete(command())).isInstanceOf(IllegalStateException.class)
            .hasMessage("用户名已存在");
        verify(credentialStore, never()).consume(any(), any());

        assertThat(service.complete(command()).token()).isEqualTo("token");
        verify(credentialStore, times(1)).consume("credential", "browser");
    }

    /** 锁内复核发现角色已换军团时必须拒绝并失效凭证。 */
    @Test
    void shouldInvalidateCredentialWhenCorporationChangedInsideLock() {
        VerifiedRegistrationIdentity identity = identity(true, false);
        when(credentialStore.find("credential", "browser")).thenReturn(snapshot(identity));
        when(factVerifier.refresh(identity))
            .thenThrow(new RegistrationQualificationChangedException("角色所属军团已变化，请重新授权"));

        assertThatThrownBy(() -> service.complete(command()))
            .isInstanceOf(RegistrationQualificationChangedException.class)
            .hasMessage("角色所属军团已变化，请重新授权");

        verify(credentialStore).consume("credential", "browser");
        verify(transactionTemplate, never()).execute(any());
    }

    /** 数据库提交后会话建立失败，重试应复用激活结果且不重复创建租户。 */
    @Test
    void shouldRetrySessionDeliveryAfterCommittedActivation() {
        RegistrationActivation activation = new RegistrationActivation(10L, 20L, true);
        when(credentialStore.find("credential", "browser")).thenReturn(snapshot(identity(true, false)), Optional
            .of(new RegistrationCredentialSnapshot(identity(true, false), activation)));
        when(characterMapper.selectByExternalId("serenity", 100L)).thenReturn(null, committedCharacter(10L, 20L));
        when(tenantApi.createEveTenant(any())).thenReturn(new EveTenantCreateResultDTO(10L, 20L));
        when(accountApi.openSession(10L, 20L, "client")).thenThrow(new IllegalStateException("session failed"))
            .thenReturn(new EveLoginSessionDTO("token", 10L));

        assertThatThrownBy(() -> service.complete(command())).hasMessage("session failed");
        EveRegistrationCompleteResult result = service.complete(command());

        assertThat(result.token()).isEqualTo("token");
        verify(tenantApi, times(1)).createEveTenant(any());
        verify(transactionTemplate, times(1)).execute(any());
        verify(accountApi, times(2)).openSession(10L, 20L, "client");
    }

    /** 凭证消费失败时不交付令牌，保留激活结果后可再次建立会话。 */
    @Test
    void shouldRetryWhenCredentialConsumptionFails() {
        RegistrationActivation activation = new RegistrationActivation(10L, 20L, false);
        RegistrationCredentialSnapshot activated = new RegistrationCredentialSnapshot(identity(false, false), activation);
        when(credentialStore.find("credential", "browser")).thenReturn(Optional.of(activated));
        when(characterMapper.selectByExternalId("serenity", 100L)).thenReturn(committedCharacter(10L, 20L));
        when(accountApi.openSession(10L, 20L, "client"))
            .thenReturn(new EveLoginSessionDTO("first", 10L), new EveLoginSessionDTO("second", 10L));
        when(credentialStore.consume("credential", "browser")).thenReturn(Optional.empty(), Optional.of(activated));

        assertThatThrownBy(() -> service.complete(command())).hasMessage("注册会话交付未确认，请重试");
        EveRegistrationCompleteResult result = service.complete(command());

        assertThat(result.token()).isEqualTo("second");
        verify(transactionTemplate, never()).execute(any());
    }

    /** Redis 拒绝记录激活结果时必须回滚事务，保留处理凭证后可完整重试。 */
    @Test
    void shouldRetryWholeTransactionWhenActivationMarkReturnsFalse() {
        RegistrationActivation activation = new RegistrationActivation(10L, 20L, true);
        when(credentialStore.find("credential", "browser")).thenReturn(snapshot(identity(true, false)));
        when(credentialStore.markActivated("credential", "browser", activation)).thenReturn(false, true);
        when(credentialStore.consume("credential", "browser")).thenReturn(Optional
            .of(new RegistrationCredentialSnapshot(identity(true, false), activation)));
        when(tenantApi.createEveTenant(any())).thenReturn(new EveTenantCreateResultDTO(10L, 20L));
        when(accountApi.openSession(10L, 20L, "client")).thenReturn(new EveLoginSessionDTO("token", 10L));

        assertThatThrownBy(() -> service.complete(command())).hasMessage("注册结果暂存失败，请保留当前页面后重试");
        EveRegistrationCompleteResult result = service.complete(command());

        assertThat(result.token()).isEqualTo("token");
        verify(transactionTemplate, times(2)).execute(any());
        verify(tenantApi, times(2)).createEveTenant(any());
        verify(accountApi, times(1)).openSession(10L, 20L, "client");
    }

    /** Redis 写入异常必须从事务内抛出，后续重试不得因角色已绑定而永久失败。 */
    @Test
    void shouldRetryWholeTransactionWhenActivationMarkThrows() {
        RegistrationActivation activation = new RegistrationActivation(10L, 20L, true);
        when(credentialStore.find("credential", "browser")).thenReturn(snapshot(identity(true, false)));
        when(credentialStore.markActivated("credential", "browser", activation))
            .thenThrow(new IllegalStateException("redis unavailable"))
            .thenReturn(true);
        when(credentialStore.consume("credential", "browser")).thenReturn(Optional
            .of(new RegistrationCredentialSnapshot(identity(true, false), activation)));
        when(tenantApi.createEveTenant(any())).thenReturn(new EveTenantCreateResultDTO(10L, 20L));
        when(accountApi.openSession(10L, 20L, "client")).thenReturn(new EveLoginSessionDTO("token", 10L));

        assertThatThrownBy(() -> service.complete(command())).hasMessage("redis unavailable");
        assertThat(service.complete(command()).token()).isEqualTo("token");

        verify(transactionTemplate, times(2)).execute(any());
        verify(accountApi, times(1)).openSession(10L, 20L, "client");
    }

    /** Redis 已记录但数据库提交失败时，重试必须识别未提交状态并重新执行事务。 */
    @Test
    void shouldRetryWhenDatabaseCommitFailsAfterActivationMark() {
        RegistrationActivation activation = new RegistrationActivation(10L, 20L, true);
        RegistrationCredentialSnapshot activated = new RegistrationCredentialSnapshot(identity(true, false), activation);
        when(credentialStore.find("credential", "browser")).thenReturn(snapshot(identity(true, false)), Optional
            .of(activated));
        doAnswer(invocation -> {
            ((TransactionCallback<?>)invocation.getArgument(0)).doInTransaction(null);
            throw new IllegalStateException("commit failed");
        }).doAnswer(invocation -> ((TransactionCallback<?>)invocation.getArgument(0)).doInTransaction(null))
            .when(transactionTemplate)
            .execute(any());
        when(tenantApi.createEveTenant(any())).thenReturn(new EveTenantCreateResultDTO(10L, 20L));
        when(accountApi.openSession(10L, 20L, "client")).thenReturn(new EveLoginSessionDTO("token", 10L));

        assertThatThrownBy(() -> service.complete(command())).hasMessage("commit failed");
        assertThat(service.complete(command()).token()).isEqualTo("token");

        verify(transactionTemplate, times(2)).execute(any());
        verify(tenantApi, times(2)).createEveTenant(any());
    }

    /** 创建测试激活命令。 */
    private static EveRegistrationCompleteCommand command() {
        return new EveRegistrationCompleteCommand("credential", "browser", "Pilot_100", "encrypted", "client");
    }

    /** 将身份包装为未激活凭证状态。 */
    private static Optional<RegistrationCredentialSnapshot> snapshot(VerifiedRegistrationIdentity identity) {
        return Optional.of(new RegistrationCredentialSnapshot(identity, null));
    }

    /** 创建已提交角色绑定，用于验证 Redis 残留激活结果。 */
    private static EveCharacterDO committedCharacter(Long tenantId, Long userId) {
        EveCharacterDO character = new EveCharacterDO();
        character.setTenantId(tenantId);
        character.setUserId(userId);
        return character;
    }

    /** 创建测试国服身份。 */
    private static VerifiedRegistrationIdentity identity(boolean ceo, boolean director) {
        return identity(ceo, director, "refresh-token");
    }

    /** 创建可指定刷新令牌的测试国服身份。 */
    private static VerifiedRegistrationIdentity identity(boolean ceo, boolean director, String refreshToken) {
        return VerifiedRegistrationIdentity.builder()
            .server("serenity")
            .characterId(100L)
            .characterName("Pilot_100")
            .corporationId(200L)
            .corporationName("测试军团")
            .corporationTicker("TEST")
            .ceoCharacterId(ceo ? 100L : 999L)
            .ownerHash("owner-hash")
            .ceo(ceo)
            .director(director)
            .roles(director ? List.of("Director") : List.of())
            .rolesAtHq(List.of("Accountant"))
            .rolesAtBase(List.of())
            .rolesAtOther(List.of())
            .scopes(List.of("esi-characters.read_corporation_roles.v1"))
            .accessToken("access-token")
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .expiresAt(Instant.now().plusSeconds(1200))
            .build();
    }
}
