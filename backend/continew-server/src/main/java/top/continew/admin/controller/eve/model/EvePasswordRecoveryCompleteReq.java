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
 * 使用 EVE 身份验证凭证完成本站密码重置的请求。
 *
 * @param credential      一次性密码重置凭证
 * @param password        RSA 公钥加密的新密码
 * @param confirmPassword RSA 公钥加密的确认密码
 * @author zhaoyuqing
 */
@Schema(description = "使用 EVE 身份验证凭证完成本站密码重置的请求")
public record EvePasswordRecoveryCompleteReq(@NotBlank(message = "密码重置凭证不能为空") String credential,
                                             @NotBlank(message = "新密码不能为空") String password,
                                             @NotBlank(message = "确认密码不能为空") String confirmPassword) {
    /** 防止日志输出凭证和密码密文。 */
    @Override
    public String toString() {
        return "EvePasswordRecoveryCompleteReq[secrets=<redacted>]";
    }
}
