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

/**
 * EVE 军团模块及其最小访问契约。
 *
 * <p>Scope 与游戏角色来自仓库内国服 Swagger 契约矩阵。</p>
 *
 * @author zhaoyuqing
 */
@Getter
@RequiredArgsConstructor
public enum EveCapability {
    ASSETS("assets", "军团资产", "资产、机库分区与容器层级", "eve:assets:view", "esi-assets.read_corporation_assets.v1", "Director"),
    STRUCTURES("structures", "建筑设施", "建筑状态、服务与燃料到期", "eve:structures:view", "esi-corporations.read_structures.v1", "Station_Manager"),
    EXTRACTIONS("extractions", "月矿计划", "矿块拉取、到达与自然碎裂时间", "eve:extractions:view", "esi-industry.read_corporation_mining.v1", "Station_Manager"),
    MINING("mining", "采矿账本", "观察者记录的矿种、数量与角色", "eve:mining:view", "esi-industry.read_corporation_mining.v1", "Accountant"),
    MEMBERS("members", "军团人员", "成员名册；位置、舰船与上下线追踪按单独权限和总监数据源控制", "eve:members:view", "esi-corporations.read_corporation_membership.v1", null);

    private final String key;
    private final String title;
    private final String description;
    private final String sitePermission;
    private final String scope;
    private final String gameRole;
}
