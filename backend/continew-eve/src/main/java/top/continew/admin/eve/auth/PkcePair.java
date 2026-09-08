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

package top.continew.admin.eve.auth;

/**
 * PKCE S256 参数对。
 *
 * @param verifier  仅保存在服务端 OAuth 事务中的校验值
 * @param challenge 发送到国服授权端点的摘要
 * @author zhaoyuqing
 */
public record PkcePair(String verifier, String challenge) {
    /** 防止日志输出 verifier。 */
    @Override
    public String toString() {
        return "PkcePair[verifier=<redacted>, challenge=<redacted>]";
    }
}
