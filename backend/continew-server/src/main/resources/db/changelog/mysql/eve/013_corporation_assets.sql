-- liquibase formatted sql

-- changeset eve-corp-manager:013-corporation-assets
-- comment 新增军团资产当前快照及资产页面菜单；资产同步必须由总监授权数据源发起
-- preconditions onFail:HALT onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM `sys_menu` WHERE (`id` = 20040 AND NOT (`title` = '军团资产' AND `parent_id` = 20000 AND `type` = 2 AND `path` = '/eve/assets' AND `name` = 'EveAssets' AND `component` = 'eve/assets/index' AND `icon` = 'storage' AND `is_external` = b'0' AND `is_cache` = b'0' AND `is_hidden` = b'0' AND `permission` IS NULL AND `sort` = 3 AND `status` = 1 AND `create_user` = 1 AND `deleted` = 0)) OR (`id` = 20041 AND NOT (`title` = '同步军团资产' AND `parent_id` = 20040 AND `type` = 3 AND `permission` = 'eve:assets:manage' AND `sort` = 2 AND `status` = 1 AND `create_user` = 1 AND `deleted` = 0))
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM `sys_menu` WHERE (`path` = '/eve/assets' AND `id` <> 20040) OR (`permission` = 'eve:assets:manage' AND `id` <> 20041)

INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
(20040, '军团资产', 20000, 2, '/eve/assets', 'EveAssets', 'eve/assets/index', 'storage', b'0', b'0', b'0', NULL, 3, 1, 1, NOW()),
(20041, '同步军团资产', 20040, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:assets:manage', 2, 1, 1, NOW());

-- 原有资产查看权限只作为工作台能力权限存在；迁移到页面下以使动态菜单与权限来源保持一致。
UPDATE `sys_menu` SET `parent_id` = 20040, `sort` = 1 WHERE `id` = 20020 AND `permission` = 'eve:assets:view' AND `deleted` = 0;

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
VALUES (20001, 20040), (20001, 20020), (20001, 20041);

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin')
  AND `role`.`deleted` = 0
  AND `menu`.`id` IN (20040, 20020, 20041)
  AND `menu`.`deleted` = 0;

CREATE TABLE `eve_corporation_asset` (
    `id` bigint AUTO_INCREMENT COMMENT 'ID',
    `tenant_id` bigint NOT NULL COMMENT '租户ID',
    `corporation_ref_id` bigint NOT NULL COMMENT 'EVE军团绑定记录ID',
    `item_id` bigint NOT NULL COMMENT '游戏物品ID',
    `type_id` int NOT NULL COMMENT '物品类型ID',
    `type_name` varchar(255) DEFAULT NULL COMMENT '物品类型名称快照',
    `item_name` varchar(255) DEFAULT NULL COMMENT '可命名物品名称快照',
    `location_id` bigint DEFAULT NULL COMMENT '上游位置ID',
    `location_name` varchar(255) DEFAULT NULL COMMENT '位置名称快照',
    `location_type` varchar(30) NOT NULL COMMENT '位置类别',
    `location_flag` varchar(80) NOT NULL COMMENT '仓位标记',
    `quantity` int NOT NULL COMMENT '数量',
    `singleton` tinyint(1) NOT NULL COMMENT '是否独立物品',
    `blueprint_copy` tinyint(1) DEFAULT NULL COMMENT '是否蓝图拷贝',
    `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '快照状态：ACTIVE或MISSING',
    `last_seen_at` datetime NOT NULL COMMENT '最近完整快照观察时间',
    `source_expires_at` datetime DEFAULT NULL COMMENT '上游缓存过期时间',
    `create_user` bigint NOT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `deleted` bigint NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_eve_corporation_asset` (`tenant_id`, `corporation_ref_id`, `item_id`, `deleted`),
    INDEX `idx_eve_corporation_asset_page` (`tenant_id`, `corporation_ref_id`, `status`, `location_type`, `last_seen_at`),
    INDEX `idx_eve_corporation_asset_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EVE军团资产当前完整快照';
