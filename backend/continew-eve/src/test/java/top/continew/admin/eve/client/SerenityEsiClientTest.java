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

package top.continew.admin.eve.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.model.serenity.SerenityCorporationMemberTrackingResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationAssetResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationDivisionResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationStructureResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationMoonExtractionResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationMiningLedgerResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationMiningObserverResponse;
import top.continew.admin.eve.model.serenity.SerenityEsiPagedResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationRolesResponse;
import top.continew.admin.eve.model.serenity.SerenityEsiResponse;
import top.continew.admin.eve.model.serenity.SerenityUniverseStructureResponse;
import top.continew.admin.eve.model.serenity.SerenityUniverseStationResponse;
import top.continew.admin.eve.model.serenity.SerenityGameMailDetailResponse;
import top.continew.admin.eve.model.serenity.SerenityGameMailHeaderResponse;
import top.continew.admin.eve.model.serenity.SerenityGameMailRecipient;
import top.continew.admin.eve.model.serenity.SerenityGameMailSendRequest;
import top.continew.admin.eve.model.serenity.SerenityGameNotificationResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 国服 ESI 客户端响应元数据测试。
 *
 * @author zhaoyuqing
 */
class SerenityEsiClientTest {

    private MockRestServiceServer server;
    private SerenityEsiClient client;

    /** 初始化固定测试基址与可验证的 RestClient。 */
    @BeforeEach
    void setUp() {
        SerenityProperties properties = new SerenityProperties();
        properties.getEsi().setBaseUrl("https://esi.example.test/latest");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SerenityEsiClient(builder.build(), properties);
    }

    /** 角色查询应保留上游 Expires 和 ETag，不再伪造接口数据时间。 */
    @Test
    void shouldPreserveRoleCacheMetadata() {
        server.expect(once(), requestTo("https://esi.example.test/latest/characters/8001/roles/?datasource=serenity"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
            .andRespond(withSuccess("""
                {"roles":["Director"],"roles_at_hq":[],"roles_at_base":[],"roles_at_other":[]}
                """, MediaType.APPLICATION_JSON).header(HttpHeaders.EXPIRES, "Wed, 09 Sep 2026 12:00:00 GMT")
                .header(HttpHeaders.ETAG, "\"roles-v1\""));

        SerenityEsiResponse<SerenityCorporationRolesResponse> response = client
            .getCorporationRolesWithMetadata(8001L, "access-token");

        assertThat(response.body().roles()).containsExactly("Director");
        assertThat(response.expiresAt()).isEqualTo(LocalDateTime.ofInstant(Instant
            .parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC));
        assertThat(response.etag()).isEqualTo("\"roles-v1\"");
        server.verify();
    }

    /** 成员名册和追踪请求必须固定路径、携带数据源授权并保留缓存时间。 */
    @Test
    void shouldRequestCorporationRosterAndTrackingWithBearerToken() {
        server
            .expect(once(), requestTo("https://esi.example.test/latest/corporations/9901/members/?datasource=serenity"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
            .andRespond(withSuccess("[1001,1002]", MediaType.APPLICATION_JSON)
                .header(HttpHeaders.EXPIRES, "Wed, 09 Sep 2026 12:00:00 GMT"));
        server
            .expect(once(), requestTo("https://esi.example.test/latest/corporations/9901/membertracking/?datasource=serenity"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
            .andRespond(withSuccess("""
                [{"character_id":1001,"location_id":30000142,"ship_type_id":603,"logon_date":"2026-09-09T01:00:00Z"}]
                """, MediaType.APPLICATION_JSON));

        SerenityEsiResponse<java.util.List<Long>> roster = client
            .getCorporationMembersWithMetadata(9901L, "access-token");
        SerenityEsiResponse<java.util.List<SerenityCorporationMemberTrackingResponse>> tracking = client
            .getCorporationMemberTrackingWithMetadata(9901L, "access-token");

        assertThat(roster.body()).containsExactly(1001L, 1002L);
        assertThat(tracking.body()).singleElement()
            .extracting(SerenityCorporationMemberTrackingResponse::characterId)
            .isEqualTo(1001L);
        server.verify();
    }

    /** 资产接口必须保留 X-Pages，并将 datasource 与页码放入同一查询串。 */
    @Test
    void shouldRequestPagedCorporationAssetsWithBearerToken() {
        server
            .expect(once(), requestTo("https://esi.example.test/latest/corporations/9901/assets/?datasource=serenity&page=2"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
            .andRespond(withSuccess("""
                [{"item_id":1000000016835,"type_id":3516,"quantity":1,"location_id":60002959,
                  "location_type":"station","location_flag":"Hangar","is_singleton":true}]
                """, MediaType.APPLICATION_JSON).header(HttpHeaders.EXPIRES, "Wed, 09 Sep 2026 12:00:00 GMT")
                .header("X-Pages", "3"));

        SerenityEsiPagedResponse<SerenityCorporationAssetResponse> response = client
            .getCorporationAssetsWithMetadata(9901L, 2, "access-token");

        assertThat(response.pageCount()).isEqualTo(3);
        assertThat(response.body()).singleElement()
            .extracting(SerenityCorporationAssetResponse::itemId)
            .isEqualTo(1000000016835L);
        server.verify();
    }

    /** 军团建筑清单必须按页读取并使用建筑读取 Scope 对应的 Bearer Token。 */
    @Test
    void shouldRequestPagedCorporationStructuresWithBearerToken() {
        server
            .expect(once(), requestTo("https://esi.example.test/latest/corporations/9901/structures/?datasource=serenity&page=2"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
            .andRespond(withSuccess("""
                [{"structure_id":1016139224840,"type_id":35835,"system_id":30001421,"name":"月矿一号",
                  "state":"online_deprecated","fuel_expires":"2026-09-12T01:00:00Z",
                  "services":[{"name":"Clone Bay","state":"online"}]}]
                """, MediaType.APPLICATION_JSON).header("X-Pages", "2"));

        SerenityEsiPagedResponse<SerenityCorporationStructureResponse> response = client
            .getCorporationStructuresWithMetadata(9901L, 2, "access-token");

        assertThat(response.pageCount()).isEqualTo(2);
        assertThat(response.body()).singleElement()
            .extracting(SerenityCorporationStructureResponse::name)
            .isEqualTo("月矿一号");
        assertThat(response.body()).singleElement()
            .extracting(SerenityCorporationStructureResponse::fuelExpiresAt)
            .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2026-09-12T01:00:00Z"), ZoneOffset.UTC));
        assertThat(response.body().get(0).services()).singleElement()
            .extracting(SerenityCorporationStructureResponse.Service::state)
            .isEqualTo("online");
        server.verify();
    }

    /** 军团机库必须读取游戏内自定义分区名称，而不是向前端暴露 CorpSAG 编号。 */
    @Test
    void shouldRequestCorporationCustomDivisionNamesWithBearerToken() {
        server
            .expect(once(), requestTo("https://esi.example.test/latest/corporations/9901/divisions/?datasource=serenity"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
            .andRespond(withSuccess("""
                {"hangar":[{"division":1,"name":"军备库"},{"division":3,"name":"工业材料库"}],"wallet":[]}
                """, MediaType.APPLICATION_JSON).header(HttpHeaders.EXPIRES, "Wed, 09 Sep 2026 12:00:00 GMT"));

        SerenityEsiResponse<SerenityCorporationDivisionResponse> response = client
            .getCorporationDivisionsWithMetadata(9901L, "access-token");

        assertThat(response.body().hangar()).extracting(item -> item.name()).containsExactly("军备库", "工业材料库");
        assertThat(response.expiresAt()).isEqualTo(LocalDateTime.ofInstant(Instant
            .parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC));
        server.verify();
    }

    /** 月矿计划必须按页解析，并保留国服时间线的全部三个时间点。 */
    @Test
    void shouldRequestPagedCorporationMoonExtractionsWithBearerToken() {
        server
            .expect(once(), requestTo("https://esi.example.test/latest/corporation/9901/mining/extractions/?datasource=serenity&page=2"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
            .andRespond(withSuccess("""
                [{"structure_id":1016139224840,"moon_id":40009001,
                  "extraction_start_time":"2026-09-01T01:00:00Z",
                  "chunk_arrival_time":"2026-09-08T01:00:00Z",
                  "natural_decay_time":"2026-09-11T01:00:00Z"}]
                """, MediaType.APPLICATION_JSON).header("X-Pages", "2"));

        SerenityEsiPagedResponse<SerenityCorporationMoonExtractionResponse> response = client
            .getCorporationMoonExtractionsWithMetadata(9901L, 2, "access-token");

        assertThat(response.pageCount()).isEqualTo(2);
        assertThat(response.body()).singleElement().satisfies(item -> {
            assertThat(item.structureId()).isEqualTo(1016139224840L);
            assertThat(item.moonId()).isEqualTo(40009001L);
            assertThat(item.extractionStartAt()).isEqualTo(LocalDateTime.ofInstant(Instant
                .parse("2026-09-01T01:00:00Z"), ZoneOffset.UTC));
            assertThat(item.chunkArrivalAt()).isEqualTo(LocalDateTime.ofInstant(Instant
                .parse("2026-09-08T01:00:00Z"), ZoneOffset.UTC));
            assertThat(item.naturalDecayAt()).isEqualTo(LocalDateTime.ofInstant(Instant
                .parse("2026-09-11T01:00:00Z"), ZoneOffset.UTC));
        });
        server.verify();
    }

    /** 会计账本必须先按观察者分页，再按观察者 ID 读取对应的明细分页。 */
    @Test
    void shouldRequestPagedCorporationMiningObserversAndLedgerWithBearerToken() {
        server
            .expect(once(), requestTo("https://esi.example.test/latest/corporation/9901/mining/observers/?datasource=serenity&page=2"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
            .andRespond(withSuccess("""
                [{"observer_id":1016139224840,"observer_type":"structure","last_updated":"2026-09-09"}]
                """, MediaType.APPLICATION_JSON).header("X-Pages", "2"));
        server
            .expect(once(), requestTo("https://esi.example.test/latest/corporation/9901/mining/observers/1016139224840/?datasource=serenity&page=3"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
            .andRespond(withSuccess("""
                [{"character_id":9001,"last_updated":"2026-09-09","quantity":42,
                  "recorded_corporation_id":9901,"type_id":1230}]
                """, MediaType.APPLICATION_JSON).header("X-Pages", "3"));

        SerenityEsiPagedResponse<SerenityCorporationMiningObserverResponse> observers = client
            .getCorporationMiningObserversWithMetadata(9901L, 2, "access-token");
        SerenityEsiPagedResponse<SerenityCorporationMiningLedgerResponse> ledger = client
            .getCorporationMiningLedgerWithMetadata(9901L, 1016139224840L, 3, "access-token");

        assertThat(observers.pageCount()).isEqualTo(2);
        assertThat(observers.body()).singleElement().satisfies(item -> {
            assertThat(item.observerId()).isEqualTo(1016139224840L);
            assertThat(item.lastUpdated()).isEqualTo(LocalDate.parse("2026-09-09"));
        });
        assertThat(ledger.pageCount()).isEqualTo(3);
        assertThat(ledger.body()).singleElement().satisfies(item -> {
            assertThat(item.characterId()).isEqualTo(9001L);
            assertThat(item.recordedCorporationId()).isEqualTo(9901L);
            assertThat(item.quantity()).isEqualTo(42L);
        });
        server.verify();
    }

    /** 资产自定义名称接口必须使用资产数据源授权，并允许空名称结果。 */
    @Test
    void shouldRequestCorporationAssetNamesWithBearerToken() {
        server
            .expect(once(), requestTo("https://esi.example.test/latest/corporations/9901/assets/names/?datasource=serenity"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
            .andRespond(withSuccess("[{\"item_id\":1000000016835,\"name\":\"矿石箱\"}]", MediaType.APPLICATION_JSON));

        assertThat(client.getCorporationAssetNames(9901L, java.util.List.of(1000000016835L), "access-token"))
            .singleElement()
            .extracting(item -> item.name())
            .isEqualTo("矿石箱");
        server.verify();
    }

    /** 私有建筑名称必须使用结构读取 Scope 对应的 Bearer Token，不能走公开名称批量接口。 */
    @Test
    void shouldRequestPrivateStructureNameWithBearerToken() {
        server
            .expect(once(), requestTo("https://esi.example.test/latest/universe/structures/1015838365469/?datasource=serenity"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
            .andRespond(withSuccess("{\"name\":\"测试前哨\",\"solar_system_id\":30000142}", MediaType.APPLICATION_JSON));

        SerenityUniverseStructureResponse response = client.getUniverseStructure(1015838365469L, "access-token");

        assertThat(response.name()).isEqualTo("测试前哨");
        assertThat(response.solarSystemId()).isEqualTo(30000142L);
        server.verify();
    }

    /** 公开空间站查询必须不携带角色令牌，并返回资产树需要的星系归属。 */
    @Test
    void shouldRequestPublicStationWithSolarSystem() {
        server
            .expect(once(), requestTo("https://esi.example.test/latest/universe/stations/60002959/?datasource=serenity"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"name\":\"艾玛 VIII（帝国海军装配车间）\",\"system_id\":30002187}", MediaType.APPLICATION_JSON));

        SerenityUniverseStationResponse response = client.getUniverseStation(60002959L);

        assertThat(response.name()).isEqualTo("艾玛 VIII（帝国海军装配车间）");
        assertThat(response.solarSystemId()).isEqualTo(30002187L);
        server.verify();
    }

    /** 游戏内邮件读取必须使用当前角色令牌，并保留邮件头和正文的缓存元数据。 */
    @Test
    void shouldReadGameMailHeadersAndDetailWithBearerToken() {
        server.expect(once(), requestTo("https://esi.example.test/latest/characters/8001/mail/?datasource=serenity"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
            .andRespond(withSuccess("""
                [{"from":9001,"is_read":false,"labels":[1],"mail_id":10001,
                  "recipients":[{"recipient_id":8001,"recipient_type":"character"}],
                  "subject":"舰队通知","timestamp":"2026-09-10T04:00:00Z"}]
                """, MediaType.APPLICATION_JSON).header(HttpHeaders.EXPIRES, "Wed, 10 Sep 2026 04:01:00 GMT"));
        server
            .expect(once(), requestTo("https://esi.example.test/latest/characters/8001/mail/10001/?datasource=serenity"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
            .andRespond(withSuccess("""
                {"body":"集合","from":9001,"is_read":false,"labels":[1],
                 "recipients":[{"recipient_id":8001,"recipient_type":"character"}],
                 "subject":"舰队通知","timestamp":"2026-09-10T04:00:00Z"}
                """, MediaType.APPLICATION_JSON));

        SerenityEsiResponse<java.util.List<SerenityGameMailHeaderResponse>> headers = client
            .getGameMailHeadersWithMetadata(8001L, "access-token");
        SerenityEsiResponse<SerenityGameMailDetailResponse> detail = client
            .getGameMailDetailWithMetadata(8001L, 10001L, "access-token");

        assertThat(headers.body()).singleElement().satisfies(item -> {
            assertThat(item.mailId()).isEqualTo(10001L);
            assertThat(item.recipients()).singleElement()
                .extracting(SerenityGameMailRecipient::recipientType)
                .isEqualTo("character");
        });
        assertThat(detail.body().body()).isEqualTo("集合");
        server.verify();
    }

    /** 游戏通知读取必须使用角色令牌，并映射国服的类型、发送方和正文事实。 */
    @Test
    void shouldReadGameNotificationsWithBearerToken() {
        server
            .expect(once(), requestTo("https://esi.example.test/latest/characters/8001/notifications/?datasource=serenity"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
            .andRespond(withSuccess("""
                [{"is_read":false,"notification_id":10001,"sender_id":9001,"sender_type":"corporation",
                  "text":"军团成员状态已变更","timestamp":"2026-09-10T04:00:00Z","type":"CorpAppNewMsg"}]
                """, MediaType.APPLICATION_JSON).header(HttpHeaders.EXPIRES, "Wed, 10 Sep 2026 04:01:00 GMT"));

        SerenityEsiResponse<java.util.List<SerenityGameNotificationResponse>> response = client
            .getGameNotificationsWithMetadata(8001L, "access-token");

        assertThat(response.body()).singleElement().satisfies(item -> {
            assertThat(item.notificationId()).isEqualTo(10001L);
            assertThat(item.senderType()).isEqualTo("corporation");
            assertThat(item.type()).isEqualTo("CorpAppNewMsg");
        });
        server.verify();
    }

    /** 游戏内邮件发送必须使用 POST、Bearer Token 并返回国服邮件 ID。 */
    @Test
    void shouldSendGameMailWithBearerToken() {
        server.expect(once(), requestTo("https://esi.example.test/latest/characters/8001/mail/?datasource=serenity"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
            .andExpect(content()
                .json("""
                    {"approved_cost":0,"body":"集合","recipients":[{"recipient_id":9001,"recipient_type":"character"}],"subject":"舰队通知"}
                    """))
            .andRespond(withSuccess("10002", MediaType.APPLICATION_JSON));

        Long mailId = client.sendGameMail(8001L, new SerenityGameMailSendRequest(null, "集合", java.util.List
            .of(new SerenityGameMailRecipient(9001L, "character")), "舰队通知"), "access-token");

        assertThat(mailId).isEqualTo(10002L);
        server.verify();
    }

    /** 本地配置错误应原样暴露，不能被归类为国服临时故障。 */
    @Test
    void shouldNotMaskLocalConfigurationFailure() {
        SerenityProperties invalidProperties = new SerenityProperties();
        invalidProperties.getEsi().setBaseUrl(null);
        SerenityEsiClient invalidClient = new SerenityEsiClient(RestClient.create(), invalidProperties);

        assertThatThrownBy(() -> invalidClient.getCharacter(8001L)).isInstanceOf(NullPointerException.class);
    }

    /** 读取超时必须归类为可重试的上游临时故障。 */
    @Test
    void shouldClassifyReadTimeoutAsTransient() {
        SerenityEsiClientException exception = SerenityEsiClient
            .classify(new ResourceAccessException("timeout", new SocketTimeoutException()));

        assertThat(exception.getFailureCode()).isEqualTo(OAuthFailureCode.TRANSIENT);
    }

    /** ESI 超时和限流状态必须保持授权可重试，不能误判为永久失效。 */
    @Test
    void shouldClassifyTimeoutAndRateLimitsAsTransient() {
        for (int status : new int[] {408, 420, 429}) {
            SerenityEsiClientException exception = SerenityEsiClient
                .classify(new HttpClientErrorException(HttpStatusCode.valueOf(status)));

            assertThat(exception.getFailureCode()).as("HTTP %s", status).isEqualTo(OAuthFailureCode.TRANSIENT);
        }
    }
}
