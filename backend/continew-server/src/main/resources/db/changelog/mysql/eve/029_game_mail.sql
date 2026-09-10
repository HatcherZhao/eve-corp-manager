-- liquibase formatted sql

-- changeset eve-corp-manager:029-game-mail
-- comment 新增当前授权角色的游戏内邮件缓存、发信审计及菜单权限；邮箱按用户和角色绑定隔离
CREATE TABLE `eve_game_mail`
(
    `id`                     bigint       NOT NULL COMMENT 'ID',
    `tenant_id`              bigint       NOT NULL COMMENT '租户 ID',
    `user_id`                bigint       NOT NULL COMMENT '本站用户 ID',
    `character_ref_id`       bigint       NOT NULL COMMENT '授权角色绑定记录 ID',
    `character_id`           bigint       NOT NULL COMMENT '授权角色游戏 ID',
    `mail_id`                bigint       NOT NULL COMMENT '国服邮件 ID',
    `from_id`                bigint       DEFAULT NULL COMMENT '发件人游戏 ID',
    `subject`                varchar(1000) DEFAULT NULL COMMENT '邮件主题',
    `sent_at`                datetime     DEFAULT NULL COMMENT '国服邮件发送时间',
    `is_read`                tinyint      NOT NULL DEFAULT 0 COMMENT '是否已读',
    `labels`                 json         DEFAULT NULL COMMENT '国服标签 ID 列表',
    `recipients`             json         DEFAULT NULL COMMENT '国服收件人快照',
    `body`                   mediumtext   DEFAULT NULL COMMENT '按需缓存的邮件正文',
    `body_synchronized_at`   datetime     DEFAULT NULL COMMENT '正文最近同步时间',
    `body_source_expires_at` datetime     DEFAULT NULL COMMENT '正文上游缓存过期时间',
    `last_seen_at`           datetime     NOT NULL COMMENT '邮件头最近同步时间',
    `source_expires_at`      datetime     DEFAULT NULL COMMENT '邮件头上游缓存过期时间',
    `create_user`            bigint       DEFAULT NULL COMMENT '创建人',
    `create_time`            datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user`            bigint       DEFAULT NULL COMMENT '修改人',
    `update_time`            datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`                tinyint      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_eve_game_mail_owner_mail` (`tenant_id`, `user_id`, `character_ref_id`, `mail_id`),
    KEY `idx_eve_game_mail_owner_sent` (`tenant_id`, `user_id`, `character_ref_id`, `sent_at`, `mail_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'EVE 授权角色游戏内邮件缓存';

CREATE TABLE `eve_game_mail_send_audit`
(
    `id`               bigint        NOT NULL COMMENT 'ID',
    `tenant_id`        bigint        NOT NULL COMMENT '租户 ID',
    `user_id`          bigint        NOT NULL COMMENT '本站用户 ID',
    `character_ref_id` bigint        NOT NULL COMMENT '授权角色绑定记录 ID',
    `character_id`     bigint        NOT NULL COMMENT '发件角色游戏 ID',
    `mail_id`          bigint        DEFAULT NULL COMMENT '国服成功返回的邮件 ID',
    `recipients`       json          NOT NULL COMMENT '收件人快照',
    `subject`          varchar(1000) NOT NULL COMMENT '邮件主题',
    `status`           varchar(16)   NOT NULL COMMENT 'PENDING、SENT、FAILED 或 UNKNOWN',
    `failure_code`     varchar(64)   DEFAULT NULL COMMENT '脱敏失败分类',
    `requested_at`     datetime      NOT NULL COMMENT '请求发起时间',
    `completed_at`     datetime      DEFAULT NULL COMMENT '结果写入时间',
    `create_user`      bigint        DEFAULT NULL COMMENT '创建人',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user`      bigint        DEFAULT NULL COMMENT '修改人',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`          tinyint       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_eve_game_mail_audit_owner_requested` (`tenant_id`, `user_id`, `character_ref_id`, `requested_at`),
    KEY `idx_eve_game_mail_audit_status` (`tenant_id`, `status`, `requested_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'EVE 游戏内邮件发送审计';

INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (20068, '游戏内邮件', 20000, 2, '/eve/mail', 'EveGameMail', 'eve/mail/index', 'mail', b'0', b'0', b'0', NULL, 9, 1, 1, NOW()),
    (20069, '查看游戏内邮件', 20068, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:mail:view', 1, 1, 1, NOW()),
    (20070, '发送游戏内邮件', 20068, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:mail:send', 2, 1, 1, NOW());

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
VALUES (20001, 20068), (20001, 20069), (20001, 20070);

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin', 'corp_member')
  AND `role`.`deleted` = 0 AND `menu`.`id` IN (20068, 20069, 20070) AND `menu`.`deleted` = 0;
