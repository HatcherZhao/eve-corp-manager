-- liquibase formatted sql

-- changeset eve-corp-manager:008-manager-capability-permissions
-- comment 为军团 CEO 和总监派生角色补齐模块站内权限，实际访问仍校验 OAuth Scope 与游戏角色
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin')
  AND `role`.`deleted` = 0
  AND `menu`.`id` IN (20020, 20021, 20022, 20023, 20024)
  AND `menu`.`deleted` = 0;
