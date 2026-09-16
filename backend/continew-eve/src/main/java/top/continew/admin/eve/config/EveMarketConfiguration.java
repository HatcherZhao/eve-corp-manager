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

package top.continew.admin.eve.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 吉他公开市场独立 HTTP 客户端。
 *
 * @author zhaoyuqing
 */
@Configuration
public class EveMarketConfiguration {

    /** 创建独立客户端，避免市场慢请求占用国服 ESI 授权通道。 */
    @Bean("eveMarketRestClient")
    public RestClient eveMarketRestClient(EveMarketProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getTimeout());
        requestFactory.setReadTimeout(properties.getTimeout());
        return RestClient.builder()
            .requestFactory(requestFactory)
            .defaultHeader("User-Agent", "EVE-Corp-Manager/1.0 (+https://eve.codeagent.cc)")
            .defaultHeader("Referer", properties.getBaseUrl() + "/home/")
            .build();
    }
}
