-- liquibase formatted sql

-- changeset eve-corp-manager:004-eve-capability-permissions
-- comment 初始化 EVE 模块站内权限并纳入军团套餐
INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(20020, '查看军团资产', 20010, 3, 'eve:assets:view', 20, 1, 1, NOW()),
(20021, '查看建筑设施', 20010, 3, 'eve:structures:view', 21, 1, 1, NOW()),
(20022, '查看月矿计划', 20010, 3, 'eve:extractions:view', 22, 1, 1, NOW()),
(20023, '查看采矿账本', 20010, 3, 'eve:mining:view', 23, 1, 1, NOW()),
(20024, '查看军团人员', 20010, 3, 'eve:members:view', 24, 1, 1, NOW());

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
VALUES (20001, 20020), (20001, 20021), (20001, 20022), (20001, 20023), (20001, 20024);
