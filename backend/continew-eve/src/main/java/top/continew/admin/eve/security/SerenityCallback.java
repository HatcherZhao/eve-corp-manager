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

package top.continew.admin.eve.security;

/**
 * 已通过严格校验的国服 OAuth 回调参数。
 *
 * @param code  授权码，错误回调时为空
 * @param state OAuth state
 * @param error 国服错误代码，成功回调时为空
 * @author zhaoyuqing
 */
public record SerenityCallback(String code, String state, String error) {

    /** 判断是否为错误回调。 */
    public boolean hasError() {
        return error != null;
    }

    /** 防止日志输出 code 与 state。 */
    @Override
    public String toString() {
        return "SerenityCallback[code=<redacted>, state=<redacted>, hasError=" + hasError() + "]";
    }
}
