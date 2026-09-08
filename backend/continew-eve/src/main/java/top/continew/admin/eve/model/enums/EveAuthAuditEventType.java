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
 * 国服授权审计事件类型。
 *
 * @author zhaoyuqing
 */
@Getter
@RequiredArgsConstructor
public enum EveAuthAuditEventType implements BaseEnum<String> {
    /** 发起授权。 */
    AUTHORIZATION_STARTED("AUTHORIZATION_STARTED", "发起授权"),
    /** 完成授权。 */
    AUTHORIZATION_COMPLETED("AUTHORIZATION_COMPLETED", "完成授权"),
    /** 刷新令牌。 */
    TOKEN_REFRESHED("TOKEN_REFRESHED", "刷新令牌"),
    /** 主动刷新游戏权限。 */
    ROLES_REFRESHED("ROLES_REFRESHED", "刷新游戏权限"),
    /** 重新授权以补充 Scope。 */
    REAUTHORIZED("REAUTHORIZED", "重新授权"),
    /** 撤销授权。 */
    AUTHORIZATION_REVOKED("AUTHORIZATION_REVOKED", "撤销授权"),
    /** 后台验证授权。 */
    AUTHORIZATION_VERIFIED("AUTHORIZATION_VERIFIED", "验证授权");

    private final String value;
    private final String description;
}
