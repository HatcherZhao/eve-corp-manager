-- liquibase formatted sql

-- changeset eve-corp-manager:025-site-favicon
-- comment 将系统默认页签图切换为轻量的 EVE Corp Manager 图标，不覆盖管理员已上传的自定义图标
UPDATE `sys_option`
SET `default_value` = '/eve-corp-manager-favicon.png'
WHERE `code` = 'SITE_FAVICON'
  AND `value` IS NULL
  AND `default_value` = '/favicon.ico';
