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
import top.continew.admin.common.api.system.EveLoginIdentityReviewApi;
import top.continew.admin.common.model.dto.EveLoginIdentityReviewDTO;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.model.EvePermissionRefreshResp;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterDO;

import java.util.List;

/**
 * 在账号密码登录创建会话前强制复核 EVE 身份。
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveLoginIdentityReviewApiImpl implements EveLoginIdentityReviewApi {

    private final EveCharacterMapper characterMapper;
    private final EveAuthorizationMapper authorizationMapper;
    private final EvePermissionRefreshService permissionRefreshService;
    private final EveDerivedIdentityService derivedIdentityService;

    /** 登录复核必须跳过主动刷新冷却，确保会话只携带最新权限。 */
    @Override
    public EveLoginIdentityReviewDTO review(Long tenantId, Long userId) {
        if (characterMapper.selectByUser(tenantId, userId).isEmpty()) {
            return result(EveLoginIdentityReviewDTO.Status.NOT_BOUND);
        }
        List<EveCharacterDO> activeCharacters = characterMapper.selectActiveByUser(tenantId, userId);
        if (activeCharacters.isEmpty()) {
            derivedIdentityService.synchronize(tenantId, userId);
            return result(EveLoginIdentityReviewDTO.Status.MEMBERSHIP_INVALID);
        }
        for (EveCharacterDO character : activeCharacters) {
            EveAuthorizationDO authorization = latestAuthorization(tenantId, userId, character.getId());
            if (authorization == null) {
                return result(EveLoginIdentityReviewDTO.Status.REAUTHORIZATION_REQUIRED);
            }
            EveLoginIdentityReviewDTO reviewed = reviewAuthorization(authorization);
            if (!EveLoginIdentityReviewDTO.Status.VERIFIED.equals(reviewed.status())) {
                return reviewed;
            }
        }
        return result(EveLoginIdentityReviewDTO.Status.VERIFIED);
    }

    /** 复核单条绑定授权，并将内部刷新状态收敛为登录门禁状态。 */
    private EveLoginIdentityReviewDTO reviewAuthorization(EveAuthorizationDO authorization) {
        EvePermissionRefreshResp response = permissionRefreshService.reviewAuthorization(authorization);
        if (response == null || response.status() == null) {
            return result(EveLoginIdentityReviewDTO.Status.UPSTREAM_UNAVAILABLE);
        }
        return switch (response.status()) {
            case REFRESHED -> result(EveLoginIdentityReviewDTO.Status.VERIFIED);
            case MEMBERSHIP_INVALID -> result(EveLoginIdentityReviewDTO.Status.MEMBERSHIP_INVALID);
            case REAUTHORIZATION_REQUIRED, AUTHORIZATION_DISABLED ->
                result(EveLoginIdentityReviewDTO.Status.REAUTHORIZATION_REQUIRED);
            case UPSTREAM_UNAVAILABLE, COOLDOWN -> result(EveLoginIdentityReviewDTO.Status.UPSTREAM_UNAVAILABLE);
        };
    }

    /** 读取角色最新授权，查询结果已按主键倒序。 */
    private EveAuthorizationDO latestAuthorization(Long tenantId, Long userId, Long characterRefId) {
        return authorizationMapper.selectByUserCharacter(tenantId, userId, characterRefId)
            .stream()
            .findFirst()
            .orElse(null);
    }

    /** 创建不可变复核结果。 */
    private static EveLoginIdentityReviewDTO result(EveLoginIdentityReviewDTO.Status status) {
        return new EveLoginIdentityReviewDTO(status);
    }
}
