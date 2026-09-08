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
 * 游戏角色可关联的本站能力元数据。
 *
 * <p>该枚举仅表达候选能力，最终访问仍需同时通过本站权限、租户、OAuth Scope 与游戏角色校验。</p>
 *
 * @author zhaoyuqing
 */
public enum EveSystemCapability {
    /** 军团管理。 */
    CORPORATION_ADMINISTRATION,
    /** 军团资产读取。 */
    CORPORATION_ASSET_READ,
    /** 军团成员追踪。 */
    CORPORATION_MEMBER_TRACKING,
    /** 财务账本读取。 */
    FINANCE_READ,
    /** 钱包资金支取。 */
    WALLET_WITHDRAW,
    /** 机库内容查询。 */
    HANGAR_QUERY,
    /** 机库物品提取。 */
    HANGAR_TAKE,
    /** 容器物品提取。 */
    CONTAINER_TAKE,
    /** 工业设施管理。 */
    INDUSTRY_MANAGEMENT,
    /** 建筑与空间站管理。 */
    STRUCTURE_MANAGEMENT,
    /** 月矿提取计划读取。 */
    MINING_EXTRACTION_READ,
    /** 月矿观察者账本读取。 */
    MINING_LEDGER_READ,
    /** 装配管理。 */
    FITTING_MANAGEMENT,
    /** 合同管理。 */
    CONTRACT_MANAGEMENT,
    /** 市场交易管理。 */
    TRADE_MANAGEMENT,
    /** 人员管理。 */
    PERSONNEL_MANAGEMENT,
    /** 外交管理。 */
    DIPLOMACY_MANAGEMENT,
    /** 通信管理。 */
    COMMUNICATION_MANAGEMENT,
    /** 安全审计。 */
    SECURITY_AUDIT,
    /** 技能规划管理。 */
    SKILL_PLAN_MANAGEMENT
}
