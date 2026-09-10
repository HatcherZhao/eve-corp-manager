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

package top.continew.admin.eve.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.model.enums.EveCapability;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Collections;

/**
 * 统一定义已确认军团运营功能的国服授权范围。
 *
 * <p>注册、角色绑定、重新授权和缺失检查必须共用本策略，避免后续每上线一个模块再向用户索取一次授权。
 * 吉他市场行情属于公开接口，不应放入 OAuth Scope。</p>
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveAuthorizationScopePolicy {

    /** 资产分部与机库名称读取。 */
    private static final String CORPORATION_DIVISIONS_SCOPE = "esi-corporations.read_divisions.v1";
    /** 成员位置、舰船和登录状态追踪。 */
    private static final String MEMBER_TRACKING_SCOPE = "esi-corporations.track_members.v1";
    /** 资产所在非公开建筑的名称与位置解析。 */
    private static final String UNIVERSE_STRUCTURES_SCOPE = "esi-universe.read_structures.v1";
    /** 游戏内邮件发送。 */
    private static final String MAIL_SEND_SCOPE = "esi-mail.send_mail.v1";
    /** 游戏内邮件读取。 */
    private static final String MAIL_READ_SCOPE = "esi-mail.read_mail.v1";
    /** 游戏内邮件标签、已读状态和删除操作。 */
    private static final String MAIL_ORGANIZE_SCOPE = "esi-mail.organize_mail.v1";

    private final SerenityProperties properties;

    /** 返回身份识别、军团数据、成员追踪和游戏内邮件所需的完整授权包。 */
    public Set<String> plannedScopes() {
        Set<String> scopes = new LinkedHashSet<>(identityScopes());
        for (EveCapability capability : EveCapability.values()) {
            scopes.add(capability.getScope());
        }
        scopes.add(CORPORATION_DIVISIONS_SCOPE);
        scopes.add(MEMBER_TRACKING_SCOPE);
        scopes.add(UNIVERSE_STRUCTURES_SCOPE);
        scopes.add(MAIL_READ_SCOPE);
        scopes.add(MAIL_SEND_SCOPE);
        scopes.add(MAIL_ORGANIZE_SCOPE);
        return Collections.unmodifiableSet(scopes);
    }

    /** 返回仅用于身份校验、注册和密码找回的基础 Scope。 */
    public Set<String> identityScopes() {
        Set<String> scopes = properties.getSso().getRequiredScopes();
        return scopes == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(scopes));
    }

    /**
     * 返回连身份与军团权限复核都无法进行时缺少的基础 Scope。
     *
     * <p>后续新增的业务 Scope 只能限制对应模块，不能被误判为网易撤销授权并清空原有刷新令牌。</p>
     */
    public List<String> missingIdentityScopes(List<String> currentScopes) {
        Set<String> missing = new LinkedHashSet<>(identityScopes());
        missing.removeAll(currentScopes == null ? List.of() : currentScopes);
        return List.copyOf(missing);
    }

    /** 返回当前授权缺少的已确认功能 Scope，保持授权页中稳定的展示顺序。 */
    public List<String> missingPlannedScopes(List<String> currentScopes) {
        Set<String> missing = new LinkedHashSet<>(plannedScopes());
        missing.removeAll(currentScopes == null ? List.of() : currentScopes);
        return List.copyOf(missing);
    }
}
