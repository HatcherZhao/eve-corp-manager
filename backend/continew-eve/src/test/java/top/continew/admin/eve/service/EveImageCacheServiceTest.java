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

package top.continew.admin.eve.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import top.continew.admin.eve.config.EveImageCacheProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * EVE 图片持久化缓存的下载、命中和上游故障回退测试。
 *
 * @author zhaoyuqing
 */
class EveImageCacheServiceTest {

    private static final byte[] IMAGE = {1, 2, 3, 4};

    @TempDir
    Path cacheDirectory;

    private MockRestServiceServer server;
    private EveImageCacheService imageCacheService;

    /** 初始化固定图片基址、临时缓存目录和可验证 HTTP 客户端。 */
    @BeforeEach
    void setUp() {
        EveImageCacheProperties properties = new EveImageCacheProperties();
        properties.setBaseUrl("https://image.example.test");
        properties.setCacheDirectory(cacheDirectory.toString());
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        imageCacheService = new EveImageCacheService(builder.build(), properties);
    }

    /** 首次访问应下载并原子落盘，后续有效缓存命中不再请求上游。 */
    @Test
    void shouldPersistTypeIconAndReuseFreshCache() throws Exception {
        server.expect(once(), requestTo("https://image.example.test/Type/16236_64.png"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(IMAGE, MediaType.IMAGE_PNG));

        EveImageCacheService.CachedImage first = imageCacheService.typeIcon(16236L);
        EveImageCacheService.CachedImage second = imageCacheService.typeIcon(16236L);

        assertThat(first.path()).isEqualTo(cacheDirectory.resolve("types/16236.png"));
        assertThat(second.path()).isEqualTo(first.path());
        assertThat(Files.readAllBytes(first.path())).isEqualTo(IMAGE);
        assertThat(first.mediaType()).isEqualTo(MediaType.IMAGE_PNG);
        server.verify();
    }

    /** 缓存刷新遇到上游错误时，应保留并返回上一次成功保存的图片。 */
    @Test
    void shouldServeStaleImageWhenRefreshFails() throws Exception {
        server.expect(once(), requestTo("https://image.example.test/Type/35835_64.png"))
            .andRespond(withSuccess(IMAGE, MediaType.IMAGE_PNG));
        EveImageCacheService.CachedImage cached = imageCacheService.typeIcon(35835L);
        server.verify();
        Files.setLastModifiedTime(cached.path(), FileTime.from(Instant.now().minusSeconds(366L * 24 * 60 * 60)));

        server.reset();
        server.expect(once(), requestTo("https://image.example.test/Type/35835_64.png"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        EveImageCacheService.CachedImage stale = imageCacheService.typeIcon(35835L);

        assertThat(stale.path()).isEqualTo(cached.path());
        assertThat(Files.readAllBytes(stale.path())).isEqualTo(IMAGE);
        server.verify();
    }
}
