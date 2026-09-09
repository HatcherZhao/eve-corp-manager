-- liquibase formatted sql

-- changeset eve-corp-manager:017-static-reference-menus
-- comment 新增 EVE 基础信息菜单及静态资料浏览、导出和全量更新权限
INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(20050, '基础信息', 20000, 1, '/eve/reference', 'EveReference', 'Layout', '/eve/reference/types', 'info-circle', b'0', b'0', b'0', NULL, 4, 1, 1, NOW()),
(20051, '物品资料', 20050, 2, '/eve/reference/types', 'EveStaticTypes', 'eve/reference/types/index', NULL, 'apps', b'0', b'0', b'0', 'eve:reference:view', 1, 1, 1, NOW()),
(20052, '位置资料', 20050, 2, '/eve/reference/locations', 'EveStaticLocations', 'eve/reference/locations/index', NULL, 'location', b'0', b'0', b'0', 'eve:reference:view', 2, 1, 1, NOW()),
(20053, '导出 EVE 静态资料', 20050, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:reference:export', 3, 1, 1, NOW()),
(20054, '更新 EVE 静态资料', 20050, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:reference:manage', 4, 1, 1, NOW());

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
VALUES (20001, 20050), (20001, 20051), (20001, 20052), (20001, 20053), (20001, 20054);

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin', 'corp_member')
  AND `role`.`deleted` = 0
  AND `menu`.`id` IN (20050, 20051, 20052, 20053)
  AND `menu`.`deleted` = 0;

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin')
  AND `role`.`deleted` = 0
  AND `menu`.`id` = 20054
  AND `menu`.`deleted` = 0;
