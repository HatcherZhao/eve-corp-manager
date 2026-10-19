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
import top.continew.admin.eve.service.EveUpstreamRequestPacer;

/**
 * 为国服 Token 与 ESI 调用提供独立且有界的 HTTP 客户端。
 *
 * <p>稳定的可识别 User-Agent 便于国服定位异常流量；具体 ESI 请求再由全局节流器串行控制。</p>
 *
 * @author zhaoyuqing
 */
@Configuration
public class SerenityHttpClientConfiguration {

    /** 创建带连接和读取超时的国服专用 RestClient。 */
    @Bean("serenityRestClient")
    public RestClient serenityRestClient(SerenityProperties properties, EveUpstreamRequestPacer requestPacer) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getHttpClient().getConnectTimeout());
        requestFactory.setReadTimeout(properties.getHttpClient().getReadTimeout());
        return RestClient.builder()
            .requestFactory(requestFactory)
            .defaultHeader("User-Agent", "EVE-Corp-Manager/1.0 (+https://eve.codeagent.cc)")
            .requestInterceptor((request, body, execution) -> {
                // Token 客户端复用此连接配置，但不得与 ESI 共用错误预算。
                String esiBase = properties.getEsi().getBaseUrl().replaceAll("/+$", "") + "/";
                boolean esiRequest = request.getURI().toString().startsWith(esiBase);
                if (esiRequest) {
                    requestPacer.assertNotCoolingDown();
                }
                var response = execution.execute(request, body);
                if (esiRequest) {
                    try {
                        requestPacer.observeResponse(response.getStatusCode(), response.getHeaders());
                    } catch (RuntimeException e) {
                        response.close();
                        throw e;
                    }
                }
                return response;
            })
            .build();
    }
}
