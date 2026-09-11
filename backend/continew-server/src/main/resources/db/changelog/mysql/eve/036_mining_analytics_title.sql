-- liquibase formatted sql

-- changeset eve-corp-manager:036-mining-analytics-title
-- comment 将采矿账本菜单与权限展示名称统一调整为月矿开采统计
UPDATE `sys_menu`
SET `title` = CASE
                  WHEN `id` = 20023 THEN '月矿开采统计'
                  WHEN `permission` = 'eve:mining:view' THEN '查看月矿开采统计'
                  WHEN `permission` = 'eve:mining:sync' THEN '同步月矿开采统计'
                  ELSE `title`
    END
WHERE `id` = 20023
   OR `permission` IN ('eve:mining:view', 'eve:mining:sync');
