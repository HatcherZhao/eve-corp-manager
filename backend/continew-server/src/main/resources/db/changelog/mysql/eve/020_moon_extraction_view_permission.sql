-- liquibase formatted sql

-- changeset eve-corp-manager:020-moon-extraction-view-permission
-- comment 将月矿页面菜单与查看权限拆分，修复 019 将原查看权限菜单升级为路由后遗失接口访问授权的问题
INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(20065, '查看月矿计划', 20022, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:extractions:view', 1, 1, 1, NOW());

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
VALUES (20001, 20065);

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin', 'corp_member')
  AND `role`.`deleted` = 0 AND `menu`.`id` = 20065 AND `menu`.`deleted` = 0;
