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

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 国服注册回调服务测试。
 *
 * @author zhaoyuqing
 */
class SerenityRegistrationCallbackServiceTest {

    /** 验证 JWT、Scope、军团与 Director 事实交叉校验后仅签发短期凭证。 */
    @Test
    void shouldIssueCredentialAfterIdentityVerification() {
        SerenityCallbackUrlParser parser = mock(SerenityCallbackUrlParser.class);
        OAuthTransactionStore transactionStore = mock(OAuthTransactionStore.class);
        SerenityTokenClient tokenClient = mock(SerenityTokenClient.class);
        SerenityJwtDecoderFactory decoderFactory = mock(SerenityJwtDecoderFactory.class);
        JwtDecoder decoder = mock(JwtDecoder.class);
        Jwt jwt = mock(Jwt.class);
        SerenityEsiClient esiClient = mock(SerenityEsiClient.class);
        RegistrationCredentialStore credentialStore = mock(RegistrationCredentialStore.class);
        SerenityProperties properties = new SerenityProperties();
        properties.getSso().setScopeClaim("scp");
        properties.getRegistration().setCredentialTtl(Duration.ofMinutes(8));
        OAuthTransaction transaction = OAuthTransaction.builder()
            .browserBindingDigest("browser-digest")
            .purpose(OAuthTransactionPurpose.REGISTER)
            .requestedScopes(Set.of("esi-characters.read_corporation_roles.v1"))
            .verifier("v".repeat(43))
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
        SerenityTokenResponse token = new SerenityTokenResponse("access-token", "refresh-token", "Bearer", 1200, "esi-characters.read_corporation_roles.v1");
        when(parser.parse("callback-url")).thenReturn(new SerenityCallback("code", "state", null));
        when(transactionStore.consume("state")).thenReturn(Optional.of(transaction));
        when(tokenClient.exchangeCode("code", transaction.getVerifier())).thenReturn(token);
        when(decoderFactory.create()).thenReturn(decoder);
        when(decoder.decode("access-token")).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn("CHARACTER:EVE:1001");
        when(jwt.getClaimAsString("owner")).thenReturn("owner-hash");
        when(jwt.getClaim("scp")).thenReturn(List.of("esi-characters.read_corporation_roles.v1"));
        when(esiClient.getCharacter(1001L)).thenReturn(new SerenityCharacterResponse("Pilot_1001", 2001L));
        when(esiClient.getCorporation(2001L))
            .thenReturn(new SerenityCorporationResponse("测试军团", "TEST", 999L, null, 50, null));
        when(esiClient.getCorporationRolesWithMetadata(1001L, "access-token"))
            .thenReturn(new SerenityEsiResponse<>(new SerenityCorporationRolesResponse(List.of("Director"), List
                .of("Accountant"), List.of(), List.of()), null, null));
        when(credentialStore.issue(org.mockito.ArgumentMatchers.eq("browser-digest"), org.mockito.ArgumentMatchers
            .any(), org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(8)))).thenReturn("registration-credential");
        SerenityRegistrationCallbackService service = new SerenityRegistrationCallbackService(parser, transactionStore, tokenClient, decoderFactory, esiClient, credentialStore, properties);

        RegistrationCallbackResult result = service.handle("callback-url", "browser-digest");

        assertThat(result.credential()).isEqualTo("registration-credential");
        assertThat(result.director()).isTrue();
        assertThat(result.ceo()).isFalse();
        assertThat(result.characterName()).isEqualTo("Pilot_1001");
        verify(credentialStore).issue(org.mockito.ArgumentMatchers.eq("browser-digest"), org.mockito.ArgumentMatchers
            .argThat(identity -> identity.characterId().equals(1001L) && identity.rolesAtHq()
                .contains("Accountant") && identity.scopes()
                    .contains("esi-characters.read_corporation_roles.v1")), org.mockito.ArgumentMatchers.eq(Duration
                        .ofMinutes(8)));
    }
}
