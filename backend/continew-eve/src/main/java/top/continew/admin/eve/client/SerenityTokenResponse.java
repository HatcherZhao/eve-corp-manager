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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 国服 OAuth Token 响应，字符串表示始终隐藏所有令牌。
 *
 * @param accessToken  访问令牌
 * @param refreshToken 刷新令牌
 * @param tokenType    令牌类型
 * @param expiresIn    有效秒数
 * @param scope        返回的 Scope 文本
 * @author zhaoyuqing
 */
public record SerenityTokenResponse(@JsonProperty("access_token") String accessToken,
                                    @JsonProperty("refresh_token") String refreshToken,
                                    @JsonProperty("token_type") String tokenType,
                                    @JsonProperty("expires_in") long expiresIn, String scope) {

    /** 防止日志输出任何令牌或 Scope。 */
    @Override
    public String toString() {
        return "SerenityTokenResponse[accessToken=<redacted>, refreshToken=<redacted>, tokenType=<redacted>]";
    }
}
