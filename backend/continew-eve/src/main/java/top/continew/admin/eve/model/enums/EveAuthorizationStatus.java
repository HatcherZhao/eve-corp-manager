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
 * 国服授权生命周期状态。
 *
 * @author zhaoyuqing
 */
@Getter
@RequiredArgsConstructor
public enum EveAuthorizationStatus implements BaseEnum<String> {
    /** 授权可用。 */
    ACTIVE("ACTIVE", "可用"),
    /** 访问令牌已过期。 */
    EXPIRED("EXPIRED", "已过期"),
    /** 用户或国服已撤销。 */
    REVOKED("REVOKED", "已撤销"),
    /** 连续刷新或验证失败。 */
    FAILED("FAILED", "失败"),
    /** Scope 不足，需要重新授权。 */
    REAUTH_REQUIRED("REAUTH_REQUIRED", "需要重新授权");

    private final String value;
    private final String description;
}
