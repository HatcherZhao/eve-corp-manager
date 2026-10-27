-- liquibase formatted sql

-- changeset eve-corp-manager:058-derived-role-policy
-- comment EVE 派生身份统一读取全局授权定义，注册时间不同不再导致菜单与接口权限分叉

CREATE TABLE IF NOT EXISTS `eve_derived_role_menu` (
    `role_code` varchar(30) NOT NULL COMMENT '派生角色编码：corp_owner、corp_admin、corp_member',
    `menu_id` bigint NOT NULL COMMENT '菜单 ID，包含目录、页面及接口能力',
    PRIMARY KEY (`role_code`, `menu_id`),
    KEY `idx_eve_derived_role_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EVE 派生角色全局授权定义，不含军团或用户数据';

-- 所有军团成员共用的功能定义，显式包含页面所需父目录。
INSERT IGNORE INTO `eve_derived_role_menu` (`role_code`, `menu_id`)
SELECT `identity`.`code`, `menu`.`id`
FROM (SELECT 'corp_owner' AS `code` UNION ALL SELECT 'corp_admin' UNION ALL SELECT 'corp_member') AS `identity`
CROSS JOIN `sys_menu` AS `menu`
WHERE `menu`.`deleted` = 0
  AND `menu`.`id` IN (
      20000, 20010, 20011, 20022, 20050, 20051, 20052, 20053, 20060, 20061, 20065,
      20068, 20069, 20070, 20071, 20072, 20073, 20074, 20075, 20076, 20077,
      20078, 20079, 20080, 20081, 20082, 20083, 20084, 20085, 20090, 20091,
      20093, 20094, 20095, 20098, 20100, 20101, 20104, 20105, 20106, 20109
  );

-- 经营管理能力只属于 CEO/总监；以后新增功能在此全局策略中定义角色范围。
INSERT IGNORE INTO `eve_derived_role_menu` (`role_code`, `menu_id`)
SELECT `identity`.`code`, `menu`.`id`
FROM (SELECT 'corp_owner' AS `code` UNION ALL SELECT 'corp_admin') AS `identity`
CROSS JOIN `sys_menu` AS `menu`
WHERE `menu`.`deleted` = 0
  AND `menu`.`id` IN (
      20020, 20023, 20024, 20030, 20031, 20032, 20033, 20034, 20035, 20040,
      20041, 20054, 20062, 20063, 20064, 20066, 20067, 20086, 20092, 20096,
      20097, 20102, 20103, 20107, 20108, 20110, 20200, 20201
  );

-- 有效授权统一服务动态路由、接口校验和 Agent 摘要。派生角色不使用历史复制数据。
-- 套餐收缩或禁用会实时限制派生权限；独立业务角色保留现有自定义授权路径。
CREATE OR REPLACE SQL SECURITY INVOKER VIEW `eve_effective_role_menu` AS
SELECT `role`.`tenant_id`, `role`.`id` AS `role_id`, `policy`.`menu_id`
FROM `sys_role` AS `role`
INNER JOIN `eve_derived_role_menu` AS `policy` ON `policy`.`role_code` = `role`.`code`
INNER JOIN `tenant` AS `tenant` ON `tenant`.`id` = `role`.`tenant_id`
    AND `tenant`.`deleted` = 0 AND `tenant`.`status` = 1
INNER JOIN `tenant_package` AS `package` ON `package`.`id` = `tenant`.`package_id`
    AND `package`.`deleted` = 0 AND `package`.`status` = 1
INNER JOIN `tenant_package_menu` AS `package_menu` ON `package_menu`.`package_id` = `package`.`id`
    AND `package_menu`.`menu_id` = `policy`.`menu_id`
INNER JOIN `sys_menu` AS `menu` ON `menu`.`id` = `policy`.`menu_id`
    AND `menu`.`deleted` = 0 AND `menu`.`status` = 1
WHERE `role`.`deleted` = 0 AND `role`.`code` IN ('corp_owner', 'corp_admin', 'corp_member')
UNION ALL
SELECT `role_menu`.`tenant_id`, `role_menu`.`role_id`, `role_menu`.`menu_id`
FROM `sys_role_menu` AS `role_menu`
INNER JOIN `sys_role` AS `role` ON `role`.`id` = `role_menu`.`role_id`
    AND `role`.`tenant_id` = `role_menu`.`tenant_id` AND `role`.`deleted` = 0
INNER JOIN `sys_menu` AS `menu` ON `menu`.`id` = `role_menu`.`menu_id`
    AND `menu`.`deleted` = 0 AND `menu`.`status` = 1
WHERE `role`.`code` NOT IN ('corp_owner', 'corp_admin', 'corp_member');
