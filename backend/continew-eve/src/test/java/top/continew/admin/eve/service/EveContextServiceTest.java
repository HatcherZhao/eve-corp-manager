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
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveCharacterRoleSnapshotMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.model.EveMeContextResp;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO;
import top.continew.admin.eve.model.enums.EveCapabilityStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * EVE 当前用户上下文测试。
 *
 * @author zhaoyuqing
 */
class EveContextServiceTest {

    /** 首屏上下文应包含最近检查、快照和缓存时间。 */
    @Test
    void shouldExposePermissionFreshness() {
        EveCharacterMapper characterMapper = mock(EveCharacterMapper.class);
        EveCorporationMapper corporationMapper = mock(EveCorporationMapper.class);
        EveCharacterRoleSnapshotMapper snapshotMapper = mock(EveCharacterRoleSnapshotMapper.class);
        EveCapabilityPolicy capabilityPolicy = mock(EveCapabilityPolicy.class);
        EveContextService service = new EveContextService(characterMapper, corporationMapper, snapshotMapper, capabilityPolicy);
        LocalDateTime checkedAt = LocalDateTime.now().minusMinutes(2);
        LocalDateTime capturedAt = LocalDateTime.now().minusMinutes(3);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(57);
        EveCharacterDO character = new EveCharacterDO();
        character.setId(100L);
        character.setTenantId(10L);
        character.setUserId(20L);
        character.setLastVerifiedAt(checkedAt);
        EveCharacterRoleSnapshotDO snapshot = new EveCharacterRoleSnapshotDO();
        snapshot.setCapturedAt(capturedAt);
        snapshot.setSourceExpiresAt(expiresAt);
        snapshot.setSourceExpiryEstimated(true);
        when(characterMapper.selectActiveByUser(10L, 20L)).thenReturn(List.of(character));
        when(snapshotMapper.selectLatest(10L, 100L)).thenReturn(snapshot);
        when(capabilityPolicy.evaluateAll(10L, Set.of())).thenReturn(List.of());
        when(capabilityPolicy.authorizationStatus(10L, 20L)).thenReturn(EveCapabilityStatus.AVAILABLE);
        UserContext context = new UserContext();
        context.setId(20L);
        context.setTenantId(10L);
        context.setPermissions(Set.of());
        context.setRoleCodes(Set.of("corp_member"));

        try (MockedStatic<UserContextHolder> holder = mockStatic(UserContextHolder.class)) {
            holder.when(UserContextHolder::getContext).thenReturn(context);
            EveMeContextResp response = service.getCurrentContext();

            assertThat(response.permissionFreshness())
                .isEqualTo(new EveMeContextResp.PermissionFreshness(checkedAt, capturedAt, expiresAt, true));
        }
    }
}
