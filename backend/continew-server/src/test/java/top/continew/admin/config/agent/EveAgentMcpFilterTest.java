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

package top.continew.admin.config.agent;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import top.continew.admin.eve.agent.EveAgentPrincipal;
import top.continew.admin.eve.agent.auth.EveAgentApplicationService;
import top.continew.starter.core.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 覆盖 MCP 独立身份、来源校验、请求上限与并发资源释放。 */
class EveAgentMcpFilterTest {

    private EveAgentApplicationService applications;
    private EveAgentMcpFilter filter;
    private EveAgentMcpProperties properties;
    private final EveAgentPrincipal principal = new EveAgentPrincipal(1L, 11L, 21L, 31L, "合成测试应用");

    /** 使用合成应用身份初始化每个测试的独立配额。 */
    @BeforeEach
    void setUp() {
        applications = mock(EveAgentApplicationService.class);
        when(applications.authenticate("synthetic")).thenReturn(principal);
        properties = new EveAgentMcpProperties();
        filter = new EveAgentMcpFilter(applications, properties);
    }

    /** 缺失、撤销或通过 URL 传递的凭据均不能进入协议处理。 */
    @Test
    void rejectsMissingInvalidAndQueryCredentials() throws Exception {
        MockHttpServletRequest request = request();
        request.removeHeader("Authorization");
        assertEquals(401, response(request).getStatus());
        request = request();
        when(applications.authenticate("synthetic")).thenThrow(new BusinessException("不能暴露的状态"));
        assertEquals(401, response(request).getStatus());
        request.setQueryString("access_token=synthetic");
        assertEquals(401, response(request).getStatus());
    }

    /** 伪造租户头和重复认证头不能覆盖服务端绑定身份。 */
    @Test
    void ignoresForgedTenantAndDoesNotReuseLoginCookies() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader("tenantId", "999");
        request.addHeader("X-Tenant-Id", "999");
        request.addHeader("Authorization", "Bearer second");
        assertEquals(401, response(request).getStatus());
        request = request();
        request.addHeader("X-Tenant-Id", "999");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> assertSame(principal, req
            .getAttribute(EveAgentMcpFilter.PRINCIPAL_ATTRIBUTE)));
        assertEquals("no-store", response.getHeader("Cache-Control"));
    }

    /** 仅精确配置的浏览器来源可通过，来源校验优先于数据库认证。 */
    @Test
    void checksOriginBeforeAuthenticationAndAllowsOnlyExplicitOrigins() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader("Origin", "https://untrusted.example");
        assertEquals(403, response(request).getStatus());
        verifyNoInteractions(applications);
        properties.setAllowedOrigins(List.of("https://untrusted.example"));
        assertEquals(200, response(request).getStatus());
    }

    /** 内容类型、压缩方式及请求体积都在 SDK 解析前检查。 */
    @Test
    void rejectsWrongMediaTypeAndOversizedUnknownLengthBody() throws Exception {
        MockHttpServletRequest request = request();
        request.setContentType("text/plain");
        assertEquals(415, response(request).getStatus());
        request = request();
        request.setContent(new byte[65537]);
        assertEquals(413, response(request).getStatus());
        request = request();
        request.addHeader("Content-Encoding", "gzip");
        assertEquals(415, response(request).getStatus());
    }

    /** 应用不能通过多个连接绕过固定分钟配额。 */
    @Test
    void enforcesSixtyRequestsPerMinute() throws Exception {
        for (int i = 0; i < 60; i++)
            assertEquals(200, response(request()).getStatus());
        assertEquals(429, response(request()).getStatus());
    }

    /** 并发额度覆盖完整调用周期，正常和异常结束后都释放。 */
    @Test
    void holdsTwoConcurrencySlotsUntilChainFinishesAndReleasesAfterFailure() throws Exception {
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var task = (java.util.concurrent.Callable<Void>)() -> {
                filter.doFilter(request(), new MockHttpServletResponse(), (req, res) -> {
                    started.countDown();
                    try {
                        assertTrue(release.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                });
                return null;
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertEquals(429, response(request()).getStatus());
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertThrows(IllegalStateException.class, () -> filter.doFilter(request(), new MockHttpServletResponse(), (
                                                                                                                       req,
                                                                                                                       res) -> {
                throw new IllegalStateException("合成异常");
            }));
            assertEquals(200, response(request()).getStatus());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    /** 验证过滤器转交给 SDK 的是经过限额但内容不变的请求体。 */
    private MockHttpServletResponse response(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> assertEquals("{}", new String(req.getInputStream()
            .readAllBytes(), StandardCharsets.UTF_8)));
        return response;
    }

    /** 构造不含真实密钥的有效请求。 */
    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader("Authorization", "Bearer synthetic");
        request.setContentType("application/json");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
