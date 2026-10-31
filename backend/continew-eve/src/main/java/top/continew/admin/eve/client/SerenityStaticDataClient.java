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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.model.serenity.SerenityEsiPagedResponse;
import top.continew.admin.eve.model.serenity.SerenityMarketGroupResponse;
import top.continew.admin.eve.model.serenity.SerenityUniverseNameResponse;
import top.continew.admin.eve.model.serenity.SerenityUniverseTypeResponse;
import top.continew.admin.eve.service.EveUpstreamRequestPacer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 读取晨曦官方物品类型与市场分类。
 *
 * <p>全量目录同步始终经过共享节流器；类型详情仅按实际查看补齐，避免为五万余种类型逐项请求。</p>
 */
@Component
public class SerenityStaticDataClient {

    private final RestClient restClient;
    private final SerenityProperties properties;
    private final EveUpstreamRequestPacer requestPacer;

    /** 创建固定使用晨曦 ESI 与共享节流器的静态资料客户端。 */
    public SerenityStaticDataClient(@Qualifier("serenityRestClient") RestClient restClient,
                                    SerenityProperties properties,
                                    EveUpstreamRequestPacer requestPacer) {
        this.restClient = restClient;
        this.properties = properties;
        this.requestPacer = requestPacer;
    }

    /** 分页读取全部类型 ID，包含未进入市场分类的游戏对象。 */
    public SerenityEsiPagedResponse<Integer> getTypeIds(int page) {
        if (page < 1) {
            throw new IllegalArgumentException("国服类型目录页码无效");
        }
        try {
            ResponseEntity<Integer[]> response = restClient.get()
                .uri(endpoint("/universe/types/") + "&page=" + page)
                .retrieve()
                .toEntity(Integer[].class);
            return new SerenityEsiPagedResponse<>(List.of(requireBody(response.getBody())), expiresAt(response), response
                .getHeaders()
                .getETag(), pageCount(response));
        } catch (RestClientException e) {
            throw SerenityEsiClient.classify(e);
        }
    }

    /** 读取全部市场组 ID。 */
    public List<Integer> getMarketGroupIds() {
        try {
            Integer[] response = restClient.get().uri(endpoint("/markets/groups/")).retrieve().body(Integer[].class);
            return List.of(requireBody(response));
        } catch (RestClientException e) {
            throw SerenityEsiClient.classify(e);
        }
    }

    /** 读取一个市场组的中文名称、父级和直属物品。 */
    public SerenityMarketGroupResponse getMarketGroup(int marketGroupId) {
        if (marketGroupId <= 0) {
            throw new IllegalArgumentException("国服市场组 ID 无效");
        }
        try {
            return requireBody(restClient.get()
                .uri(endpoint("/markets/groups/{id}/") + "&language=zh", marketGroupId)
                .retrieve()
                .body(SerenityMarketGroupResponse.class));
        } catch (RestClientException e) {
            throw SerenityEsiClient.classify(e);
        }
    }

    /** 批量读取类型中文名，每批最多 1000 个 ID。 */
    public List<SerenityUniverseNameResponse> getTypeNames(List<Long> typeIds) {
        if (typeIds == null || typeIds.isEmpty() || typeIds.size() > 1000) {
            throw new IllegalArgumentException("国服类型名称批量查询参数无效");
        }
        try {
            SerenityUniverseNameResponse[] response = restClient.post()
                .uri(endpoint("/universe/names/") + "&language=zh")
                .body(typeIds)
                .retrieve()
                .body(SerenityUniverseNameResponse[].class);
            return response == null ? List.of() : List.of(response);
        } catch (RestClientException e) {
            throw SerenityEsiClient.classify(e);
        }
    }

    /** 按需读取单个类型的官方中文说明。 */
    public SerenityUniverseTypeResponse getType(int typeId) {
        if (typeId <= 0) {
            throw new IllegalArgumentException("国服类型 ID 无效");
        }
        try {
            return requireBody(restClient.get()
                .uri(endpoint("/universe/types/{id}/") + "&language=zh", typeId)
                .retrieve()
                .body(SerenityUniverseTypeResponse.class));
        } catch (RestClientException e) {
            throw SerenityEsiClient.classify(e);
        }
    }

    /** 拼接固定晨曦数据源，并为实际请求申请共享节流许可。 */
    private String endpoint(String path) {
        requestPacer.awaitPermit();
        return properties.getEsi().getBaseUrl().replaceAll("/+$", "") + path + "?datasource=" + properties.getEsi()
            .getDatasource()
            .getValue();
    }

    private static int pageCount(ResponseEntity<?> response) {
        try {
            String value = response.getHeaders().getFirst("X-Pages");
            return value == null ? 1 : Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            throw new SerenityEsiClientException(OAuthFailureCode.PERMANENT);
        }
    }

    private static LocalDateTime expiresAt(ResponseEntity<?> response) {
        long expires = response.getHeaders().getExpires();
        return expires < 0 ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(expires), ZoneOffset.UTC);
    }

    private static <T> T requireBody(T body) {
        if (body == null) {
            throw new SerenityEsiClientException(OAuthFailureCode.INVALID_TOKEN_RESPONSE);
        }
        return body;
    }
}
