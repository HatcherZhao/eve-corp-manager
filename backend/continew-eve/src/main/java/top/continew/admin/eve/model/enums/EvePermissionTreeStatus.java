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
 * 当前用户游戏权限树的数据状态。
 *
 * @author zhaoyuqing
 */
public enum EvePermissionTreeStatus {
    /** 权限树包含可用的最新快照和有效授权。 */
    READY,
    /** 当前租户用户未绑定有效主角色。 */
    CHARACTER_NOT_BOUND,
    /** 已绑定角色但尚未取得权限快照。 */
    SNAPSHOT_UNAVAILABLE,
    /** 当前角色没有授权记录。 */
    AUTHORIZATION_UNAVAILABLE,
    /** 当前角色授权已撤销、失效或要求重新授权。 */
    AUTHORIZATION_INACTIVE
}
