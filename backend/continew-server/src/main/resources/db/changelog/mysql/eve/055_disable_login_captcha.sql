-- liquibase formatted sql

-- changeset eve-corp-manager:055-disable-login-captcha
-- comment 关闭账号登录图形验证码，保留密码错误锁定等既有安全策略
UPDATE `sys_option`
SET `value` = '0', `default_value` = '0'
WHERE `code` = 'LOGIN_CAPTCHA_ENABLED';
