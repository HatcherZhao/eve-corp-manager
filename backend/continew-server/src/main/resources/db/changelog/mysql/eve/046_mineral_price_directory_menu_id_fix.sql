-- liquibase formatted sql

-- changeset eve-corp-manager:046-mineral-price-directory-menu-id-fix
-- comment 修正与星图目录冲突的历史菜单编号，并为已执行 045 的环境补齐菜单授权
INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (20098, '矿物价格', 20090, 2, '/eve/mineral-prices', 'EveMineralPrices', 'eve/mineral-prices/index', NULL, 'experiment', b'0', b'0', b'0', 'eve:market:view', 2, 1, 1, NOW());

UPDATE `sys_menu`
SET `sort` = 3
WHERE `id` = 20092
  AND `deleted` = 0;

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
VALUES (20001, 20098);

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin', 'corp_member')
  AND `role`.`deleted` = 0
  AND `menu`.`id` = 20098
  AND `menu`.`deleted` = 0;
