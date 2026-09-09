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
import top.continew.admin.common.api.system.EveDerivedRoleApi;
import top.continew.admin.common.enums.EveDerivedIdentity;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveCharacterRoleSnapshotMapper;
import top.continew.admin.eve.mapper.EveCorporationMemberMapper;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO;
import top.continew.admin.eve.model.enums.EveCorporationMemberStatus;

import java.util.List;
import java.util.Objects;

/**
 * 基于同租户用户的全部有效角色事实聚合唯一站内派生身份。
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveDerivedIdentityService {

    private final EveCharacterMapper characterMapper;
    private final EveCharacterRoleSnapshotMapper roleSnapshotMapper;
    private final EveCorporationMemberMapper memberMapper;
    private final EveDerivedRoleApi derivedRoleApi;

    /**
     * 聚合当前军团成员的最近已验证角色事实，按 CEO、总监、普通成员的优先级同步站内身份。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     */
    public void synchronize(Long tenantId, Long userId) {
        boolean member = false;
        boolean director = false;
        for (EveCharacterDO character : characterMapper.selectActiveByUser(tenantId, userId)) {
            if (!ownedBy(character, tenantId, userId) || !hasActiveMembership(tenantId, userId, character.getId())) {
                continue;
            }
            member = true;
            EveCharacterRoleSnapshotDO snapshot = roleSnapshotMapper.selectLatest(tenantId, character.getId());
            if (!isOwnedSnapshot(snapshot, tenantId, character.getId())) {
                continue;
            }
            if (Boolean.TRUE.equals(snapshot.getIsCeo())) {
                derivedRoleApi.synchronize(tenantId, userId, EveDerivedIdentity.OWNER);
                return;
            }
            director |= safeRoles(snapshot.getRoles()).contains("Director");
        }
        derivedRoleApi.synchronize(tenantId, userId, director
            ? EveDerivedIdentity.ADMIN
            : member ? EveDerivedIdentity.MEMBER : EveDerivedIdentity.NONE);
    }

    /** 判断角色是否严格属于当前租户用户。 */
    private static boolean ownedBy(EveCharacterDO character, Long tenantId, Long userId) {
        return Objects.equals(tenantId, character.getTenantId()) && Objects.equals(userId, character.getUserId());
    }

    /** 仅保存当前仍归属于该军团租户的成员基础身份。 */
    private boolean hasActiveMembership(Long tenantId, Long userId, Long characterRefId) {
        var member = memberMapper.selectCurrent(tenantId, userId, characterRefId);
        return member != null && EveCorporationMemberStatus.ACTIVE.equals(member.getStatus());
    }

    /** 快照必须严格属于当前租户角色；其缓存期限只影响数据刷新，不影响本站登录身份。 */
    private static boolean isOwnedSnapshot(EveCharacterRoleSnapshotDO snapshot, Long tenantId, Long characterRefId) {
        return snapshot != null && Objects.equals(tenantId, snapshot.getTenantId()) && Objects
            .equals(characterRefId, snapshot.getCharacterRefId());
    }

    /** 归一可空角色集合。 */
    private static List<String> safeRoles(List<String> roles) {
        return roles == null ? List.of() : roles;
    }
}
