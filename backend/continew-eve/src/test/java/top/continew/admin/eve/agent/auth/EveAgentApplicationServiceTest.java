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

package top.continew.admin.eve.agent.auth;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.agent.EveAgentPrincipal;
import top.continew.admin.eve.agent.model.EveAgentCredentialResp;
import top.continew.admin.eve.mapper.EveAgentApplicationMapper;
import top.continew.admin.eve.mapper.EveAgentAuditMapper;
import top.continew.admin.eve.mapper.EveAgentGrantMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.model.EveMeContextResp;
import top.continew.admin.eve.model.entity.EveAgentApplicationDO;
import top.continew.admin.eve.model.entity.EveAgentAuditDO;
import top.continew.admin.eve.model.entity.EveAgentGrantDO;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.enums.EveCorporationStatus;
import top.continew.admin.eve.service.EveContextService;
import top.continew.starter.core.exception.BusinessException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 军团级外部 Agent 授权、认证和审计测试。
 *
 * @author zhaoyuqing
 */
class EveAgentApplicationServiceTest {

    /** 创建时仅返回一次原始凭据，持久层只能看到摘要和展示前缀。 */
    @Test
    void shouldCreateHashedCredentialWithoutLeakingTokenToAudit() {
        Fixture fixture = new Fixture();
        fixture.allowManagement();
        ArgumentCaptor<EveAgentApplicationDO> applicationCaptor = ArgumentCaptor.forClass(EveAgentApplicationDO.class);
        ArgumentCaptor<EveAgentAuditDO> auditCaptor = ArgumentCaptor.forClass(EveAgentAuditDO.class);
        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(fixture.user);
            EveAgentCredentialResp result = fixture.service.createApplication(" QQ 机器人 ", 30);
            verify(fixture.applications).insert(applicationCaptor.capture());
            verify(fixture.audits).insert(auditCaptor.capture());

            EveAgentApplicationDO stored = applicationCaptor.getValue();
            assertThat(result.token()).startsWith("eve_agent_").hasSize(53);
            assertThat(stored.getName()).isEqualTo("QQ 机器人");
            assertThat(stored.getTokenHash()).matches("[0-9a-f]{64}").isNotEqualTo(result.token());
            assertThat(stored.getTokenPrefix()).startsWith("eve_agent_").endsWith("...");
            assertThat(auditCaptor.getValue().toString()).doesNotContain(result.token(), stored.getTokenHash());
        }
    }

    /** 服务层仍要校验名称与有效期，不能只依赖 Controller Bean Validation。 */
    @Test
    void shouldValidateCreationArgumentsInService() {
        Fixture fixture = new Fixture();
        fixture.allowManagement();
        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(fixture.user);
            assertThatThrownBy(() -> fixture.service.createApplication("应用", 0)).hasMessage("有效期必须在 1 到 365 天之间");
            assertThatThrownBy(() -> fixture.service.createApplication("x".repeat(81), 30))
                .hasMessage("应用名称不能超过 80 个字符");
        }
        verifyNoInteractions(fixture.audits);
    }

    /** 有效应用必须重新核对所有者军团，但认证阶段不能预选数据军团。 */
    @Test
    void shouldAuthenticateActiveApplicationAgainstCorporationBinding() {
        Fixture fixture = new Fixture();
        EveAgentApplicationDO application = fixture.activeApplication();
        when(fixture.applications.selectByTokenHash(anyString())).thenReturn(application);
        when(fixture.corporations.selectActiveByTenantAndRefId(10L, 100L)).thenReturn(fixture.corporation);

        EveAgentPrincipal principal = fixture.service.authenticate(fixture.token);

        assertThat(principal).isEqualTo(new EveAgentPrincipal(900L, null, null, null, "QQ 机器人"));
        verify(fixture.applications).selectByTokenHash(EveAgentApplicationService.hashToken(fixture.token));
    }

    /** 格式错误、撤销、过期和军团边界漂移均返回统一认证失败。 */
    @Test
    void shouldRejectInvalidRevokedExpiredAndMismatchedCredentials() {
        Fixture fixture = new Fixture();
        assertThatThrownBy(() -> fixture.service.authenticate("eve_agent_" + "!".repeat(43)))
            .isInstanceOf(BusinessException.class)
            .hasMessage("外部 Agent 凭据无效或已失效");

        EveAgentApplicationDO application = fixture.activeApplication();
        application.setStatus("REVOKED");
        when(fixture.applications.selectByTokenHash(anyString())).thenReturn(application);
        assertThatThrownBy(() -> fixture.service.authenticate(fixture.token)).hasMessage("外部 Agent 凭据无效或已失效");

        application.setStatus("ACTIVE");
        application.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        assertThatThrownBy(() -> fixture.service.authenticate(fixture.token)).hasMessage("外部 Agent 凭据无效或已失效");

        application.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        EveCorporationDO moved = fixture.corporation();
        moved.setCorporationId(201L);
        when(fixture.corporations.selectActiveByTenantAndRefId(10L, 100L)).thenReturn(moved);
        assertThatThrownBy(() -> fixture.service.authenticate(fixture.token)).hasMessage("外部 Agent 凭据无效或已失效");
    }

    /** 普通成员或缺少专用权限的管理角色均不能管理外部应用。 */
    @Test
    void shouldRequireManagerRoleAndDedicatedPermission() {
        Fixture fixture = new Fixture();
        fixture.user.setRoleCodes(Set.of("corp_member"));
        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(fixture.user);
            assertThatThrownBy(fixture.service::listApplications).hasMessage("仅军团 CEO 或总监可以管理外部 Agent 授权");
            fixture.user.setRoleCodes(Set.of("corp_admin"));
            fixture.user.setPermissions(Set.of("eve:mail:view"));
            assertThatThrownBy(fixture.service::listApplications).hasMessage("仅军团 CEO 或总监可以管理外部 Agent 授权");
        }
        verifyNoInteractions(fixture.contexts);
    }

    /** 应用 ID 必须属于当前军团，跨军团轮换不能泄露应用是否存在。 */
    @Test
    void shouldRejectCrossCorporationApplicationManagement() {
        Fixture fixture = new Fixture();
        fixture.allowManagement();
        when(fixture.applications.selectManaged(10L, 100L, 999L)).thenReturn(null);
        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(fixture.user);
            assertThatThrownBy(() -> fixture.service.rotateCredential(999L)).hasMessage("外部 Agent 应用不存在");
        }
        verify(fixture.applications, never())
            .rotateCredential(anyLong(), anyLong(), anyLong(), anyString(), anyString(), any(), anyLong());
    }

    /** 原子轮换只有数据库成功修改一行时才返回新凭据。 */
    @Test
    void shouldReturnNewTokenOnlyAfterAtomicRotation() {
        Fixture fixture = new Fixture();
        fixture.allowManagement();
        EveAgentApplicationDO application = fixture.activeApplication();
        when(fixture.applications.selectManaged(10L, 100L, 900L)).thenReturn(application);
        when(fixture.applications
            .rotateCredential(eq(10L), eq(100L), eq(900L), anyString(), anyString(), any(), eq(20L))).thenReturn(1);
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(fixture.user);
            EveAgentCredentialResp result = fixture.service.rotateCredential(900L);
            verify(fixture.applications).rotateCredential(eq(10L), eq(100L), eq(900L), hashCaptor
                .capture(), anyString(), any(), eq(20L));
            assertThat(result.token()).startsWith("eve_agent_").isNotEqualTo(fixture.token);
            assertThat(hashCaptor.getValue()).isEqualTo(EveAgentApplicationService.hashToken(result.token()));
        }
    }

    /** 并发轮换或撤销导致更新零行时必须失败，不能伪造成功审计。 */
    @Test
    void shouldRejectConcurrentStateChanges() {
        Fixture fixture = new Fixture();
        fixture.allowManagement();
        EveAgentApplicationDO application = fixture.activeApplication();
        when(fixture.applications.selectManaged(10L, 100L, 900L)).thenReturn(application);
        when(fixture.applications
            .rotateCredential(eq(10L), eq(100L), eq(900L), anyString(), anyString(), any(), eq(20L))).thenReturn(0);
        when(fixture.applications.revoke(eq(10L), eq(100L), eq(900L), any(), eq(20L))).thenReturn(0);
        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(fixture.user);
            assertThatThrownBy(() -> fixture.service.rotateCredential(900L)).hasMessage("应用状态已变化，请刷新后重试");
            assertThatThrownBy(() -> fixture.service.revoke(900L)).hasMessage("应用已撤销或状态已变化");
        }
        verifyNoInteractions(fixture.audits);
    }

    /** 调用审计只保存固定工具标识，并收紧负数指标。 */
    @Test
    void shouldSanitizeAgentCallAuditWithoutContent() {
        Fixture fixture = new Fixture();
        ArgumentCaptor<EveAgentAuditDO> auditCaptor = ArgumentCaptor.forClass(EveAgentAuditDO.class);
        EveAgentPrincipal principal = new EveAgentPrincipal(900L, 10L, 100L, 200L, "QQ 机器人");
        when(fixture.applications.selectAnyById(900L)).thenReturn(fixture.activeApplication());

        fixture.service.recordCall(principal, "mail.query?keyword=secret", true, -10L, -2);

        verify(fixture.audits).insert(auditCaptor.capture());
        EveAgentAuditDO audit = auditCaptor.getValue();
        assertThat(audit.getToolName()).isEqualTo("unknown");
        assertThat(audit.getDurationMs()).isZero();
        assertThat(audit.getResultCount()).isZero();
        assertThat(audit.toString()).doesNotContain("secret");
        verify(fixture.applications).touchLastUsed(10L, 100L, 900L, audit.getOccurredAt());
    }

    /** 多军团应用必须显式选择目标，且只能解析当前有效授权。 */
    @Test
    void shouldRequireCorporationSelectionForMultipleActiveGrants() {
        Fixture fixture = new Fixture();
        EveAgentApplicationDO application = fixture.activeApplication();
        when(fixture.applications.selectActiveById(900L)).thenReturn(application);
        when(fixture.corporations.selectActiveByTenantAndRefId(10L, 100L)).thenReturn(fixture.corporation);
        EveAgentGrantDO first = fixture.grant(10L, 100L, 200L);
        EveAgentGrantDO second = fixture.grant(11L, 101L, 201L);
        EveCorporationDO other = fixture.corporation();
        other.setTenantId(11L);
        other.setId(101L);
        other.setCorporationId(201L);
        other.setName("第二军团");
        when(fixture.grants.selectActiveByApplication(900L)).thenReturn(List.of(first, second));
        when(fixture.corporations.selectActiveByTenantAndRefId(11L, 101L)).thenReturn(other);
        EveAgentPrincipal unscoped = new EveAgentPrincipal(900L, null, null, null, "QQ 机器人");

        assertThatThrownBy(() -> fixture.service.resolveCorporation(unscoped, null))
            .hasMessage("该应用已授权多个军团，请明确提供 corporationId");
        assertThat(fixture.service.resolveCorporation(unscoped, "201"))
            .isEqualTo(new EveAgentPrincipal(900L, 11L, 101L, 201L, "QQ 机器人"));
    }

    /** 目标军团管理者重新确认后可原子恢复已撤销授权。 */
    @Test
    void shouldReactivateRevokedGrantAfterExplicitApproval() {
        Fixture fixture = new Fixture();
        fixture.allowManagement();
        EveAgentApplicationDO application = fixture.activeApplication();
        application.setInvitationCode("A".repeat(32));
        EveAgentGrantDO revoked = fixture.grant(10L, 100L, 200L);
        revoked.setStatus("REVOKED");
        when(fixture.applications.selectByInvitationCode("A".repeat(32))).thenReturn(application);
        when(fixture.corporations.selectActiveByTenantAndRefId(10L, 100L)).thenReturn(fixture.corporation);
        when(fixture.grants.selectByApplicationAndCorporation(900L, 10L, 100L)).thenReturn(revoked);
        when(fixture.grants.reactivate(eq(900L), eq(10L), eq(100L), any(), eq(20L))).thenReturn(1);
        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(fixture.user);
            assertThat(fixture.service.approveInvitation("A".repeat(32), true).status()).isEqualTo("ACTIVE");
        }
        verify(fixture.grants).reactivate(eq(900L), eq(10L), eq(100L), any(), eq(20L));
        verify(fixture.grants, never()).insert(any(EveAgentGrantDO.class));
    }

    /** 已有效授权重复批准保持幂等，不刷新授权时间或重复写审计。 */
    @Test
    void shouldKeepActiveGrantUnchangedOnRepeatedApproval() {
        Fixture fixture = new Fixture();
        fixture.allowManagement();
        EveAgentApplicationDO application = fixture.activeApplication();
        EveAgentGrantDO active = fixture.grant(10L, 100L, 200L);
        LocalDateTime grantedAt = active.getGrantedAt();
        when(fixture.applications.selectByInvitationCode("A".repeat(32))).thenReturn(application);
        when(fixture.corporations.selectActiveByTenantAndRefId(10L, 100L)).thenReturn(fixture.corporation);
        when(fixture.grants.selectByApplicationAndCorporation(900L, 10L, 100L)).thenReturn(active);
        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(fixture.user);
            assertThat(fixture.service.approveInvitation("A".repeat(32), true).grantedAt()).isEqualTo(grantedAt);
        }
        verify(fixture.grants, never()).reactivate(anyLong(), anyLong(), anyLong(), any(), anyLong());
        verifyNoInteractions(fixture.audits);
    }

    /** 测试所需依赖和固定军团边界。 */
    private static final class Fixture {
        private final EveAgentApplicationMapper applications = mock(EveAgentApplicationMapper.class);
        private final EveAgentAuditMapper audits = mock(EveAgentAuditMapper.class);
        private final EveAgentGrantMapper grants = mock(EveAgentGrantMapper.class);
        private final EveCorporationMapper corporations = mock(EveCorporationMapper.class);
        private final EveContextService contexts = mock(EveContextService.class);
        private final EveAgentApplicationService service = new EveAgentApplicationService(applications, audits, grants, corporations, contexts);
        private final UserContext user = user();
        private final EveCorporationDO corporation = corporation();
        private final String token = "eve_agent_" + "A".repeat(43);

        private Fixture() {
            doAnswer(invocation -> {
                EveAgentApplicationDO application = invocation.getArgument(0);
                application.setId(900L);
                application.setCreateTime(LocalDateTime.now());
                return 1;
            }).when(applications).insert(any(EveAgentApplicationDO.class));
            doAnswer(invocation -> {
                EveAgentAuditDO audit = invocation.getArgument(0);
                audit.setId(901L);
                return 1;
            }).when(audits).insert(any(EveAgentAuditDO.class));
            doAnswer(invocation -> {
                EveAgentGrantDO grant = invocation.getArgument(0);
                grant.setId(902L);
                return 1;
            }).when(grants).insert(any(EveAgentGrantDO.class));
        }

        /** 配置当前用户、上下文和军团记录为可管理状态。 */
        private void allowManagement() {
            when(contexts.getCurrentContext())
                .thenReturn(new EveMeContextResp(10L, null, new EveMeContextResp.CorporationInfo(200L, "测试军团", "TEST"), "corp_admin", null, null, null, null));
            when(corporations.selectByTenantAndCorporationId(10L, 200L)).thenReturn(corporation);
        }

        /** 构造有效外部应用。 */
        private EveAgentApplicationDO activeApplication() {
            EveAgentApplicationDO application = new EveAgentApplicationDO();
            application.setId(900L);
            application.setTenantId(10L);
            application.setCorporationRefId(100L);
            application.setCorporationId(200L);
            application.setName("QQ 机器人");
            application.setInvitationCode("A".repeat(32));
            application.setTokenHash(EveAgentApplicationService.hashToken(token));
            application.setTokenPrefix("eve_agent_AAAAAAAA...");
            application.setStatus("ACTIVE");
            application.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
            application.setCreateTime(LocalDateTime.now());
            return application;
        }

        /** 构造有效军团授权。 */
        private EveAgentGrantDO grant(Long tenantId, Long corporationRefId, Long corporationId) {
            EveAgentGrantDO grant = new EveAgentGrantDO();
            grant.setId(corporationRefId + 800L);
            grant.setTenantId(tenantId);
            grant.setApplicationId(900L);
            grant.setCorporationRefId(corporationRefId);
            grant.setCorporationId(corporationId);
            grant.setStatus("ACTIVE");
            grant.setGrantedAt(LocalDateTime.now(ZoneOffset.UTC));
            return grant;
        }

        /** 构造管理用户。 */
        private static UserContext user() {
            UserContext context = new UserContext();
            context.setId(20L);
            context.setTenantId(10L);
            context.setRoleCodes(Set.of("corp_admin"));
            context.setPermissions(Set.of("eve:agents:manage"));
            return context;
        }

        /** 构造有效军团绑定。 */
        private static EveCorporationDO corporation() {
            EveCorporationDO corporation = new EveCorporationDO();
            corporation.setId(100L);
            corporation.setTenantId(10L);
            corporation.setCorporationId(200L);
            corporation.setName("测试军团");
            corporation.setStatus(EveCorporationStatus.ACTIVE);
            return corporation;
        }
    }
}
