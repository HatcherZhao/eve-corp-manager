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

import java.util.Set;

/**
 * 国服 Swagger 角色接口声明的完整军团角色目录。
 *
 * @author zhaoyuqing
 */
public enum EveCorporationRole {
    ACCOUNT_TAKE_1("Account_Take_1", "支取钱包部门 1", CorporationRoleCategory.FINANCE, "可从军团钱包部门 1 支取资金", EveSystemCapability.WALLET_WITHDRAW),
    ACCOUNT_TAKE_2("Account_Take_2", "支取钱包部门 2", CorporationRoleCategory.FINANCE, "可从军团钱包部门 2 支取资金", EveSystemCapability.WALLET_WITHDRAW),
    ACCOUNT_TAKE_3("Account_Take_3", "支取钱包部门 3", CorporationRoleCategory.FINANCE, "可从军团钱包部门 3 支取资金", EveSystemCapability.WALLET_WITHDRAW),
    ACCOUNT_TAKE_4("Account_Take_4", "支取钱包部门 4", CorporationRoleCategory.FINANCE, "可从军团钱包部门 4 支取资金", EveSystemCapability.WALLET_WITHDRAW),
    ACCOUNT_TAKE_5("Account_Take_5", "支取钱包部门 5", CorporationRoleCategory.FINANCE, "可从军团钱包部门 5 支取资金", EveSystemCapability.WALLET_WITHDRAW),
    ACCOUNT_TAKE_6("Account_Take_6", "支取钱包部门 6", CorporationRoleCategory.FINANCE, "可从军团钱包部门 6 支取资金", EveSystemCapability.WALLET_WITHDRAW),
    ACCOUNT_TAKE_7("Account_Take_7", "支取钱包部门 7", CorporationRoleCategory.FINANCE, "可从军团钱包部门 7 支取资金", EveSystemCapability.WALLET_WITHDRAW),
    ACCOUNTANT("Accountant", "会计", CorporationRoleCategory.FINANCE, "查看军团财务、钱包与月矿观察者账本", EveSystemCapability.FINANCE_READ, EveSystemCapability.MINING_LEDGER_READ),
    AUDITOR("Auditor", "审计员", CorporationRoleCategory.SECURITY, "审计军团成员与资产使用情况", EveSystemCapability.SECURITY_AUDIT),
    COMMUNICATIONS_OFFICER("Communications_Officer", "通信官", CorporationRoleCategory.SECURITY, "管理军团通信频道与公告", EveSystemCapability.COMMUNICATION_MANAGEMENT),
    CONFIG_EQUIPMENT("Config_Equipment", "设备配置员", CorporationRoleCategory.STRUCTURE, "配置军团部署的设备", EveSystemCapability.STRUCTURE_MANAGEMENT),
    CONFIG_STARBASE_EQUIPMENT("Config_Starbase_Equipment", "恒星基地（POS）设备配置员", CorporationRoleCategory.STRUCTURE, "配置恒星基地（POS）及相关设施设备", EveSystemCapability.STRUCTURE_MANAGEMENT),
    CONTAINER_TAKE_1("Container_Take_1", "提取容器部门 1", CorporationRoleCategory.HANGAR, "可从军团容器部门 1 提取物品", EveSystemCapability.CONTAINER_TAKE),
    CONTAINER_TAKE_2("Container_Take_2", "提取容器部门 2", CorporationRoleCategory.HANGAR, "可从军团容器部门 2 提取物品", EveSystemCapability.CONTAINER_TAKE),
    CONTAINER_TAKE_3("Container_Take_3", "提取容器部门 3", CorporationRoleCategory.HANGAR, "可从军团容器部门 3 提取物品", EveSystemCapability.CONTAINER_TAKE),
    CONTAINER_TAKE_4("Container_Take_4", "提取容器部门 4", CorporationRoleCategory.HANGAR, "可从军团容器部门 4 提取物品", EveSystemCapability.CONTAINER_TAKE),
    CONTAINER_TAKE_5("Container_Take_5", "提取容器部门 5", CorporationRoleCategory.HANGAR, "可从军团容器部门 5 提取物品", EveSystemCapability.CONTAINER_TAKE),
    CONTAINER_TAKE_6("Container_Take_6", "提取容器部门 6", CorporationRoleCategory.HANGAR, "可从军团容器部门 6 提取物品", EveSystemCapability.CONTAINER_TAKE),
    CONTAINER_TAKE_7("Container_Take_7", "提取容器部门 7", CorporationRoleCategory.HANGAR, "可从军团容器部门 7 提取物品", EveSystemCapability.CONTAINER_TAKE),
    CONTRACT_MANAGER("Contract_Manager", "合同管理员", CorporationRoleCategory.COMMERCE, "代表军团创建和管理合同", EveSystemCapability.CONTRACT_MANAGEMENT),
    DIPLOMAT("Diplomat", "外交官", CorporationRoleCategory.PERSONNEL, "管理军团外交关系", EveSystemCapability.DIPLOMACY_MANAGEMENT),
    DIRECTOR("Director", "总监", CorporationRoleCategory.GOVERNANCE, "拥有除 CEO 专属操作外的军团管理权限", EveSystemCapability.CORPORATION_ADMINISTRATION, EveSystemCapability.CORPORATION_ASSET_READ, EveSystemCapability.CORPORATION_MEMBER_TRACKING),
    FACTORY_MANAGER("Factory_Manager", "工厂管理员", CorporationRoleCategory.INDUSTRY, "管理军团工业作业与工厂设施", EveSystemCapability.INDUSTRY_MANAGEMENT),
    FITTING_MANAGER("Fitting_Manager", "装配管理员", CorporationRoleCategory.INDUSTRY, "管理军团装配方案", EveSystemCapability.FITTING_MANAGEMENT),
    HANGAR_QUERY_1("Hangar_Query_1", "查询机库部门 1", CorporationRoleCategory.HANGAR, "可查看军团机库部门 1", EveSystemCapability.HANGAR_QUERY),
    HANGAR_QUERY_2("Hangar_Query_2", "查询机库部门 2", CorporationRoleCategory.HANGAR, "可查看军团机库部门 2", EveSystemCapability.HANGAR_QUERY),
    HANGAR_QUERY_3("Hangar_Query_3", "查询机库部门 3", CorporationRoleCategory.HANGAR, "可查看军团机库部门 3", EveSystemCapability.HANGAR_QUERY),
    HANGAR_QUERY_4("Hangar_Query_4", "查询机库部门 4", CorporationRoleCategory.HANGAR, "可查看军团机库部门 4", EveSystemCapability.HANGAR_QUERY),
    HANGAR_QUERY_5("Hangar_Query_5", "查询机库部门 5", CorporationRoleCategory.HANGAR, "可查看军团机库部门 5", EveSystemCapability.HANGAR_QUERY),
    HANGAR_QUERY_6("Hangar_Query_6", "查询机库部门 6", CorporationRoleCategory.HANGAR, "可查看军团机库部门 6", EveSystemCapability.HANGAR_QUERY),
    HANGAR_QUERY_7("Hangar_Query_7", "查询机库部门 7", CorporationRoleCategory.HANGAR, "可查看军团机库部门 7", EveSystemCapability.HANGAR_QUERY),
    HANGAR_TAKE_1("Hangar_Take_1", "提取机库部门 1", CorporationRoleCategory.HANGAR, "可从军团机库部门 1 提取物品", EveSystemCapability.HANGAR_TAKE),
    HANGAR_TAKE_2("Hangar_Take_2", "提取机库部门 2", CorporationRoleCategory.HANGAR, "可从军团机库部门 2 提取物品", EveSystemCapability.HANGAR_TAKE),
    HANGAR_TAKE_3("Hangar_Take_3", "提取机库部门 3", CorporationRoleCategory.HANGAR, "可从军团机库部门 3 提取物品", EveSystemCapability.HANGAR_TAKE),
    HANGAR_TAKE_4("Hangar_Take_4", "提取机库部门 4", CorporationRoleCategory.HANGAR, "可从军团机库部门 4 提取物品", EveSystemCapability.HANGAR_TAKE),
    HANGAR_TAKE_5("Hangar_Take_5", "提取机库部门 5", CorporationRoleCategory.HANGAR, "可从军团机库部门 5 提取物品", EveSystemCapability.HANGAR_TAKE),
    HANGAR_TAKE_6("Hangar_Take_6", "提取机库部门 6", CorporationRoleCategory.HANGAR, "可从军团机库部门 6 提取物品", EveSystemCapability.HANGAR_TAKE),
    HANGAR_TAKE_7("Hangar_Take_7", "提取机库部门 7", CorporationRoleCategory.HANGAR, "可从军团机库部门 7 提取物品", EveSystemCapability.HANGAR_TAKE),
    JUNIOR_ACCOUNTANT("Junior_Accountant", "初级会计", CorporationRoleCategory.FINANCE, "查看受限的军团财务信息", EveSystemCapability.FINANCE_READ),
    PERSONNEL_MANAGER("Personnel_Manager", "人事经理", CorporationRoleCategory.PERSONNEL, "管理军团成员与申请", EveSystemCapability.PERSONNEL_MANAGEMENT),
    RENT_FACTORY_FACILITY("Rent_Factory_Facility", "租用工厂设施", CorporationRoleCategory.INDUSTRY, "代表军团租用工厂设施", EveSystemCapability.INDUSTRY_MANAGEMENT),
    RENT_OFFICE("Rent_Office", "租用办公室", CorporationRoleCategory.STRUCTURE, "代表军团租用办公室", EveSystemCapability.STRUCTURE_MANAGEMENT),
    RENT_RESEARCH_FACILITY("Rent_Research_Facility", "租用研究设施", CorporationRoleCategory.INDUSTRY, "代表军团租用研究设施", EveSystemCapability.INDUSTRY_MANAGEMENT),
    SECURITY_OFFICER("Security_Officer", "安全官", CorporationRoleCategory.SECURITY, "管理军团安全与成员访问", EveSystemCapability.SECURITY_AUDIT),
    SKILL_PLAN_MANAGER("Skill_Plan_Manager", "技能规划管理员", CorporationRoleCategory.SKILL, "管理军团技能规划", EveSystemCapability.SKILL_PLAN_MANAGEMENT),
    STARBASE_DEFENSE_OPERATOR("Starbase_Defense_Operator", "恒星基地（POS）防御操作员", CorporationRoleCategory.STRUCTURE, "操作恒星基地（POS）防御设施", EveSystemCapability.STRUCTURE_MANAGEMENT),
    STARBASE_FUEL_TECHNICIAN("Starbase_Fuel_Technician", "恒星基地（POS）燃料技师", CorporationRoleCategory.STRUCTURE, "维护恒星基地（POS）燃料", EveSystemCapability.STRUCTURE_MANAGEMENT),
    STATION_MANAGER("Station_Manager", "空间站管理员", CorporationRoleCategory.STRUCTURE, "管理军团建筑并读取月矿提取情报", EveSystemCapability.STRUCTURE_MANAGEMENT, EveSystemCapability.MINING_EXTRACTION_READ),
    TRADER("Trader", "交易员", CorporationRoleCategory.COMMERCE, "代表军团进行市场交易", EveSystemCapability.TRADE_MANAGEMENT);

    private final String code;
    private final String displayName;
    private final CorporationRoleCategory category;
    private final String description;
    private final Set<EveSystemCapability> capabilities;

    EveCorporationRole(String code,
                       String displayName,
                       CorporationRoleCategory category,
                       String description,
                       EveSystemCapability... capabilities) {
        this.code = code;
        this.displayName = displayName;
        this.category = category;
        this.description = description;
        this.capabilities = Set.of(capabilities);
    }

    /** 获取国服接口角色代码。 */
    public String getCode() {
        return code;
    }

    /** 获取中文角色名称。 */
    public String getDisplayName() {
        return displayName;
    }

    /** 获取角色分类。 */
    public CorporationRoleCategory getCategory() {
        return category;
    }

    /** 获取角色说明。 */
    public String getDescription() {
        return description;
    }

    /** 获取候选本站能力。 */
    public Set<EveSystemCapability> getCapabilities() {
        return capabilities;
    }
}
