-- liquibase formatted sql

-- changeset eve-corp-manager:049-industry-menu-structure
-- comment 将工业制造拆分为机会、计算、计划、库存、配置和数据校验六个页面，保留旧入口兼容

UPDATE `sys_menu`
SET `redirect` = '/eve/industry/opportunities',
    `update_user` = 1,
    `update_time` = NOW()
WHERE `id` = 20100
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `title` = '机会分析',
    `path` = '/eve/industry/opportunities',
    `name` = 'EveIndustryOpportunities',
    `component` = 'eve/industry/opportunities/index',
    `redirect` = NULL,
    `icon` = 'dashboard',
    `sort` = 1,
    `update_user` = 1,
    `update_time` = NOW()
WHERE `id` = 20101
  AND `deleted` = 0;

INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (20104, '蓝图计算', 20100, 2, '/eve/industry/calculator', 'EveIndustryCalculator', 'eve/industry/calculator/index', NULL, 'experiment', b'0', b'0', b'0', 'eve:industry:view', 2, 1, 1, NOW()),
    (20105, '生产计划', 20100, 2, '/eve/industry/plans', 'EveIndustryPlans', 'eve/industry/plans/index', NULL, 'calendar', b'0', b'0', b'0', 'eve:industry:view', 3, 1, 1, NOW()),
    (20106, '库存与材料', 20100, 2, '/eve/industry/inventory', 'EveIndustryInventory', 'eve/industry/inventory/index', NULL, 'storage', b'0', b'0', b'0', 'eve:industry:view', 4, 1, 1, NOW()),
    (20107, '工业配置', 20100, 2, '/eve/industry/config', 'EveIndustryConfig', 'eve/industry/config/index', NULL, 'settings', b'0', b'0', b'0', 'eve:industry:config:manage', 5, 1, 1, NOW()),
    (20108, '数据与校验', 20100, 2, '/eve/industry/data', 'EveIndustryData', 'eve/industry/data/index', NULL, 'check-circle', b'0', b'0', b'0', 'eve:industry:data:manage', 6, 1, 1, NOW());

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
VALUES (20001, 20104), (20001, 20105), (20001, 20106), (20001, 20107), (20001, 20108);

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin', 'corp_member')
  AND `role`.`deleted` = 0
  AND `menu`.`id` IN (20100, 20101, 20104, 20105, 20106)
  AND `menu`.`deleted` = 0;

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin')
  AND `role`.`deleted` = 0
  AND `menu`.`id` IN (20102, 20103, 20107, 20108)
  AND `menu`.`deleted` = 0;
