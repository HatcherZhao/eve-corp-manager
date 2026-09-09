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
import top.continew.admin.eve.model.serenity.SerenityCorporationStructureResponse;
import top.continew.admin.eve.model.serenity.SerenityEsiPagedResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationRolesResponse;
import top.continew.admin.eve.model.serenity.SerenityEsiResponse;
import top.continew.admin.eve.model.serenity.SerenityUniverseStructureResponse;
import top.continew.admin.eve.model.serenity.SerenityUniverseStationResponse;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
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
                [{"structure_id":1016139224840,"type_id":35835,"system_id":30001421,"name":"月矿一号"}]
                """, MediaType.APPLICATION_JSON).header("X-Pages", "2"));

        SerenityEsiPagedResponse<SerenityCorporationStructureResponse> response = client
            .getCorporationStructuresWithMetadata(9901L, 2, "access-token");

        assertThat(response.pageCount()).isEqualTo(2);
        assertThat(response.body()).singleElement()
            .extracting(SerenityCorporationStructureResponse::name)
            .isEqualTo("月矿一号");
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
