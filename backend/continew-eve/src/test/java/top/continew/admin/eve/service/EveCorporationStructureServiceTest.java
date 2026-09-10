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
import org.redisson.api.RedissonClient;
import top.continew.admin.eve.client.SerenityEsiClient;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterRoleSnapshotMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.mapper.EveCorporationStructureMapper;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 军团建筑同步数据源选择测试。
 *
 * @author zhaoyuqing
 */
class EveCorporationStructureServiceTest {

    /** 角色快照已过期时，应先复核国服角色而非误拒绝有效总监授权。 */
    @Test
    void shouldRefreshExpiredRoleSnapshotBeforeSelectingStructureSource() throws Exception {
        EveAuthorizationMapper authorizationMapper = mock(EveAuthorizationMapper.class);
        EveCharacterRoleSnapshotMapper roleSnapshotMapper = mock(EveCharacterRoleSnapshotMapper.class);
        EvePermissionRefreshService permissionRefreshService = mock(EvePermissionRefreshService.class);
        EveCorporationStructureService service = new EveCorporationStructureService(mock(EveContextService.class), mock(EveCorporationMapper.class), mock(EveCorporationStructureMapper.class), authorizationMapper, roleSnapshotMapper, mock(EveAuthorizationLifecycleService.class), permissionRefreshService, mock(EveStaticReferenceService.class), mock(SerenityEsiClient.class), mock(RedissonClient.class), mock(EveDataFreshnessService.class));
        EveAuthorizationDO authorization = authorization();
        when(authorizationMapper.selectTenantCandidates(10L)).thenReturn(List.of(authorization));
        when(roleSnapshotMapper.selectLatest(10L, 100L)).thenReturn(snapshot(LocalDateTime.now(ZoneOffset.UTC)
            .minusMinutes(1)), snapshot(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5)));

        Method selectSource = EveCorporationStructureService.class.getDeclaredMethod("selectSource", Long.class);
        selectSource.setAccessible(true);
        EveAuthorizationDO result = (EveAuthorizationDO)selectSource.invoke(service, 10L);

        assertThat(result).isSameAs(authorization);
        verify(permissionRefreshService).reviewAuthorization(authorization);
    }

    /** 构造最小可用的建筑读取授权。 */
    private static EveAuthorizationDO authorization() {
        EveAuthorizationDO authorization = new EveAuthorizationDO();
        authorization.setTenantId(10L);
        authorization.setCharacterRefId(100L);
        authorization.setStatus(EveAuthorizationStatus.ACTIVE);
        authorization.setScopes(List.of("esi-corporations.read_structures.v1"));
        return authorization;
    }

    /** 构造总监角色快照，并由调用方指定上游缓存截止时间。 */
    private static EveCharacterRoleSnapshotDO snapshot(LocalDateTime expiresAt) {
        EveCharacterRoleSnapshotDO snapshot = new EveCharacterRoleSnapshotDO();
        snapshot.setTenantId(10L);
        snapshot.setCharacterRefId(100L);
        snapshot.setRoles(List.of("Director"));
        snapshot.setSourceExpiresAt(expiresAt);
        return snapshot;
    }
}
