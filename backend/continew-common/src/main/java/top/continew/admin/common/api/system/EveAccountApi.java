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

import top.continew.admin.common.model.dto.EveAccountCreateDTO;
import top.continew.admin.common.model.dto.EveLoginSessionDTO;

/**
 * EVE 注册流程所需的本站账号内部 API。
 *
 * @author zhaoyuqing
 */
public interface EveAccountApi {

    /**
     * 在指定租户创建普通军团成员账号。
     *
     * @param request 账号创建信息
     * @return 用户 ID
     */
    Long createMember(EveAccountCreateDTO request);

    /**
     * 为刚激活的本站账号建立 Sa-Token 会话。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param clientId 客户端 ID
     * @return 登录会话
     */
    EveLoginSessionDTO openSession(Long tenantId, Long userId, String clientId);
}
