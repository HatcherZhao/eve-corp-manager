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
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * EVE 身份验证后的本站账号激活请求。
 *
 * @param credential      一次性注册凭证
 * @param username        本站用户名
 * @param password        RSA 公钥加密密码
 * @param confirmPassword RSA 公钥加密确认密码
 * @param clientId        本站客户端 ID
 * @author zhaoyuqing
 */
@Schema(description = "EVE 身份验证后的本站账号激活请求")
public record EveRegistrationCompleteReq(@NotBlank(message = "注册凭证不能为空") String credential,
                                         @NotBlank(message = "邮箱账号不能为空") @Size(max = 64, message = "邮箱账号最长 64 个字符") @Email(message = "请输入有效的邮箱账号") String username,
                                         @NotBlank(message = "密码不能为空") String password,
                                         @NotBlank(message = "确认密码不能为空") String confirmPassword,
                                         @NotBlank(message = "客户端 ID 不能为空") String clientId) {

    /** 防止日志输出注册凭证和密码密文。 */
    @Override
    public String toString() {
        return "EveRegistrationCompleteReq[username=" + username + ", clientId=" + clientId + ", secrets=<redacted>]";
    }
}
