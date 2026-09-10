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

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.redisson.api.RedissonClient;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.client.SerenityEsiClient;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterRoleSnapshotMapper;
import top.continew.admin.eve.mapper.EveCorporationMemberTrackingMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.mapper.EveCorporationRosterMemberMapper;
import top.continew.admin.eve.mapper.EveMemberTrackingAccessAuditMapper;
import top.continew.admin.eve.mapper.EveMemberSyncRunMapper;
import top.continew.admin.eve.model.EveMeContextResp;
import top.continew.admin.eve.model.EveMemberResp;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.entity.EveCorporationRosterMemberDO;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 军团成员追踪字段权限裁剪测试。
 *
 * @author zhaoyuqing
 */
class EveCorporationMemberServiceTest {

    /** 没有追踪查看权限时，详情接口不得读取追踪快照或泄露追踪字段。 */
    @Test
    void shouldNotReadTrackingForMemberWithoutTrackingPermission() {
        EveContextService contextService = mock(EveContextService.class);
        EveCorporationMapper corporationMapper = mock(EveCorporationMapper.class);
        EveCorporationRosterMemberMapper rosterMemberMapper = mock(EveCorporationRosterMemberMapper.class);
        EveCorporationMemberTrackingMapper trackingMapper = mock(EveCorporationMemberTrackingMapper.class);
        EveCorporationMemberService service = service(contextService, corporationMapper, rosterMemberMapper, trackingMapper, mock(EveMemberOperationAuditService.class));
        UserContext context = context(Set.of("eve:members:view"));
        EveCorporationDO corporation = new EveCorporationDO();
        corporation.setId(300L);
        corporation.setTenantId(10L);
        EveCorporationRosterMemberDO member = new EveCorporationRosterMemberDO();
        member.setId(500L);
        member.setCharacterId(8001L);
        member.setCharacterName("测试成员");
        member.setStatus("ACTIVE");
        when(contextService.getCurrentContext())
            .thenReturn(new EveMeContextResp(10L, null, new EveMeContextResp.CorporationInfo(9001L, "测试军团", "TEST"), "corp_member", null, List
                .of(), null, List.of()));
        when(corporationMapper.selectByTenantAndCorporationId(10L, 9001L)).thenReturn(corporation);
        when(rosterMemberMapper.selectByCharacterId(eq(10L), eq(300L), eq(8001L))).thenReturn(member);

        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(context);
            EveMemberResp response = service.get(8001L);

            assertThat(response.tracking()).isNull();
        }

        verifyNoInteractions(trackingMapper);
    }

    /** 清空组织信息时必须显式写入空值，并记录不含备注正文的审计。 */
    @Test
    void shouldClearOrganizationAndRecordDesensitizedAudit() {
        EveContextService contextService = mock(EveContextService.class);
        EveCorporationMapper corporationMapper = mock(EveCorporationMapper.class);
        EveCorporationRosterMemberMapper rosterMemberMapper = mock(EveCorporationRosterMemberMapper.class);
        EveCorporationMemberTrackingMapper trackingMapper = mock(EveCorporationMemberTrackingMapper.class);
        EveMemberOperationAuditService auditService = mock(EveMemberOperationAuditService.class);
        EveCorporationMemberService service = service(contextService, corporationMapper, rosterMemberMapper, trackingMapper, auditService);
        UserContext context = context(Set.of("eve:members:organize"));
        EveCorporationDO corporation = new EveCorporationDO();
        corporation.setId(300L);
        corporation.setTenantId(10L);
        EveCorporationRosterMemberDO member = new EveCorporationRosterMemberDO();
        member.setId(500L);
        member.setCharacterId(8001L);
        member.setCharacterName("测试成员");
        member.setStatus("ACTIVE");
        member.setOrganizationGroup("旧分组");
        member.setMemberNote("旧备注");
        when(contextService.getCurrentContext())
            .thenReturn(new EveMeContextResp(10L, null, new EveMeContextResp.CorporationInfo(9001L, "测试军团", "TEST"), "corp_member", null, List
                .of(), null, List.of()));
        when(corporationMapper.selectByTenantAndCorporationId(10L, 9001L)).thenReturn(corporation);
        when(rosterMemberMapper.selectByCharacterId(10L, 300L, 8001L)).thenReturn(member);

        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(context);
            EveMemberResp response = service.updateOrganization(8001L, null, null);

            assertThat(response.organizationGroup()).isNull();
            assertThat(response.memberNote()).isNull();
        }

        verify(rosterMemberMapper).updateOrganization(eq(10L), eq(500L), isNull(), isNull(), eq(20L));
        verify(auditService).recordOrganizationUpdate(context, 500L);
        verifyNoInteractions(trackingMapper);
    }

    /** 构造包含新增依赖的服务实例，保持单元测试只覆盖本服务行为。 */
    private static EveCorporationMemberService service(EveContextService contextService,
                                                       EveCorporationMapper corporationMapper,
                                                       EveCorporationRosterMemberMapper rosterMemberMapper,
                                                       EveCorporationMemberTrackingMapper trackingMapper,
                                                       EveMemberOperationAuditService operationAuditService) {
        return new EveCorporationMemberService(contextService, corporationMapper, mock(EveAuthorizationMapper.class), mock(EveCharacterRoleSnapshotMapper.class), rosterMemberMapper, trackingMapper, mock(EveMemberTrackingAccessAuditMapper.class), mock(EveAuthorizationLifecycleService.class), mock(SerenityEsiClient.class), mock(RedissonClient.class), mock(EveMemberSyncRunService.class), mock(EveMemberSyncRunMapper.class), operationAuditService, new EveStaticNameReference(), mock(EveDataFreshnessService.class));
    }

    /** 构造当前租户普通成员会话。 */
    private static UserContext context(Set<String> permissions) {
        UserContext context = new UserContext();
        context.setId(20L);
        context.setTenantId(10L);
        context.setPermissions(permissions);
        return context;
    }
}
