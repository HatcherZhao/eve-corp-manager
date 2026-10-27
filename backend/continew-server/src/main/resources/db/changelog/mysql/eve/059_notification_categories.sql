-- liquibase formatted sql

-- changeset eve-corp-manager:059-notification-categories
-- comment 修正已有角色通知的业务分类，保留原始类型、正文和角色隔离归属

UPDATE `eve_game_notification`
SET `category` = 'CORPORATION_MEMBER'
WHERE `deleted` = 0
  AND UPPER(TRIM(`notification_type`)) IN ('CHARAPPACCEPTMSG', 'CHARAPPWITHDRAWMSG', 'CHARTERMINATIONMSG');

UPDATE `eve_game_notification`
SET `category` = 'STRUCTURE_ASSET_SAFETY'
WHERE `deleted` = 0
  AND UPPER(TRIM(`notification_type`)) IN ('OWNERSHIPTRANSFERRED', 'UNKNOWN NOTIFICATION TYPE (6040)', 'UNKNOWN NOTIFICATION TYPE (6041)');

UPDATE `eve_game_notification`
SET `category` = 'WAR_SOVEREIGNTY'
WHERE `deleted` = 0
  AND UPPER(TRIM(`notification_type`)) IN ('ENTOSISCAPTURESTARTED', 'ACCEPTEDSURRENDER');
