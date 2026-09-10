-- liquibase formatted sql

-- changeset eve-corp-manager:031-game-mail-menu-group
-- comment 将游戏内邮件调整为二级菜单，并修正侧边栏图标
UPDATE `sys_menu`
SET `type` = 1,
    `path` = '/eve/mail',
    `name` = 'EveGameMailDirectory',
    `component` = 'Layout',
    `redirect` = '/eve/mail/inbox',
    `icon` = 'email',
    `permission` = NULL,
    `update_user` = 1,
    `update_time` = NOW()
WHERE `id` = 20068 AND `deleted` = 0;

INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (20071, '收件箱', 20068, 2, '/eve/mail/inbox', 'EveGameMailInbox', 'eve/mail/index', 'message', b'0', b'0', b'0', 'eve:mail:view', 1, 1, 1, NOW()),
    (20072, '已发送', 20068, 2, '/eve/mail/sent', 'EveGameMailSent', 'eve/mail/index', 'send', b'0', b'0', b'0', 'eve:mail:view', 2, 1, 1, NOW()),
    (20073, '写邮件', 20068, 2, '/eve/mail/compose', 'EveGameMailCompose', 'eve/mail/index', 'edit', b'0', b'0', b'0', 'eve:mail:send', 3, 1, 1, NOW());

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
VALUES (20001, 20071), (20001, 20072), (20001, 20073);

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin', 'corp_member')
  AND `role`.`deleted` = 0
  AND `menu`.`id` IN (20071, 20072, 20073)
  AND `menu`.`deleted` = 0;
