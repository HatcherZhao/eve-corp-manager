-- liquibase formatted sql

-- changeset eve-corp-manager:009-manager-capability-permission-tenant-fix
-- comment 修复派生管理角色模块菜单被写入默认租户导致登录同步冲突的问题
UPDATE `sys_role_menu` AS `role_menu`
INNER JOIN `sys_role` AS `role` ON `role`.`id` = `role_menu`.`role_id`
SET `role_menu`.`tenant_id` = `role`.`tenant_id`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin')
  AND `role`.`deleted` = 0
  AND `role_menu`.`menu_id` IN (20020, 20021, 20022, 20023, 20024)
  AND `role_menu`.`tenant_id` <> `role`.`tenant_id`;
