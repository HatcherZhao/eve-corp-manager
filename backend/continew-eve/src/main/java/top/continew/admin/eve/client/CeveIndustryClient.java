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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import top.continew.admin.eve.config.CeveIndustryProperties;
import top.continew.starter.core.exception.BusinessException;

/** 读取 ceve-industry.cn 公开蓝图接口。 */
@Component
public class CeveIndustryClient {

    private final RestClient restClient;
    private final CeveIndustryProperties properties;
    private final ObjectMapper objectMapper;

    /** 创建公开蓝图客户端并绑定独立 HTTP 通道。 */
    @Autowired
    public CeveIndustryClient(@Qualifier("ceveIndustryRestClient") RestClient restClient,
                              CeveIndustryProperties properties,
                              ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** 获取公开站点提供的全量蓝图 JSON。 */
    public JsonNode getAllBlueprints() {
        try {
            String body = restClient.get()
                .uri(properties.getBaseUrl() + "/pricedata/allBluePrints")
                .retrieve()
                .body(String.class);
            JsonNode result = objectMapper.readTree(body);
            if (!result.isArray()) {
                throw new BusinessException("ceve-industry.cn 蓝图接口返回格式无效");
            }
            return result;
        } catch (RuntimeException e) {
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException("读取 ceve-industry.cn 蓝图数据失败");
        } catch (Exception e) {
            throw new BusinessException("解析 ceve-industry.cn 蓝图数据失败");
        }
    }
}
