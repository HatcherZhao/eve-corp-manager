-- liquibase formatted sql

-- changeset eve-corp-manager:022-mining-sync-permission-fix
-- comment 修复已发布月矿查看菜单占用 20065 时的采矿同步权限，保留既有菜单和授权关系
INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES (20066, '同步采矿账本', 20023, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:mining:sync', 1, 1, 1, NOW());

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`) VALUES (20001, 20066);

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin')
  AND `role`.`deleted` = 0 AND `menu`.`id` = 20066 AND `menu`.`deleted` = 0;
