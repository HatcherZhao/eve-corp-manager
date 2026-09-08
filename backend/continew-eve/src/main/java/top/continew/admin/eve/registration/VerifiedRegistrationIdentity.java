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

package top.continew.admin.eve.registration;

import lombok.Builder;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 已通过 JWT、Scope 和国服 ESI 交叉验证的短期注册身份。
 *
 * @author zhaoyuqing
 */
@Builder
public record VerifiedRegistrationIdentity(String server, Long characterId, String characterName, Long corporationId,
                                           String corporationName, String corporationTicker, Long ceoCharacterId,
                                           Long allianceId, Integer memberCount, BigDecimal taxRate, String ownerHash,
                                           boolean ceo, boolean director, List<String> roles, List<String> rolesAtHq,
                                           List<String> rolesAtBase, List<String> rolesAtOther, List<String> scopes,
                                           String accessToken, String refreshToken, String tokenType, Instant expiresAt,
                                           LocalDateTime roleSourceExpiresAt) implements Serializable {

    /** 防止日志输出令牌与身份校验摘要。 */
    @Override
    public String toString() {
        return "VerifiedRegistrationIdentity[server=" + server + ", characterId=" + characterId + ", corporationId=" + corporationId + ", ceo=" + ceo + ", director=" + director + "]";
    }
}
