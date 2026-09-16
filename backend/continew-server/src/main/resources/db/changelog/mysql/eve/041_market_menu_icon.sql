-- liquibase formatted sql

-- changeset eve-corp-manager:041-market-menu-icon
-- comment 使用前端已注册的行情图标，修复市场菜单图标缺失
UPDATE `sys_menu`
SET `icon` = 'area-chart'
WHERE `id` IN (20090, 20091);
