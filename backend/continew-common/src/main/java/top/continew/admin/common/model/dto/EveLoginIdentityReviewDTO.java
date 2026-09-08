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
 * 登录前 EVE 身份复核结果。
 *
 * @param status 复核状态
 * @author zhaoyuqing
 */
public record EveLoginIdentityReviewDTO(Status status) {

    /** 登录前身份复核状态。 */
    public enum Status {
        /** 用户未绑定 EVE 角色，不介入普通账号登录。 */
        NOT_BOUND,
        /** 已完成上游复核，可以按最新权限创建会话。 */
        VERIFIED,
        /** 用户已离开或更换当前租户对应军团。 */
        MEMBERSHIP_INVALID,
        /** 授权已永久失效，需要重新授权。 */
        REAUTHORIZATION_REQUIRED,
        /** 上游暂时不可用，按保守策略拒绝本次登录。 */
        UPSTREAM_UNAVAILABLE
    }
}
