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
import top.continew.admin.common.api.system.EveDerivedRoleApi;
import top.continew.admin.common.enums.EveDerivedIdentity;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveCharacterRoleSnapshotMapper;
import top.continew.admin.eve.mapper.EveCorporationMemberMapper;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO;
import top.continew.admin.eve.model.entity.EveCorporationMemberDO;
import top.continew.admin.eve.model.enums.EveCorporationMemberStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多角色派生身份聚合测试。
 *
 * @author zhaoyuqing
 */
class EveDerivedIdentityServiceTest {

    /** 普通主角色不能覆盖同用户次要角色的 CEO 身份。 */
    @Test
    void shouldPreserveHigherIdentityFromSecondaryCharacter() {
        EveCharacterMapper characterMapper = mock(EveCharacterMapper.class);
        EveCharacterRoleSnapshotMapper snapshotMapper = mock(EveCharacterRoleSnapshotMapper.class);
        EveCorporationMemberMapper memberMapper = mock(EveCorporationMemberMapper.class);
        EveDerivedRoleApi roleApi = mock(EveDerivedRoleApi.class);
        EveDerivedIdentityService service = new EveDerivedIdentityService(characterMapper, snapshotMapper, memberMapper, roleApi);
        EveCharacterDO primary = character(100L);
        EveCharacterDO secondary = character(101L);
        when(characterMapper.selectActiveByUser(10L, 20L)).thenReturn(List.of(primary, secondary));
        when(memberMapper.selectCurrent(10L, 20L, 100L)).thenReturn(member());
        when(memberMapper.selectCurrent(10L, 20L, 101L)).thenReturn(member());
        when(snapshotMapper.selectLatest(10L, 100L)).thenReturn(snapshot(100L, false, List.of()));
        when(snapshotMapper.selectLatest(10L, 101L)).thenReturn(snapshot(101L, true, List.of()));

        service.synchronize(10L, 20L);

        verify(roleApi).synchronize(10L, 20L, EveDerivedIdentity.OWNER);
    }

    /** 待重新授权时保留最近已验证的军团身份，确保用户仍可登录并完成重新授权。 */
    @Test
    void shouldKeepLastVerifiedIdentityWhenAuthorizationNeedsReauthorization() {
        EveCharacterMapper characterMapper = mock(EveCharacterMapper.class);
        EveCharacterRoleSnapshotMapper snapshotMapper = mock(EveCharacterRoleSnapshotMapper.class);
        EveCorporationMemberMapper memberMapper = mock(EveCorporationMemberMapper.class);
        EveDerivedRoleApi roleApi = mock(EveDerivedRoleApi.class);
        EveDerivedIdentityService service = new EveDerivedIdentityService(characterMapper, snapshotMapper, memberMapper, roleApi);
        EveCharacterDO character = character(100L);
        when(characterMapper.selectActiveByUser(10L, 20L)).thenReturn(List.of(character));
        when(memberMapper.selectCurrent(10L, 20L, 100L)).thenReturn(member());
        when(snapshotMapper.selectLatest(10L, 100L)).thenReturn(snapshot(100L, false, List.of("Director")));

        service.synchronize(10L, 20L);

        verify(roleApi).synchronize(10L, 20L, EveDerivedIdentity.ADMIN);
    }

    /** 缺少上游有效期只影响刷新，不得降级最近已验证的 CEO 身份。 */
    @Test
    void shouldKeepIdentityWhenSnapshotExpiryIsMissing() {
        EveCharacterMapper characterMapper = mock(EveCharacterMapper.class);
        EveCharacterRoleSnapshotMapper snapshotMapper = mock(EveCharacterRoleSnapshotMapper.class);
        EveCorporationMemberMapper memberMapper = mock(EveCorporationMemberMapper.class);
        EveDerivedRoleApi roleApi = mock(EveDerivedRoleApi.class);
        EveDerivedIdentityService service = new EveDerivedIdentityService(characterMapper, snapshotMapper, memberMapper, roleApi);
        EveCharacterDO character = character(100L);
        EveCharacterRoleSnapshotDO snapshot = snapshot(100L, true, List.of("Director"));
        snapshot.setSourceExpiresAt(null);
        when(characterMapper.selectActiveByUser(10L, 20L)).thenReturn(List.of(character));
        when(memberMapper.selectCurrent(10L, 20L, 100L)).thenReturn(member());
        when(snapshotMapper.selectLatest(10L, 100L)).thenReturn(snapshot);

        service.synchronize(10L, 20L);

        verify(roleApi).synchronize(10L, 20L, EveDerivedIdentity.OWNER);
    }

    /** 快照过期只影响数据新鲜度，不得让本站菜单在未获得反向游戏事实时消失。 */
    @Test
    void shouldKeepIdentityWhenSnapshotIsExpired() {
        EveCharacterMapper characterMapper = mock(EveCharacterMapper.class);
        EveCharacterRoleSnapshotMapper snapshotMapper = mock(EveCharacterRoleSnapshotMapper.class);
        EveCorporationMemberMapper memberMapper = mock(EveCorporationMemberMapper.class);
        EveDerivedRoleApi roleApi = mock(EveDerivedRoleApi.class);
        EveDerivedIdentityService service = new EveDerivedIdentityService(characterMapper, snapshotMapper, memberMapper, roleApi);
        EveCharacterDO character = character(100L);
        EveCharacterRoleSnapshotDO snapshot = snapshot(100L, false, List.of("Director"));
        snapshot.setSourceExpiresAt(LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(1));
        when(characterMapper.selectActiveByUser(10L, 20L)).thenReturn(List.of(character));
        when(memberMapper.selectCurrent(10L, 20L, 100L)).thenReturn(member());
        when(snapshotMapper.selectLatest(10L, 100L)).thenReturn(snapshot);

        service.synchronize(10L, 20L);

        verify(roleApi).synchronize(10L, 20L, EveDerivedIdentity.ADMIN);
    }

    /** 创建属于当前用户的角色。 */
    private static EveCharacterDO character(Long id) {
        EveCharacterDO character = new EveCharacterDO();
        character.setId(id);
        character.setTenantId(10L);
        character.setUserId(20L);
        return character;
    }

    /** 创建最新角色快照。 */
    private static EveCharacterRoleSnapshotDO snapshot(Long characterRefId, boolean ceo, List<String> roles) {
        EveCharacterRoleSnapshotDO snapshot = new EveCharacterRoleSnapshotDO();
        snapshot.setTenantId(10L);
        snapshot.setCharacterRefId(characterRefId);
        snapshot.setIsCeo(ceo);
        snapshot.setRoles(roles);
        snapshot.setSourceExpiresAt(LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(10));
        return snapshot;
    }

    /** 创建当前有效的军团成员关系。 */
    private static EveCorporationMemberDO member() {
        EveCorporationMemberDO member = new EveCorporationMemberDO();
        member.setStatus(EveCorporationMemberStatus.ACTIVE);
        return member;
    }
}
