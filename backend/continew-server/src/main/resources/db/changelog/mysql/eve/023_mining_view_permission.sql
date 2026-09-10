-- liquibase formatted sql

-- changeset eve-corp-manager:023-mining-view-permission
-- comment 将采矿账本页面菜单与查看权限拆分，修复 021 将原查看权限菜单升级为路由后遗失接口访问授权的问题
INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES (20067, '查看采矿账本', 20023, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:mining:view', 1, 1, 1, NOW());

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`) VALUES (20001, 20067);

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin')
  AND `role`.`deleted` = 0 AND `menu`.`id` = 20067 AND `menu`.`deleted` = 0;
