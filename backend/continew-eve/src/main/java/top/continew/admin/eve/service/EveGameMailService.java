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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.client.SerenityEsiClient;
import top.continew.admin.eve.client.SerenityEsiClientException;
import top.continew.admin.eve.client.SerenityTokenClientException;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveGameMailMapper;
import top.continew.admin.eve.mapper.EveGameMailSendAuditMapper;
import top.continew.admin.eve.model.EveGameMailDetailResp;
import top.continew.admin.eve.model.EveGameMailLabelResp;
import top.continew.admin.eve.model.EveGameMailPartyResp;
import top.continew.admin.eve.model.EveGameMailRecipientNameReq;
import top.continew.admin.eve.model.EveGameMailResp;
import top.continew.admin.eve.model.EveGameMailSendResp;
import top.continew.admin.eve.model.EveGameMailSyncResp;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveGameMailDO;
import top.continew.admin.eve.model.entity.EveGameMailSendAuditDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.model.serenity.SerenityEsiResponse;
import top.continew.admin.eve.model.serenity.SerenityGameMailDetailResponse;
import top.continew.admin.eve.model.serenity.SerenityGameMailHeaderResponse;
import top.continew.admin.eve.model.serenity.SerenityGameMailLabelResponse;
import top.continew.admin.eve.model.serenity.SerenityGameMailRecipient;
import top.continew.admin.eve.model.serenity.SerenityGameMailSendRequest;
import top.continew.admin.eve.model.serenity.SerenityGameMailUpdateRequest;
import top.continew.admin.eve.model.serenity.SerenityUniverseIdResponse;
import top.continew.admin.eve.model.serenity.SerenityUniverseIdsResponse;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 管理当前登录用户授权角色的游戏内邮件。
 *
 * <p>国服邮箱属于角色个人资源。本服务只使用当前会话的角色绑定和授权，不能读取或代发同军团其他成员邮件。</p>
 *
 * @author zhaoyuqing
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EveGameMailService {

    private static final String MAIL_READ_SCOPE = "esi-mail.read_mail.v1";
    private static final String MAIL_SEND_SCOPE = "esi-mail.send_mail.v1";
    private static final String MAIL_ORGANIZE_SCOPE = "esi-mail.organize_mail.v1";
    private static final Set<String> RECIPIENT_TYPES = Set.of("character", "corporation", "alliance", "mailing_list");

    private final EveCharacterMapper characterMapper;
    private final EveAuthorizationMapper authorizationMapper;
    private final EveGameMailMapper mailMapper;
    private final EveGameMailSendAuditMapper sendAuditMapper;
    private final EveAuthorizationLifecycleService authorizationLifecycleService;
    private final SerenityEsiClient esiClient;

    /** 分页返回当前授权角色已缓存的邮件头。 */
    public PageResp<EveGameMailResp> page(int page, int size, String keyword, Integer labelId) {
        MailSource source = requireCurrentSource(MAIL_READ_SCOPE);
        LambdaQueryWrapper<EveGameMailDO> query = new LambdaQueryWrapper<EveGameMailDO>()
            .eq(EveGameMailDO::getUserId, source.userId())
            .eq(EveGameMailDO::getCharacterRefId, source.character().getId())
            .eq(EveGameMailDO::getDeleted, 0L)
            .orderByDesc(EveGameMailDO::getSentAt)
            .orderByDesc(EveGameMailDO::getMailId);
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            query.and(item -> item.like(EveGameMailDO::getSubject, value).or().like(EveGameMailDO::getFromId, value));
        }
        if (labelId != null && labelId > 0) {
            query.apply("JSON_CONTAINS(labels, JSON_ARRAY({0}))", labelId);
        }
        Page<EveGameMailDO> result = mailMapper.selectPage(new Page<>(page, size), query);
        Map<Long, String> partyNames = resolvePartyNames(result.getRecords());
        return new PageResp<>(result.getRecords().stream().map(item -> toListResp(item, partyNames)).toList(), result
            .getTotal());
    }

    /** 读取游戏原生邮件分类及各分类未读数，用于收件箱、已发送和自定义文件夹导航。 */
    public List<EveGameMailLabelResp> labels() {
        MailSource source = requireCurrentSource(MAIL_READ_SCOPE);
        String accessToken = authorizationLifecycleService.ensureAccessToken(source.authorization());
        try {
            return esiClient.getGameMailLabelsWithMetadata(source.character().getCharacterId(), accessToken)
                .body()
                .stream()
                .filter(item -> item != null && item.labelId() != null && item.labelId() > 0 && item.name() != null)
                .map(EveGameMailService::toLabelResp)
                .toList();
        } catch (SerenityEsiClientException e) {
            // 分类只用于前端筛选；国服该可选端点不可用时，邮件列表仍应能读取本地缓存。
            log.warn("EVE 邮件分类读取失败，返回空分类供列表降级展示，failureCode={}", e.getFailureCode());
            return List.of();
        }
    }

    /** 读取当前角色最近 50 封邮件头，并安全覆盖同一邮件的展示快照。 */
    @Transactional(rollbackFor = Exception.class)
    public EveGameMailSyncResp syncCurrentUser() {
        MailSource source = requireCurrentSource(MAIL_READ_SCOPE);
        return synchronize(source);
    }

    /**
     * 由后台调度器同步指定授权角色的邮箱。
     *
     * <p>邮箱是个人资源，因此目标必须同时匹配租户、本站用户和角色绑定；不伪造任何浏览器会话。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public EveGameMailSyncResp syncForCharacter(Long tenantId, Long userId, Long characterRefId) {
        return synchronize(requireSource(tenantId, userId, characterRefId, MAIL_READ_SCOPE));
    }

    /** 读取最近邮件头并安全覆盖同一邮件的展示快照。 */
    private EveGameMailSyncResp synchronize(MailSource source) {
        String accessToken = authorizationLifecycleService.ensureAccessToken(source.authorization());
        SerenityEsiResponse<List<SerenityGameMailHeaderResponse>> response = esiClient
            .getGameMailHeadersWithMetadata(source.character().getCharacterId(), accessToken);
        LocalDateTime synchronizedAt = LocalDateTime.now(ZoneOffset.UTC);
        for (SerenityGameMailHeaderResponse header : response.body()) {
            if (header == null || header.mailId() == null || header.mailId() <= 0) {
                continue;
            }
            EveGameMailDO target = findMail(source, header.mailId());
            if (target == null) {
                target = newMail(source, header.mailId());
            }
            applyHeader(target, header, synchronizedAt, response.expiresAt());
            saveMail(target);
        }
        return new EveGameMailSyncResp(response.body().size(), synchronizedAt, response.expiresAt());
    }

    /** 按需从国服读取邮件正文；正文缓存仍按当前用户与角色绑定隔离。 */
    @Transactional(rollbackFor = Exception.class)
    public EveGameMailDetailResp detail(Long mailId) {
        MailSource source = requireCurrentSource(MAIL_READ_SCOPE);
        EveGameMailDO mail = requireMail(source, mailId);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (mail.getBody() == null || mail.getBodySourceExpiresAt() == null || !mail.getBodySourceExpiresAt()
            .isAfter(now)) {
            String accessToken = authorizationLifecycleService.ensureAccessToken(source.authorization());
            SerenityEsiResponse<SerenityGameMailDetailResponse> response = esiClient
                .getGameMailDetailWithMetadata(source.character().getCharacterId(), mailId, accessToken);
            applyDetail(mail, response.body(), now, response.expiresAt());
            saveMail(mail);
        }
        markReadWhenAuthorized(source, mail);
        return toDetailResp(mail, resolvePartyNames(List.of(mail)));
    }

    /** 显式将当前角色自己的邮件标记为已读，供详情页读取失败时重试。 */
    @Transactional(rollbackFor = Exception.class)
    public EveGameMailDetailResp markRead(Long mailId) {
        MailSource readSource = requireCurrentSource(MAIL_READ_SCOPE);
        EveGameMailDO mail = requireMail(readSource, mailId);
        MailSource organizeSource = requireCurrentSource(MAIL_ORGANIZE_SCOPE);
        markRead(organizeSource, mail);
        return toDetailResp(mail, resolvePartyNames(List.of(mail)));
    }

    /**
     * 使用当前授权角色立即发送游戏内邮件。
     *
     * <p>审计先写入 PENDING；任何可能已经到达国服但未取得明确 mail_id 的异常都会写作 UNKNOWN，严禁自动重发。</p>
     */
    public EveGameMailSendResp sendByRecipientNames(List<EveGameMailRecipientNameReq> recipients,
                                                    String subject,
                                                    String body,
                                                    Long approvedCost) {
        validateRecipientNames(recipients);
        MailSource source = requireCurrentSource(MAIL_SEND_SCOPE);
        List<SerenityGameMailRecipient> resolvedRecipients = resolveRecipientNames(recipients);
        validateSendRequest(resolvedRecipients, subject, body, approvedCost);
        String accessToken = authorizationLifecycleService.ensureAccessToken(source.authorization());
        LocalDateTime requestedAt = LocalDateTime.now(ZoneOffset.UTC);
        EveGameMailSendAuditDO audit = newSendAudit(source, resolvedRecipients, subject, requestedAt);
        sendAuditMapper.insert(audit);
        try {
            Long mailId = esiClient.sendGameMail(source.character()
                .getCharacterId(), new SerenityGameMailSendRequest(approvedCost, body, resolvedRecipients, subject), accessToken);
            LocalDateTime completedAt = LocalDateTime.now(ZoneOffset.UTC);
            audit.setMailId(mailId);
            audit.setStatus("SENT");
            audit.setCompletedAt(completedAt);
            sendAuditMapper.updateById(audit);
            return new EveGameMailSendResp(mailId, "SENT", completedAt);
        } catch (RuntimeException e) {
            audit.setStatus("UNKNOWN");
            audit.setFailureCode(failureCode(e));
            audit.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
            sendAuditMapper.updateById(audit);
            throw e;
        }
    }

    /** 在用户填写收件人名称时立即校验国服中的实体身份，不产生任何游戏写入。 */
    public EveGameMailPartyResp resolveRecipientName(EveGameMailRecipientNameReq recipient) {
        validateRecipientNames(List.of(recipient));
        SerenityGameMailRecipient resolved = resolveRecipientNames(List.of(recipient)).get(0);
        return new EveGameMailPartyResp(resolved.recipientId(), resolved.recipientType(), recipient.recipientName()
            .trim());
    }

    /** 从当前会话反查主角色和匹配 Scope 的有效授权，绝不接收客户端角色参数。 */
    private MailSource requireCurrentSource(String requiredScope) {
        UserContext context = UserContextHolder.getContext();
        EveCharacterDO character = characterMapper.selectActiveByUser(context.getTenantId(), context.getId())
            .stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException("当前账号未绑定有效 EVE 角色，请先完成授权"));
        return requireSource(context.getTenantId(), context.getId(), character.getId(), requiredScope);
    }

    /** 校验并读取指定角色的有效邮件数据源，供无会话调度任务调用。 */
    private MailSource requireSource(Long tenantId, Long userId, Long characterRefId, String requiredScope) {
        EveCharacterDO character = characterMapper.selectActiveByTenantUserAndRefId(tenantId, userId, characterRefId);
        if (character == null) {
            throw new BusinessException("自动同步目标角色已不可用");
        }
        EveAuthorizationDO authorization = authorizationMapper.selectByUserCharacter(tenantId, userId, characterRefId)
            .stream()
            .filter(item -> EveAuthorizationStatus.ACTIVE.equals(item.getStatus()))
            .filter(item -> item.getScopes() != null && item.getScopes().contains(requiredScope))
            .findFirst()
            .orElseThrow(() -> new BusinessException(MAIL_ORGANIZE_SCOPE.equals(requiredScope)
                ? "当前角色缺少游戏内邮件整理授权，请重新授权后再试"
                : "当前角色缺少游戏内邮件授权，请重新授权后再试"));
        return new MailSource(tenantId, userId, character, authorization);
    }

    /** 详情读取后尽力同步已读状态；缺少整理 Scope 时仍允许阅读原邮件。 */
    private void markReadWhenAuthorized(MailSource readSource, EveGameMailDO mail) {
        if (Boolean.TRUE.equals(mail.getIsRead())) {
            return;
        }
        try {
            markRead(requireCurrentSource(MAIL_ORGANIZE_SCOPE), mail);
        } catch (BusinessException e) {
            log.info("游戏内邮件已读状态未同步，缺少邮件整理授权");
        }
    }

    /** 调用国服邮件整理接口并仅更新本站缓存的已读字段。 */
    private void markRead(MailSource source, EveGameMailDO mail) {
        if (Boolean.TRUE.equals(mail.getIsRead())) {
            return;
        }
        String accessToken = authorizationLifecycleService.ensureAccessToken(source.authorization());
        esiClient.updateGameMail(source.character().getCharacterId(), mail
            .getMailId(), new SerenityGameMailUpdateRequest(null, true), accessToken);
        mail.setIsRead(true);
        saveMail(mail);
    }

    /** 查询严格属于当前用户和当前授权角色的邮件缓存。 */
    private EveGameMailDO findMail(MailSource source, Long mailId) {
        return mailMapper.selectOne(new LambdaQueryWrapper<EveGameMailDO>().eq(EveGameMailDO::getUserId, source
            .userId())
            .eq(EveGameMailDO::getCharacterRefId, source.character().getId())
            .eq(EveGameMailDO::getMailId, mailId)
            .eq(EveGameMailDO::getDeleted, 0L));
    }

    /** 返回当前用户自己的邮件，防止可猜测 mail_id 越权读取。 */
    private EveGameMailDO requireMail(MailSource source, Long mailId) {
        if (mailId == null || mailId <= 0) {
            throw new BusinessException("游戏内邮件不存在");
        }
        EveGameMailDO mail = findMail(source, mailId);
        if (mail == null) {
            throw new BusinessException("请先同步邮件列表后再查看详情");
        }
        return mail;
    }

    /** 创建一条与当前用户和当前授权角色绑定的邮件缓存。 */
    private static EveGameMailDO newMail(MailSource source, Long mailId) {
        EveGameMailDO target = new EveGameMailDO();
        target.setTenantId(source.tenantId());
        target.setUserId(source.userId());
        target.setCharacterRefId(source.character().getId());
        target.setCharacterId(source.character().getCharacterId());
        target.setMailId(mailId);
        target.setCreateUser(source.userId());
        target.setDeleted(0L);
        return target;
    }

    /** 覆盖来自最近邮件列表的可展示字段，但不清除已按需读取的正文。 */
    private static void applyHeader(EveGameMailDO target,
                                    SerenityGameMailHeaderResponse source,
                                    LocalDateTime synchronizedAt,
                                    LocalDateTime sourceExpiresAt) {
        target.setFromId(source.fromId());
        target.setSubject(source.subject());
        target.setSentAt(source.sentAt());
        target.setIsRead(Boolean.TRUE.equals(source.read()));
        target.setLabels(source.labels() == null ? List.of() : List.copyOf(source.labels()));
        target.setRecipients(source.recipients() == null ? List.of() : List.copyOf(source.recipients()));
        target.setLastSeenAt(synchronizedAt);
        target.setSourceExpiresAt(sourceExpiresAt);
    }

    /** 将正文接口返回的最新字段更新到同一封邮件缓存。 */
    private static void applyDetail(EveGameMailDO target,
                                    SerenityGameMailDetailResponse source,
                                    LocalDateTime synchronizedAt,
                                    LocalDateTime sourceExpiresAt) {
        target.setFromId(source.fromId());
        target.setSubject(source.subject());
        target.setSentAt(source.sentAt());
        target.setIsRead(Boolean.TRUE.equals(source.read()));
        target.setLabels(source.labels() == null ? List.of() : List.copyOf(source.labels()));
        target.setRecipients(source.recipients() == null ? List.of() : List.copyOf(source.recipients()));
        target.setBody(source.body());
        target.setBodySynchronizedAt(synchronizedAt);
        target.setBodySourceExpiresAt(sourceExpiresAt);
    }

    /** 根据主键存在与否插入或更新邮件缓存。 */
    private void saveMail(EveGameMailDO mail) {
        if (mail.getId() == null) {
            mailMapper.insert(mail);
        } else {
            mailMapper.updateById(mail);
        }
    }

    /** 初始化未发出的审计记录，避免正文进入审计库。 */
    private static EveGameMailSendAuditDO newSendAudit(MailSource source,
                                                       List<SerenityGameMailRecipient> recipients,
                                                       String subject,
                                                       LocalDateTime requestedAt) {
        EveGameMailSendAuditDO audit = new EveGameMailSendAuditDO();
        audit.setTenantId(source.tenantId());
        audit.setUserId(source.userId());
        audit.setCharacterRefId(source.character().getId());
        audit.setCharacterId(source.character().getCharacterId());
        audit.setRecipients(List.copyOf(recipients));
        audit.setSubject(subject);
        audit.setStatus("PENDING");
        audit.setRequestedAt(requestedAt);
        audit.setCreateUser(source.userId());
        audit.setDeleted(0L);
        return audit;
    }

    /**
     * 将页面输入的角色、军团或联盟名称反查为国服邮件接口所需的 ID。
     *
     * <p>邮件列表没有公开的名称反查能力，不能把猜测的名称静默转换为错误目标。</p>
     */
    private List<SerenityGameMailRecipient> resolveRecipientNames(List<EveGameMailRecipientNameReq> recipients) {
        List<String> names = recipients.stream().map(item -> item.recipientName().trim()).distinct().toList();
        SerenityUniverseIdsResponse response = esiClient.resolveUniverseIds(names);
        List<SerenityGameMailRecipient> resolved = new ArrayList<>(recipients.size());
        for (EveGameMailRecipientNameReq recipient : recipients) {
            String name = recipient.recipientName().trim();
            SerenityUniverseIdResponse match = findRecipientMatch(response, recipient.recipientType(), name);
            if (match == null || match.id() == null || match.id() <= 0) {
                throw new BusinessException("未找到“" + name + "”对应的" + recipientTypeLabel(recipient
                    .recipientType()) + "，请检查名称和收件人类型");
            }
            resolved.add(new SerenityGameMailRecipient(match.id(), recipient.recipientType()));
        }
        return resolved;
    }

    /** 在国服按名称返回的分类结果中选取与用户指定类型完全一致的单个目标。 */
    private static SerenityUniverseIdResponse findRecipientMatch(SerenityUniverseIdsResponse source,
                                                                 String recipientType,
                                                                 String name) {
        List<SerenityUniverseIdResponse> candidates = switch (recipientType) {
            case "character" -> source.characters();
            case "corporation" -> source.corporations();
            case "alliance" -> source.alliances();
            default -> List.of();
        };
        if (candidates == null) {
            return null;
        }
        List<SerenityUniverseIdResponse> matches = candidates.stream()
            .filter(Objects::nonNull)
            .filter(item -> item.name() != null && item.name().trim().equalsIgnoreCase(name))
            .toList();
        if (matches.size() > 1) {
            throw new BusinessException("“" + name + "”存在多个同名" + recipientTypeLabel(recipientType) + "，请改用更准确的游戏名称");
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    /** 校验名称输入和可由公开接口反查的收件人类型。 */
    private static void validateRecipientNames(List<EveGameMailRecipientNameReq> recipients) {
        if (recipients == null || recipients.isEmpty() || recipients.size() > 50 || recipients.stream()
            .anyMatch(item -> item == null || item.recipientName() == null || item.recipientName().isBlank() || item
                .recipientName()
                .trim()
                .length() > 100 || item.recipientType() == null || !RECIPIENT_TYPES.contains(item.recipientType()))) {
            throw new BusinessException("请填写 1 至 50 名有效的游戏收件人名称");
        }
        if (recipients.stream().anyMatch(item -> "mailing_list".equals(item.recipientType()))) {
            throw new BusinessException("邮件列表暂不支持按名称解析，请先选择角色、军团或联盟收件人");
        }
    }

    /** 将上游邮件参与方 ID 批量解析为可读名称；解析失败不影响邮件列表与正文阅读。 */
    private Map<Long, String> resolvePartyNames(Collection<EveGameMailDO> mails) {
        List<Long> ids = mails.stream().filter(Objects::nonNull).flatMap(mail -> {
            List<Long> values = new ArrayList<>();
            values.add(mail.getFromId());
            if (mail.getRecipients() != null) {
                mail.getRecipients()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(SerenityGameMailRecipient::recipientId)
                    .forEach(values::add);
            }
            return values.stream();
        }).filter(Objects::nonNull).filter(id -> id > 0 && id <= Integer.MAX_VALUE).distinct().toList();
        Map<Long, String> names = new HashMap<>();
        for (int index = 0; index < ids.size(); index += 1000) {
            try {
                esiClient.resolveUniverseNames(ids.subList(index, Math.min(index + 1000, ids.size())))
                    .stream()
                    .filter(Objects::nonNull)
                    .filter(item -> item.id() != null && item.name() != null && !item.name().isBlank())
                    .forEach(item -> names.put(item.id(), item.name()));
            } catch (SerenityEsiClientException e) {
                // 名称只是阅读体验增强，公开解析暂时失败时仍返回原始邮件事实。
                log.warn("EVE 邮件参与方名称解析失败，failureCode={}", e.getFailureCode());
            }
        }
        return names;
    }

    /** 返回收件人类型的中文名称，用于精确的输入校验提示。 */
    private static String recipientTypeLabel(String recipientType) {
        return switch (recipientType) {
            case "character" -> "游戏角色";
            case "corporation" -> "军团";
            case "alliance" -> "联盟";
            case "mailing_list" -> "邮件列表";
            default -> "收件人";
        };
    }

    /** 校验国服邮件契约的字符数、收件人数量和目标类型。 */
    private static void validateSendRequest(List<SerenityGameMailRecipient> recipients,
                                            String subject,
                                            String body,
                                            Long approvedCost) {
        if (recipients == null || recipients.isEmpty() || recipients.size() > 50 || recipients.stream()
            .anyMatch(item -> item == null || item.recipientId() == null || item.recipientId() <= 0 || item
                .recipientType() == null || !RECIPIENT_TYPES.contains(item.recipientType()))) {
            throw new BusinessException("请填写 1 至 50 个有效游戏邮件收件人");
        }
        if (subject == null || subject.isBlank() || subject.length() > 1000) {
            throw new BusinessException("邮件主题不能为空且不能超过 1000 个字符");
        }
        if (body == null || body.isBlank() || body.length() > 10000) {
            throw new BusinessException("邮件正文不能为空且不能超过 10000 个字符");
        }
        if (approvedCost != null && approvedCost < 0) {
            throw new BusinessException("认可费用不能为负数");
        }
    }

    /** 返回仅含固定分类的失败码，避免持久化令牌、上游正文或异常详情。 */
    private static String failureCode(RuntimeException exception) {
        if (exception instanceof SerenityEsiClientException esiException) {
            return esiException.getFailureCode().name();
        }
        if (exception instanceof SerenityTokenClientException tokenException) {
            return tokenException.getFailureCode().name();
        }
        return "SEND_UNCONFIRMED";
    }

    /** 转换邮件头页面响应，不含正文与授权信息。 */
    private static EveGameMailResp toListResp(EveGameMailDO source, Map<Long, String> partyNames) {
        return new EveGameMailResp(source.getMailId(), toParty(source.getFromId(), "character", partyNames), source
            .getSubject(), source.getSentAt(), source.getIsRead(), source.getLabels() == null
                ? List.of()
                : source.getLabels(), toRecipientParties(source.getRecipients(), partyNames), source
                    .getBody() != null, source.getLastSeenAt(), source.getSourceExpiresAt());
    }

    /** 转换邮件正文响应。 */
    private static EveGameMailDetailResp toDetailResp(EveGameMailDO source, Map<Long, String> partyNames) {
        return new EveGameMailDetailResp(source.getMailId(), toParty(source
            .getFromId(), "character", partyNames), source.getSubject(), source.getBody(), source.getSentAt(), source
                .getIsRead(), source.getLabels() == null ? List.of() : source.getLabels(), toRecipientParties(source
                    .getRecipients(), partyNames), source.getBodySynchronizedAt(), source.getBodySourceExpiresAt());
    }

    /** 将国服标签转换为页面可显示的游戏原生分类。 */
    private static EveGameMailLabelResp toLabelResp(SerenityGameMailLabelResponse source) {
        return new EveGameMailLabelResp(source.labelId(), source.name(), source.unreadCount());
    }

    /** 将国服收件人快照转换为包含名称解析结果的展示对象。 */
    private static List<EveGameMailPartyResp> toRecipientParties(List<SerenityGameMailRecipient> recipients,
                                                                 Map<Long, String> partyNames) {
        if (recipients == null) {
            return List.of();
        }
        return recipients.stream()
            .filter(Objects::nonNull)
            .map(item -> toParty(item.recipientId(), item.recipientType(), partyNames))
            .toList();
    }

    /** 即使公开名称解析暂时不可用，也保留游戏 ID 和参与方类型。 */
    private static EveGameMailPartyResp toParty(Long id, String type, Map<Long, String> partyNames) {
        return new EveGameMailPartyResp(id, type, id == null ? null : partyNames.get(id));
    }

    /** 当前会话可用的个人邮箱数据源。 */
    private record MailSource(Long tenantId, Long userId, EveCharacterDO character, EveAuthorizationDO authorization) {
    }
}
