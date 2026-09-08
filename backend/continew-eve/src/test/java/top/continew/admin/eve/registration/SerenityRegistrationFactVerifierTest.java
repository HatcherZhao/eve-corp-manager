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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import top.continew.admin.eve.client.OAuthFailureCode;
import top.continew.admin.eve.client.SerenityEsiClient;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 最终注册实时资格复核测试。
 *
 * @author zhaoyuqing
 */
class SerenityRegistrationFactVerifierTest {

    private SerenityTokenClient tokenClient;
    private SerenityEsiClient esiClient;
    private SerenityRegistrationFactVerifier verifier;

    /** 初始化刷新授权与 ESI 测试替身。 */
    @BeforeEach
    void setUp() {
        tokenClient = mock(SerenityTokenClient.class);
        esiClient = mock(SerenityEsiClient.class);
        SerenityJwtDecoderFactory decoderFactory = mock(SerenityJwtDecoderFactory.class);
        JwtDecoder decoder = mock(JwtDecoder.class);
        Jwt jwt = mock(Jwt.class);
        when(decoderFactory.create()).thenReturn(decoder);
        when(decoder.decode("new-access")).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn("CHARACTER:EVE:100");
        when(jwt.getClaimAsString("owner")).thenReturn("owner-hash");
        when(tokenClient.refresh("refresh-token"))
            .thenReturn(new SerenityTokenResponse("new-access", "new-refresh", "Bearer", 1200, null));
        when(esiClient.getCharacter(100L)).thenReturn(new SerenityCharacterResponse("Pilot_100", 200L));
        when(esiClient.getCorporation(200L))
            .thenReturn(new SerenityCorporationResponse("测试军团", "TEST", 999L, null, 10, null));
        verifier = new SerenityRegistrationFactVerifier(tokenClient, decoderFactory, esiClient, new SerenityProperties());
    }

    /** 服务端仍确认总监资格时返回刷新后的令牌与实时角色。 */
    @Test
    void shouldReturnCurrentFactsFromRefreshedAuthorization() {
        when(esiClient.getCorporationRolesWithMetadata(100L, "new-access"))
            .thenReturn(new SerenityEsiResponse<>(new SerenityCorporationRolesResponse(List.of("Director"), List
                .of(), List.of(), List.of()), null, null));

        VerifiedRegistrationIdentity current = verifier.refresh(identity(true));

        assertThat(current.director()).isTrue();
        assertThat(current.accessToken()).isEqualTo("new-access");
        assertThat(current.refreshToken()).isEqualTo("new-refresh");
    }

    /** 回调后被撤销总监权限时必须拒绝最终注册。 */
    @Test
    void shouldRejectRevokedDirectorPrivilege() {
        when(esiClient.getCorporationRolesWithMetadata(100L, "new-access"))
            .thenReturn(new SerenityEsiResponse<>(new SerenityCorporationRolesResponse(List.of(), List.of(), List
                .of(), List.of()), null, null));

        assertThatThrownBy(() -> verifier.refresh(identity(true)))
            .isInstanceOf(RegistrationQualificationChangedException.class)
            .hasMessage("CEO 或总监资格已变化，请重新授权");
    }

    /** 回调后角色换军团时必须拒绝最终注册。 */
    @Test
    void shouldRejectChangedCorporation() {
        when(esiClient.getCharacter(100L)).thenReturn(new SerenityCharacterResponse("Pilot_100", 201L));

        assertThatThrownBy(() -> verifier.refresh(identity(false)))
            .isInstanceOf(RegistrationQualificationChangedException.class)
            .hasMessage("角色所属军团已变化，请重新授权");
    }

    /** 刷新令牌被上游撤销时必须归一为需重新授权的资格变化。 */
    @Test
    void shouldRejectRevokedRefreshToken() {
        when(tokenClient.refresh("refresh-token"))
            .thenThrow(new SerenityTokenClientException(OAuthFailureCode.INVALID_GRANT));

        assertThatThrownBy(() -> verifier.refresh(identity(false)))
            .isInstanceOf(RegistrationQualificationChangedException.class)
            .hasMessage("国服授权已失效，请重新授权");
    }

    /** 创建回调阶段的测试身份。 */
    private static VerifiedRegistrationIdentity identity(boolean director) {
        return VerifiedRegistrationIdentity.builder()
            .server("serenity")
            .characterId(100L)
            .characterName("Pilot_100")
            .corporationId(200L)
            .corporationName("测试军团")
            .ownerHash("owner-hash")
            .ceo(false)
            .director(director)
            .roles(director ? List.of("Director") : List.of())
            .rolesAtHq(List.of())
            .rolesAtBase(List.of())
            .rolesAtOther(List.of())
            .scopes(List.of("esi-characters.read_corporation_roles.v1"))
            .accessToken("old-access")
            .refreshToken("refresh-token")
            .tokenType("Bearer")
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
    }
}
