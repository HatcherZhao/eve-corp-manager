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
 * 官网资讯独立 HTTP 客户端配置，不占用国服 ESI 的授权请求通道。
 *
 * @author zhaoyuqing
 */
@Configuration
public class EveOfficialNewsConfiguration {

    /** 创建带超时和固定标识的官网公开页面客户端。 */
    @Bean("eveOfficialNewsRestClient")
    public RestClient eveOfficialNewsRestClient(EveOfficialNewsProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getTimeout());
        requestFactory.setReadTimeout(properties.getTimeout());
        return RestClient.builder()
            .requestFactory(requestFactory)
            .defaultHeader("User-Agent", "EVE-Corp-Manager/1.0 (+https://eve.codeagent.cc)")
            .build();
    }
}
