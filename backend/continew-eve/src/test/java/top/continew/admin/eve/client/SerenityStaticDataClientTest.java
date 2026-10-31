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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.service.EveUpstreamRequestPacer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 晨曦官方静态资料客户端契约测试。 */
class SerenityStaticDataClientTest {

    private MockRestServiceServer server;
    private SerenityStaticDataClient client;

    @BeforeEach
    void setUp() {
        SerenityProperties properties = new SerenityProperties();
        properties.getEsi().setBaseUrl("https://esi.example.test/latest");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SerenityStaticDataClient(builder.build(), properties, mock(EveUpstreamRequestPacer.class));
    }

    /** 全部类型目录必须保留官方分页信息，供可续跑任务逐页读取。 */
    @Test
    void shouldReadPagedUniverseTypeIds() {
        server.expect(requestTo("https://esi.example.test/latest/universe/types/?datasource=serenity&page=2"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[34,88087]", MediaType.APPLICATION_JSON).header("X-Pages", "57"));

        var result = client.getTypeIds(2);

        assertThat(result.body()).containsExactly(34, 88087);
        assertThat(result.pageCount()).isEqualTo(57);
        server.verify();
    }

    /** 批量名称查询必须显式请求中文并保留 inventory_type 分类。 */
    @Test
    void shouldResolveOfficialChineseTypeNames() {
        server.expect(requestTo("https://esi.example.test/latest/universe/names/?datasource=serenity&language=zh"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("[34,88087]"))
            .andRespond(withSuccess("""
                [{"category":"inventory_type","id":34,"name":"三钛合金"},
                 {"category":"inventory_type","id":88087,"name":"艾琉特瑞姆矿"}]
                """, MediaType.APPLICATION_JSON));

        var result = client.getTypeNames(List.of(34L, 88087L));

        assertThat(result).extracting(item -> item.name()).containsExactly("三钛合金", "艾琉特瑞姆矿");
        server.verify();
    }
}
