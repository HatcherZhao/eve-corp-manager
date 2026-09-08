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

package top.continew.admin.controller.eve.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * EVE 注册 OAuth 回调导入请求。
 *
 * @param callbackUrl 浏览器返回的完整固定回调 URL
 * @author zhaoyuqing
 */
@Schema(description = "EVE 注册 OAuth 回调导入请求")
public record EveRegistrationCallbackReq(@NotBlank(message = "回调地址不能为空") String callbackUrl) {

    /** 防止日志输出授权码和 state。 */
    @Override
    public String toString() {
        return "EveRegistrationCallbackReq[callbackUrl=<redacted>]";
    }
}
