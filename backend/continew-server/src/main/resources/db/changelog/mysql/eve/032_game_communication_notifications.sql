-- liquibase formatted sql

-- changeset eve-corp-manager:032-game-communication-notifications
-- comment 新增授权角色游戏通知缓存，并将邮件与通知归并到游戏内通信菜单组
CREATE TABLE `eve_game_notification`
(
    `id`                bigint        NOT NULL COMMENT 'ID',
    `tenant_id`         bigint        NOT NULL COMMENT '租户 ID',
    `user_id`           bigint        NOT NULL COMMENT '本站用户 ID',
    `character_ref_id`  bigint        NOT NULL COMMENT '授权角色绑定记录 ID',
    `character_id`      bigint        NOT NULL COMMENT '授权角色游戏 ID',
    `notification_id`   bigint        NOT NULL COMMENT '国服通知 ID',
    `is_read`           tinyint       NOT NULL DEFAULT 0 COMMENT '国服是否已读',
    `sender_id`         bigint        DEFAULT NULL COMMENT '通知发送方游戏 ID',
    `sender_type`       varchar(32)   DEFAULT NULL COMMENT '通知发送方实体类型',
    `notification_type` varchar(128)  DEFAULT NULL COMMENT '国服原始通知类型',
    `category`          varchar(40)   NOT NULL COMMENT '平台通知分类',
    `content`           mediumtext    DEFAULT NULL COMMENT '国服通知正文',
    `sent_at`           datetime      DEFAULT NULL COMMENT '国服通知产生时间',
    `last_seen_at`      datetime      NOT NULL COMMENT '最近同步时间',
    `source_expires_at` datetime      DEFAULT NULL COMMENT '国服响应有效期',
    `create_user`       bigint        DEFAULT NULL COMMENT '创建人',
    `create_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user`       bigint        DEFAULT NULL COMMENT '修改人',
    `update_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`           tinyint       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_eve_game_notification_owner_notice` (`tenant_id`, `user_id`, `character_ref_id`, `notification_id`),
    KEY `idx_eve_game_notification_owner_category_sent` (`tenant_id`, `user_id`, `character_ref_id`, `category`, `sent_at`, `notification_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'EVE 授权角色游戏通知缓存';

INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (20074, '游戏内通信', 20000, 1, '/eve/communication', 'EveGameCommunicationDirectory', 'Layout', '/eve/mail/inbox', 'message', b'0', b'0', b'0', NULL, 9, 1, 1, NOW()),
    (20075, '游戏通知', 20074, 1, '/eve/notifications', 'EveGameNotificationDirectory', 'Layout', '/eve/notifications/all', 'notification', b'0', b'0', b'0', NULL, 2, 1, 1, NOW()),
    (20076, '全部', 20075, 2, '/eve/notifications/all', 'EveGameNotificationsAll', 'eve/notifications/index', NULL, 'apps', b'0', b'0', b'0', 'eve:notifications:view', 1, 1, 1, NOW()),
    (20077, '军团与成员', 20075, 2, '/eve/notifications/corporation-members', 'EveGameNotificationsCorporationMembers', 'eve/notifications/index', NULL, 'user-group', b'0', b'0', b'0', 'eve:notifications:view', 2, 1, 1, NOW()),
    (20078, '建筑与资产安全', 20075, 2, '/eve/notifications/structures-assets', 'EveGameNotificationsStructuresAssets', 'eve/notifications/index', NULL, 'home', b'0', b'0', b'0', 'eve:notifications:view', 3, 1, 1, NOW()),
    (20079, '战争与主权', 20075, 2, '/eve/notifications/war-sovereignty', 'EveGameNotificationsWarSovereignty', 'eve/notifications/index', NULL, 'sword', b'0', b'0', b'0', 'eve:notifications:view', 4, 1, 1, NOW()),
    (20080, '月矿与工业', 20075, 2, '/eve/notifications/moon-industry', 'EveGameNotificationsMoonIndustry', 'eve/notifications/index', NULL, 'experiment', b'0', b'0', b'0', 'eve:notifications:view', 5, 1, 1, NOW()),
    (20081, '其他', 20075, 2, '/eve/notifications/other', 'EveGameNotificationsOther', 'eve/notifications/index', NULL, 'more', b'0', b'0', b'0', 'eve:notifications:view', 6, 1, 1, NOW()),
    (20082, '查看游戏通知', 20075, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:notifications:view', 7, 1, 1, NOW());

UPDATE `sys_menu`
SET `parent_id` = 20074,
    `sort` = 1,
    `update_user` = 1,
    `update_time` = NOW()
WHERE `id` = 20068 AND `deleted` = 0;

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
VALUES (20001, 20074), (20001, 20075), (20001, 20076), (20001, 20077), (20001, 20078), (20001, 20079),
       (20001, 20080), (20001, 20081), (20001, 20082);

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin', 'corp_member')
  AND `role`.`deleted` = 0
  AND `menu`.`id` IN (20068, 20071, 20072, 20073, 20074, 20075, 20076, 20077, 20078, 20079, 20080, 20081, 20082)
  AND `menu`.`deleted` = 0;
