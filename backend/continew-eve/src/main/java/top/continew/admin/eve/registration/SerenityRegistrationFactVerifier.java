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

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import top.continew.admin.eve.client.OAuthFailureCode;
import top.continew.admin.eve.client.SerenityEsiClient;
import top.continew.admin.eve.client.SerenityEsiClientException;
import top.continew.admin.eve.client.SerenityTokenClient;
import top.continew.admin.eve.client.SerenityTokenClientException;
import top.continew.admin.eve.client.SerenityTokenResponse;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.model.serenity.SerenityCharacterResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationRolesResponse;
import top.continew.admin.eve.model.serenity.SerenityEsiResponse;
import top.continew.admin.eve.security.SerenityJwtDecoderFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 在最终注册锁内刷新授权并重新核验国服角色资格。
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class SerenityRegistrationFactVerifier {

    private static final Pattern CHARACTER_ID_SUFFIX = Pattern.compile("(?:^|:)([1-9][0-9]*)$");

    private final SerenityTokenClient tokenClient;
    private final SerenityJwtDecoderFactory jwtDecoderFactory;
    private final SerenityEsiClient esiClient;
    private final SerenityProperties properties;

    /**
     * 使用刷新令牌取得新的服务端授权，并以实时 ESI 事实替换短期快照。
     *
     * @param previous 回调阶段已验证身份
     * @return 当前有效身份事实与轮换后的令牌
     */
    public VerifiedRegistrationIdentity refresh(VerifiedRegistrationIdentity previous) {
        if (previous.refreshToken() == null || previous.refreshToken().isBlank()) {
            throw new RegistrationQualificationChangedException("国服授权已失效，请重新授权");
        }
        SerenityTokenResponse token;
        try {
            token = tokenClient.refresh(previous.refreshToken());
        } catch (SerenityTokenClientException e) {
            if (isRevokedAuthorization(e.getFailureCode())) {
                throw new RegistrationQualificationChangedException("国服授权已失效，请重新授权");
            }
            throw e;
        }
        Jwt jwt;
        try {
            jwt = jwtDecoderFactory.create().decode(token.accessToken());
        } catch (JwtException e) {
            throw new RegistrationQualificationChangedException("国服授权已失效，请重新授权");
        }
        Long characterId = parseCharacterId(jwt.getSubject());
        String ownerHash = jwt.getClaimAsString("owner");
        if (!previous.characterId().equals(characterId) || ownerHash == null || !previous.ownerHash()
            .equals(ownerHash)) {
            throw new RegistrationQualificationChangedException("国服授权角色已变化，请重新授权");
        }

        SerenityCharacterResponse character = esiClient.getCharacter(characterId);
        if (character.corporationId() == null || !previous.corporationId().equals(character.corporationId())) {
            throw new RegistrationQualificationChangedException("角色所属军团已变化，请重新授权");
        }
        SerenityCorporationResponse corporation = esiClient.getCorporation(character.corporationId());
        SerenityEsiResponse<SerenityCorporationRolesResponse> roleResult;
        try {
            roleResult = esiClient.getCorporationRolesWithMetadata(characterId, token.accessToken());
        } catch (SerenityEsiClientException e) {
            if (isRevokedAuthorization(e.getFailureCode())) {
                throw new RegistrationQualificationChangedException("国服授权已失效，请重新授权");
            }
            throw e;
        }
        SerenityCorporationRolesResponse roleResponse = roleResult.body();
        List<String> roles = safeList(roleResponse.roles());
        boolean ceo = characterId.equals(corporation.ceoId());
        boolean director = roles.contains("Director");
        if ((previous.ceo() || previous.director()) && !ceo && !director) {
            throw new RegistrationQualificationChangedException("CEO 或总监资格已变化，请重新授权");
        }

        return VerifiedRegistrationIdentity.builder()
            .server(previous.server())
            .characterId(characterId)
            .characterName(requireText(character.name()))
            .corporationId(character.corporationId())
            .corporationName(requireText(corporation.name()))
            .corporationTicker(corporation.ticker())
            .ceoCharacterId(corporation.ceoId())
            .allianceId(corporation.allianceId())
            .memberCount(corporation.memberCount())
            .taxRate(corporation.taxRate())
            .ownerHash(ownerHash)
            .ceo(ceo)
            .director(director)
            .roles(roles)
            .rolesAtHq(safeList(roleResponse.rolesAtHq()))
            .rolesAtBase(safeList(roleResponse.rolesAtBase()))
            .rolesAtOther(safeList(roleResponse.rolesAtOther()))
            .scopes(resolveScopes(token.scope(), previous.scopes()))
            .accessToken(token.accessToken())
            .refreshToken(token.refreshToken() == null || token.refreshToken().isBlank()
                ? previous.refreshToken()
                : token.refreshToken())
            .tokenType(token.tokenType())
            .expiresAt(Instant.now().plusSeconds(token.expiresIn()))
            .roleSourceExpiresAt(roleResult.expiresAt())
            .build();
    }

    /** 从刷新令牌 JWT subject 中提取并校验角色 ID。 */
    private static Long parseCharacterId(String subject) {
        Matcher matcher = CHARACTER_ID_SUFFIX.matcher(subject == null ? "" : subject);
        if (!matcher.find()) {
            throw new RegistrationQualificationChangedException("国服授权角色已变化，请重新授权");
        }
        return Long.valueOf(matcher.group(1));
    }

    /** 清洗 ESI 返回的可空角色列表。 */
    private static List<String> safeList(List<String> values) {
        return values == null
            ? List.of()
            : values.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
    }

    /** 使用刷新响应 Scope；上游省略时保持原授权范围。 */
    private static List<String> resolveScopes(String scope, List<String> previousScopes) {
        return scope == null || scope.isBlank()
            ? previousScopes
            : Arrays.stream(scope.trim().split("\\s+")).distinct().toList();
    }

    /** 拒绝缺失的角色或军团名称。 */
    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new RegistrationQualificationChangedException("国服角色或军团资料已失效，请重新授权");
        }
        return value;
    }

    /** 判断上游是否已经明确确认授权不可继续使用。 */
    private static boolean isRevokedAuthorization(OAuthFailureCode failureCode) {
        return failureCode == OAuthFailureCode.INVALID_GRANT || failureCode == OAuthFailureCode.UNAUTHORIZED || failureCode == OAuthFailureCode.FORBIDDEN;
    }
}
