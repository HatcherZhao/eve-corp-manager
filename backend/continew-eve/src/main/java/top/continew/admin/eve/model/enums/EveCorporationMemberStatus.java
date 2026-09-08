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
 * EVE 军团成员关系状态。
 *
 * @author zhaoyuqing
 */
@Getter
@RequiredArgsConstructor
public enum EveCorporationMemberStatus implements BaseEnum<String> {
    /** 等待审批或验证。 */
    PENDING("PENDING", "待处理"),
    /** 当前有效成员。 */
    ACTIVE("ACTIVE", "有效成员"),
    /** 已离开军团。 */
    LEFT("LEFT", "已离团"),
    /** 加入申请被拒绝。 */
    REJECTED("REJECTED", "已拒绝");

    private final String value;
    private final String description;
}
