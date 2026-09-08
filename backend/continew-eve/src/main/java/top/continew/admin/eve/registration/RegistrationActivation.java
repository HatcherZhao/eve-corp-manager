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

import java.io.Serializable;

/**
 * 数据库已提交的稳定注册结果，用于失败后的会话重新交付。
 *
 * @author zhaoyuqing
 */
public record RegistrationActivation(Long tenantId, Long userId, boolean tenantCreated) implements Serializable {
}
