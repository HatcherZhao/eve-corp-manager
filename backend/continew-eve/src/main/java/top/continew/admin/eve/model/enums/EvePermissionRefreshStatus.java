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
 * 主动游戏权限刷新结果。
 *
 * @author zhaoyuqing
 */
public enum EvePermissionRefreshStatus {
    /** 已从国服更新权限事实。 */
    REFRESHED,
    /** 仍在用户级冷却期，未请求国服。 */
    COOLDOWN,
    /** 缺少必要 Scope 或授权已永久失效。 */
    REAUTHORIZATION_REQUIRED,
    /** 本地授权不可用。 */
    AUTHORIZATION_DISABLED,
    /** 国服或网络临时不可用，授权仍有效并可稍后重试。 */
    UPSTREAM_UNAVAILABLE,
    /** 角色已离开或更换军团，成员身份已停用。 */
    MEMBERSHIP_INVALID
}
