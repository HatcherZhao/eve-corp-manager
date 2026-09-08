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

package top.continew.admin.eve.model;

import top.continew.admin.eve.model.enums.EveCapability;
import top.continew.admin.eve.model.enums.EveCapabilityStatus;

/**
 * EVE 模块能力判定结果，不暴露令牌或其他授权秘密。
 *
 * @author zhaoyuqing
 */
public record EveCapabilityResult(String key, String title, String description, EveCapabilityStatus status,
                                  String requiredScope, String requiredGameRole) {

    /** 由能力定义和稳定状态构造响应。 */
    public static EveCapabilityResult of(EveCapability capability, EveCapabilityStatus status) {
        return new EveCapabilityResult(capability.getKey(), capability.getTitle(), capability
            .getDescription(), status, capability.getScope(), capability.getGameRole());
    }
}
