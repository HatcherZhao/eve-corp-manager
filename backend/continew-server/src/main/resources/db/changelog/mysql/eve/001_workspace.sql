-- liquibase formatted sql
-- changeset eve-corp-manager:001-workspace
-- comment EVE workspace menu and project branding; preserve upstream changesets.
INSERT INTO sys_menu
(id, title, parent_id, type, path, name, component, redirect, icon, is_external, is_cache, is_hidden, permission, sort, status, create_user, create_time)
VALUES
(20000, 'EVE 军团', 0, 1, '/eve', 'Eve', 'Layout', '/eve/workspace', 'apps', b'0', b'0', b'0', NULL, 0, 1, 1, NOW()),
(20010, '军团工作台', 20000, 2, '/eve/workspace', 'EveWorkspace', 'eve/workspace/index', NULL, 'desktop', b'0', b'0', b'0', NULL, 1, 1, 1, NOW()),
(20011, '查看工作台', 20010, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:workspace:view', 1, 1, 1, NOW());

UPDATE sys_option SET value = 'EVE Corp Manager' WHERE code = 'SITE_TITLE';
UPDATE sys_option SET value = 'EVE Online 网易国服军团管理平台' WHERE code = 'SITE_DESCRIPTION';
UPDATE sys_option SET value = 'EVE Corp Manager · Powered by ContiNew Admin (Apache-2.0)' WHERE code = 'SITE_COPYRIGHT';

-- Do not grant EVE permissions to ordinary users or tenant packages automatically.
-- The upstream super-admin uses its built-in all-permissions mechanism.

-- EVE skeleton does not enable the upstream demonstration accounts.
UPDATE sys_user SET status = 2 WHERE id <> 1;
