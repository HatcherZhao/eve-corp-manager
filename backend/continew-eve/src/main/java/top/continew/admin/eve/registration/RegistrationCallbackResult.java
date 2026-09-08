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

package top.continew.admin.eve.registration;

/**
 * 国服身份验证完成后的非敏感注册引导信息。
 *
 * @param credential      一次性注册凭证
 * @param characterName   游戏角色名
 * @param corporationName 军团名称
 * @param ceo             是否为 CEO
 * @param director        是否为总监
 * @author zhaoyuqing
 */
public record RegistrationCallbackResult(String credential, String characterName, String corporationName, boolean ceo,
                                         boolean director) {
}
