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

package top.continew.admin.eve.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import top.continew.starter.core.enums.BaseEnum;

/**
 * 授权最近一次验证状态。
 *
 * @author zhaoyuqing
 */
@Getter
@RequiredArgsConstructor
public enum EveAuthorizationVerificationStatus implements BaseEnum<String> {
    /** 尚未验证。 */
    PENDING("PENDING", "待验证"),
    /** 验证有效。 */
    VALID("VALID", "有效"),
    /** 令牌或角色身份无效。 */
    INVALID("INVALID", "无效"),
    /** 验证请求失败。 */
    FAILED("FAILED", "验证失败");

    private final String value;
    private final String description;
}
