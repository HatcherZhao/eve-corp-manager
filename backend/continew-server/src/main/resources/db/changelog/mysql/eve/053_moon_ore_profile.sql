-- liquibase formatted sql

-- changeset eve-corp-manager:053-moon-ore-profile
-- comment 新增按军团月矿堡长期保存的矿种及产出比例档案，仅从本人已缓存的游戏通知初始化
CREATE TABLE `eve_moon_ore_profile` (
    `id` bigint AUTO_INCREMENT COMMENT 'ID',
    `tenant_id` bigint NOT NULL COMMENT '租户ID',
    `corporation_ref_id` bigint NOT NULL COMMENT 'EVE军团绑定记录ID',
    `structure_id` bigint NOT NULL COMMENT '游戏月矿堡建筑ID',
    `moon_id` bigint DEFAULT NULL COMMENT '最近匹配的游戏月球ID',
    `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '档案状态：READY或PENDING',
    `ores_json` json DEFAULT NULL COMMENT '矿种及归一化产出比例JSON',
    `ore_names` text DEFAULT NULL COMMENT '供矿种名称搜索的冗余文本',
    `source_notification_id` bigint DEFAULT NULL COMMENT '来源游戏通知ID，仅内部追踪',
    `source_sent_at` datetime DEFAULT NULL COMMENT '来源游戏通知发送时间',
    `last_cache_checked_at` datetime DEFAULT NULL COMMENT 'PENDING档案最近本地缓存检查时间',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `deleted` bigint NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_eve_moon_ore_profile` (`tenant_id`, `corporation_ref_id`, `structure_id`, `deleted`),
    INDEX `idx_eve_moon_ore_profile_status` (`tenant_id`, `corporation_ref_id`, `status`, `deleted`),
    INDEX `idx_eve_moon_ore_profile_moon` (`tenant_id`, `corporation_ref_id`, `moon_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EVE月矿堡长期矿种档案';
