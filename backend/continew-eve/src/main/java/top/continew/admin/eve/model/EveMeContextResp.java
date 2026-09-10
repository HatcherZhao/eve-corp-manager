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

package top.continew.admin.eve.model;

import top.continew.admin.eve.model.enums.EveCapabilityStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 当前用户的 EVE 军团身份与能力上下文。
 *
 * @author zhaoyuqing
 */
public record EveMeContextResp(Long tenantId, CharacterInfo character, CorporationInfo corporation,
                               String derivedIdentity, EveCapabilityStatus authorizationStatus,
                               List<EveCapabilityResult> capabilities, PermissionFreshness permissionFreshness,
                               List<EveDataFreshnessResp> dataFreshness) {
    /** 当前游戏角色摘要。 */
    public record CharacterInfo(Long id, Long characterId, String name) {
    }

    /** 当前军团摘要。 */
    public record CorporationInfo(Long corporationId, String name, String ticker) {
    }

    /** 最近游戏权限检查与本地缓存时间。 */
    public record PermissionFreshness(LocalDateTime lastCheckedAt, LocalDateTime snapshotCapturedAt,
                                      LocalDateTime cacheExpiresAt, boolean cacheExpiryEstimated) {
    }
}
