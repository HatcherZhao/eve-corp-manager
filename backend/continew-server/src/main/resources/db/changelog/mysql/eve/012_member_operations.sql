-- liquibase formatted sql

-- changeset eve-corp-manager:012-member-operations
-- comment 新增成员分组、备注、成员运营审计及相应菜单权限
ALTER TABLE `eve_corporation_roster_member`
    ADD COLUMN `organization_group` varchar(50) DEFAULT NULL COMMENT '军团内部成员分组' AFTER `character_name`,
    ADD COLUMN `member_note` varchar(500) DEFAULT NULL COMMENT '军团内部成员备注' AFTER `organization_group`,
    ADD INDEX `idx_eve_roster_organization_group` (`tenant_id`, `corporation_ref_id`, `organization_group`, `deleted`);

CREATE TABLE `eve_member_operation_audit` (
    `id` bigint AUTO_INCREMENT COMMENT 'ID',
    `tenant_id` bigint NOT NULL COMMENT '租户ID',
    `roster_member_id` bigint DEFAULT NULL COMMENT '名册成员ID',
    `target_user_id` bigint DEFAULT NULL COMMENT '本站目标用户ID',
    `event_type` varchar(50) NOT NULL COMMENT '成员运营事件类型',
    `summary` varchar(255) NOT NULL COMMENT '脱敏操作摘要',
    `actor_user_id` bigint NOT NULL COMMENT '操作者本站用户ID',
    `actor_username` varchar(100) DEFAULT NULL COMMENT '操作者用户名快照',
    `occurred_at` datetime NOT NULL COMMENT '发生时间',
    `create_user` bigint NOT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `deleted` bigint NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_eve_member_operation_member` (`tenant_id`, `roster_member_id`, `occurred_at`),
    INDEX `idx_eve_member_operation_user` (`tenant_id`, `target_user_id`, `occurred_at`),
    INDEX `idx_eve_member_operation_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EVE成员运营操作审计';

INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(20033, '管理成员组织信息', 20030, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:members:organize', 3, 1, 1, NOW()),
(20034, '导出成员数据', 20030, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:members:export', 4, 1, 1, NOW()),
(20035, '查看成员同步历史', 20030, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:members:history:view', 5, 1, 1, NOW());

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
VALUES (20001, 20033), (20001, 20034), (20001, 20035);

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin')
  AND `role`.`deleted` = 0
  AND `menu`.`id` IN (20033, 20034, 20035)
  AND `menu`.`deleted` = 0;
