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

package top.continew.admin.common.model.dto;

/**
 * 已入驻军团的本站成员账号创建信息。
 *
 * @param tenantId          租户 ID
 * @param username          本站用户名
 * @param encryptedPassword RSA 公钥加密密码
 * @param nickname          游戏角色名
 * @author zhaoyuqing
 */
public record EveAccountCreateDTO(Long tenantId, String username, String encryptedPassword, String nickname) {
}
