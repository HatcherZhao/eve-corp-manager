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
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveCharacterRoleSnapshotMapper;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.model.enums.EveCapability;
import top.continew.admin.eve.model.enums.EveCapabilityStatus;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 四层 EVE 能力策略测试。
 *
 * @author zhaoyuqing
 */
class EveCapabilityPolicyTest {

    private EveCharacterMapper characterMapper;
    private EveAuthorizationMapper authorizationMapper;
    private EveCharacterRoleSnapshotMapper snapshotMapper;
    private EveCapabilityPolicy policy;

    /** 初始化策略依赖。 */
    @BeforeEach
    void setUp() {
        characterMapper = mock(EveCharacterMapper.class);
        authorizationMapper = mock(EveAuthorizationMapper.class);
        snapshotMapper = mock(EveCharacterRoleSnapshotMapper.class);
        policy = new EveCapabilityPolicy(characterMapper, authorizationMapper, snapshotMapper);
    }

    /** 缺少站内权限时不得读取授权数据。 */
    @Test
    void shouldRejectMissingSitePermissionFirst() {
        assertThat(evaluate(EveCapability.ASSETS, Set.of()).status())
            .isEqualTo(EveCapabilityStatus.MISSING_SITE_PERMISSION);
        verify(characterMapper, never()).selectActiveByUser(10L, 20L);
        verify(authorizationMapper, never()).selectTenantCandidates(10L);
    }

    /** 无有效角色绑定时返回无数据源。 */
    @Test
    void shouldReportNoDataSourceWithoutCharacter() {
        when(authorizationMapper.selectTenantCandidates(10L)).thenReturn(List.of());
        assertThat(evaluate(EveCapability.ASSETS, Set.of("eve:assets:view")).status())
            .isEqualTo(EveCapabilityStatus.NO_DATA_SOURCE);
    }

    /** 授权过期时返回稳定的授权失效状态。 */
    @Test
    void shouldReportExpiredAuthorization() {
        when(authorizationMapper.selectTenantCandidates(10L)).thenReturn(List.of(authorization(100L, 30L, List
            .of(EveCapability.ASSETS.getScope()), false)));
        assertThat(evaluate(EveCapability.ASSETS, Set.of("eve:assets:view")).status())
            .isEqualTo(EveCapabilityStatus.AUTH_EXPIRED);
    }

    /** 访问令牌过期但刷新令牌可用时授权仍可作为能力数据源。 */
    @Test
    void shouldKeepRefreshableExpiredAccessAuthorizationAvailable() {
        EveAuthorizationDO authorization = authorization(100L, List.of(EveCapability.ASSETS.getScope()), true);
        authorization.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        authorization.setRefreshToken("refresh-token");
        stubCharacterAndAuthorizations(List.of(authorization));
        stubSnapshots(snapshot(100L, List.of("Director")));

        assertThat(evaluate(EveCapability.ASSETS, Set.of("eve:assets:view")).status())
            .isEqualTo(EveCapabilityStatus.AVAILABLE);
        assertThat(policy.authorizationStatus(10L, 20L)).isEqualTo(EveCapabilityStatus.AVAILABLE);
    }

    /** 访问令牌过期且无刷新令牌时不得把能力误判为可用。 */
    @Test
    void shouldRejectUnrefreshableExpiredAccessAuthorization() {
        EveAuthorizationDO authorization = authorization(100L, List.of(EveCapability.ASSETS.getScope()), true);
        authorization.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        authorization.setRefreshToken(null);
        stubCharacterAndAuthorizations(List.of(authorization));
        stubSnapshots(snapshot(100L, List.of("Director")));

        assertThat(evaluate(EveCapability.ASSETS, Set.of("eve:assets:view")).status())
            .isEqualTo(EveCapabilityStatus.AUTH_EXPIRED);
    }

    /** 缺失或过期的角色快照必须关闭能力，不能沿用陈旧角色。 */
    @Test
    void shouldFailClosedForExpiredSnapshot() {
        stubCharacterAndAuthorizations(List.of(authorization(100L, List.of(EveCapability.ASSETS.getScope()), true)));
        EveCharacterRoleSnapshotDO snapshot = snapshot(100L, List.of("Director"));
        snapshot.setSourceExpiresAt(LocalDateTime.now().minusSeconds(1));
        stubSnapshots(snapshot);

        assertThat(evaluate(EveCapability.ASSETS, Set.of("eve:assets:view")).status())
            .isEqualTo(EveCapabilityStatus.NO_DATA_SOURCE);
    }

    /** 有效授权缺少模块 Scope 时返回 Scope 不足。 */
    @Test
    void shouldReportMissingScope() {
        stubCharacterAndAuthorizations(List.of(authorization(100L, List.of("another-scope"), true)));
        assertThat(evaluate(EveCapability.ASSETS, Set.of("eve:assets:view")).status())
            .isEqualTo(EveCapabilityStatus.MISSING_SCOPE);
    }

    /** 角色快照只读取全局角色，不接受总部范围角色冒充。 */
    @Test
    void shouldKeepRoleScopesStrictlySeparated() {
        stubCharacterAndAuthorizations(List.of(authorization(100L, List.of(EveCapability.MINING.getScope()), true)));
        EveCharacterRoleSnapshotDO snapshot = snapshot(100L, List.of());
        snapshot.setRolesAtHq(List.of("Accountant"));
        stubSnapshots(snapshot);
        assertThat(evaluate(EveCapability.MINING, Set.of("eve:mining:view")).status())
            .isEqualTo(EveCapabilityStatus.MISSING_GAME_ROLE);
    }

    /** Scope 与游戏角色必须在同一个授权绑定内同时满足。 */
    @Test
    void shouldNotCombineDifferentAuthorizations() {
        when(authorizationMapper.selectTenantCandidates(10L)).thenReturn(List.of(authorization(100L, 30L, List
            .of(EveCapability.ASSETS.getScope()), true), authorization(101L, 31L, List.of("another-scope"), true)));
        stubSnapshots(snapshot(100L, List.of()));
        assertThat(evaluate(EveCapability.ASSETS, Set.of("eve:assets:view")).status())
            .isEqualTo(EveCapabilityStatus.MISSING_GAME_ROLE);
    }

    /** 普通查看者可复用同租户其他用户的总监授权数据源。 */
    @Test
    void shouldAllowViewerToUseTenantDirectorDataSource() {
        stubTenantAuthorizations(List.of(authorization(100L, 30L, List.of(EveCapability.ASSETS.getScope()), true)));
        stubSnapshots(snapshot(100L, List.of("Director")));

        assertThat(evaluate(EveCapability.ASSETS, Set.of("eve:assets:view")).status())
            .isEqualTo(EveCapabilityStatus.AVAILABLE);
        verify(characterMapper, never()).selectActiveByUser(10L, 20L);
    }

    /** 普通查看者可复用同租户其他用户的设施管理员授权数据源。 */
    @Test
    void shouldAllowViewerToUseTenantStationManagerDataSource() {
        stubTenantAuthorizations(List.of(authorization(100L, 30L, List.of(EveCapability.STRUCTURES.getScope()), true)));
        stubSnapshots(snapshot(100L, List.of("Station_Manager")));

        assertThat(evaluate(EveCapability.STRUCTURES, Set.of("eve:structures:view")).status())
            .isEqualTo(EveCapabilityStatus.AVAILABLE);
    }

    /** 军团总监拥有全部军团管理能力，不要求令牌重复列出各细分角色。 */
    @Test
    void shouldAllowDirectorToSatisfySpecializedGameRoles() {
        stubTenantAuthorizations(List.of(authorization(100L, 30L, List.of(EveCapability.STRUCTURES
            .getScope(), EveCapability.MINING.getScope()), true)));
        stubSnapshots(snapshot(100L, List.of("Director")));

        assertThat(evaluate(EveCapability.STRUCTURES, Set.of("eve:structures:view")).status())
            .isEqualTo(EveCapabilityStatus.AVAILABLE);
        assertThat(evaluate(EveCapability.MINING, Set.of("eve:mining:view")).status())
            .isEqualTo(EveCapabilityStatus.AVAILABLE);
    }

    /** 普通查看者可复用同租户其他用户的会计授权数据源。 */
    @Test
    void shouldAllowViewerToUseTenantAccountantDataSource() {
        stubTenantAuthorizations(List.of(authorization(100L, 30L, List.of(EveCapability.MINING.getScope()), true)));
        stubSnapshots(snapshot(100L, List.of("Accountant")));

        assertThat(evaluate(EveCapability.MINING, Set.of("eve:mining:view")).status())
            .isEqualTo(EveCapabilityStatus.AVAILABLE);
    }

    /** 普通查看者可复用同租户其他用户的 CEO 授权数据源。 */
    @Test
    void shouldAllowViewerToUseTenantCeoDataSource() {
        stubTenantAuthorizations(List.of(authorization(100L, 30L, List.of(EveCapability.MEMBERS.getScope()), true)));
        EveCharacterRoleSnapshotDO snapshot = snapshot(100L, List.of());
        snapshot.setIsCeo(true);
        stubSnapshots(snapshot);

        assertThat(evaluate(EveCapability.MEMBERS, Set.of("eve:members:view")).status())
            .isEqualTo(EveCapabilityStatus.AVAILABLE);
    }

    /** 同一授权的 Scope 与全局角色满足时能力可用。 */
    @Test
    void shouldAllowMatchingAuthorizationAndRole() {
        stubCharacterAndAuthorizations(List.of(authorization(100L, List.of(EveCapability.STRUCTURES
            .getScope()), true)));
        stubSnapshots(snapshot(100L, List.of("Station_Manager")));
        assertThat(evaluate(EveCapability.STRUCTURES, Set.of("eve:structures:view")).status())
            .isEqualTo(EveCapabilityStatus.AVAILABLE);
    }

    /** CEO 作为军团所有者可满足军团角色条件。 */
    @Test
    void shouldAllowCeoAsCorporationOwner() {
        stubCharacterAndAuthorizations(List.of(authorization(100L, List.of(EveCapability.MEMBERS.getScope()), true)));
        EveCharacterRoleSnapshotDO snapshot = snapshot(100L, List.of());
        snapshot.setIsCeo(true);
        stubSnapshots(snapshot);
        assertThat(evaluate(EveCapability.MEMBERS, Set.of("eve:members:view")).status())
            .isEqualTo(EveCapabilityStatus.AVAILABLE);
    }

    /** 授权健康状态不应被站内菜单权限掩盖。 */
    @Test
    void shouldReportAuthorizationHealthIndependently() {
        stubCharacterAndAuthorizations(List.of(authorization(100L, List.of(), true)));
        assertThat(policy.authorizationStatus(10L, 20L)).isEqualTo(EveCapabilityStatus.AVAILABLE);
    }

    /** 批量判断结果与逐项判断一致，且查询次数不随能力数量增长。 */
    @Test
    void shouldEvaluateAllCapabilitiesWithSingleCandidateAndSnapshotQuery() {
        List<EveAuthorizationDO> authorizations = List.of(authorization(100L, List.of(EveCapability.ASSETS
            .getScope(), EveCapability.STRUCTURES.getScope()), true));
        when(authorizationMapper.selectTenantCandidates(10L)).thenReturn(authorizations);
        when(snapshotMapper.selectLatestByCharacterRefs(eq(10L), anyList())).thenReturn(List.of(snapshot(100L, List
            .of("Director", "Station_Manager"))));
        Set<String> permissions = Set.of("eve:assets:view", "eve:structures:view");
        List<top.continew.admin.eve.model.EveCapabilityResult> expected = Arrays.stream(EveCapability.values())
            .map(capability -> policy.evaluate(capability, 10L, permissions))
            .toList();
        clearInvocations(authorizationMapper, snapshotMapper);

        List<top.continew.admin.eve.model.EveCapabilityResult> results = policy.evaluateAll(10L, permissions);

        assertThat(results).containsExactlyElementsOf(expected);
        verify(authorizationMapper, times(1)).selectTenantCandidates(10L);
        verify(snapshotMapper, times(1)).selectLatestByCharacterRefs(10L, List.of(100L));
        verify(snapshotMapper, never()).selectLatest(anyLong(), anyLong());
    }

    /** 执行统一能力判断。 */
    private top.continew.admin.eve.model.EveCapabilityResult evaluate(EveCapability capability,
                                                                      Set<String> permissions) {
        return policy.evaluate(capability, 10L, permissions);
    }

    /** 建立单角色授权候选。 */
    private void stubCharacterAndAuthorizations(List<EveAuthorizationDO> authorizations) {
        when(characterMapper.selectActiveByUser(10L, 20L)).thenReturn(List.of(character(100L)));
        when(authorizationMapper.selectByUserCharacter(10L, 20L, 100L)).thenReturn(authorizations);
        when(authorizationMapper.selectTenantCandidates(10L)).thenReturn(authorizations);
    }

    /** 建立租户共享授权候选。 */
    private void stubTenantAuthorizations(List<EveAuthorizationDO> authorizations) {
        when(authorizationMapper.selectTenantCandidates(10L)).thenReturn(authorizations);
    }

    /** 建立批量最新快照查询结果。 */
    private void stubSnapshots(EveCharacterRoleSnapshotDO... snapshots) {
        List<Long> characterRefIds = Arrays.stream(snapshots)
            .map(EveCharacterRoleSnapshotDO::getCharacterRefId)
            .toList();
        when(snapshotMapper.selectLatestByCharacterRefs(10L, characterRefIds)).thenReturn(List.of(snapshots));
    }

    /** 创建租户内角色绑定。 */
    private static EveCharacterDO character(Long id) {
        EveCharacterDO character = new EveCharacterDO();
        character.setId(id);
        character.setTenantId(10L);
        character.setUserId(20L);
        return character;
    }

    /** 创建授权候选。 */
    private static EveAuthorizationDO authorization(Long characterRefId, List<String> scopes, boolean active) {
        return authorization(characterRefId, 20L, scopes, active);
    }

    /** 创建指定数据源用户的授权候选。 */
    private static EveAuthorizationDO authorization(Long characterRefId,
                                                    Long userId,
                                                    List<String> scopes,
                                                    boolean active) {
        EveAuthorizationDO authorization = new EveAuthorizationDO();
        authorization.setTenantId(10L);
        authorization.setUserId(userId);
        authorization.setCharacterRefId(characterRefId);
        authorization.setScopes(scopes);
        authorization.setStatus(active ? EveAuthorizationStatus.ACTIVE : EveAuthorizationStatus.EXPIRED);
        authorization.setExpiresAt(active
            ? LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10)
            : LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        authorization.setAccessToken(active ? "access-token" : null);
        return authorization;
    }

    /** 创建全局角色快照。 */
    private static EveCharacterRoleSnapshotDO snapshot(Long characterRefId, List<String> roles) {
        EveCharacterRoleSnapshotDO snapshot = new EveCharacterRoleSnapshotDO();
        snapshot.setTenantId(10L);
        snapshot.setCharacterRefId(characterRefId);
        snapshot.setRoles(roles);
        snapshot.setIsCeo(false);
        snapshot.setSourceExpiresAt(LocalDateTime.now().plusMinutes(10));
        return snapshot;
    }
}
