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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.agent.EveAgentPrincipal;
import top.continew.admin.eve.agent.model.EveAgentApplicationResp;
import top.continew.admin.eve.agent.model.EveAgentAuditResp;
import top.continew.admin.eve.agent.model.EveAgentCredentialResp;
import top.continew.admin.eve.mapper.EveAgentApplicationMapper;
import top.continew.admin.eve.mapper.EveAgentAuditMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.model.EveMeContextResp;
import top.continew.admin.eve.model.entity.EveAgentApplicationDO;
import top.continew.admin.eve.model.entity.EveAgentAuditDO;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.enums.EveCorporationStatus;
import top.continew.admin.eve.service.EveContextService;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 管理并认证军团级外部 Agent 只读应用。
 *
 * <p>原始凭据只在创建和轮换响应中出现一次，数据库仅保存 SHA-256 摘要。</p>
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveAgentApplicationService {

    private static final String TOKEN_PREFIX = "eve_agent_";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_REVOKED = "REVOKED";
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^eve_agent_[A-Za-z0-9_-]{43}$");
    private static final Set<String> MANAGER_ROLE_CODES = Set.of("corp_owner", "corp_admin");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EveAgentApplicationMapper applicationMapper;
    private final EveAgentAuditMapper auditMapper;
    private final EveCorporationMapper corporationMapper;
    private final EveContextService contextService;

    /** 查询当前军团的外部 Agent 应用。 */
    public List<EveAgentApplicationResp> listApplications() {
        ManagementContext context = requireManagerContext();
        return applicationMapper.selectList(new LambdaQueryWrapper<EveAgentApplicationDO>()
            .eq(EveAgentApplicationDO::getTenantId, context.tenantId())
            .eq(EveAgentApplicationDO::getCorporationRefId, context.corporation().getId())
            .eq(EveAgentApplicationDO::getDeleted, 0L)
            .orderByDesc(EveAgentApplicationDO::getCreateTime))
            .stream()
            .map(EveAgentApplicationService::toResponse)
            .toList();
    }

    /** 创建军团级只读 Agent 应用并仅返回一次原始凭据。 */
    @Transactional(rollbackFor = Exception.class)
    public EveAgentCredentialResp createApplication(String name, int expiresInDays) {
        ManagementContext context = requireManagerContext();
        if (expiresInDays < 1 || expiresInDays > 365) {
            throw new BusinessException("有效期必须在 1 到 365 天之间");
        }
        String token = generateToken();
        LocalDateTime now = utcNow();
        EveAgentApplicationDO application = new EveAgentApplicationDO();
        application.setTenantId(context.tenantId());
        application.setCorporationRefId(context.corporation().getId());
        application.setCorporationId(context.corporation().getCorporationId());
        application.setName(normalizeName(name));
        application.setTokenHash(hashToken(token));
        application.setTokenPrefix(displayPrefix(token));
        application.setStatus(STATUS_ACTIVE);
        application.setExpiresAt(now.plusDays(expiresInDays));
        application.setLastRotatedAt(now);
        application.setCreateUser(context.userId());
        applicationMapper.insert(application);
        recordManagement(application, context.userId(), "system.application_created");
        return new EveAgentCredentialResp(toResponse(application), token);
    }

    /** 原子轮换当前军团仍有效应用的凭据，旧凭据在提交后立即失效。 */
    @Transactional(rollbackFor = Exception.class)
    public EveAgentCredentialResp rotateCredential(Long applicationId) {
        ManagementContext context = requireManagerContext();
        EveAgentApplicationDO application = requireManaged(context, applicationId);
        LocalDateTime now = utcNow();
        if (!STATUS_ACTIVE.equals(application.getStatus()) || application.getExpiresAt() == null || !application
            .getExpiresAt()
            .isAfter(now)) {
            throw new BusinessException("应用已撤销或已过期，请重新创建授权");
        }
        String token = generateToken();
        String tokenPrefix = displayPrefix(token);
        int changed = applicationMapper.rotateCredential(context.tenantId(), context.corporation()
            .getId(), applicationId, hashToken(token), tokenPrefix, now, context.userId());
        if (changed != 1) {
            throw new BusinessException("应用状态已变化，请刷新后重试");
        }
        application.setTokenPrefix(tokenPrefix);
        application.setLastRotatedAt(now);
        recordManagement(application, context.userId(), "system.token_rotated");
        return new EveAgentCredentialResp(toResponse(application), token);
    }

    /** 撤销当前军团应用，重复或并发撤销不会覆盖原状态。 */
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long applicationId) {
        ManagementContext context = requireManagerContext();
        EveAgentApplicationDO application = requireManaged(context, applicationId);
        LocalDateTime now = utcNow();
        int changed = applicationMapper.revoke(context.tenantId(), context.corporation()
            .getId(), applicationId, now, context.userId());
        if (changed != 1) {
            throw new BusinessException("应用已撤销或状态已变化");
        }
        application.setStatus(STATUS_REVOKED);
        application.setRevokedAt(now);
        recordManagement(application, context.userId(), "system.application_revoked");
    }

    /** 分页查询当前军团的脱敏调用与管理审计。 */
    public PageResp<EveAgentAuditResp> pageAudit(int page, int size) {
        ManagementContext context = requireManagerContext();
        Page<EveAgentAuditDO> result = auditMapper
            .selectPage(new Page<>(page, size), new LambdaQueryWrapper<EveAgentAuditDO>()
                .eq(EveAgentAuditDO::getTenantId, context.tenantId())
                .eq(EveAgentAuditDO::getCorporationRefId, context.corporation().getId())
                .eq(EveAgentAuditDO::getDeleted, 0L)
                .orderByDesc(EveAgentAuditDO::getOccurredAt)
                .orderByDesc(EveAgentAuditDO::getId));
        return new PageResp<>(result.getRecords()
            .stream()
            .map(EveAgentApplicationService::toAuditResponse)
            .toList(), result.getTotal());
    }

    /**
     * 使用原始 Bearer token 认证军团应用。
     *
     * <p>该入口不信任请求中的租户头或用户会话，每次均重新校验应用、有效期和军团状态。</p>
     */
    public EveAgentPrincipal authenticate(String bearerToken) {
        String token = normalizeToken(bearerToken);
        EveAgentApplicationDO application = applicationMapper.selectByTokenHash(hashToken(token));
        LocalDateTime now = utcNow();
        if (application == null || !STATUS_ACTIVE.equals(application.getStatus()) || application
            .getExpiresAt() == null || !application.getExpiresAt().isAfter(now)) {
            throw invalidCredential();
        }
        EveCorporationDO corporation = corporationMapper.selectActiveByTenantAndRefId(application
            .getTenantId(), application.getCorporationRefId());
        if (corporation == null || !EveCorporationStatus.ACTIVE.equals(corporation.getStatus()) || !application
            .getCorporationId()
            .equals(corporation.getCorporationId())) {
            throw invalidCredential();
        }
        return new EveAgentPrincipal(application.getId(), application.getTenantId(), application
            .getCorporationRefId(), application.getCorporationId(), application.getName());
    }

    /** 记录一次不含查询值和正文的 Agent 工具调用审计。 */
    @Transactional(rollbackFor = Exception.class)
    public void recordCall(EveAgentPrincipal principal,
                           String toolName,
                           boolean success,
                           long durationMs,
                           int resultCount) {
        if (principal == null) {
            return;
        }
        EveAgentAuditDO audit = newAudit(principal.applicationId(), principal.applicationName(), principal
            .tenantId(), principal
                .corporationRefId(), "AGENT", null, normalizeToolName(toolName), success, durationMs, resultCount);
        auditMapper.insert(audit);
        if (success) {
            applicationMapper.touchLastUsed(principal.tenantId(), principal.corporationRefId(), principal
                .applicationId(), audit.getOccurredAt());
        }
    }

    /** 管理操作同时校验实时角色、站内权限与当前军团绑定。 */
    private ManagementContext requireManagerContext() {
        UserContext user = UserContextHolder.getContext();
        if (user == null || user.getId() == null || user.getTenantId() == null || user.getRoleCodes() == null || user
            .getRoleCodes()
            .stream()
            .noneMatch(MANAGER_ROLE_CODES::contains) || user.getPermissions() == null || !hasManagePermission(user)) {
            throw new BusinessException("仅军团 CEO 或总监可以管理外部 Agent 授权");
        }
        EveMeContextResp current = contextService.getCurrentContext();
        if (current == null || current.corporation() == null || !user.getTenantId().equals(current.tenantId())) {
            throw new BusinessException("当前账号没有可管理的军团");
        }
        EveCorporationDO corporation = corporationMapper.selectByTenantAndCorporationId(user.getTenantId(), current
            .corporation()
            .corporationId());
        if (corporation == null || !EveCorporationStatus.ACTIVE.equals(corporation.getStatus())) {
            throw new BusinessException("当前军团绑定不可用");
        }
        return new ManagementContext(user.getTenantId(), user.getId(), corporation);
    }

    /** 超级权限与专用权限均可通过，但角色仍必须是 CEO 或总监。 */
    private static boolean hasManagePermission(UserContext context) {
        return context.getPermissions().contains("eve:agents:manage") || context.getPermissions().contains("*:*:*");
    }

    /** 读取当前军团应用，跨租户或跨军团 ID 统一按不存在处理。 */
    private EveAgentApplicationDO requireManaged(ManagementContext context, Long applicationId) {
        EveAgentApplicationDO application = applicationId == null
            ? null
            : applicationMapper.selectManaged(context.tenantId(), context.corporation().getId(), applicationId);
        if (application == null) {
            throw new BusinessException("外部 Agent 应用不存在");
        }
        return application;
    }

    /** 写入不含凭据的授权管理审计。 */
    private void recordManagement(EveAgentApplicationDO application, Long userId, String event) {
        auditMapper.insert(newAudit(application.getId(), application.getName(), application.getTenantId(), application
            .getCorporationRefId(), "SITE_USER", userId, event, true, 0L, 0));
    }

    /** 构造统一审计记录并收紧数值边界。 */
    private static EveAgentAuditDO newAudit(Long applicationId,
                                            String applicationName,
                                            Long tenantId,
                                            Long corporationRefId,
                                            String actorType,
                                            Long actorUserId,
                                            String toolName,
                                            boolean success,
                                            long durationMs,
                                            int resultCount) {
        EveAgentAuditDO audit = new EveAgentAuditDO();
        audit.setTenantId(tenantId);
        audit.setApplicationId(applicationId);
        audit.setApplicationName(applicationName);
        audit.setCorporationRefId(corporationRefId);
        audit.setActorType(actorType);
        audit.setActorUserId(actorUserId);
        audit.setToolName(toolName);
        audit.setSuccess(success);
        audit.setDurationMs(Math.max(durationMs, 0L));
        audit.setResultCount(Math.max(resultCount, 0));
        audit.setOccurredAt(utcNow());
        return audit;
    }

    /** 生成 256 位随机凭据，使用 URL 安全编码便于放入 Bearer 请求头。 */
    private static String generateToken() {
        byte[] random = new byte[32];
        SECURE_RANDOM.nextBytes(random);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    /** 仅保留固定前缀和少量随机字符供管理员区分应用凭据。 */
    private static String displayPrefix(String token) {
        return token.substring(0, Math.min(token.length(), TOKEN_PREFIX.length() + 8)) + "...";
    }

    /** 计算凭据摘要，原始值不得持久化或写审计。 */
    static String hashToken(String token) {
        try {
            return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", e);
        }
    }

    /** 只接受本站生成的高熵原始 token，不接受租户或用户标识替代凭据。 */
    private static String normalizeToken(String token) {
        if (token == null || !TOKEN_PATTERN.matcher(token).matches()) {
            throw invalidCredential();
        }
        return token;
    }

    /** 应用名称用于管理展示，去除首尾空白并限制异常调用。 */
    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("应用名称不能为空");
        }
        String normalized = name.trim();
        if (normalized.length() > 80) {
            throw new BusinessException("应用名称不能超过 80 个字符");
        }
        return normalized;
    }

    /** 工具名只记录固定标识，不允许审计表接收查询正文。 */
    private static String normalizeToolName(String toolName) {
        if (toolName == null || !toolName.matches("[a-z0-9_.-]{1,96}")) {
            return "unknown";
        }
        return toolName;
    }

    /** 返回统一认证失败，避免根据消息判断应用存在性。 */
    private static BusinessException invalidCredential() {
        return new BusinessException("外部 Agent 凭据无效或已失效");
    }

    /** 使用 UTC 保存外部授权与审计的业务时间。 */
    private static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /** 转换不含凭据摘要的应用 DTO。 */
    private static EveAgentApplicationResp toResponse(EveAgentApplicationDO application) {
        return new EveAgentApplicationResp(application.getId(), application.getName(), application
            .getStatus(), application.getCreateTime(), application.getExpiresAt(), application
                .getLastUsedAt(), application.getTokenPrefix());
    }

    /** 转换不含查询参数和正文的审计 DTO。 */
    private static EveAgentAuditResp toAuditResponse(EveAgentAuditDO audit) {
        return new EveAgentAuditResp(audit.getId(), audit.getApplicationId(), audit.getApplicationName(), audit
            .getToolName(), Boolean.TRUE.equals(audit.getSuccess()), audit.getDurationMs() == null
                ? 0L
                : audit.getDurationMs(), audit.getResultCount() == null ? 0 : audit.getResultCount(), audit
                    .getOccurredAt());
    }

    /** 当前已验证管理者与军团上下文。 */
    private record ManagementContext(Long tenantId, Long userId, EveCorporationDO corporation) {
    }
}
