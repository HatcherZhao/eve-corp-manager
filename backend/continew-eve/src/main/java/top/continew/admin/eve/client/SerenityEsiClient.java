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
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.model.serenity.SerenityCharacterResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationResponse;
import top.continew.admin.eve.model.serenity.SerenityCorporationRolesResponse;
import top.continew.admin.eve.model.serenity.SerenityEsiResponse;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 仅访问固定国服 ESI 基址的身份事实客户端。
 *
 * @author zhaoyuqing
 */
@Component
public class SerenityEsiClient {

    private final RestClient restClient;
    private final SerenityProperties properties;

    /** 创建国服 ESI 客户端。 */
    public SerenityEsiClient(@Qualifier("serenityRestClient") RestClient restClient, SerenityProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
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

    /** 将 HTTP Expires 转换为 UTC 时间，上游未提供时返回空。 */
    private static LocalDateTime expiresAt(ResponseEntity<?> response) {
        long expires = response.getHeaders().getExpires();
        return expires < 0 ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(expires), ZoneId.systemDefault());
    }

    /** 拼接固定 ESI 基址与固定路径模板。 */
    private String endpoint(String path) {
        return properties.getEsi().getBaseUrl().replaceAll("/+$", "") + path + "?datasource=" + properties.getEsi()
            .getDatasource()
            .getValue();
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
