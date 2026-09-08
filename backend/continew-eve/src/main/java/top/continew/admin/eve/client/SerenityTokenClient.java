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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient.RequestBodySpec;
import top.continew.admin.eve.config.SerenityProperties;

import java.nio.charset.StandardCharsets;

/**
 * 仅访问固定国服端点的 OAuth Token 客户端。
 *
 * @author zhaoyuqing
 */
@Component
public class SerenityTokenClient {

    private final RestClient restClient;
    private final SerenityProperties properties;

    /**
     * 创建 Token 客户端。
     *
     * @param builder    RestClient 构建器
     * @param properties 国服配置
     */
    public SerenityTokenClient(@Qualifier("serenityRestClient") RestClient restClient, SerenityProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /** 使用授权码和 PKCE verifier 换取令牌。 */
    public SerenityTokenResponse exchangeCode(String code, String verifier) {
        requireSecret(code);
        requireSecret(verifier);
        if (verifier.length() < 43 || verifier.length() > 128 || !verifier.matches("[A-Za-z0-9._~-]+")) {
            throw new SerenityTokenClientException(OAuthFailureCode.VALIDATION_FAILED);
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("code_verifier", verifier);
        form.add("redirect_uri", properties.getSso().getCallbackUrl());
        SerenityTokenResponse response = postToken(form);
        if (response.refreshToken() == null || response.refreshToken().isBlank()) {
            throw new SerenityTokenClientException(OAuthFailureCode.INVALID_TOKEN_RESPONSE);
        }
        return response;
    }

    /** 使用刷新令牌换取新令牌。 */
    public SerenityTokenResponse refresh(String refreshToken) {
        requireSecret(refreshToken);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return postToken(form);
    }

    /** 在固定撤销端点撤销指定令牌。 */
    public void revoke(String token, String tokenTypeHint) {
        requireSecret(token);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        if ("access_token".equals(tokenTypeHint) || "refresh_token".equals(tokenTypeHint)) {
            form.add("token_type_hint", tokenTypeHint);
        }
        try {
            RequestBodySpec request = restClient.post().uri(properties.getSso().getRevocationEndpoint());
            applyClientAuthentication(request, form);
            request.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /** 向固定 Token 端点提交表单。 */
    private SerenityTokenResponse postToken(MultiValueMap<String, String> form) {
        try {
            RequestBodySpec request = restClient.post().uri(properties.getSso().getTokenEndpoint());
            applyClientAuthentication(request, form);
            SerenityTokenResponse response = request.contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(SerenityTokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank() || response
                .expiresIn() <= 0 || !"Bearer".equalsIgnoreCase(response.tokenType())) {
                throw new SerenityTokenClientException(OAuthFailureCode.INVALID_TOKEN_RESPONSE);
            }
            return response;
        } catch (SerenityTokenClientException e) {
            throw e;
        } catch (RestClientException e) {
            throw classify(e);
        }
    }

    /**
     * 按客户端类型附加认证信息：机密客户端使用 Basic，Swagger 公共客户端在表单中提交 client_id。
     *
     * @param request HTTP 请求
     * @param form    OAuth 表单
     */
    private void applyClientAuthentication(RequestBodySpec request, MultiValueMap<String, String> form) {
        SerenityProperties.Sso sso = properties.getSso();
        if (sso.hasClientSecret()) {
            request.headers(headers -> headers.setBasicAuth(sso.getClientId(), sso
                .getClientSecret(), StandardCharsets.UTF_8));
            return;
        }
        form.set("client_id", sso.getClientId());
    }

    /** 拒绝空敏感参数且不回显其内容。 */
    private static void requireSecret(String value) {
        if (value == null || value.isBlank()) {
            throw new SerenityTokenClientException(OAuthFailureCode.VALIDATION_FAILED);
        }
    }

    /** 将上游异常归一为不含响应正文的安全分类。 */
    static SerenityTokenClientException classify(RestClientException exception) {
        OAuthFailureCode code = OAuthFailureCode.TRANSIENT;
        if (exception instanceof HttpClientErrorException clientError) {
            if (isRetryableClientStatus(clientError.getStatusCode())) {
                code = OAuthFailureCode.TRANSIENT;
            } else if (clientError.getStatusCode() == HttpStatus.BAD_REQUEST) {
                code = OAuthFailureCode.INVALID_GRANT;
            } else if (clientError.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                code = OAuthFailureCode.UNAUTHORIZED;
            } else if (clientError.getStatusCode() == HttpStatus.FORBIDDEN) {
                code = OAuthFailureCode.FORBIDDEN;
            } else {
                code = OAuthFailureCode.PERMANENT;
            }
        }
        return new SerenityTokenClientException(code);
    }

    /** 将请求超时和限流状态保留为可重试故障，避免误清空有效令牌。 */
    private static boolean isRetryableClientStatus(HttpStatusCode statusCode) {
        int value = statusCode.value();
        return value == 408 || value == 420 || value == 429;
    }
}
