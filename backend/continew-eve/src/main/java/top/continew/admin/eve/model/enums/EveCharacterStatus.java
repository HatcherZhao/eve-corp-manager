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
 * EVE 角色绑定状态。
 *
 * @author zhaoyuqing
 */
@Getter
@RequiredArgsConstructor
public enum EveCharacterStatus implements BaseEnum<String> {
    /** 正常绑定。 */
    ACTIVE("ACTIVE", "正常"),
    /** 已离开当前军团。 */
    LEFT_CORPORATION("LEFT_CORPORATION", "已离团"),
    /** 等待重新验证。 */
    PENDING_VERIFICATION("PENDING_VERIFICATION", "待验证"),
    /** 已停用。 */
    DISABLED("DISABLED", "已停用");

    private final String value;
    private final String description;
}
