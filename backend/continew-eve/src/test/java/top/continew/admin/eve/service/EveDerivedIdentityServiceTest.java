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
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveCharacterRoleSnapshotMapper;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
        EveAuthorizationMapper authorizationMapper = mock(EveAuthorizationMapper.class);
        EveCharacterRoleSnapshotMapper snapshotMapper = mock(EveCharacterRoleSnapshotMapper.class);
        EveDerivedRoleApi roleApi = mock(EveDerivedRoleApi.class);
        EveDerivedIdentityService service = new EveDerivedIdentityService(characterMapper, authorizationMapper, snapshotMapper, roleApi);
        EveCharacterDO primary = character(100L);
        EveCharacterDO secondary = character(101L);
        when(characterMapper.selectActiveByUser(10L, 20L)).thenReturn(List.of(primary, secondary));
        when(authorizationMapper.selectByUserCharacter(10L, 20L, 100L)).thenReturn(List.of(authorization(100L)));
        when(authorizationMapper.selectByUserCharacter(10L, 20L, 101L)).thenReturn(List.of(authorization(101L)));
        when(snapshotMapper.selectLatest(10L, 100L)).thenReturn(snapshot(100L, false, List.of()));
        when(snapshotMapper.selectLatest(10L, 101L)).thenReturn(snapshot(101L, true, List.of()));

        service.synchronize(10L, 20L);

        verify(roleApi).synchronize(10L, 20L, EveDerivedIdentity.OWNER);
    }

    /** 已失效的高权限授权不得继续参与派生身份。 */
    @Test
    void shouldIgnoreInactiveAuthorizationWhenAggregating() {
        EveCharacterMapper characterMapper = mock(EveCharacterMapper.class);
        EveAuthorizationMapper authorizationMapper = mock(EveAuthorizationMapper.class);
        EveCharacterRoleSnapshotMapper snapshotMapper = mock(EveCharacterRoleSnapshotMapper.class);
        EveDerivedRoleApi roleApi = mock(EveDerivedRoleApi.class);
        EveDerivedIdentityService service = new EveDerivedIdentityService(characterMapper, authorizationMapper, snapshotMapper, roleApi);
        EveCharacterDO character = character(100L);
        EveAuthorizationDO authorization = authorization(100L);
        authorization.setStatus(EveAuthorizationStatus.REAUTH_REQUIRED);
        when(characterMapper.selectActiveByUser(10L, 20L)).thenReturn(List.of(character));
        when(authorizationMapper.selectByUserCharacter(10L, 20L, 100L)).thenReturn(List.of(authorization));

        service.synchronize(10L, 20L);

        verify(roleApi).synchronize(10L, 20L, EveDerivedIdentity.NONE);
    }

    /** 缺少上游有效期的高权限快照不得继续派生站内身份。 */
    @Test
    void shouldDowngradeWhenSnapshotExpiryIsMissing() {
        EveCharacterMapper characterMapper = mock(EveCharacterMapper.class);
        EveAuthorizationMapper authorizationMapper = mock(EveAuthorizationMapper.class);
        EveCharacterRoleSnapshotMapper snapshotMapper = mock(EveCharacterRoleSnapshotMapper.class);
        EveDerivedRoleApi roleApi = mock(EveDerivedRoleApi.class);
        EveDerivedIdentityService service = new EveDerivedIdentityService(characterMapper, authorizationMapper, snapshotMapper, roleApi);
        EveCharacterDO character = character(100L);
        EveCharacterRoleSnapshotDO snapshot = snapshot(100L, true, List.of("Director"));
        snapshot.setSourceExpiresAt(null);
        when(characterMapper.selectActiveByUser(10L, 20L)).thenReturn(List.of(character));
        when(authorizationMapper.selectByUserCharacter(10L, 20L, 100L)).thenReturn(List.of(authorization(100L)));
        when(snapshotMapper.selectLatest(10L, 100L)).thenReturn(snapshot);

        service.synchronize(10L, 20L);

        verify(roleApi).synchronize(10L, 20L, EveDerivedIdentity.NONE);
    }

    /** 已过期的高权限快照必须立即降权，不能继续派生 OWNER 或 ADMIN。 */
    @Test
    void shouldDowngradeWhenSnapshotIsExpired() {
        EveCharacterMapper characterMapper = mock(EveCharacterMapper.class);
        EveAuthorizationMapper authorizationMapper = mock(EveAuthorizationMapper.class);
        EveCharacterRoleSnapshotMapper snapshotMapper = mock(EveCharacterRoleSnapshotMapper.class);
        EveDerivedRoleApi roleApi = mock(EveDerivedRoleApi.class);
        EveDerivedIdentityService service = new EveDerivedIdentityService(characterMapper, authorizationMapper, snapshotMapper, roleApi);
        EveCharacterDO character = character(100L);
        EveCharacterRoleSnapshotDO snapshot = snapshot(100L, false, List.of("Director"));
        snapshot.setSourceExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(characterMapper.selectActiveByUser(10L, 20L)).thenReturn(List.of(character));
        when(authorizationMapper.selectByUserCharacter(10L, 20L, 100L)).thenReturn(List.of(authorization(100L)));
        when(snapshotMapper.selectLatest(10L, 100L)).thenReturn(snapshot);

        service.synchronize(10L, 20L);

        verify(roleApi).synchronize(10L, 20L, EveDerivedIdentity.NONE);
    }

    /** 创建属于当前用户的角色。 */
    private static EveCharacterDO character(Long id) {
        EveCharacterDO character = new EveCharacterDO();
        character.setId(id);
        character.setTenantId(10L);
        character.setUserId(20L);
        return character;
    }

    /** 创建有效授权。 */
    private static EveAuthorizationDO authorization(Long characterRefId) {
        EveAuthorizationDO authorization = new EveAuthorizationDO();
        authorization.setTenantId(10L);
        authorization.setUserId(20L);
        authorization.setCharacterRefId(characterRefId);
        authorization.setStatus(EveAuthorizationStatus.ACTIVE);
        authorization.setAccessToken("encrypted-access");
        authorization.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10));
        return authorization;
    }

    /** 创建最新角色快照。 */
    private static EveCharacterRoleSnapshotDO snapshot(Long characterRefId, boolean ceo, List<String> roles) {
        EveCharacterRoleSnapshotDO snapshot = new EveCharacterRoleSnapshotDO();
        snapshot.setTenantId(10L);
        snapshot.setCharacterRefId(characterRefId);
        snapshot.setIsCeo(ceo);
        snapshot.setRoles(roles);
        snapshot.setSourceExpiresAt(LocalDateTime.now().plusMinutes(10));
        return snapshot;
    }
}
