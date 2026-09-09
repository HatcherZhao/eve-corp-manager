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

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.mapper.EveMemberOperationAuditMapper;
import top.continew.admin.eve.model.EveMemberOperationAuditResp;
import top.continew.admin.eve.model.entity.EveMemberOperationAuditDO;
import top.continew.starter.core.exception.BusinessException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/**
 * 记录和读取军团成员组织、业务角色操作的脱敏审计。
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveMemberOperationAuditService {

    /** 成员组织信息调整事件。 */
    public static final String MEMBER_ORGANIZATION_UPDATED = "MEMBER_ORGANIZATION_UPDATED";
    /** 成员业务角色调整事件。 */
    public static final String MEMBER_BUSINESS_ROLE_UPDATED = "MEMBER_BUSINESS_ROLE_UPDATED";

    private static final Set<String> MANAGER_ROLE_CODES = Set.of("corp_owner", "corp_admin");

    private final EveMemberOperationAuditMapper auditMapper;

    /** 记录成员分组或备注更新，不保存备注原文。 */
    public void recordOrganizationUpdate(UserContext actor, Long rosterMemberId) {
        record(actor, rosterMemberId, null, MEMBER_ORGANIZATION_UPDATED, "更新成员分组或备注");
    }

    /** 记录成员业务角色更新，不保存完整权限快照或敏感请求体。 */
    public void recordBusinessRoleUpdate(UserContext actor, Long targetUserId) {
        record(actor, null, targetUserId, MEMBER_BUSINESS_ROLE_UPDATED, "更新成员业务角色");
    }

    /** 查询当前军团最近成员组织操作。 */
    public List<EveMemberOperationAuditResp> listOrganizationActivities(int limit) {
        return listActivities(MEMBER_ORGANIZATION_UPDATED, limit, false);
    }

    /** 查询当前军团最近成员业务角色操作。 */
    public List<EveMemberOperationAuditResp> listBusinessRoleActivities(int limit) {
        return listActivities(MEMBER_BUSINESS_ROLE_UPDATED, limit, true);
    }

    /** 在当前租户内写入最小审计事实。 */
    private void record(UserContext actor, Long rosterMemberId, Long targetUserId, String eventType, String summary) {
        EveMemberOperationAuditDO audit = new EveMemberOperationAuditDO();
        audit.setTenantId(actor.getTenantId());
        audit.setRosterMemberId(rosterMemberId);
        audit.setTargetUserId(targetUserId);
        audit.setEventType(eventType);
        audit.setSummary(summary);
        audit.setActorUserId(actor.getId());
        audit.setActorUsername(actor.getUsername());
        audit.setOccurredAt(LocalDateTime.now(ZoneOffset.UTC));
        audit.setCreateUser(actor.getId());
        audit.setDeleted(0L);
        auditMapper.insert(audit);
    }

    /** 管理层可查看同军团内的操作历史。 */
    private List<EveMemberOperationAuditResp> listActivities(String eventType, int limit, boolean managerOnly) {
        UserContext context = UserContextHolder.getContext();
        boolean manager = context.getRoleCodes() != null && context.getRoleCodes()
            .stream()
            .anyMatch(MANAGER_ROLE_CODES::contains);
        boolean canOrganize = context.getPermissions() != null && (context.getPermissions()
            .contains("eve:members:organize") || context.getPermissions().contains("*:*:*"));
        if (managerOnly && !manager || !managerOnly && !manager && !canOrganize) {
            throw new BusinessException("仅军团 CEO 或总监可查看成员管理审计");
        }
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return auditMapper.selectList(Wrappers.<EveMemberOperationAuditDO>lambdaQuery()
            .eq(EveMemberOperationAuditDO::getTenantId, context.getTenantId())
            .eq(EveMemberOperationAuditDO::getEventType, eventType)
            .eq(EveMemberOperationAuditDO::getDeleted, 0L)
            .orderByDesc(EveMemberOperationAuditDO::getOccurredAt, EveMemberOperationAuditDO::getId)
            .last("LIMIT " + safeLimit))
            .stream()
            .map(item -> new EveMemberOperationAuditResp(item.getId(), item.getRosterMemberId(), item
                .getTargetUserId(), item.getEventType(), item.getSummary(), item.getActorUserId(), item
                    .getActorUsername(), item.getOccurredAt()))
            .toList();
    }
}
