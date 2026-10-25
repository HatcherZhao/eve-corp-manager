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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import top.continew.admin.eve.agent.EveAgentPrincipal;
import top.continew.admin.eve.agent.auth.EveAgentApplicationService;
import top.continew.starter.core.exception.BusinessException;

/** MCP 独立认证边界，不使用网页登录身份或客户端提供的租户头。 */
public class EveAgentMcpFilter extends OncePerRequestFilter {

    public static final String PRINCIPAL_ATTRIBUTE = EveAgentMcpFilter.class.getName() + ".principal";
    private static final int MAX_BODY_BYTES = 65536;
    private static final int REQUESTS_PER_MINUTE = 60;
    private static final int MAX_APPLICATION_WINDOWS = 4096;

    private final EveAgentApplicationService applications;
    private final EveAgentMcpProperties properties;
    private final Semaphore capacity = new Semaphore(8);
    private final Map<Long, Window> windows = new LinkedHashMap<>();

    /** 注入独立应用认证服务及可信 Origin 列表。 */
    public EveAgentMcpFilter(EveAgentApplicationService applications, EveAgentMcpProperties properties) {
        this.applications = applications;
        this.properties = properties;
    }

    /** 每次请求重新验证密钥、Origin、体积与配额，撤销不依赖协议会话结束。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        String origin = request.getHeader("Origin");
        if (origin != null && !properties.getAllowedOrigins().contains(origin)) {
            reject(response, 403, "请求来源未获允许");
            return;
        }
        var authorization = Collections.list(request.getHeaders("Authorization"));
        if (authorization.size() != 1 || !authorization.get(0).regionMatches(true, 0, "Bearer ", 0, 7) || request
            .getQueryString() != null) {
            unauthorized(response);
            return;
        }
        EveAgentPrincipal principal;
        try {
            principal = applications.authenticate(authorization.get(0).substring(7));
        } catch (BusinessException e) {
            unauthorized(response);
            return;
        }
        if (principal == null) {
            unauthorized(response);
            return;
        }
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            reject(response, 405, "此接入点只接受 POST 请求");
            return;
        }
        String contentType = request.getContentType();
        String encoding = request.getHeader("Content-Encoding");
        if (contentType == null || !contentType.split(";", 2)[0].trim()
            .equalsIgnoreCase("application/json") || encoding != null && !encoding.equalsIgnoreCase("identity")) {
            reject(response, 415, "请求须使用未压缩的 application/json");
            return;
        }
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            reject(response, 413, "请求内容超过 64 KiB");
            return;
        }
        Window window = acquire(principal.applicationId());
        if (window == null) {
            response.setHeader("Retry-After", "60");
            reject(response, 429, "调用过于频繁或并发已满，请稍后重试");
            return;
        }
        try {
            byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                reject(response, 413, "请求内容超过 64 KiB");
                return;
            }
            request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
            chain.doFilter(new BufferedRequest(request, body), response);
        } finally {
            synchronized (windows) {
                window.inFlight--;
            }
            capacity.release();
        }
    }

    /** 固定分钟窗口限制单应用调用与并发，表容量有上限且不移除仍在执行的配额。 */
    private Window acquire(Long applicationId) {
        synchronized (windows) {
            long minute = System.currentTimeMillis() / 60000;
            windows.entrySet().removeIf(entry -> entry.getValue().minute < minute && entry.getValue().inFlight == 0);
            Window window = windows.get(applicationId);
            if (window == null) {
                if (windows.size() >= MAX_APPLICATION_WINDOWS)
                    return null;
                window = new Window(minute);
                windows.put(applicationId, window);
            }
            if (window.minute < minute) {
                window.minute = minute;
                window.count = 0;
            }
            if (window.count >= REQUESTS_PER_MINUTE || window.inFlight >= 2 || !capacity.tryAcquire())
                return null;
            window.count++;
            window.inFlight++;
            return window;
        }
    }

    /** 认证失败统一响应，避免泄露应用是否存在或到期原因。 */
    private static void unauthorized(HttpServletResponse response) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer realm=\"eve-agent\"");
        reject(response, 401, "应用密钥无效、已到期或已撤销");
    }

    /** 协议处理前的错误使用 HTTP 状态，不进入站内响应包装。 */
    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    /** 保存单个已认证应用的分钟配额。 */
    private static final class Window {
        private long minute;
        private int count;
        private int inFlight;

        private Window(long minute) {
            this.minute = minute;
        }
    }

    /** SDK 从已限额的内存流读取请求，防止无 Content-Length 的请求绕过体积限制。 */
    private static final class BufferedRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private BufferedRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
