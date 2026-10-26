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

import java.net.URI;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import top.continew.admin.eve.agent.EveAgentPrincipal;
import top.continew.admin.eve.agent.auth.EveAgentApplicationService;
import top.continew.admin.eve.agent.model.EveAgentCorporationResp;
import top.continew.admin.eve.agent.query.EveAgentQueryService;
import top.continew.starter.core.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用官方 MCP 客户端和真实 Servlet HTTP 验证发现、结构化结果、身份与撤销。 */
class EveAgentMcpProtocolTest {

    /** 真实网络上不结束的请求体会在阻塞读取超时后关闭，不能无限占用并发。 */
    @Test
    void incompleteBodyTimesOutBeforeEnteringMcpTool() throws Exception {
        try (var context = new AnnotationConfigServletWebServerApplicationContext(TestConfiguration.class)) {
            when(context.getBean(EveAgentApplicationService.class).authenticate("synthetic"))
                .thenReturn(new EveAgentPrincipal(1L, 2L, 3L, 4L, "合成应用"));
            try (var socket = new Socket("127.0.0.1", context.getWebServer().getPort())) {
                socket.setSoTimeout(15000);
                socket.getOutputStream()
                    .write(("POST /mcp HTTP/1.1\r\nHost: localhost\r\n" + "Authorization: Bearer synthetic\r\nContent-Type: application/json\r\nContent-Length: 20\r\n\r\n{")
                        .getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                long started = System.nanoTime();
                int response = socket.getInputStream().read();
                assertEquals(-1, response);
                assertTrue(Duration.ofNanos(System.nanoTime() - started).toSeconds() < 14);
            }
            verifyNoInteractions(context.getBean(EveAgentQueryService.class));
        }
    }

    /** 从初始化到军团发现与四类查询均走真实 HTTP，并覆盖撤销与 JSON-RPC 数字 ID。 */
    @Test
    void realClientDiscoversToolsQueriesAndCannotReuseRevokedCredentials() throws Exception {
        try (var context = new AnnotationConfigServletWebServerApplicationContext(TestConfiguration.class)) {
            String base = "http://127.0.0.1:" + context.getWebServer().getPort();
            var applications = context.getBean(EveAgentApplicationService.class);
            var queries = context.getBean(EveAgentQueryService.class);
            var principal = new EveAgentPrincipal(1L, 2L, 3L, 4L, "合成军团应用");
            var identity = new EveAgentPrincipal(1L, null, null, null, "合成军团应用");
            var revoked = new AtomicBoolean(false);
            when(applications.authenticate(anyString())).thenAnswer(invocation -> {
                if (!"synthetic".equals(invocation.getArgument(0)) || revoked.get())
                    throw new BusinessException("无效凭据");
                return identity;
            });
            when(applications.resolveCorporation(identity, null)).thenReturn(principal);
            when(applications.listAuthorizedCorporations(identity)).thenReturn(List
                .of(new EveAgentCorporationResp(4L, "合成军团", "TEST")));
            when(queries.catalog(principal)).thenReturn(Map.of("datasets", List.of(Map
                .of("key", "mail")), "readOnly", true));
            when(queries.query(eq(principal), anyMap())).thenReturn(Map.of("items", List.of(Map
                .of("mailId", "9007199254740993")), "total", 1, "page", 1, "size", 20));
            when(queries.aggregate(eq(principal), anyMap())).thenReturn(Map.of("items", List.of(Map.of("count", "2"))));
            when(queries.readDocument(eq(principal), anyMap())).thenReturn(Map
                .of("content", "合成邮件正文", "hasMore", false));
            try (var client = client(base)) {
                assertNotNull(client.initialize());
                var tools = client.listTools().tools();
                assertEquals(5, tools.size());
                assertTrue(tools.stream().allMatch(tool -> Boolean.TRUE.equals(tool.annotations().readOnlyHint())));
                var authorized = call(client, "eve_corporations", Map.of());
                assertFalse(authorized.isError());
                assertEquals(1, ((List<?>)((Map<?, ?>)authorized.structuredContent()).get("corporations")).size());
                assertFalse(call(client, "eve_catalog", Map.of()).isError());
                var result = call(client, "eve_query", Map.of("dataset", "mail"));
                assertFalse(result.isError());
                assertNotNull(result.structuredContent());
                assertFalse(call(client, "eve_aggregate", Map.of("dataset", "mail", "metrics", List.of(Map
                    .of("function", "count", "alias", "total")))).isError());
                assertFalse(call(client, "eve_read_document", Map.of("dataset", "mail", "id", "7", "characterId", "9"))
                    .isError());
                clearInvocations(queries);
                // Schema 拒绝越界参数，不能把客户端传入的 tenantId 当身份。
                var invalid = call(client, "eve_query", Map.of("dataset", "mail", "tenantId", "999"));
                assertTrue(invalid.isError());
                verifyNoInteractions(queries);
                var rpc = HttpClient.newHttpClient()
                    .send(HttpRequest.newBuilder(URI.create(base + "/mcp"))
                        .header("Authorization", "Bearer synthetic")
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json, text/event-stream")
                        .header("MCP-Protocol-Version", "2025-06-18")
                        .POST(HttpRequest.BodyPublishers
                            .ofString("{\"jsonrpc\":\"2.0\",\"id\":9007199254740993,\"method\":\"tools/list\",\"params\":{}}"))
                        .build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, rpc.statusCode());
                var rpcId = new ObjectMapper().readTree(rpc.body()).get("id");
                assertTrue(rpcId.isIntegralNumber());
                assertEquals(9007199254740993L, rpcId.longValue());
                revoked.set(true);
                assertThrows(RuntimeException.class, () -> call(client, "eve_catalog", Map.of()));
            }
            verify(applications).recordCall(eq(principal), eq("eve_read_document"), eq(true), anyLong(), eq(1));
            var denied = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(URI.create(base + "/mcp"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, denied.statusCode());
            assertFalse(denied.body().contains("synthetic"));
        }
    }

    /** 并行的两个军团客户端经过 SDK 工作线程后仍保持各自的显式身份。 */
    @Test
    void concurrentCorporationsKeepIndependentTransportContexts() throws Exception {
        try (var context = new AnnotationConfigServletWebServerApplicationContext(TestConfiguration.class)) {
            String base = "http://127.0.0.1:" + context.getWebServer().getPort();
            var applications = context.getBean(EveAgentApplicationService.class);
            var queries = context.getBean(EveAgentQueryService.class);
            var first = new EveAgentPrincipal(1L, 2L, 3L, 4L, "军团甲");
            var second = new EveAgentPrincipal(11L, 12L, 13L, 14L, "军团乙");
            when(applications.authenticate("first")).thenReturn(first);
            when(applications.authenticate("second")).thenReturn(second);
            when(applications.resolveCorporation(first, null)).thenReturn(first);
            when(applications.resolveCorporation(second, null)).thenReturn(second);
            var entered = new CountDownLatch(2);
            when(queries.catalog(any())).thenAnswer(invocation -> {
                EveAgentPrincipal principal = invocation.getArgument(0);
                entered.countDown();
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                return Map.of("datasets", List.of(), "readOnly", true, "corporationId", principal.corporationId()
                    .toString());
            });
            var executor = Executors.newFixedThreadPool(2);
            try (var firstClient = client(base, "first"); var secondClient = client(base, "second")) {
                firstClient.initialize();
                secondClient.initialize();
                var firstResult = executor.submit(() -> call(firstClient, "eve_catalog", Map.of()));
                var secondResult = executor.submit(() -> call(secondClient, "eve_catalog", Map.of()));
                assertEquals("4", ((Map<?, ?>)firstResult.get(10, TimeUnit.SECONDS).structuredContent())
                    .get("corporationId"));
                assertEquals("14", ((Map<?, ?>)secondResult.get(10, TimeUnit.SECONDS).structuredContent())
                    .get("corporationId"));
                verify(applications).recordCall(eq(first), eq("eve_catalog"), eq(true), anyLong(), eq(0));
                verify(applications).recordCall(eq(second), eq("eve_catalog"), eq(true), anyLong(), eq(0));
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    /** 同一密钥显式选择两个军团，并在单团撤销后保留另一团授权。 */
    @Test
    void oneApplicationSelectsCorporationsWithoutSharingQueryOrDocumentScope() throws Exception {
        try (var context = new AnnotationConfigServletWebServerApplicationContext(TestConfiguration.class)) {
            String base = "http://127.0.0.1:" + context.getWebServer().getPort();
            var applications = context.getBean(EveAgentApplicationService.class);
            var queries = context.getBean(EveAgentQueryService.class);
            var identity = new EveAgentPrincipal(1L, null, null, null, "多军团合成应用");
            var first = new EveAgentPrincipal(1L, 2L, 3L, 4L, identity.applicationName());
            var second = new EveAgentPrincipal(1L, 12L, 13L, 14L, identity.applicationName());
            var firstRevoked = new AtomicBoolean();
            when(applications.authenticate("synthetic")).thenReturn(identity);
            when(applications.listAuthorizedCorporations(identity)).thenReturn(List
                .of(new EveAgentCorporationResp(4L, "军团甲", "AAA"), new EveAgentCorporationResp(14L, "军团乙", "BBB")));
            when(applications.resolveCorporation(eq(identity), nullable(String.class))).thenAnswer(invocation -> {
                String corporationId = invocation.getArgument(1);
                if (corporationId == null) {
                    throw new IllegalArgumentException("已授权多个军团，请指定 corporationId");
                }
                if ("4".equals(corporationId) && !firstRevoked.get())
                    return first;
                if ("14".equals(corporationId))
                    return second;
                throw new IllegalArgumentException("该军团不存在或未获有效授权");
            });
            when(queries.query(any(), anyMap())).thenAnswer(invocation -> {
                EveAgentPrincipal scope = invocation.getArgument(0);
                Map<?, ?> arguments = invocation.getArgument(1);
                assertFalse(arguments.containsKey("corporationId"));
                return Map.of("items", List.of(Map.of("characterId", scope.corporationRefId().toString())));
            });
            when(queries.readDocument(any(), anyMap())).thenAnswer(invocation -> {
                EveAgentPrincipal scope = invocation.getArgument(0);
                return Map.of("content", "合成正文-" + scope.corporationId(), "hasMore", false);
            });
            when(queries.aggregate(any(), anyMap())).thenReturn(Map.of("items", List.of()));
            try (var client = client(base)) {
                client.initialize();
                assertTrue(call(client, "eve_query", Map.of("dataset", "mail")).isError());
                assertTrue(call(client, "eve_query", Map.of("dataset", "mail", "corporationId", "99")).isError());
                assertTrue(call(client, "eve_query", Map.of("dataset", "mail", "corporationId", 4)).isError());
                verifyNoInteractions(queries);
                var firstResult = call(client, "eve_query", Map.of("dataset", "mail", "corporationId", "4"));
                var secondResult = call(client, "eve_query", Map.of("dataset", "mail", "corporationId", "14"));
                assertEquals("4", ((Map<?, ?>)firstResult.structuredContent()).get("corporationId"));
                assertEquals("14", ((Map<?, ?>)secondResult.structuredContent()).get("corporationId"));
                var document = call(client, "eve_read_document", Map
                    .of("dataset", "mail", "id", "7", "characterId", "9", "corporationId", "14"));
                assertEquals("合成正文-14", ((Map<?, ?>)document.structuredContent()).get("content"));
                assertFalse(call(client, "eve_aggregate", Map
                    .of("dataset", "mail", "corporationId", "14", "metrics", List.of(Map
                        .of("function", "count", "alias", "total")))).isError());
                clearInvocations(queries);
                firstRevoked.set(true);
                assertTrue(call(client, "eve_query", Map.of("dataset", "mail", "corporationId", "4")).isError());
                verifyNoInteractions(queries);
                assertFalse(call(client, "eve_query", Map.of("dataset", "mail", "corporationId", "14")).isError());
                verify(applications).recordCall(eq(second), eq("eve_read_document"), eq(true), anyLong(), eq(1));
            }
        }
    }

    /** 同一应用并发查两团时，军团选择保存在本次调用栈而非共享会话或线程变量。 */
    @Test
    void concurrentRequestsWithOneTokenKeepTheirSelectedCorporations() throws Exception {
        try (var context = new AnnotationConfigServletWebServerApplicationContext(TestConfiguration.class)) {
            String base = "http://127.0.0.1:" + context.getWebServer().getPort();
            var applications = context.getBean(EveAgentApplicationService.class);
            var queries = context.getBean(EveAgentQueryService.class);
            var identity = new EveAgentPrincipal(1L, null, null, null, "共享应用");
            var first = new EveAgentPrincipal(1L, 2L, 3L, 4L, identity.applicationName());
            var second = new EveAgentPrincipal(1L, 12L, 13L, 14L, identity.applicationName());
            when(applications.authenticate("synthetic")).thenReturn(identity);
            when(applications.resolveCorporation(identity, "4")).thenReturn(first);
            when(applications.resolveCorporation(identity, "14")).thenReturn(second);
            var entered = new CountDownLatch(2);
            when(queries.catalog(any())).thenAnswer(invocation -> {
                EveAgentPrincipal scope = invocation.getArgument(0);
                entered.countDown();
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                return Map.of("datasets", List.of(), "readOnly", true, "application", scope.applicationName());
            });
            var executor = Executors.newFixedThreadPool(2);
            try (var firstClient = client(base); var secondClient = client(base)) {
                firstClient.initialize();
                secondClient.initialize();
                var firstResult = executor.submit(() -> call(firstClient, "eve_catalog", Map.of("corporationId", "4")));
                var secondResult = executor.submit(() -> call(secondClient, "eve_catalog", Map
                    .of("corporationId", "14")));
                assertEquals("4", ((Map<?, ?>)firstResult.get(10, TimeUnit.SECONDS).structuredContent())
                    .get("corporationId"));
                assertEquals("14", ((Map<?, ?>)secondResult.get(10, TimeUnit.SECONDS).structuredContent())
                    .get("corporationId"));
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    /** 军团发现与查询都必须先写入审计，审计不可用时不返回任何敏感结果。 */
    @Test
    void auditFailureBlocksBothCorporationDiscoveryAndScopedData() throws Exception {
        try (var context = new AnnotationConfigServletWebServerApplicationContext(TestConfiguration.class)) {
            String base = "http://127.0.0.1:" + context.getWebServer().getPort();
            var applications = context.getBean(EveAgentApplicationService.class);
            var queries = context.getBean(EveAgentQueryService.class);
            var identity = new EveAgentPrincipal(1L, null, null, null, "合成应用");
            var scope = new EveAgentPrincipal(1L, 2L, 3L, 4L, "合成应用");
            when(applications.authenticate("synthetic")).thenReturn(identity);
            when(applications.resolveCorporation(identity, "4")).thenReturn(scope);
            when(applications.listAuthorizedCorporations(identity)).thenReturn(List
                .of(new EveAgentCorporationResp(4L, "不应返回的合成军团名", "HIDDEN")));
            when(queries.query(eq(scope), anyMap())).thenReturn(Map.of("items", List.of(Map.of("note", "不应返回的合成备注"))));
            doThrow(new IllegalStateException("合成审计不可用")).when(applications)
                .recordCall(any(), anyString(), anyBoolean(), anyLong(), anyInt());
            try (var client = client(base)) {
                client.initialize();
                var discovery = call(client, "eve_corporations", Map.of());
                var query = call(client, "eve_query", Map.of("dataset", "members", "corporationId", "4"));
                for (var result : List.of(discovery, query)) {
                    assertTrue(result.isError());
                    assertNull(result.structuredContent());
                    assertFalse(result.content().toString().contains("不应返回"));
                    assertFalse(result.content().toString().contains("合成审计"));
                }
            }
        }
    }

    /** 使用官方客户端而非手写 JSON-RPC 模拟正常调用。 */
    private McpSyncClient client(String base) {
        return client(base, "synthetic");
    }

    /** 为不同军团构造独立的服务端应用凭据。 */
    private McpSyncClient client(String base, String token) {
        var transport = HttpClientStreamableHttpTransport.builder(base)
            .endpoint("/mcp")
            .jsonMapper(new JacksonMcpJsonMapper(new ObjectMapper()))
            .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + token))
            .build();
        return McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build();
    }

    /** 构造标准工具请求。 */
    private McpSchema.CallToolResult call(McpSyncClient client, String name, Map<String, Object> arguments) {
        return client.callTool(McpSchema.CallToolRequest.builder(name).arguments(arguments).build());
    }

    /** 只装配协议边界和合成业务替身，不连接真实上游。 */
    @Configuration(proxyBeanMethods = false)
    @Import(EveAgentMcpConfiguration.class)
    static class TestConfiguration {
        @Bean
        UndertowServletWebServerFactory server(WebServerFactoryCustomizer<UndertowServletWebServerFactory> customizer) {
            var factory = new UndertowServletWebServerFactory(0);
            customizer.customize(factory);
            return factory;
        }

        @Bean
        ObjectMapper mapper() {
            return new ObjectMapper();
        }

        @Bean
        EveAgentApplicationService applications() {
            return mock(EveAgentApplicationService.class);
        }

        @Bean
        EveAgentQueryService queries() {
            return mock(EveAgentQueryService.class);
        }

        @Bean
        EveAgentMcpTools tools(EveAgentQueryService queries,
                               EveAgentApplicationService applications,
                               ObjectMapper mapper) {
            return new EveAgentMcpTools(queries, applications, mapper);
        }
    }
}
