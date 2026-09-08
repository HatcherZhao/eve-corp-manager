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

package top.continew.admin.eve.auth;

/**
 * 国服 OAuth 事务用途。
 *
 * @author zhaoyuqing
 */
public enum OAuthTransactionPurpose {
    /** 未登录用户注册并认领或加入军团。 */
    REGISTER,
    /** 已登录用户绑定新的游戏角色。 */
    BIND_CHARACTER,
    /** 未登录用户验证已绑定角色后重置本站密码。 */
    RECOVER_PASSWORD,
    /** 为已有授权补充 Scope。 */
    EXPAND_SCOPES
}
