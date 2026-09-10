-- liquibase formatted sql

-- changeset eve-corp-manager:018-corporation-structures
-- comment 新增军团建筑当前快照、菜单及同步权限；状态服务与燃料字段完全以国服响应为准
INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(20060, '军团建筑', 20000, 2, '/eve/structures', 'EveStructures', 'eve/structures/index', 'home', b'0', b'0', b'0', NULL, 5, 1, 1, NOW()),
(20061, '查看军团建筑', 20060, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:structures:view', 1, 1, 1, NOW()),
(20062, '同步军团建筑', 20060, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:structures:manage', 2, 1, 1, NOW());

UPDATE `sys_menu` SET `deleted` = `id` WHERE `id` = 20021 AND `permission` = 'eve:structures:view' AND `deleted` = 0;

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
VALUES (20001, 20060), (20001, 20061), (20001, 20062);

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin', 'corp_member')
  AND `role`.`deleted` = 0 AND `menu`.`id` IN (20060, 20061) AND `menu`.`deleted` = 0;

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin')
  AND `role`.`deleted` = 0 AND `menu`.`id` = 20062 AND `menu`.`deleted` = 0;

CREATE TABLE `eve_corporation_structure` (
    `id` bigint AUTO_INCREMENT COMMENT 'ID',
    `tenant_id` bigint NOT NULL COMMENT '租户ID',
    `corporation_ref_id` bigint NOT NULL COMMENT 'EVE军团绑定记录ID',
    `structure_id` bigint NOT NULL COMMENT '游戏建筑ID',
    `type_id` int NOT NULL COMMENT '建筑类型ID',
    `type_name` varchar(255) DEFAULT NULL COMMENT '建筑类型名称快照',
    `structure_name` varchar(255) DEFAULT NULL COMMENT '建筑自定义名称快照',
    `solar_system_id` bigint NOT NULL COMMENT '所在星系ID',
    `solar_system_name` varchar(255) DEFAULT NULL COMMENT '所在星系名称快照',
    `state` varchar(50) NOT NULL COMMENT '国服建筑状态',
    `fuel_expires_at` datetime DEFAULT NULL COMMENT '燃料耗尽时间',
    `state_timer_start_at` datetime DEFAULT NULL COMMENT '状态计时开始时间',
    `state_timer_end_at` datetime DEFAULT NULL COMMENT '状态计时结束时间',
    `unanchors_at` datetime DEFAULT NULL COMMENT '拆锚时间',
    `services` json DEFAULT NULL COMMENT '建筑服务状态快照',
    `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '快照状态：ACTIVE或MISSING',
    `last_seen_at` datetime NOT NULL COMMENT '最近完整同步时间',
    `source_expires_at` datetime DEFAULT NULL COMMENT '上游缓存过期时间',
    `create_user` bigint NOT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `deleted` bigint NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_eve_corporation_structure` (`tenant_id`, `corporation_ref_id`, `structure_id`, `deleted`),
    INDEX `idx_eve_corporation_structure_page` (`tenant_id`, `corporation_ref_id`, `status`, `state`, `fuel_expires_at`),
    INDEX `idx_eve_corporation_structure_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EVE军团建筑当前完整快照';
