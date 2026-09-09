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
import top.continew.admin.common.api.system.RoleApi;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.common.enums.DataScopeEnum;
import top.continew.admin.common.model.dto.EveSiteRoleDTO;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveCharacterRoleSnapshotMapper;
import top.continew.admin.eve.model.EvePermissionTreeResp;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.model.enums.EvePermissionTreeStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 当前用户游戏权限树服务测试。
 *
 * @author zhaoyuqing
 */
class EvePermissionTreeServiceTest {

    private EveCharacterMapper characterMapper;
    private EveCharacterRoleSnapshotMapper snapshotMapper;
    private EveAuthorizationMapper authorizationMapper;
    private RoleApi roleApi;
    private SerenityProperties properties;
    private EvePermissionTreeService service;

    @BeforeEach
    void setUp() {
        characterMapper = mock(EveCharacterMapper.class);
        snapshotMapper = mock(EveCharacterRoleSnapshotMapper.class);
        authorizationMapper = mock(EveAuthorizationMapper.class);
        roleApi = mock(RoleApi.class);
        properties = new SerenityProperties();
        properties.getSso().setRequiredScopes(Set.of("esi-characters.read_corporation_roles.v1"));
        service = new EvePermissionTreeService(characterMapper, snapshotMapper, authorizationMapper, roleApi, new EveAuthorizationScopePolicy(properties));
    }

    /** 每个范围都返回 49 项完整目录，未知角色只追加在原范围且 CEO 独立展示。 */
    @Test
    void shouldBuildCompleteScopedTreeAndPreserveUnknownRole() {
        EveCharacterDO character = character();
        EveCharacterRoleSnapshotDO snapshot = snapshot();
        snapshot.setIsCeo(true);
        snapshot.setRoles(List.of("Director"));
        snapshot.setRolesAtHq(List.of("Director", "Future_Role"));
        stubCharacter(character, snapshot, authorization(EveAuthorizationStatus.ACTIVE));

        EvePermissionTreeResp response = service.getTree(10L, 20L, Set.of());

        assertThat(response.status()).isEqualTo(EvePermissionTreeStatus.READY);
        assertThat(response.gamePermissions().ceo().owned()).isTrue();
        assertThat(response.gamePermissions().ceo().sourceScope()).isEqualTo("corporation_identity");
        assertThat(response.gamePermissions().groups())
            .extracting(EvePermissionTreeResp.GamePermissionGroup::sourceScope)
            .containsExactly("roles", "roles_at_hq", "roles_at_base", "roles_at_other");
        assertThat(group(response, "roles").children()).hasSize(49);
        assertThat(group(response, "roles_at_hq").children()).hasSize(50);
        assertThat(group(response, "roles_at_base").children()).hasSize(49);
        assertThat(group(response, "roles_at_other").children()).hasSize(49);

        EvePermissionTreeResp.GamePermissionNode globalDirector = node(response, "roles", "Director");
        EvePermissionTreeResp.GamePermissionNode hqDirector = node(response, "roles_at_hq", "Director");
        assertThat(globalDirector.owned()).isTrue();
        assertThat(hqDirector.owned()).isTrue();
        assertThat(globalDirector.key()).isNotEqualTo(hqDirector.key());
        assertThat(node(response, "roles_at_base", "Director").owned()).isFalse();
        assertThat(node(response, "roles_at_hq", "Future_Role")).satisfies(node -> {
            assertThat(node.known()).isFalse();
            assertThat(node.owned()).isTrue();
            assertThat(node.code()).isEqualTo("Future_Role");
        });
        assertThat(group(response, "roles").children()).noneMatch(node -> "Future_Role".equals(node.code()));
    }

    /** 本站角色与 Scope 独立分区，派生来源、人工来源及缺失 Scope 均明确标记。 */
    @Test
    void shouldSeparateSiteRolesAndScopes() {
        EveCharacterDO character = character();
        stubCharacter(character, snapshot(), authorization(EveAuthorizationStatus.ACTIVE));
        when(roleApi.listEveSiteRoles(10L, 20L)).thenReturn(List
            .of(new EveSiteRoleDTO(1L, "corp_admin", "军团总监", "游戏身份派生", DataScopeEnum.ALL, true, List
                .of("eve:assets:view")), new EveSiteRoleDTO(2L, "mining_viewer", "月矿查看员", "人工配置", DataScopeEnum.ALL, false, List
                    .of("eve:mining:view"))));
        EveAuthorizationDO authorization = authorization(EveAuthorizationStatus.ACTIVE);
        authorization.setScopes(List.of("esi-characters.read_corporation_roles.v1", "custom.scope.v1"));
        when(authorizationMapper.selectByUserCharacter(10L, 20L, 30L)).thenReturn(List.of(authorization));

        EvePermissionTreeResp response = service.getTree(10L, 20L, Set.of("eve:assets:view", "eve:mining:view"));

        assertThat(response.siteRoles().roles()).extracting(EvePermissionTreeResp.SiteRoleItem::source)
            .containsExactly("EVE_DERIVED", "MANUAL_CONFIGURATION");
        assertThat(response.siteRoles().roles()).extracting(EvePermissionTreeResp.SiteRoleItem::derived)
            .containsExactly(true, false);
        assertThat(response.siteRoles().effectivePermissions()).containsExactly("eve:assets:view", "eve:mining:view");
        assertThat(scope(response, "esi-characters.read_corporation_roles.v1")).satisfies(item -> {
            assertThat(item.owned()).isTrue();
            assertThat(item.identityRequired()).isTrue();
        });
        assertThat(scope(response, "esi-assets.read_corporation_assets.v1").owned()).isFalse();
        assertThat(scope(response, "custom.scope.v1").owned()).isTrue();
        assertThat(response.scopes().authorizationStatus()).isEqualTo(EveAuthorizationStatus.ACTIVE);
    }

    /** 无角色时返回稳定空态，且所有查询都严格使用当前租户与用户。 */
    @Test
    void shouldReturnStructuredEmptyStateWithoutCharacter() {
        when(characterMapper.selectActiveByUser(10L, 20L)).thenReturn(List.of());
        when(roleApi.listEveSiteRoles(10L, 20L)).thenReturn(List.of());

        EvePermissionTreeResp response = service.getTree(10L, 20L, Set.of());

        assertThat(response.status()).isEqualTo(EvePermissionTreeStatus.CHARACTER_NOT_BOUND);
        assertThat(response.character()).isNull();
        assertThat(response.gamePermissions().ceo().owned()).isFalse();
        assertThat(response.gamePermissions().groups()).hasSize(4).allMatch(group -> group.children().size() == 49);
        assertThat(response.gamePermissions().groups())
            .flatExtracting(EvePermissionTreeResp.GamePermissionGroup::children)
            .allMatch(node -> !node.owned());
        assertThat(response.scopes().items()).isNotEmpty().allMatch(item -> !item.owned());
        verify(characterMapper).selectActiveByUser(10L, 20L);
        verify(roleApi).listEveSiteRoles(10L, 20L);
        verifyNoInteractions(snapshotMapper, authorizationMapper);
    }

    /** 无快照仍返回完整可浏览目录，并以状态阻止把未检查结果当成有效权限。 */
    @Test
    void shouldReturnCatalogWhenSnapshotIsUnavailable() {
        EveCharacterDO character = character();
        when(characterMapper.selectActiveByUser(10L, 20L)).thenReturn(List.of(character));
        when(snapshotMapper.selectLatest(10L, 30L)).thenReturn(null);
        when(authorizationMapper.selectByUserCharacter(10L, 20L, 30L)).thenReturn(List
            .of(authorization(EveAuthorizationStatus.ACTIVE)));

        EvePermissionTreeResp response = service.getTree(10L, 20L, Set.of());

        assertThat(response.status()).isEqualTo(EvePermissionTreeStatus.SNAPSHOT_UNAVAILABLE);
        assertThat(response.gamePermissions().groups()).allMatch(group -> group.children().size() == 49);
        assertThat(response.gamePermissions().groups())
            .flatExtracting(EvePermissionTreeResp.GamePermissionGroup::children)
            .allMatch(node -> !node.owned());
    }

    /** 授权失效单独返回状态，但不会丢弃最近快照供用户核对。 */
    @Test
    void shouldReportInactiveAuthorizationWithoutDroppingSnapshot() {
        stubCharacter(character(), snapshot(), authorization(EveAuthorizationStatus.REAUTH_REQUIRED));

        EvePermissionTreeResp response = service.getTree(10L, 20L, Set.of());

        assertThat(response.status()).isEqualTo(EvePermissionTreeStatus.AUTHORIZATION_INACTIVE);
        assertThat(response.scopes().authorizationStatus()).isEqualTo(EveAuthorizationStatus.REAUTH_REQUIRED);
        assertThat(response.gamePermissions().groups()).hasSize(4);
    }

    /** 当前入口只能从会话上下文取 tenant/user，不能接受外部目标参数。 */
    @Test
    void shouldUseCurrentSessionTenantAndUser() {
        UserContext context = new UserContext();
        context.setTenantId(10L);
        context.setId(20L);
        context.setPermissions(Set.of("eve:workspace:view"));
        when(characterMapper.selectActiveByUser(10L, 20L)).thenReturn(List.of());
        when(roleApi.listEveSiteRoles(10L, 20L)).thenReturn(List.of());

        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(context);
            EvePermissionTreeResp response = service.getCurrentTree();
            assertThat(response.status()).isEqualTo(EvePermissionTreeStatus.CHARACTER_NOT_BOUND);
        }
        verify(characterMapper).selectActiveByUser(10L, 20L);
    }

    /** 构造当前租户用户的主角色。 */
    private static EveCharacterDO character() {
        EveCharacterDO character = new EveCharacterDO();
        character.setId(30L);
        character.setTenantId(10L);
        character.setUserId(20L);
        character.setCharacterId(90000001L);
        character.setCorporationId(98000001L);
        character.setName("测试角色");
        character.setIsPrimary(true);
        return character;
    }

    /** 构造四范围空数组的权限快照。 */
    private static EveCharacterRoleSnapshotDO snapshot() {
        EveCharacterRoleSnapshotDO snapshot = new EveCharacterRoleSnapshotDO();
        snapshot.setTenantId(10L);
        snapshot.setCharacterRefId(30L);
        snapshot.setRoles(List.of());
        snapshot.setRolesAtHq(List.of());
        snapshot.setRolesAtBase(List.of());
        snapshot.setRolesAtOther(List.of());
        snapshot.setCapturedAt(LocalDateTime.of(2026, 9, 7, 20, 0));
        snapshot.setSourceExpiresAt(LocalDateTime.now(java.time.ZoneOffset.UTC).plusHours(1));
        return snapshot;
    }

    /** 构造指定生命周期状态的授权。 */
    private static EveAuthorizationDO authorization(EveAuthorizationStatus status) {
        EveAuthorizationDO authorization = new EveAuthorizationDO();
        authorization.setTenantId(10L);
        authorization.setCharacterRefId(30L);
        authorization.setUserId(20L);
        authorization.setStatus(status);
        authorization.setScopes(List.of("esi-characters.read_corporation_roles.v1"));
        authorization.setLastVerifiedAt(LocalDateTime.of(2026, 9, 7, 20, 0));
        return authorization;
    }

    /** 配置角色、快照和授权的同租户同用户查询。 */
    private void stubCharacter(EveCharacterDO character,
                               EveCharacterRoleSnapshotDO snapshot,
                               EveAuthorizationDO authorization) {
        when(characterMapper.selectActiveByUser(10L, 20L)).thenReturn(List.of(character));
        when(snapshotMapper.selectLatest(10L, 30L)).thenReturn(snapshot);
        when(authorizationMapper.selectByUserCharacter(10L, 20L, 30L)).thenReturn(List.of(authorization));
        when(roleApi.listEveSiteRoles(10L, 20L)).thenReturn(List.of());
    }

    /** 即使持久层异常返回其他租户实体，也不得暴露角色、授权或快照。 */
    @Test
    void shouldRejectCrossTenantMapperResults() {
        EveCharacterDO foreignCharacter = character();
        foreignCharacter.setTenantId(11L);
        when(characterMapper.selectActiveByUser(10L, 20L)).thenReturn(List.of(foreignCharacter));
        when(roleApi.listEveSiteRoles(10L, 20L)).thenReturn(List.of());

        EvePermissionTreeResp response = service.getTree(10L, 20L, Set.of());

        assertThat(response.status()).isEqualTo(EvePermissionTreeStatus.CHARACTER_NOT_BOUND);
        assertThat(response.character()).isNull();
        verifyNoInteractions(snapshotMapper, authorizationMapper);
    }

    /** 按原始范围字段查询分组。 */
    private static EvePermissionTreeResp.GamePermissionGroup group(EvePermissionTreeResp response, String scope) {
        return response.gamePermissions()
            .groups()
            .stream()
            .filter(group -> scope.equals(group.sourceScope()))
            .findFirst()
            .orElseThrow();
    }

    /** 按范围和原始代码查询游戏角色节点。 */
    private static EvePermissionTreeResp.GamePermissionNode node(EvePermissionTreeResp response,
                                                                 String scope,
                                                                 String code) {
        return group(response, scope).children()
            .stream()
            .filter(node -> code.equals(node.code()))
            .findFirst()
            .orElseThrow();
    }

    /** 按 Scope 原文查询授权节点。 */
    private static EvePermissionTreeResp.ScopeItem scope(EvePermissionTreeResp response, String scope) {
        return response.scopes().items().stream().filter(item -> scope.equals(item.scope())).findFirst().orElseThrow();
    }
}
