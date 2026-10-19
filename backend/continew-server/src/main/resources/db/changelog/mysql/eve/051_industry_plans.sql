-- liquibase formatted sql

-- changeset eve-corp-manager:051-industry-plans
-- comment 新增可审计的工业生产计划与步骤
CREATE TABLE `eve_industry_plan`
(
    `id` bigint NOT NULL COMMENT '生产计划 ID',
    `tenant_id` bigint NOT NULL COMMENT '租户 ID',
    `name` varchar(120) NOT NULL COMMENT '计划名称',
    `source_opportunity_id` bigint DEFAULT NULL COMMENT '来源机会快照 ID',
    `status` varchar(24) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT、CONFIRMED、PURCHASING、PRODUCING、DELIVERABLE、DELIVERED、CANCELLED、ARCHIVED',
    `activity` varchar(32) DEFAULT NULL COMMENT '工业活动',
    `product_type_id` int DEFAULT NULL COMMENT '产品类型 ID',
    `product_name` varchar(255) DEFAULT NULL COMMENT '产品名称',
    `runs` int NOT NULL DEFAULT 0 COMMENT '计划流程数',
    `target_quantity` bigint NOT NULL DEFAULT 0 COMMENT '目标产量',
    `config_id` bigint DEFAULT NULL COMMENT '生产配置 ID',
    `facility_id` bigint DEFAULT NULL COMMENT '生产设施 ID',
    `blueprint_id` bigint DEFAULT NULL COMMENT '蓝图活动 ID',
    `static_version_id` bigint DEFAULT NULL COMMENT '静态工业数据版本 ID',
    `config_version` int DEFAULT NULL COMMENT '生产配置版本',
    `blueprint_override_version` int DEFAULT NULL COMMENT '蓝图研究覆盖版本',
    `market_snapshot_id` bigint DEFAULT NULL COMMENT '市场输入快照 ID',
    `system_snapshot_id` bigint DEFAULT NULL COMMENT '星系事实快照 ID',
    `rule_version` varchar(32) DEFAULT NULL COMMENT '规则版本',
    `total_material_cost` decimal(20,2) NOT NULL DEFAULT 0,
    `total_install_cost` decimal(20,2) NOT NULL DEFAULT 0,
    `expected_revenue` decimal(20,2) NOT NULL DEFAULT 0,
    `expected_profit` decimal(20,2) NOT NULL DEFAULT 0,
    `expected_time_seconds` decimal(20,6) NOT NULL DEFAULT 0,
    `risk_tags_json` mediumtext DEFAULT NULL COMMENT '风险标签 JSON',
    `assumptions_json` mediumtext DEFAULT NULL COMMENT '创建时假设快照 JSON',
    `input_snapshot_json` mediumtext NOT NULL COMMENT '计划创建时配置、设施、蓝图和来源版本快照',
    `confirmed_at` datetime DEFAULT NULL,
    `archived_at` datetime DEFAULT NULL,
    `create_user` bigint DEFAULT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_user` bigint DEFAULT NULL,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_eve_industry_plan_list` (`tenant_id`, `status`, `update_time`, `deleted`),
    KEY `idx_eve_industry_plan_opportunity` (`tenant_id`, `source_opportunity_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工业生产计划';

CREATE TABLE `eve_industry_plan_step`
(
    `id` bigint NOT NULL COMMENT '计划步骤 ID',
    `tenant_id` bigint NOT NULL COMMENT '租户 ID',
    `plan_id` bigint NOT NULL COMMENT '计划 ID',
    `step_key` varchar(64) NOT NULL COMMENT '步骤键',
    `step_type` varchar(24) NOT NULL COMMENT 'PURCHASE、PRODUCTION、SALES、RESEARCH',
    `sequence_no` int NOT NULL DEFAULT 0 COMMENT '展示顺序',
    `depends_on_json` mediumtext DEFAULT NULL COMMENT '前置步骤键 JSON 数组',
    `status` varchar(24) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING、IN_PROGRESS、DONE、SKIPPED、CANCELLED',
    `title` varchar(255) NOT NULL COMMENT '步骤标题',
    `type_id` int DEFAULT NULL COMMENT '关联物品类型 ID',
    `quantity` bigint NOT NULL DEFAULT 0 COMMENT '步骤数量',
    `unit_cost` decimal(20,6) DEFAULT NULL COMMENT '单位成本',
    `total_cost` decimal(20,2) NOT NULL DEFAULT 0 COMMENT '步骤成本',
    `duration_seconds` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '步骤时长',
    `payload_json` mediumtext DEFAULT NULL COMMENT '步骤扩展数据快照',
    `started_at` datetime DEFAULT NULL,
    `completed_at` datetime DEFAULT NULL,
    `create_user` bigint DEFAULT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_user` bigint DEFAULT NULL,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_eve_industry_plan_step_key` (`tenant_id`, `plan_id`, `step_key`, `deleted`),
    KEY `idx_eve_industry_plan_step_list` (`tenant_id`, `plan_id`, `sequence_no`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工业生产计划步骤';

UPDATE `sys_menu`
SET `permission` = 'eve:industry:plan:view', `update_user` = 1, `update_time` = NOW()
WHERE `id` = 20105 AND `deleted` = 0;

INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (20109, '查看生产计划', 20105, 3, 'eve:industry:plan:view', 1, 1, 1, NOW()),
    (20110, '维护生产计划', 20105, 3, 'eve:industry:plan:manage', 2, 1, 1, NOW());

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
VALUES (20001, 20109), (20001, 20110);

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin', 'corp_member')
  AND `role`.`deleted` = 0 AND `menu`.`id` = 20109 AND `menu`.`deleted` = 0;

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin')
  AND `role`.`deleted` = 0 AND `menu`.`id` = 20110 AND `menu`.`deleted` = 0;
