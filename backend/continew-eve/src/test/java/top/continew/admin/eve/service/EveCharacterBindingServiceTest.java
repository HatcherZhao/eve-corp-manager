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
import org.mockito.MockedStatic;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.auth.OAuthTransaction;
import top.continew.admin.eve.auth.OAuthTransactionPurpose;
import top.continew.admin.eve.auth.OAuthTransactionStore;
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
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.enums.EveCorporationStatus;
import top.continew.admin.eve.model.serenity.SerenityCharacterResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationRolesResponse;
import top.continew.admin.eve.security.SerenityCallback;
import top.continew.admin.eve.security.SerenityCallbackUrlParser;
import top.continew.admin.eve.security.SerenityJwtDecoderFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 已登录用户绑定 EVE 角色的跨租户和唯一性测试。
 *
 * @author zhaoyuqing
 */
class EveCharacterBindingServiceTest {

    private OAuthTransactionStore transactionStore;
    private SerenityTokenClient tokenClient;
    private JwtDecoder decoder;
    private SerenityEsiClient esiClient;
    private EveCharacterMapper characterMapper;
    private EveCorporationMapper corporationMapper;
    private EveDerivedIdentityService derivedIdentityService;
    private RLock userLock;
    private EveCharacterBindingService service;

    /** 初始化当前用户、同军团和一次性 OAuth 事务。 */
    @BeforeEach
    void setUp() throws InterruptedException {
        transactionStore = mock(OAuthTransactionStore.class);
        tokenClient = mock(SerenityTokenClient.class);
        SerenityJwtDecoderFactory decoderFactory = mock(SerenityJwtDecoderFactory.class);
        decoder = mock(JwtDecoder.class);
        esiClient = mock(SerenityEsiClient.class);
        characterMapper = mock(EveCharacterMapper.class);
        corporationMapper = mock(EveCorporationMapper.class);
        derivedIdentityService = mock(EveDerivedIdentityService.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        userLock = mock(RLock.class);
        when(redissonClient.getLock("eve:serenity:permission-refresh:10:20")).thenReturn(userLock);
        when(userLock.tryLock(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(userLock.isHeldByCurrentThread()).thenReturn(true);
        SerenityCallbackUrlParser callbackParser = mock(SerenityCallbackUrlParser.class);
        SerenityProperties properties = new SerenityProperties();
        properties.getSso().setScopeClaim("scp");
        OAuthTransaction transaction = transaction(10L, 20L);
        when(callbackParser.parse("callback-url")).thenReturn(new SerenityCallback("code", "state", null));
        when(transactionStore.consume("state")).thenReturn(Optional.of(transaction));
        when(tokenClient.exchangeCode("code", transaction.getVerifier()))
            .thenReturn(new SerenityTokenResponse("access", "refresh", "Bearer", 1200, null));
        when(decoderFactory.create()).thenReturn(decoder);
        when(decoder.decode("access")).thenReturn(jwt(1001L));
        service = new EveCharacterBindingService(mock(SerenityAuthorizationStartService.class), transactionStore, callbackParser, tokenClient, decoderFactory, esiClient, characterMapper, corporationMapper, mock(EveCorporationMemberMapper.class), mock(EveAuthorizationMapper.class), mock(EveCharacterRoleSnapshotMapper.class), derivedIdentityService, redissonClient, properties, new EveAuthorizationScopePolicy(properties));
    }

    /** 同军团、未绑定角色可进入持久化流程并同步派生身份。 */
    @Test
    void shouldBindUnclaimedCharacterInCurrentCorporation() {
        EveCorporationDO corporation = corporation();
        when(esiClient.getCharacter(1001L)).thenReturn(new SerenityCharacterResponse("Pilot_1001", 3001L));
        when(corporationMapper.selectByTenantAndCorporationId(10L, 3001L)).thenReturn(corporation);
        when(characterMapper.selectByExternalId("serenity", 1001L)).thenReturn(null);
        when(esiClient.getCorporation(3001L))
            .thenReturn(new SerenityCorporationResponse("Corp", "CORP", 999L, null, 1, null));
        when(esiClient.getCorporationRoles(1001L, "access")).thenReturn(new SerenityCorporationRolesResponse(List
            .of("Director"), List.of(), List.of(), List.of()));
        doAnswer(invocation -> {
            EveCharacterDO inserted = invocation.getArgument(0);
            inserted.setId(100L);
            return 1;
        }).when(characterMapper).insert(any(EveCharacterDO.class));

        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(context(10L, 20L));
            assertThat(service.bindCurrent("callback-url", "browser").characterId()).isEqualTo(1001L);
        }

        verify(characterMapper).insert(any(EveCharacterDO.class));
        verify(derivedIdentityService).synchronize(any(), any());
        verify(userLock).unlock();
    }

    /** 用户级锁必须保持到绑定事务完成后再释放。 */
    @Test
    void shouldKeepUserLockUntilTransactionCompletion() {
        prepareSuccessfulBinding();
        TransactionSynchronizationManager.initSynchronization();
        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(context(10L, 20L));
            service.bindCurrent("callback-url", "browser");
            verify(userLock, never()).unlock();
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization
                    .afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            verify(userLock).unlock();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 用户级锁竞争失败时不得消费一次性回调或写入绑定。 */
    @Test
    void shouldRejectBindingWhenUserLockIsBusy() throws InterruptedException {
        when(userLock.tryLock(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(false);

        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(context(10L, 20L));
            assertThatThrownBy(() -> service.bindCurrent("callback-url", "browser"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("EVE 角色绑定正在处理，请稍后重试");
        }

        verify(transactionStore, never()).consume(any());
        verify(characterMapper, never()).insert(any(EveCharacterDO.class));
    }

    /** 事务中的租户与当前会话不同必须在调用国服前拒绝。 */
    @Test
    void shouldRejectCrossTenantCallback() {
        when(transactionStore.consume("state")).thenReturn(Optional.of(transaction(11L, 20L)));

        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(context(10L, 20L));
            assertThatThrownBy(() -> service.bindCurrent("callback-url", "browser"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("EVE 角色绑定授权校验失败，请重新发起");
        }

        verify(tokenClient, never()).exchangeCode(any(), any());
    }

    /** 已被他人绑定的游戏角色绝不能被当前用户再次绑定。 */
    @Test
    void shouldRejectCharacterBoundToAnotherUser() {
        when(esiClient.getCharacter(1001L)).thenReturn(new SerenityCharacterResponse("Pilot_1001", 3001L));
        when(corporationMapper.selectByTenantAndCorporationId(10L, 3001L)).thenReturn(corporation());
        EveCharacterDO existing = new EveCharacterDO();
        existing.setUserId(99L);
        when(characterMapper.selectByExternalId("serenity", 1001L)).thenReturn(existing);

        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(context(10L, 20L));
            assertThatThrownBy(() -> service.bindCurrent("callback-url", "browser"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("该 EVE 角色已绑定其他本站账号");
        }

        verify(characterMapper, never()).insert(any(EveCharacterDO.class));
    }

    /** 角色所属军团不同于当前租户时必须拒绝。 */
    @Test
    void shouldRejectCharacterFromAnotherCorporation() {
        when(esiClient.getCharacter(1001L)).thenReturn(new SerenityCharacterResponse("Pilot_1001", 3002L));
        when(corporationMapper.selectByTenantAndCorporationId(10L, 3002L)).thenReturn(null);

        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(context(10L, 20L));
            assertThatThrownBy(() -> service.bindCurrent("callback-url", "browser"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("该 EVE 角色不属于当前军团，不能绑定到此账号");
        }

        verify(characterMapper, never()).selectByExternalId(any(), any());
    }

    /** 绑定授权缺少刷新令牌时不得写入 ACTIVE 角色授权。 */
    @Test
    void shouldRejectBindingWithoutRefreshToken() {
        when(tokenClient.exchangeCode(any(), any()))
            .thenReturn(new SerenityTokenResponse("access", null, "Bearer", 1200, null));
        when(esiClient.getCharacter(1001L)).thenReturn(new SerenityCharacterResponse("Pilot_1001", 3001L));
        when(corporationMapper.selectByTenantAndCorporationId(10L, 3001L)).thenReturn(corporation());
        when(characterMapper.selectByExternalId("serenity", 1001L)).thenReturn(null);
        when(esiClient.getCorporation(3001L))
            .thenReturn(new SerenityCorporationResponse("Corp", "CORP", 999L, null, 1, null));
        when(esiClient.getCorporationRoles(1001L, "access")).thenReturn(new SerenityCorporationRolesResponse(List
            .of(), List.of(), List.of(), List.of()));

        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(context(10L, 20L));
            assertThatThrownBy(() -> service.bindCurrent("callback-url", "browser"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("国服授权未返回可续期令牌，请重新授权");
        }

        verify(characterMapper, never()).insert(any(EveCharacterDO.class));
    }

    /** 准备一条可完整落库的绑定测试数据。 */
    private void prepareSuccessfulBinding() {
        when(esiClient.getCharacter(1001L)).thenReturn(new SerenityCharacterResponse("Pilot_1001", 3001L));
        when(corporationMapper.selectByTenantAndCorporationId(10L, 3001L)).thenReturn(corporation());
        when(characterMapper.selectByExternalId("serenity", 1001L)).thenReturn(null);
        when(esiClient.getCorporation(3001L))
            .thenReturn(new SerenityCorporationResponse("Corp", "CORP", 999L, null, 1, null));
        when(esiClient.getCorporationRoles(1001L, "access")).thenReturn(new SerenityCorporationRolesResponse(List
            .of("Director"), List.of(), List.of(), List.of()));
        doAnswer(invocation -> {
            EveCharacterDO inserted = invocation.getArgument(0);
            inserted.setId(100L);
            return 1;
        }).when(characterMapper).insert(any(EveCharacterDO.class));
    }

    /** 创建与当前会话绑定的一次性授权事务。 */
    private static OAuthTransaction transaction(Long tenantId, Long userId) {
        return OAuthTransaction.builder()
            .purpose(OAuthTransactionPurpose.BIND_CHARACTER)
            .boundTenantId(tenantId)
            .boundUserId(userId)
            .browserBindingDigest("browser")
            .requestedScopes(Set.of("esi-characters.read_corporation_roles.v1"))
            .verifier("v".repeat(43))
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
    }

    /** 创建当前租户的正常军团绑定。 */
    private static EveCorporationDO corporation() {
        EveCorporationDO corporation = new EveCorporationDO();
        corporation.setId(30L);
        corporation.setTenantId(10L);
        corporation.setStatus(EveCorporationStatus.ACTIVE);
        return corporation;
    }

    /** 创建当前已登录用户上下文。 */
    private static UserContext context(Long tenantId, Long userId) {
        UserContext context = new UserContext();
        context.setTenantId(tenantId);
        context.setId(userId);
        return context;
    }

    /** 创建带所需 Scope 和所有者声明的已验证 JWT。 */
    private static Jwt jwt(Long characterId) {
        return Jwt.withTokenValue("access")
            .header("alg", "RS256")
            .subject("CHARACTER:EVE:" + characterId)
            .claim("owner", "owner-hash")
            .claim("scp", List.of("esi-characters.read_corporation_roles.v1"))
            .build();
    }
}
