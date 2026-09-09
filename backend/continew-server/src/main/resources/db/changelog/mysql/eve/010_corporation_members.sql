-- liquibase formatted sql

-- changeset eve-corp-manager:010-corporation-members
-- comment 新增军团完整成员名册、追踪快照与成员管理菜单；追踪数据按本站独立权限隔离
-- preconditions onFail:HALT onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM `sys_menu` WHERE (`id` = 20030 AND NOT (`title` = '成员管理' AND `parent_id` = 20000 AND `type` = 2 AND `path` = '/eve/members' AND `name` = 'EveMembers' AND `component` = 'eve/members/index' AND `icon` = 'user-group' AND `is_external` = b'0' AND `is_cache` = b'0' AND `is_hidden` = b'0' AND `permission` = 'eve:members:view' AND `sort` = 2 AND `status` = 1 AND `create_user` = 1 AND `deleted` = 0)) OR (`id` = 20031 AND NOT (`title` = '查看成员追踪' AND `parent_id` = 20030 AND `type` = 3 AND `permission` = 'eve:members:track:view' AND `sort` = 1 AND `status` = 1 AND `create_user` = 1 AND `deleted` = 0)) OR (`id` = 20032 AND NOT (`title` = '同步成员数据' AND `parent_id` = 20030 AND `type` = 3 AND `permission` = 'eve:members:manage' AND `sort` = 2 AND `status` = 1 AND `create_user` = 1 AND `deleted` = 0))
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM `sys_menu` WHERE (`path` = '/eve/members' AND `id` <> 20030) OR (`permission` = 'eve:members:track:view' AND `id` <> 20031) OR (`permission` = 'eve:members:manage' AND `id` <> 20032)
INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(20030, '成员管理', 20000, 2, '/eve/members', 'EveMembers', 'eve/members/index', 'user-group', b'0', b'0', b'0', 'eve:members:view', 2, 1, 1, NOW()),
(20031, '查看成员追踪', 20030, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:members:track:view', 1, 1, 1, NOW()),
(20032, '同步成员数据', 20030, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:members:manage', 2, 1, 1, NOW());

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
VALUES (20001, 20030), (20001, 20031), (20001, 20032);

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin')
  AND `role`.`deleted` = 0
  AND `menu`.`id` IN (20030, 20031, 20032)
  AND `menu`.`deleted` = 0;

CREATE TABLE `eve_corporation_roster_member` (
    `id` bigint AUTO_INCREMENT COMMENT 'ID',
    `tenant_id` bigint NOT NULL COMMENT '租户ID',
    `corporation_ref_id` bigint NOT NULL COMMENT 'EVE军团绑定记录ID',
    `character_id` bigint NOT NULL COMMENT '游戏角色ID',
    `character_name` varchar(100) DEFAULT NULL COMMENT '角色名称，名称解析失败时可为空',
    `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '名册状态',
    `joined_at` datetime DEFAULT NULL COMMENT '上游成员追踪提供的入团时间',
    `left_at` datetime DEFAULT NULL COMMENT '本站确认离团时间',
    `last_seen_at` datetime NOT NULL COMMENT '最近完整名册发现时间',
    `create_user` bigint NOT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `deleted` bigint NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_eve_roster_member` (`tenant_id`, `corporation_ref_id`, `character_id`, `deleted`),
    INDEX `idx_eve_roster_active` (`tenant_id`, `corporation_ref_id`, `status`, `character_name`),
    INDEX `idx_eve_roster_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EVE军团完整游戏成员名册';

CREATE TABLE `eve_corporation_member_tracking` (
    `id` bigint AUTO_INCREMENT COMMENT 'ID',
    `tenant_id` bigint NOT NULL COMMENT '租户ID',
    `roster_member_id` bigint NOT NULL COMMENT '名册成员ID',
    `base_id` bigint DEFAULT NULL COMMENT '上游基地ID',
    `base_name` varchar(255) DEFAULT NULL COMMENT '基地名称解析快照',
    `location_id` bigint DEFAULT NULL COMMENT '上游位置ID',
    `location_name` varchar(255) DEFAULT NULL COMMENT '位置名称解析快照',
    `ship_type_id` bigint DEFAULT NULL COMMENT '上游舰船类型ID',
    `ship_type_name` varchar(255) DEFAULT NULL COMMENT '舰船类型名称解析快照',
    `last_logon_at` datetime DEFAULT NULL COMMENT '上游最近登录记录时间',
    `last_logoff_at` datetime DEFAULT NULL COMMENT '上游最近登出记录时间',
    `source_observed_at` datetime NOT NULL COMMENT '上游数据观测时间',
    `source_expires_at` datetime DEFAULT NULL COMMENT '上游缓存过期时间',
    `create_user` bigint NOT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `deleted` bigint NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_eve_member_tracking` (`tenant_id`, `roster_member_id`, `deleted`),
    INDEX `idx_eve_member_tracking_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EVE军团成员追踪快照';

CREATE TABLE `eve_member_sync_run` (
    `id` bigint AUTO_INCREMENT COMMENT 'ID',
    `tenant_id` bigint NOT NULL COMMENT '租户ID',
    `corporation_ref_id` bigint NOT NULL COMMENT 'EVE军团绑定记录ID',
    `resource` varchar(30) NOT NULL COMMENT '资源类型',
    `status` varchar(30) NOT NULL COMMENT '同步状态',
    `record_count` int NOT NULL DEFAULT 0 COMMENT '本次记录数',
    `source_expires_at` datetime DEFAULT NULL COMMENT '上游缓存过期时间',
    `failure_code` varchar(100) DEFAULT NULL COMMENT '脱敏失败分类',
    `started_at` datetime NOT NULL COMMENT '开始时间',
    `finished_at` datetime DEFAULT NULL COMMENT '结束时间',
    `create_user` bigint NOT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `deleted` bigint NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_eve_member_sync_run` (`tenant_id`, `corporation_ref_id`, `resource`, `started_at`),
    INDEX `idx_eve_member_sync_run_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EVE成员资源同步批次';

CREATE TABLE `eve_member_tracking_access_audit` (
    `id` bigint AUTO_INCREMENT COMMENT 'ID',
    `tenant_id` bigint NOT NULL COMMENT '租户ID',
    `roster_member_id` bigint DEFAULT NULL COMMENT '查看的名册成员ID，列表读取可为空',
    `user_id` bigint NOT NULL COMMENT '查看者本站用户ID',
    `access_scope` varchar(30) NOT NULL COMMENT 'LIST或DETAIL',
    `occurred_at` datetime NOT NULL COMMENT '发生时间',
    `create_user` bigint NOT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `deleted` bigint NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_eve_tracking_access` (`tenant_id`, `roster_member_id`, `occurred_at`),
    INDEX `idx_eve_tracking_access_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EVE成员追踪查看审计';
