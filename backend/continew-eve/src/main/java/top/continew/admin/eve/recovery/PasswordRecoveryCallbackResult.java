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

package top.continew.admin.eve.recovery;

/**
 * EVE 验证成功后返回的一次性密码重置凭证。
 *
 * @param credential    一次性重置凭证
 * @param characterName 已验证的 EVE 角色名称
 * @author zhaoyuqing
 */
public record PasswordRecoveryCallbackResult(String credential, String characterName) {
    /** 防止日志输出一次性重置凭证。 */
    @Override
    public String toString() {
        return "PasswordRecoveryCallbackResult[credential=<redacted>, characterName=" + characterName + "]";
    }
}
