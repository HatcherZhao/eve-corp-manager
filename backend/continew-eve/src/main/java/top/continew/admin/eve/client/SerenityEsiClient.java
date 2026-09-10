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

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.service.EveUpstreamRequestPacer;
import top.continew.admin.eve.model.serenity.SerenityCharacterResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationRolesResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationMemberTrackingResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationAssetResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationAssetNameResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationStructureResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationDivisionResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationMoonExtractionResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationMiningLedgerResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationMiningObserverResponse;
import top.continew.admin.eve.model.serenity.SerenityEsiResponse;
import top.continew.admin.eve.model.serenity.SerenityEsiPagedResponse;
import top.continew.admin.eve.model.serenity.SerenityUniverseNameResponse;
import top.continew.admin.eve.model.serenity.SerenityUniverseIdsResponse;
import top.continew.admin.eve.model.serenity.SerenityUniverseStationResponse;
import top.continew.admin.eve.model.serenity.SerenityUniverseStructureResponse;
import top.continew.admin.eve.model.serenity.SerenityUniverseMoonResponse;
import top.continew.admin.eve.model.serenity.SerenityGameMailDetailResponse;
import top.continew.admin.eve.model.serenity.SerenityGameMailHeaderResponse;
import top.continew.admin.eve.model.serenity.SerenityGameMailLabelResponse;
import top.continew.admin.eve.model.serenity.SerenityGameMailSendRequest;
import top.continew.admin.eve.model.serenity.SerenityGameMailUpdateRequest;
import top.continew.admin.eve.model.serenity.SerenityGameNotificationResponse;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 仅访问固定国服 ESI 基址的身份事实客户端。
 *
 * @author zhaoyuqing
 */
@Component
public class SerenityEsiClient {

    private final RestClient restClient;
    private final SerenityProperties properties;
    private final EveUpstreamRequestPacer requestPacer;

    /** 兼容现有单元测试创建的未接入 Spring 节流器客户端。 */
    public SerenityEsiClient(@Qualifier("serenityRestClient") RestClient restClient, SerenityProperties properties) {
        this(restClient, properties, null);
    }

    /** 创建受全局节流器保护的国服 ESI 客户端。 */
    @Autowired
    public SerenityEsiClient(@Qualifier("serenityRestClient") RestClient restClient,
                             SerenityProperties properties,
                             EveUpstreamRequestPacer requestPacer) {
        this.restClient = restClient;
        this.properties = properties;
        this.requestPacer = requestPacer;
    }

    /** 查询公开角色资料。 */
    public SerenityCharacterResponse getCharacter(Long characterId) {
        try {
            return requireBody(restClient.get()
                .uri(endpoint("/characters/{id}/"), characterId)
                .retrieve()
                .body(SerenityCharacterResponse.class));
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /** 查询公开军团资料。 */
    public SerenityCorporationResponse getCorporation(Long corporationId) {
        try {
            return requireBody(restClient.get()
                .uri(endpoint("/corporations/{id}/"), corporationId)
                .retrieve()
                .body(SerenityCorporationResponse.class));
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /** 使用当前角色授权查询四范围军团角色。 */
    public SerenityCorporationRolesResponse getCorporationRoles(Long characterId, String accessToken) {
        return getCorporationRolesWithMetadata(characterId, accessToken).body();
    }

    /** 查询四范围军团角色，同时保留上游缓存元数据。 */
    public SerenityEsiResponse<SerenityCorporationRolesResponse> getCorporationRolesWithMetadata(Long characterId,
                                                                                                 String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("国服访问令牌不能为空");
        }
        try {
            ResponseEntity<SerenityCorporationRolesResponse> response = restClient.get()
                .uri(endpoint("/characters/{id}/roles/"), characterId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toEntity(SerenityCorporationRolesResponse.class);
            return new SerenityEsiResponse<>(requireBody(response.getBody()), expiresAt(response), response.getHeaders()
                .getETag());
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /** 使用军团成员授权查询完整成员 ID 名册并保留上游缓存元数据。 */
    public SerenityEsiResponse<List<Long>> getCorporationMembersWithMetadata(Long corporationId, String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("国服访问令牌不能为空");
        }
        try {
            ResponseEntity<Long[]> response = restClient.get()
                .uri(endpoint("/corporations/{id}/members/"), corporationId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toEntity(Long[].class);
            return new SerenityEsiResponse<>(List.of(requireBody(response.getBody())), expiresAt(response), response
                .getHeaders()
                .getETag());
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /** 使用总监授权查询成员追踪快照并保留上游缓存元数据。 */
    public SerenityEsiResponse<List<SerenityCorporationMemberTrackingResponse>> getCorporationMemberTrackingWithMetadata(Long corporationId,
                                                                                                                         String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("国服访问令牌不能为空");
        }
        try {
            ResponseEntity<SerenityCorporationMemberTrackingResponse[]> response = restClient.get()
                .uri(endpoint("/corporations/{id}/membertracking/"), corporationId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toEntity(SerenityCorporationMemberTrackingResponse[].class);
            return new SerenityEsiResponse<>(List.of(requireBody(response.getBody())), expiresAt(response), response
                .getHeaders()
                .getETag());
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /**
     * 查询军团资产的单页快照，并保留分页和上游缓存元数据。
     *
     * <p>调用方负责按 {@link SerenityEsiPagedResponse#pageCount()} 读取后续页面，避免把不完整的
     * 上游响应发布为完整资产快照。</p>
     */
    public SerenityEsiPagedResponse<SerenityCorporationAssetResponse> getCorporationAssetsWithMetadata(Long corporationId,
                                                                                                       int page,
                                                                                                       String accessToken) {
        if (corporationId == null || corporationId <= 0 || page < 1 || accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("军团资产查询参数无效");
        }
        try {
            ResponseEntity<SerenityCorporationAssetResponse[]> response = restClient.get()
                .uri(pagedEndpoint("/corporations/{id}/assets/", page), corporationId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toEntity(SerenityCorporationAssetResponse[].class);
            int pageCount = response.getHeaders().getFirst("X-Pages") == null
                ? 1
                : Math.max(1, Integer.parseInt(response.getHeaders().getFirst("X-Pages")));
            return new SerenityEsiPagedResponse<>(List.of(requireBody(response
                .getBody())), expiresAt(response), response.getHeaders().getETag(), pageCount);
        } catch (NumberFormatException e) {
            throw new SerenityEsiClientException(OAuthFailureCode.PERMANENT);
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /** 使用总监或空间站管理员授权读取军团自有玩家建筑清单。 */
    public SerenityEsiPagedResponse<SerenityCorporationStructureResponse> getCorporationStructuresWithMetadata(Long corporationId,
                                                                                                               int page,
                                                                                                               String accessToken) {
        if (corporationId == null || corporationId <= 0 || page < 1 || accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("国服军团建筑查询参数无效");
        }
        try {
            ResponseEntity<SerenityCorporationStructureResponse[]> response = restClient.get()
                .uri(pagedEndpoint("/corporations/{id}/structures/", page), corporationId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toEntity(SerenityCorporationStructureResponse[].class);
            int pageCount = response.getHeaders().getFirst("X-Pages") == null
                ? 1
                : Math.max(1, Integer.parseInt(response.getHeaders().getFirst("X-Pages")));
            return new SerenityEsiPagedResponse<>(List.of(requireBody(response
                .getBody())), expiresAt(response), response.getHeaders().getETag(), pageCount);
        } catch (NumberFormatException e) {
            throw new SerenityEsiClientException(OAuthFailureCode.PERMANENT);
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /** 使用总监授权读取军团自定义机库与钱包分区名称，并保留上游缓存元数据。 */
    public SerenityEsiResponse<SerenityCorporationDivisionResponse> getCorporationDivisionsWithMetadata(Long corporationId,
                                                                                                        String accessToken) {
        if (corporationId == null || corporationId <= 0 || accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("军团分区查询参数无效");
        }
        try {
            ResponseEntity<SerenityCorporationDivisionResponse> response = restClient.get()
                .uri(endpoint("/corporations/{id}/divisions/"), corporationId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toEntity(SerenityCorporationDivisionResponse.class);
            return new SerenityEsiResponse<>(requireBody(response.getBody()), expiresAt(response), response.getHeaders()
                .getETag());
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /** 使用空间站管理员授权读取军团当前月矿提取情报。 */
    public SerenityEsiPagedResponse<SerenityCorporationMoonExtractionResponse> getCorporationMoonExtractionsWithMetadata(Long corporationId,
                                                                                                                         int page,
                                                                                                                         String accessToken) {
        if (corporationId == null || corporationId <= 0 || page < 1 || accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("军团月矿情报查询参数无效");
        }
        try {
            ResponseEntity<SerenityCorporationMoonExtractionResponse[]> response = restClient.get()
                .uri(pagedEndpoint("/corporation/{id}/mining/extractions/", page), corporationId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toEntity(SerenityCorporationMoonExtractionResponse[].class);
            int pageCount = response.getHeaders().getFirst("X-Pages") == null
                ? 1
                : Math.max(1, Integer.parseInt(response.getHeaders().getFirst("X-Pages")));
            return new SerenityEsiPagedResponse<>(List.of(requireBody(response
                .getBody())), expiresAt(response), response.getHeaders().getETag(), pageCount);
        } catch (NumberFormatException e) {
            throw new SerenityEsiClientException(OAuthFailureCode.PERMANENT);
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /** 使用会计授权读取军团采矿观察者的单页清单。 */
    public SerenityEsiPagedResponse<SerenityCorporationMiningObserverResponse> getCorporationMiningObserversWithMetadata(Long corporationId,
                                                                                                                         int page,
                                                                                                                         String accessToken) {
        if (corporationId == null || corporationId <= 0 || page < 1 || accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("军团采矿观察者查询参数无效");
        }
        try {
            ResponseEntity<SerenityCorporationMiningObserverResponse[]> response = restClient.get()
                .uri(pagedEndpoint("/corporation/{id}/mining/observers/", page), corporationId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toEntity(SerenityCorporationMiningObserverResponse[].class);
            return new SerenityEsiPagedResponse<>(List.of(requireBody(response
                .getBody())), expiresAt(response), response.getHeaders().getETag(), pageCount(response));
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /** 使用会计授权读取一个观察者采矿账本的单页明细。 */
    public SerenityEsiPagedResponse<SerenityCorporationMiningLedgerResponse> getCorporationMiningLedgerWithMetadata(Long corporationId,
                                                                                                                    Long observerId,
                                                                                                                    int page,
                                                                                                                    String accessToken) {
        if (corporationId == null || corporationId <= 0 || observerId == null || observerId <= 0 || page < 1 || accessToken == null || accessToken
            .isBlank()) {
            throw new IllegalArgumentException("军团采矿账本查询参数无效");
        }
        try {
            ResponseEntity<SerenityCorporationMiningLedgerResponse[]> response = restClient.get()
                .uri(pagedEndpoint("/corporation/{corporationId}/mining/observers/{observerId}/", page), corporationId, observerId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toEntity(SerenityCorporationMiningLedgerResponse[].class);
            return new SerenityEsiPagedResponse<>(List.of(requireBody(response
                .getBody())), expiresAt(response), response.getHeaders().getETag(), pageCount(response));
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /** 批量读取可自定义命名军团资产的名称；调用方必须将请求控制在 1000 个物品 ID 内。 */
    public List<SerenityCorporationAssetNameResponse> getCorporationAssetNames(Long corporationId,
                                                                               List<Long> itemIds,
                                                                               String accessToken) {
        if (corporationId == null || corporationId <= 0 || itemIds == null || itemIds.isEmpty() || itemIds
            .size() > 1000 || accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("军团资产名称查询参数无效");
        }
        try {
            SerenityCorporationAssetNameResponse[] response = restClient.post()
                .uri(endpoint("/corporations/{id}/assets/names/"), corporationId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .body(itemIds)
                .retrieve()
                .body(SerenityCorporationAssetNameResponse[].class);
            return response == null ? List.of() : List.of(response);
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /** 批量解析游戏 ID 名称；该 POST 是公开读取接口，不携带授权令牌。 */
    public List<SerenityUniverseNameResponse> resolveUniverseNames(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        try {
            SerenityUniverseNameResponse[] body = restClient.post()
                .uri(endpoint("/universe/names/"))
                .body(ids)
                .retrieve()
                .body(SerenityUniverseNameResponse[].class);
            return body == null ? List.of() : List.of(body);
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /** 通过公开国服接口将角色、军团或联盟名称反查为可用于邮件发送的游戏 ID。 */
    public SerenityUniverseIdsResponse resolveUniverseIds(List<String> names) {
        if (names == null || names.isEmpty() || names.size() > 1000 || names.stream()
            .anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("国服名称反查参数无效");
        }
        try {
            return requireBody(restClient.post()
                .uri(endpoint("/universe/ids/"))
                .body(names)
                .retrieve()
                .body(SerenityUniverseIdsResponse.class));
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /** 使用已授权角色读取私有建筑名称，用于解析成员追踪中的大型位置 ID。 */
    public SerenityUniverseStructureResponse getUniverseStructure(Long structureId, String accessToken) {
        if (structureId == null || structureId <= 0 || accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("国服建筑查询参数无效");
        }
        try {
            return requireBody(restClient.get()
                .uri(endpoint("/universe/structures/{id}/"), structureId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(SerenityUniverseStructureResponse.class));
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /** 查询公开月球资料，用于补全月矿情报的月球和星系名称。 */
    public SerenityUniverseMoonResponse getUniverseMoon(Long moonId) {
        if (moonId == null || moonId <= 0) {
            throw new IllegalArgumentException("国服月球查询参数无效");
        }
        try {
            return requireBody(restClient.get()
                .uri(endpoint("/universe/moons/{id}/"), moonId)
                .retrieve()
                .body(SerenityUniverseMoonResponse.class));
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /** 查询公开空间站及其所属星系，用于构建资产物理位置树。 */
    public SerenityUniverseStationResponse getUniverseStation(Long stationId) {
        if (stationId == null || stationId <= 0) {
            throw new IllegalArgumentException("国服空间站查询参数无效");
        }
        try {
            return requireBody(restClient.get()
                .uri(endpoint("/universe/stations/{id}/"), stationId)
                .retrieve()
                .body(SerenityUniverseStationResponse.class));
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /** 使用授权角色读取最近 50 封游戏内邮件头，并保留上游缓存元数据。 */
    public SerenityEsiResponse<List<SerenityGameMailHeaderResponse>> getGameMailHeadersWithMetadata(Long characterId,
                                                                                                    String accessToken) {
        requireCharacterToken(characterId, accessToken, "游戏内邮件读取参数无效");
        try {
            ResponseEntity<SerenityGameMailHeaderResponse[]> response = restClient.get()
                .uri(endpoint("/characters/{id}/mail/"), characterId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toEntity(SerenityGameMailHeaderResponse[].class);
            return new SerenityEsiResponse<>(List.of(requireBody(response.getBody())), expiresAt(response), response
                .getHeaders()
                .getETag());
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /** 使用授权角色读取最近游戏通知，并保留上游缓存元数据。 */
    public SerenityEsiResponse<List<SerenityGameNotificationResponse>> getGameNotificationsWithMetadata(Long characterId,
                                                                                                        String accessToken) {
        requireCharacterToken(characterId, accessToken, "游戏通知读取参数无效");
        try {
            ResponseEntity<SerenityGameNotificationResponse[]> response = restClient.get()
                .uri(endpoint("/characters/{id}/notifications/"), characterId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toEntity(SerenityGameNotificationResponse[].class);
            return new SerenityEsiResponse<>(List.of(requireBody(response.getBody())), expiresAt(response), response
                .getHeaders()
                .getETag());
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /** 读取当前角色在游戏中的邮件分类及各分类未读数。 */
    public SerenityEsiResponse<List<SerenityGameMailLabelResponse>> getGameMailLabelsWithMetadata(Long characterId,
                                                                                                  String accessToken) {
        requireCharacterToken(characterId, accessToken, "游戏内邮件分类读取参数无效");
        try {
            ResponseEntity<SerenityGameMailLabelResponse[]> response = restClient.get()
                .uri(endpoint("/characters/{id}/mail/labels/"), characterId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toEntity(SerenityGameMailLabelResponse[].class);
            return new SerenityEsiResponse<>(List.of(requireBody(response.getBody())), expiresAt(response), response
                .getHeaders()
                .getETag());
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /** 使用授权角色读取单封游戏内邮件正文，并保留上游缓存元数据。 */
    public SerenityEsiResponse<SerenityGameMailDetailResponse> getGameMailDetailWithMetadata(Long characterId,
                                                                                             Long mailId,
                                                                                             String accessToken) {
        requireCharacterToken(characterId, accessToken, "游戏内邮件读取参数无效");
        if (mailId == null || mailId <= 0) {
            throw new IllegalArgumentException("游戏内邮件 ID 无效");
        }
        try {
            ResponseEntity<SerenityGameMailDetailResponse> response = restClient.get()
                .uri(endpoint("/characters/{characterId}/mail/{mailId}/"), characterId, mailId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toEntity(SerenityGameMailDetailResponse.class);
            return new SerenityEsiResponse<>(requireBody(response.getBody()), expiresAt(response), response.getHeaders()
                .getETag());
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /** 使用授权角色立即发送游戏内邮件，返回国服生成的邮件 ID。 */
    public Long sendGameMail(Long characterId, SerenityGameMailSendRequest request, String accessToken) {
        requireCharacterToken(characterId, accessToken, "游戏内邮件发送参数无效");
        if (request == null || request.recipients() == null || request.recipients().isEmpty()) {
            throw new IllegalArgumentException("游戏内邮件收件人不能为空");
        }
        try {
            return requireBody(restClient.post()
                .uri(endpoint("/characters/{id}/mail/"), characterId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .body(request)
                .retrieve()
                .body(Long.class));
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /** 将单封游戏内邮件标记为已读或更新其游戏内分类，需邮件整理 Scope。 */
    public void updateGameMail(Long characterId,
                               Long mailId,
                               SerenityGameMailUpdateRequest request,
                               String accessToken) {
        requireCharacterToken(characterId, accessToken, "游戏内邮件整理参数无效");
        if (mailId == null || mailId <= 0 || request == null) {
            throw new IllegalArgumentException("游戏内邮件整理参数无效");
        }
        try {
            restClient.put()
                .uri(endpoint("/characters/{characterId}/mail/{mailId}/"), characterId, mailId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .body(request)
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /** 将 HTTP Expires 转换为 UTC 时间，上游未提供时返回空。 */
    private static LocalDateTime expiresAt(ResponseEntity<?> response) {
        long expires = response.getHeaders().getExpires();
        return expires < 0 ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(expires), ZoneOffset.UTC);
    }

    /** 校验角色私有接口的共同参数。 */
    private static void requireCharacterToken(Long characterId, String accessToken, String message) {
        if (characterId == null || characterId <= 0 || accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    /** 拼接固定 ESI 基址与固定路径模板。 */
    private String endpoint(String path) {
        if (requestPacer != null) {
            requestPacer.awaitPermit();
        }
        return properties.getEsi().getBaseUrl().replaceAll("/+$", "") + path + "?datasource=" + properties.getEsi()
            .getDatasource()
            .getValue();
    }

    /** 为带 X-Pages 的接口追加页码，确保 datasource 与 page 处于同一查询串。 */
    private String pagedEndpoint(String path, int page) {
        return endpoint(path) + "&page=" + page;
    }

    /** 从上游分页响应读取并校验总页数。 */
    private static int pageCount(ResponseEntity<?> response) {
        try {
            String value = response.getHeaders().getFirst("X-Pages");
            return value == null ? 1 : Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            throw new SerenityEsiClientException(OAuthFailureCode.PERMANENT);
        }
    }

    /** 拒绝空响应。 */
    private static <T> T requireBody(T body) {
        if (body == null) {
            throw new SerenityEsiClientException(OAuthFailureCode.INVALID_TOKEN_RESPONSE);
        }
        return body;
    }

    /** 将上游异常归一为不含响应正文的安全分类。 */
    static SerenityEsiClientException classify(RestClientException exception) {
        OAuthFailureCode code = OAuthFailureCode.TRANSIENT;
        if (exception instanceof HttpClientErrorException clientError) {
            if (isRetryableClientStatus(clientError.getStatusCode())) {
                code = OAuthFailureCode.TRANSIENT;
            } else if (clientError.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                code = OAuthFailureCode.UNAUTHORIZED;
            } else if (clientError.getStatusCode() == HttpStatus.FORBIDDEN) {
                code = OAuthFailureCode.FORBIDDEN;
            } else {
                code = OAuthFailureCode.PERMANENT;
            }
        }
        return new SerenityEsiClientException(code);
    }

    /** 将请求超时和国服限流状态保留为可重试故障，避免误撤销有效授权。 */
    private static boolean isRetryableClientStatus(HttpStatusCode statusCode) {
        int value = statusCode.value();
        return value == 408 || value == 420 || value == 429;
    }
}
