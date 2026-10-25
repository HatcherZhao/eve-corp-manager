-- liquibase formatted sql

-- changeset eve-corp-manager:056-agent-menu-layout
-- comment 外部 Agent 使用一级目录加实际页面，保留侧栏、顶部栏和既有管理权限
UPDATE `sys_menu`
SET `type` = 1, `path` = '/eve/agents-directory', `name` = 'EveAgentsDirectory',
    `component` = 'Layout', `redirect` = '/eve/agents', `permission` = NULL,
    `update_user` = 1, `update_time` = NOW()
WHERE `id` = 20200 AND `deleted` = 0;

INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES (20201, '外部 Agent', 20200, 2, '/eve/agents', 'EveAgents', 'eve/agents/index', 'tool', b'0', b'0', b'0', 'eve:agents:manage', 1, 1, 1, NOW());

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
VALUES (20001, 20201);

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, 20201
FROM `sys_role` AS `role`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin') AND `role`.`deleted` = 0;
