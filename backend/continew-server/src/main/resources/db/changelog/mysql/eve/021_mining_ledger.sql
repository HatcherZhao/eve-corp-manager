-- liquibase formatted sql

-- changeset eve-corp-manager:021-mining-ledger
-- comment 新增国服采矿观察者快照、账本聚合明细和同步审计；同一自然键的后续同步覆盖更新而非累计
UPDATE `sys_menu`
SET `title` = '采矿账本', `parent_id` = 20000, `type` = 2, `path` = '/eve/mining',
    `name` = 'EveMining', `component` = 'eve/mining/index', `icon` = 'storage',
    `is_external` = b'0', `is_cache` = b'0', `is_hidden` = b'0', `permission` = NULL, `sort` = 7
WHERE `id` = 20023 AND `permission` = 'eve:mining:view' AND `deleted` = 0;

INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES (20065, '同步采矿账本', 20023, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:mining:sync', 1, 1, 1, NOW());

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`) VALUES (20001, 20065);

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin')
  AND `role`.`deleted` = 0 AND `menu`.`id` = 20065 AND `menu`.`deleted` = 0;

CREATE TABLE `eve_mining_observer` (
    `id` bigint AUTO_INCREMENT COMMENT 'ID',
    `tenant_id` bigint NOT NULL COMMENT '租户ID',
    `corporation_ref_id` bigint NOT NULL COMMENT 'EVE军团绑定记录ID',
    `observer_id` bigint NOT NULL COMMENT '国服观察者实体ID',
    `observer_type` varchar(32) NOT NULL COMMENT '国服观察者类别',
    `observer_name` varchar(255) DEFAULT NULL COMMENT '观察者名称快照',
    `source_last_updated` date NOT NULL COMMENT '国服观察者最后账本日期',
    `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '快照状态：ACTIVE或MISSING',
    `last_seen_at` datetime NOT NULL COMMENT '最近完整同步时间',
    `source_expires_at` datetime DEFAULT NULL COMMENT '观察者清单上游缓存过期时间',
    `create_user` bigint NOT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `deleted` bigint NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_eve_mining_observer` (`tenant_id`, `corporation_ref_id`, `observer_id`, `deleted`),
    INDEX `idx_eve_mining_observer_page` (`tenant_id`, `corporation_ref_id`, `status`, `source_last_updated`),
    INDEX `idx_eve_mining_observer_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EVE军团采矿账本观察者快照';

CREATE TABLE `eve_mining_ledger` (
    `id` bigint AUTO_INCREMENT COMMENT 'ID',
    `tenant_id` bigint NOT NULL COMMENT '租户ID',
    `corporation_ref_id` bigint NOT NULL COMMENT 'EVE军团绑定记录ID',
    `observer_id` bigint NOT NULL COMMENT '国服观察者实体ID',
    `observer_name` varchar(255) DEFAULT NULL COMMENT '观察者名称快照',
    `character_id` bigint NOT NULL COMMENT '采矿角色游戏ID',
    `character_name` varchar(100) DEFAULT NULL COMMENT '采矿角色名称快照',
    `recorded_corporation_id` bigint NOT NULL COMMENT '国服记录时角色所属军团ID',
    `type_id` int NOT NULL COMMENT '矿物类型ID',
    `type_name` varchar(255) DEFAULT NULL COMMENT '矿物类型名称快照',
    `recorded_at` date NOT NULL COMMENT '国服账本日期',
    `quantity` bigint NOT NULL COMMENT '国服聚合数量',
    `last_seen_at` datetime NOT NULL COMMENT '最近成功同步发现时间',
    `source_expires_at` datetime DEFAULT NULL COMMENT '账本明细上游缓存过期时间',
    `create_user` bigint NOT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `deleted` bigint NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_eve_mining_ledger` (`tenant_id`, `corporation_ref_id`, `observer_id`, `recorded_at`, `character_id`, `recorded_corporation_id`, `type_id`, `deleted`),
    INDEX `idx_eve_mining_ledger_page` (`tenant_id`, `corporation_ref_id`, `recorded_at`, `observer_id`, `character_id`, `type_id`),
    INDEX `idx_eve_mining_ledger_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EVE军团观察者采矿账本聚合明细';

CREATE TABLE `eve_mining_sync_run` (
    `id` bigint AUTO_INCREMENT COMMENT 'ID',
    `tenant_id` bigint NOT NULL COMMENT '租户ID',
    `corporation_ref_id` bigint NOT NULL COMMENT 'EVE军团绑定记录ID',
    `source_character_ref_id` bigint NOT NULL COMMENT '本次数据源角色绑定ID',
    `observer_count` int NOT NULL COMMENT '读取完成的观察者数',
    `ledger_count` int NOT NULL COMMENT '发布的账本明细数',
    `page_count` int NOT NULL COMMENT '读取的国服页数',
    `source_expires_at` datetime DEFAULT NULL COMMENT '上游缓存最晚过期时间',
    `synchronized_at` datetime NOT NULL COMMENT '同步完成时间',
    `create_user` bigint NOT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `deleted` bigint NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_eve_mining_sync_run_page` (`tenant_id`, `corporation_ref_id`, `synchronized_at`),
    INDEX `idx_eve_mining_sync_run_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EVE军团采矿账本同步批次';
