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
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.mapper.EveCharacterRoleSnapshotMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.model.EveCapabilityResult;
import top.continew.admin.eve.model.EveMeContextResp;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO;
import top.continew.admin.eve.model.entity.EveCorporationDO;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 汇总当前登录用户的 EVE 身份、军团和模块能力。
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveContextService {

    private final EveCharacterMapper characterMapper;
    private final EveCorporationMapper corporationMapper;
    private final EveCharacterRoleSnapshotMapper roleSnapshotMapper;
    private final EveCapabilityPolicy capabilityPolicy;

    /** 构建当前登录用户的完整 EVE 上下文。 */
    public EveMeContextResp getCurrentContext() {
        UserContext userContext = UserContextHolder.getContext();
        Long tenantId = userContext.getTenantId();
        Long userId = userContext.getId();
        Set<String> permissions = userContext.getPermissions();
        List<EveCapabilityResult> capabilities = capabilityPolicy.evaluateAll(tenantId, permissions);
        List<EveCharacterDO> characters = characterMapper.selectActiveByUser(tenantId, userId);
        EveCharacterDO character = characters.stream()
            .filter(item -> Objects.equals(tenantId, item.getTenantId()) && Objects.equals(userId, item.getUserId()))
            .findFirst()
            .orElse(null);
        EveCorporationDO corporation = character == null
            ? null
            : corporationMapper.selectByTenantAndCorporationId(tenantId, character.getCorporationId());
        if (corporation != null && !Objects.equals(tenantId, corporation.getTenantId())) {
            corporation = null;
        }
        EveCharacterRoleSnapshotDO snapshot = character == null
            ? null
            : roleSnapshotMapper.selectLatest(tenantId, character.getId());
        return new EveMeContextResp(tenantId, toCharacterInfo(character), toCorporationInfo(corporation), resolveDerivedIdentity(userContext
            .getRoleCodes()), capabilityPolicy
                .authorizationStatus(tenantId, userId), capabilities, toPermissionFreshness(character, snapshot));
    }

    /** 派生身份按 CEO、总监、成员优先级返回。 */
    private static String resolveDerivedIdentity(Set<String> roleCodes) {
        if (roleCodes != null && roleCodes.contains("corp_owner")) {
            return "OWNER";
        }
        if (roleCodes != null && roleCodes.contains("corp_admin")) {
            return "ADMIN";
        }
        return roleCodes != null && roleCodes.contains("corp_member") ? "MEMBER" : null;
    }

    /** 转换角色安全摘要。 */
    private static EveMeContextResp.CharacterInfo toCharacterInfo(EveCharacterDO character) {
        return character == null
            ? null
            : new EveMeContextResp.CharacterInfo(character.getId(), character.getCharacterId(), character.getName());
    }

    /** 转换军团安全摘要。 */
    private static EveMeContextResp.CorporationInfo toCorporationInfo(EveCorporationDO corporation) {
        return corporation == null
            ? null
            : new EveMeContextResp.CorporationInfo(corporation.getCorporationId(), corporation.getName(), corporation
                .getTicker());
    }

    /** 转换首屏可直接展示的权限时间信息。 */
    private static EveMeContextResp.PermissionFreshness toPermissionFreshness(EveCharacterDO character,
                                                                              EveCharacterRoleSnapshotDO snapshot) {
        if (character == null && snapshot == null) {
            return null;
        }
        return new EveMeContextResp.PermissionFreshness(character == null
            ? null
            : character.getLastVerifiedAt(), snapshot == null ? null : snapshot.getCapturedAt(), snapshot == null
                ? null
                : snapshot.getSourceExpiresAt(), snapshot != null && Boolean.TRUE.equals(snapshot
                    .getSourceExpiryEstimated()));
    }
}
