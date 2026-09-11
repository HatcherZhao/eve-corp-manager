-- liquibase formatted sql

-- changeset eve-corp-manager:035-login-session-six-hours
-- comment 将本站 PC 客户端的登录有效期和无操作超时统一调整为六小时
UPDATE `sys_client`
SET `active_timeout` = 21600,
    `timeout`        = 21600
WHERE `id` = 1
  AND `client_type` = 'PC';
