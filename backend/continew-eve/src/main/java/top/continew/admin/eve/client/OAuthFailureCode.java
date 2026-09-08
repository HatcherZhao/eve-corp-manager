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

/**
 * 可安全持久化的 OAuth 失败分类，不包含上游响应正文。
 *
 * @author zhaoyuqing
 */
public enum OAuthFailureCode {
    /** 授权码或刷新令牌失效。 */
    INVALID_GRANT,
    /** 上游确认授权已撤销或访问令牌无效。 */
    UNAUTHORIZED,
    /** 上游拒绝当前授权访问目标资源。 */
    FORBIDDEN,
    /** 上游返回不可恢复的客户端错误。 */
    PERMANENT,
    /** 上游或网络临时失败。 */
    TRANSIENT,
    /** Token 响应缺少必要字段。 */
    INVALID_TOKEN_RESPONSE,
    /** 本地输入校验失败。 */
    VALIDATION_FAILED
}
