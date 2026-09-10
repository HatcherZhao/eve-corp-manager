-- liquibase formatted sql

-- changeset eve-corp-manager:024-site-logo
-- comment 将系统默认 Logo 切换为 README 展示的 EVE Corp Manager 标志，不覆盖管理员已上传的自定义 Logo
UPDATE `sys_option`
SET `default_value` = '/eve-corp-manager-logo.png'
WHERE `code` = 'SITE_LOGO'
  AND `value` IS NULL
  AND `default_value` = '/logo.svg';
