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

package top.continew.admin.common.api.system;

import top.continew.admin.common.model.dto.EveLoginIdentityReviewDTO;

/**
 * 账号密码登录前的 EVE 身份复核内部 API。
 *
 * @author zhaoyuqing
 */
public interface EveLoginIdentityReviewApi {

    /**
     * 在创建本站会话前复核已绑定用户的最新游戏身份。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 复核结果
     */
    EveLoginIdentityReviewDTO review(Long tenantId, Long userId);
}
