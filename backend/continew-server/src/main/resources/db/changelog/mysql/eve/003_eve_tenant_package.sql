-- liquibase formatted sql

-- changeset eve-corp-manager:003-eve-tenant-package
-- comment 初始化 EVE 军团专用租户套餐，仅开放军团模块基础菜单
INSERT INTO `tenant_package`
(`id`, `name`, `sort`, `menu_check_strictly`, `description`, `status`, `create_user`, `create_time`, `deleted`)
SELECT 20001, 'EVE军团套餐', 1, b'1', 'EVE军团认领流程专用套餐', 1, 1, NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `tenant_package` WHERE `id` = 20001);

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
VALUES (20001, 20000), (20001, 20010), (20001, 20011);
