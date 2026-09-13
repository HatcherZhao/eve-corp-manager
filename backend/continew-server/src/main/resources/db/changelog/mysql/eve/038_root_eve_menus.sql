-- liquibase formatted sql

-- changeset eve-corp-manager:038-root-eve-menus
-- comment 移除 EVE 军团总目录，将其原二级功能提升为一级；查看权限菜单升级为页面路由，保留既有角色和套餐授权

-- 原本位于 EVE 军团下的页面和功能组提升到侧边栏一级。
UPDATE `sys_menu`
SET `parent_id`   = 0,
    `update_user` = 1,
    `update_time` = NOW()
WHERE `parent_id` = 20000
  AND `deleted` = 0;

-- 单页面功能改为一级目录加一个实际页面，以保留 Layout、顶部栏及现有访问权限。
UPDATE `sys_menu`
SET `type` = 1,
    `path` = CASE `id`
                 WHEN 20010 THEN '/eve/workspace-directory'
                 WHEN 20030 THEN '/eve/members-directory'
                 WHEN 20040 THEN '/eve/assets-directory'
                 WHEN 20060 THEN '/eve/structures-directory'
                 WHEN 20022 THEN '/eve/extractions-directory'
                 WHEN 20023 THEN '/eve/mining-directory'
        END,
    `name` = CASE `id`
                 WHEN 20010 THEN 'EveWorkspaceDirectory'
                 WHEN 20030 THEN 'EveMembersDirectory'
                 WHEN 20040 THEN 'EveAssetsDirectory'
                 WHEN 20060 THEN 'EveStructuresDirectory'
                 WHEN 20022 THEN 'EveExtractionsDirectory'
                 WHEN 20023 THEN 'EveMiningDirectory'
        END,
    `component` = 'Layout',
    `redirect` = CASE `id`
                     WHEN 20010 THEN '/eve/workspace'
                     WHEN 20030 THEN '/eve/members'
                     WHEN 20040 THEN '/eve/assets'
                     WHEN 20060 THEN '/eve/structures'
                     WHEN 20022 THEN '/eve/extractions'
                     WHEN 20023 THEN '/eve/mining'
        END,
    `permission`  = NULL,
    `update_user` = 1,
    `update_time` = NOW()
WHERE `id` IN (20010, 20030, 20040, 20060, 20022, 20023)
  AND `deleted` = 0;

-- 复用既有查看权限菜单作为实际页面，避免新建重复权限或改变角色授权范围。
UPDATE `sys_menu`
SET `title` = CASE `id`
                  WHEN 20011 THEN '军团工作台'
                  WHEN 20024 THEN '成员管理'
                  WHEN 20020 THEN '军团资产'
                  WHEN 20061 THEN '军团建筑'
                  WHEN 20065 THEN '月矿情报'
                  WHEN 20067 THEN '月矿开采统计'
        END,
    `parent_id` = CASE `id`
                      WHEN 20011 THEN 20010
                      WHEN 20024 THEN 20030
                      WHEN 20020 THEN 20040
                      WHEN 20061 THEN 20060
                      WHEN 20065 THEN 20022
                      WHEN 20067 THEN 20023
        END,
    `type` = 2,
    `path` = CASE `id`
                 WHEN 20011 THEN '/eve/workspace'
                 WHEN 20024 THEN '/eve/members'
                 WHEN 20020 THEN '/eve/assets'
                 WHEN 20061 THEN '/eve/structures'
                 WHEN 20065 THEN '/eve/extractions'
                 WHEN 20067 THEN '/eve/mining'
        END,
    `name` = CASE `id`
                 WHEN 20011 THEN 'EveWorkspacePage'
                 WHEN 20024 THEN 'EveMembersPage'
                 WHEN 20020 THEN 'EveAssetsPage'
                 WHEN 20061 THEN 'EveStructuresPage'
                 WHEN 20065 THEN 'EveExtractionsPage'
                 WHEN 20067 THEN 'EveMiningPage'
        END,
    `component` = CASE `id`
                      WHEN 20011 THEN 'eve/workspace/index'
                      WHEN 20024 THEN 'eve/members/index'
                      WHEN 20020 THEN 'eve/assets/index'
                      WHEN 20061 THEN 'eve/structures/index'
                      WHEN 20065 THEN 'eve/extractions/index'
                      WHEN 20067 THEN 'eve/mining/index'
        END,
    `redirect` = NULL,
    `icon` = CASE `id`
                 WHEN 20011 THEN 'desktop'
                 WHEN 20024 THEN 'user-group'
                 WHEN 20020 THEN 'storage'
                 WHEN 20061 THEN 'home'
                 WHEN 20065 THEN 'calendar'
                 WHEN 20067 THEN 'storage'
        END,
    `is_external` = b'0',
    `is_cache`    = b'0',
    `is_hidden`   = b'0',
    `sort`        = 1,
    `update_user` = 1,
    `update_time` = NOW()
WHERE `id` IN (20011, 20024, 20020, 20061, 20065, 20067)
  AND `deleted` = 0;

-- 总目录不再参与路由树；关联记录保留，以免影响历史套餐或角色数据。
UPDATE `sys_menu`
SET `deleted`     = `id`,
    `update_user` = 1,
    `update_time` = NOW()
WHERE `id` = 20000
  AND `deleted` = 0;
