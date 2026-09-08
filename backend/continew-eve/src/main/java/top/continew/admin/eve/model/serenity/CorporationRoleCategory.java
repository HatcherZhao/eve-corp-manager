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

package top.continew.admin.eve.model.serenity;

/**
 * 游戏军团角色分类。
 *
 * @author zhaoyuqing
 */
public enum CorporationRoleCategory {
    /** 军团治理。 */
    GOVERNANCE("军团治理"),
    /** 财务。 */
    FINANCE("财务"),
    /** 机库与容器。 */
    HANGAR("机库与容器"),
    /** 工业与设施。 */
    INDUSTRY("工业与设施"),
    /** 空间站与建筑。 */
    STRUCTURE("空间站与建筑"),
    /** 人事与外交。 */
    PERSONNEL("人事与外交"),
    /** 市场与合同。 */
    COMMERCE("市场与合同"),
    /** 通信与安全。 */
    SECURITY("通信与安全"),
    /** 技能规划。 */
    SKILL("技能规划"),
    /** 国服新增或尚未识别。 */
    UNKNOWN("未识别");

    private final String displayName;

    CorporationRoleCategory(String displayName) {
        this.displayName = displayName;
    }

    /** 获取中文分类名称。 */
    public String getDisplayName() {
        return displayName;
    }
}
