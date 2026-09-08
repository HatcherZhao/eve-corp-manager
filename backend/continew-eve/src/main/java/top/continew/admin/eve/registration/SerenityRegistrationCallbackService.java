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
import org.springframework.stereotype.Service;
import top.continew.admin.eve.auth.OAuthTransaction;
import top.continew.admin.eve.auth.OAuthTransactionPurpose;
import top.continew.admin.eve.auth.OAuthTransactionStore;
import top.continew.admin.eve.client.SerenityEsiClient;
import top.continew.admin.eve.client.SerenityTokenClient;
import top.continew.admin.eve.client.SerenityTokenResponse;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.model.serenity.SerenityCharacterResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationRolesResponse;
import top.continew.admin.eve.model.serenity.SerenityEsiResponse;
import top.continew.admin.eve.security.SerenityCallback;
import top.continew.admin.eve.security.SerenityCallbackUrlParser;
import top.continew.admin.eve.security.SerenityJwtDecoderFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 消费注册 OAuth 回调并签发本站一次性注册凭证。
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class SerenityRegistrationCallbackService {

    private static final Pattern CHARACTER_ID_SUFFIX = Pattern.compile("(?:^|:)([1-9][0-9]*)$");

    private final SerenityCallbackUrlParser callbackParser;
    private final OAuthTransactionStore transactionStore;
    private final SerenityTokenClient tokenClient;
    private final SerenityJwtDecoderFactory jwtDecoderFactory;
    private final SerenityEsiClient esiClient;
    private final RegistrationCredentialStore credentialStore;
    private final SerenityProperties properties;

    /**
     * 验证回调、国服 JWT 与 ESI 身份事实，成功后只签发短期注册凭证。
     *
     * @param callbackUrl          完整回调 URL
     * @param browserBindingDigest 浏览器绑定摘要
     * @return 非敏感注册引导结果
     */
    public RegistrationCallbackResult handle(String callbackUrl, String browserBindingDigest) {
        SerenityCallback callback = callbackParser.parse(callbackUrl);
        OAuthTransaction transaction = transactionStore.consume(callback.state())
            .orElseThrow(() -> new IllegalStateException("国服授权事务已失效"));
        validateTransaction(transaction, browserBindingDigest, callback);

        SerenityTokenResponse token = tokenClient.exchangeCode(callback.code(), transaction.getVerifier());
        Jwt jwt = jwtDecoderFactory.create().decode(token.accessToken());
        Long characterId = parseCharacterId(jwt.getSubject());
        String ownerHash = requireText(jwt.getClaimAsString("owner"), "国服角色所有者声明缺失");
        List<String> scopes = readScopes(jwt.getClaim(properties.getSso().getScopeClaim()));

        SerenityCharacterResponse character = esiClient.getCharacter(characterId);
        if (character.corporationId() == null || character.corporationId() <= 0 || character.name() == null || character
            .name()
            .isBlank()) {
            throw new IllegalStateException("国服角色资料无效");
        }
        SerenityCorporationResponse corporation = esiClient.getCorporation(character.corporationId());
        SerenityEsiResponse<SerenityCorporationRolesResponse> roleResult = esiClient
            .getCorporationRolesWithMetadata(characterId, token.accessToken());
        SerenityCorporationRolesResponse roleResponse = roleResult.body();
        List<String> roles = safeList(roleResponse.roles());
        boolean ceo = characterId.equals(corporation.ceoId());
        boolean director = roles.contains("Director");

        VerifiedRegistrationIdentity identity = VerifiedRegistrationIdentity.builder()
            .server(properties.getEsi().getDatasource().getValue())
            .characterId(characterId)
            .characterName(character.name())
            .corporationId(character.corporationId())
            .corporationName(requireText(corporation.name(), "国服军团资料无效"))
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
            .scopes(scopes)
            .accessToken(token.accessToken())
            .refreshToken(token.refreshToken())
            .tokenType(token.tokenType())
            .expiresAt(Instant.now().plusSeconds(token.expiresIn()))
            .roleSourceExpiresAt(roleResult.expiresAt())
            .build();
        String credential = credentialStore.issue(browserBindingDigest, identity, properties.getRegistration()
            .getCredentialTtl());
        return new RegistrationCallbackResult(credential, character.name(), corporation.name(), ceo, director);
    }

    /** 校验一次性事务用途、时效、浏览器绑定及上游错误。 */
    private static void validateTransaction(OAuthTransaction transaction,
                                            String browserBindingDigest,
                                            SerenityCallback callback) {
        boolean sameBrowser = browserBindingDigest != null && MessageDigest.isEqual(transaction
            .getBrowserBindingDigest()
            .getBytes(StandardCharsets.US_ASCII), browserBindingDigest.getBytes(StandardCharsets.US_ASCII));
        if (transaction.getPurpose() != OAuthTransactionPurpose.REGISTER || transaction
            .getExpiresAt() == null || transaction.getExpiresAt().isBefore(Instant.now()) || !sameBrowser || callback
                .hasError()) {
            throw new IllegalStateException("国服授权事务校验失败");
        }
    }

    /** 从已校验 subject 尾部提取角色 ID。 */
    private static Long parseCharacterId(String subject) {
        Matcher matcher = CHARACTER_ID_SUFFIX.matcher(subject == null ? "" : subject);
        if (!matcher.find()) {
            throw new IllegalStateException("国服角色标识无效");
        }
        return Long.valueOf(matcher.group(1));
    }

    /** 读取字符串或字符串集合形式的 Scope。 */
    private static List<String> readScopes(Object claim) {
        if (claim instanceof String value) {
            return value.isBlank() ? List.of() : List.of(value.trim().split("\\s+"));
        }
        if (claim instanceof Collection<?> values && values.stream().allMatch(String.class::isInstance)) {
            return values.stream().map(String.class::cast).distinct().toList();
        }
        throw new IllegalStateException("国服授权 Scope 无效");
    }

    /** 将国服可空角色数组归一为空列表。 */
    private static List<String> safeList(List<String> values) {
        return values == null
            ? List.of()
            : values.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
    }

    /** 校验必填国服文本，不回显原始值。 */
    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value;
    }

}
