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
import top.continew.admin.common.api.system.RoleApi;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.common.enums.EveDerivedIdentity;
import top.continew.admin.common.model.dto.EveSiteRoleDTO;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveCharacterRoleSnapshotMapper;
import top.continew.admin.eve.model.EvePermissionTreeResp;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.model.enums.EveCapability;
import top.continew.admin.eve.model.enums.EvePermissionTreeStatus;
import top.continew.admin.eve.model.serenity.CorporationRoleDefinition;
import top.continew.admin.eve.model.serenity.CorporationRoleScope;
import top.continew.admin.eve.model.serenity.EveCorporationRoleCatalog;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 构建当前会话的只读游戏权限、本站角色与 OAuth Scope 分区视图。
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EvePermissionTreeService {

    private static final String ROLE_DATA_SOURCE = "SERENITY_CHARACTER_ROLES_SNAPSHOT";
    private static final String CEO_DATA_SOURCE = "SERENITY_CORPORATION_PROFILE_SNAPSHOT";
    private static final String SITE_ROLE_DATA_SOURCE = "LOCAL_ROLE_BINDINGS";
    private static final String SCOPE_DATA_SOURCE = "SERENITY_OAUTH_AUTHORIZATION";

    private final EveCharacterMapper characterMapper;
    private final EveCharacterRoleSnapshotMapper snapshotMapper;
    private final EveAuthorizationMapper authorizationMapper;
    private final RoleApi roleApi;
    private final EveAuthorizationScopePolicy scopePolicy;

    /** 从当前会话服务端上下文定位租户与用户，不接收客户端角色或军团参数。 */
    public EvePermissionTreeResp getCurrentTree() {
        UserContext context = UserContextHolder.getContext();
        return getTree(context.getTenantId(), context.getId(), context.getPermissions());
    }

    /**
     * 按明确租户与用户构建权限树，供当前会话入口和定向测试复用。
     *
     * @param tenantId             当前会话租户 ID
     * @param userId               当前会话用户 ID
     * @param effectivePermissions 当前会话聚合权限码
     * @return 三分区只读权限响应
     */
    EvePermissionTreeResp getTree(Long tenantId, Long userId, Set<String> effectivePermissions) {
        LocalDateTime checkedAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
        List<EveSiteRoleDTO> roles = roleApi.listEveSiteRoles(tenantId, userId);
        EvePermissionTreeResp.SiteRoleSection siteRoles = toSiteRoles(roles, effectivePermissions, checkedAt);
        List<EveCharacterDO> characters = characterMapper.selectActiveByUser(tenantId, userId);
        if (characters.isEmpty()) {
            return response(EvePermissionTreeStatus.CHARACTER_NOT_BOUND, "当前账号未绑定有效的 EVE 角色", null, emptyGamePermissions(), siteRoles, emptyScopes());
        }

        EveCharacterDO character = characters.stream()
            .filter(item -> Objects.equals(tenantId, item.getTenantId()) && Objects.equals(userId, item.getUserId()))
            .findFirst()
            .orElse(null);
        if (character == null) {
            return response(EvePermissionTreeStatus.CHARACTER_NOT_BOUND, "当前账号未绑定有效的 EVE 角色", null, emptyGamePermissions(), siteRoles, emptyScopes());
        }
        EvePermissionTreeResp.CharacterInfo characterInfo = new EvePermissionTreeResp.CharacterInfo(character
            .getId(), character.getCharacterId(), character.getName(), character.getCorporationId());
        EveCharacterRoleSnapshotDO snapshot = snapshotMapper.selectLatest(tenantId, character.getId());
        if (snapshot != null && (!Objects.equals(tenantId, snapshot.getTenantId()) || !Objects.equals(character
            .getId(), snapshot.getCharacterRefId()) || snapshot.getSourceExpiresAt() == null || !snapshot
                .getSourceExpiresAt()
                .isAfter(checkedAt))) {
            snapshot = null;
        }
        EveAuthorizationDO authorization = authorizationMapper.selectByUserCharacter(tenantId, userId, character
            .getId())
            .stream()
            .filter(item -> Objects.equals(tenantId, item.getTenantId()) && Objects.equals(userId, item
                .getUserId()) && Objects.equals(character.getId(), item.getCharacterRefId()))
            .findFirst()
            .orElse(null);
        EvePermissionTreeResp.ScopeSection scopes = toScopes(authorization);
        if (snapshot == null) {
            EvePermissionTreeStatus status = authorizationStatus(authorization, EvePermissionTreeStatus.SNAPSHOT_UNAVAILABLE);
            return response(status, message(status), characterInfo, catalogWithoutSnapshot(), siteRoles, scopes);
        }

        EvePermissionTreeStatus status = authorizationStatus(authorization, EvePermissionTreeStatus.READY);
        return response(status, message(status), characterInfo, toGamePermissions(snapshot), siteRoles, scopes);
    }

    /** 授权缺失或失效优先于候选正常状态。 */
    private static EvePermissionTreeStatus authorizationStatus(EveAuthorizationDO authorization,
                                                               EvePermissionTreeStatus candidate) {
        if (authorization == null) {
            return EvePermissionTreeStatus.AUTHORIZATION_UNAVAILABLE;
        }
        return EveAuthorizationStatus.ACTIVE.equals(authorization.getStatus())
            ? candidate
            : EvePermissionTreeStatus.AUTHORIZATION_INACTIVE;
    }

    /** 返回各状态可直接展示的短说明。 */
    private static String message(EvePermissionTreeStatus status) {
        return switch (status) {
            case READY -> "权限树已按最近一次国服数据构建";
            case CHARACTER_NOT_BOUND -> "当前账号未绑定有效的 EVE 角色";
            case SNAPSHOT_UNAVAILABLE -> "角色已绑定，尚未取得游戏权限快照，请刷新游戏权限";
            case AUTHORIZATION_UNAVAILABLE -> "当前角色尚未完成 EVE 授权";
            case AUTHORIZATION_INACTIVE -> "当前 EVE 授权已失效，请重新授权";
        };
    }

    /** 构建含 CEO 独立节点和四个严格范围分组的游戏权限树。 */
    private static EvePermissionTreeResp.GamePermissionSection toGamePermissions(EveCharacterRoleSnapshotDO snapshot) {
        Map<CorporationRoleScope, List<String>> ownedByScope = new EnumMap<>(CorporationRoleScope.class);
        ownedByScope.put(CorporationRoleScope.ROLES, snapshot.getRoles());
        ownedByScope.put(CorporationRoleScope.ROLES_AT_HQ, snapshot.getRolesAtHq());
        ownedByScope.put(CorporationRoleScope.ROLES_AT_BASE, snapshot.getRolesAtBase());
        ownedByScope.put(CorporationRoleScope.ROLES_AT_OTHER, snapshot.getRolesAtOther());
        List<EvePermissionTreeResp.GamePermissionGroup> groups = Arrays.stream(CorporationRoleScope.values())
            .map(scope -> toGroup(scope, ownedByScope.get(scope), snapshot.getCapturedAt()))
            .toList();
        EvePermissionTreeResp.GamePermissionNode ceo = ceoNode(Boolean.TRUE.equals(snapshot.getIsCeo()), snapshot
            .getCapturedAt());
        return new EvePermissionTreeResp.GamePermissionSection(ceo, groups, snapshot.getCapturedAt(), snapshot
            .getSourceExpiresAt(), Boolean.TRUE.equals(snapshot.getSourceExpiryEstimated()), ROLE_DATA_SOURCE);
    }

    /** 无快照时仍返回完整目录，但通过顶层状态明确表示拥有情况尚未验证。 */
    private static EvePermissionTreeResp.GamePermissionSection catalogWithoutSnapshot() {
        List<EvePermissionTreeResp.GamePermissionGroup> groups = Arrays.stream(CorporationRoleScope.values())
            .map(scope -> toGroup(scope, List.of(), null))
            .toList();
        return new EvePermissionTreeResp.GamePermissionSection(ceoNode(false, null), groups, null, null, false, ROLE_DATA_SOURCE);
    }

    /** 未绑定角色时仍返回完整目录，所有拥有状态保持未验证的 false。 */
    private static EvePermissionTreeResp.GamePermissionSection emptyGamePermissions() {
        return catalogWithoutSnapshot();
    }

    /** 单个范围始终包含 49 项已知目录，并仅在该范围追加未知角色。 */
    private static EvePermissionTreeResp.GamePermissionGroup toGroup(CorporationRoleScope scope,
                                                                     List<String> rawOwnedCodes,
                                                                     LocalDateTime checkedAt) {
        Set<String> ownedCodes = normalizedCodes(rawOwnedCodes);
        List<EvePermissionTreeResp.GamePermissionNode> nodes = new ArrayList<>();
        Set<String> knownCodes = new LinkedHashSet<>();
        for (CorporationRoleDefinition role : EveCorporationRoleCatalog.all()) {
            knownCodes.add(role.code());
            nodes.add(toRoleNode(scope, role, ownedCodes.contains(role.code()), checkedAt));
        }
        ownedCodes.stream()
            .filter(code -> !knownCodes.contains(code))
            .map(EveCorporationRoleCatalog::resolve)
            .map(role -> toRoleNode(scope, role, true, checkedAt))
            .forEach(nodes::add);
        return new EvePermissionTreeResp.GamePermissionGroup("game-scope:" + scope.getFieldName(), scope
            .getDisplayName(), scope.getFieldName(), List.copyOf(nodes));
    }

    /** 规范化上游数组并保持未知角色原始顺序。 */
    private static Set<String> normalizedCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        codes.stream().filter(code -> code != null && !code.isBlank()).forEach(normalized::add);
        return normalized;
    }

    /** 转换单个角色节点，稳定 key 同时包含范围与原始 code。 */
    private static EvePermissionTreeResp.GamePermissionNode toRoleNode(CorporationRoleScope scope,
                                                                       CorporationRoleDefinition role,
                                                                       boolean owned,
                                                                       LocalDateTime checkedAt) {
        List<String> capabilities = role.capabilities().stream().map(Enum::name).sorted().toList();
        return new EvePermissionTreeResp.GamePermissionNode("game-role:" + scope.getFieldName() + ':' + role
            .code(), role.displayName(), role.code(), role.description(), owned, role.known(), scope
                .getFieldName(), capabilities, checkedAt, ROLE_DATA_SOURCE);
    }

    /** CEO 是独立军团身份事实，不混入四类角色数组。 */
    private static EvePermissionTreeResp.GamePermissionNode ceoNode(boolean owned, LocalDateTime checkedAt) {
        return new EvePermissionTreeResp.GamePermissionNode("game-identity:ceo", "军团 CEO", "CEO", "由军团公开资料的 ceo_id 与当前角色交叉验证", owned, true, "corporation_identity", List
            .of("CORPORATION_ADMINISTRATION"), checkedAt, CEO_DATA_SOURCE);
    }

    /** 转换本站角色并明确区分游戏派生角色和人工配置业务角色。 */
    private static EvePermissionTreeResp.SiteRoleSection toSiteRoles(List<EveSiteRoleDTO> roles,
                                                                     Set<String> effectivePermissions,
                                                                     LocalDateTime checkedAt) {
        List<EvePermissionTreeResp.SiteRoleItem> items = roles.stream().map(role -> {
            boolean derived = EveDerivedIdentity.ROLE_CODES.contains(role.code());
            String source = resolveRoleSource(derived, role.system());
            return new EvePermissionTreeResp.SiteRoleItem(role.id(), role.code(), role.name(), role
                .description(), source, derived, role.system(), role.dataScope(), role.permissions());
        }).toList();
        List<String> permissions = effectivePermissions == null
            ? List.of()
            : effectivePermissions.stream().sorted().toList();
        return new EvePermissionTreeResp.SiteRoleSection(items, permissions, checkedAt, SITE_ROLE_DATA_SOURCE);
    }

    /** 区分游戏派生、系统内置和人工配置角色的数据来源。 */
    private static String resolveRoleSource(boolean derived, boolean system) {
        if (derived) {
            return "EVE_DERIVED";
        }
        return system ? "SYSTEM_BUILT_IN" : "MANUAL_CONFIGURATION";
    }

    /** 合并身份必需、模块相关和授权额外 Scope，并分别标记是否已授予。 */
    private EvePermissionTreeResp.ScopeSection toScopes(EveAuthorizationDO authorization) {
        Set<String> owned = authorization == null || authorization.getScopes() == null
            ? Set.of()
            : new LinkedHashSet<>(authorization.getScopes());
        Set<String> required = scopePolicy.plannedScopes();
        Map<String, Set<String>> capabilitiesByScope = new LinkedHashMap<>();
        Arrays.stream(EveCapability.values())
            .forEach(capability -> capabilitiesByScope.computeIfAbsent(capability
                .getScope(), ignored -> new LinkedHashSet<>()).add(capability.getKey()));
        Set<String> all = new LinkedHashSet<>(required);
        all.addAll(capabilitiesByScope.keySet());
        owned.stream().sorted().forEach(all::add);
        List<EvePermissionTreeResp.ScopeItem> items = all.stream().map(scope -> {
            List<String> capabilities = capabilitiesByScope.getOrDefault(scope, Set.of()).stream().sorted().toList();
            String displayName = scopeDisplayName(scope, capabilities);
            return new EvePermissionTreeResp.ScopeItem("oauth-scope:" + scope, scope, displayName, owned
                .contains(scope), required.contains(scope), capabilities);
        }).toList();
        LocalDateTime updatedAt = authorization == null
            ? null
            : firstNonNull(authorization.getLastVerifiedAt(), authorization.getUpdateTime(), authorization
                .getCreateTime());
        return new EvePermissionTreeResp.ScopeSection(authorization == null
            ? null
            : authorization.getStatus(), items, updatedAt, SCOPE_DATA_SOURCE);
    }

    /** 返回用户可理解的授权用途，避免在权限树中只显示机器 Scope 名称。 */
    private static String scopeDisplayName(String scope, List<String> capabilities) {
        if (!capabilities.isEmpty()) {
            return "模块授权：" + String.join("、", capabilities);
        }
        return switch (scope) {
            case "esi-corporations.read_divisions.v1" -> "资产分部与机库名称";
            case "esi-corporations.track_members.v1" -> "成员位置、舰船与上下线追踪";
            case "esi-universe.read_structures.v1" -> "资产所在建筑位置解析";
            case "esi-mail.send_mail.v1" -> "游戏内邮件发送";
            default -> scope;
        };
    }

    /** 返回首个非空时间。 */
    private static LocalDateTime firstNonNull(LocalDateTime... values) {
        return Arrays.stream(values).filter(value -> value != null).findFirst().orElse(null);
    }

    /** 创建无授权时仍包含必需与能力 Scope 的结构化空态。 */
    private EvePermissionTreeResp.ScopeSection emptyScopes() {
        return toScopes(null);
    }

    /** 统一创建响应。 */
    private static EvePermissionTreeResp response(EvePermissionTreeStatus status,
                                                  String message,
                                                  EvePermissionTreeResp.CharacterInfo character,
                                                  EvePermissionTreeResp.GamePermissionSection gamePermissions,
                                                  EvePermissionTreeResp.SiteRoleSection siteRoles,
                                                  EvePermissionTreeResp.ScopeSection scopes) {
        return new EvePermissionTreeResp(status, message, character, gamePermissions, siteRoles, scopes);
    }
}
