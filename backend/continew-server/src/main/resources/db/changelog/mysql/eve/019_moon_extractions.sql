-- liquibase formatted sql

-- changeset eve-corp-manager:019-moon-extractions
-- comment 新增月矿国服当前快照、站内排产与协同任务；游戏时间线和本站安排严格分表存储
UPDATE `sys_menu`
SET `title` = '月矿计划', `parent_id` = 20000, `type` = 2, `path` = '/eve/extractions',
    `name` = 'EveExtractions', `component` = 'eve/extractions/index', `icon` = 'calendar',
    `is_external` = b'0', `is_cache` = b'0', `is_hidden` = b'0', `permission` = NULL, `sort` = 6
WHERE `id` = 20022 AND `permission` = 'eve:extractions:view' AND `deleted` = 0;

INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(20063, '同步月矿计划', 20022, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:extractions:sync', 1, 1, 1, NOW()),
(20064, '管理月矿排产', 20022, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:extractions:manage', 2, 1, 1, NOW());

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
VALUES (20001, 20063), (20001, 20064);

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin')
  AND `role`.`deleted` = 0 AND `menu`.`id` IN (20063, 20064) AND `menu`.`deleted` = 0;

CREATE TABLE `eve_moon_extraction` (
    `id` bigint AUTO_INCREMENT COMMENT 'ID',
    `tenant_id` bigint NOT NULL COMMENT '租户ID',
    `corporation_ref_id` bigint NOT NULL COMMENT 'EVE军团绑定记录ID',
    `structure_id` bigint NOT NULL COMMENT '游戏炼化厂建筑ID',
    `structure_name` varchar(255) DEFAULT NULL COMMENT '炼化厂名称快照',
    `structure_type_name` varchar(255) DEFAULT NULL COMMENT '炼化厂类型名称快照',
    `moon_id` bigint NOT NULL COMMENT '游戏月球ID',
    `moon_name` varchar(255) DEFAULT NULL COMMENT '月球名称快照',
    `solar_system_id` bigint DEFAULT NULL COMMENT '所在星系ID',
    `solar_system_name` varchar(255) DEFAULT NULL COMMENT '所在星系名称快照',
    `extraction_start_at` datetime NOT NULL COMMENT '国服提取开始时间',
    `chunk_arrival_at` datetime NOT NULL COMMENT '国服矿块到达时间',
    `natural_decay_at` datetime NOT NULL COMMENT '国服矿块自然碎裂时间',
    `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '快照状态：ACTIVE或MISSING',
    `last_seen_at` datetime NOT NULL COMMENT '最近完整同步时间',
    `source_expires_at` datetime DEFAULT NULL COMMENT '上游缓存过期时间',
    `create_user` bigint NOT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `deleted` bigint NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_eve_moon_extraction` (`tenant_id`, `corporation_ref_id`, `structure_id`, `extraction_start_at`, `deleted`),
    INDEX `idx_eve_moon_extraction_page` (`tenant_id`, `corporation_ref_id`, `status`, `chunk_arrival_at`),
    INDEX `idx_eve_moon_extraction_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EVE军团月矿当前完整快照';

CREATE TABLE `eve_moon_operation` (
    `id` bigint AUTO_INCREMENT COMMENT 'ID',
    `tenant_id` bigint NOT NULL COMMENT '租户ID',
    `extraction_ref_id` bigint NOT NULL COMMENT '月矿快照记录ID',
    `responsible_character_id` bigint DEFAULT NULL COMMENT '负责人游戏角色ID',
    `responsible_character_name` varchar(100) DEFAULT NULL COMMENT '负责人角色名称快照',
    `planned_at` datetime DEFAULT NULL COMMENT '站内计划执行时间',
    `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '站内排产状态',
    `note` varchar(1000) DEFAULT NULL COMMENT '排产说明',
    `create_user` bigint NOT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `deleted` bigint NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_eve_moon_operation` (`tenant_id`, `extraction_ref_id`, `deleted`),
    INDEX `idx_eve_moon_operation_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EVE月矿本站排产';

CREATE TABLE `eve_moon_operation_task` (
    `id` bigint AUTO_INCREMENT COMMENT 'ID',
    `tenant_id` bigint NOT NULL COMMENT '租户ID',
    `operation_ref_id` bigint NOT NULL COMMENT '月矿排产记录ID',
    `title` varchar(100) NOT NULL COMMENT '协同任务标题',
    `assigned_character_id` bigint DEFAULT NULL COMMENT '指派游戏角色ID',
    `assigned_character_name` varchar(100) DEFAULT NULL COMMENT '指派角色名称快照',
    `scheduled_at` datetime DEFAULT NULL COMMENT '计划执行时间',
    `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '协同任务状态',
    `sort` int NOT NULL DEFAULT 0 COMMENT '同一排产下展示排序值',
    `note` varchar(500) DEFAULT NULL COMMENT '任务说明',
    `create_user` bigint NOT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `deleted` bigint NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_eve_moon_operation_task_page` (`tenant_id`, `operation_ref_id`, `sort`, `deleted`),
    INDEX `idx_eve_moon_operation_task_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EVE月矿本站协同任务';
