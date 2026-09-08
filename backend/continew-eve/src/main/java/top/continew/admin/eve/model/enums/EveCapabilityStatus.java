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

/**
 * EVE 模块能力的稳定判定状态。
 *
 * @author zhaoyuqing
 */
public enum EveCapabilityStatus {
    /** 所有访问条件均满足。 */
    AVAILABLE,
    /** 缺少站内功能权限。 */
    MISSING_SITE_PERMISSION,
    /** 单个有效授权缺少所需 Scope。 */
    MISSING_SCOPE,
    /** 同一授权对应角色缺少所需游戏角色。 */
    MISSING_GAME_ROLE,
    /** 授权已失效或访问令牌已过期。 */
    AUTH_EXPIRED,
    /** 缺少角色、授权或角色权限快照。 */
    NO_DATA_SOURCE
}
