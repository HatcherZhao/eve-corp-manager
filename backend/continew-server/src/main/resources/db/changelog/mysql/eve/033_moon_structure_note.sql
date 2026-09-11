-- liquibase formatted sql

-- changeset eve-corp-manager:033-moon-structure-note
-- comment 新增按月矿堡长期保存的军团备注，并迁移已有单次月矿备注
CREATE TABLE `eve_moon_structure_note` (
    `id` bigint AUTO_INCREMENT COMMENT 'ID',
    `tenant_id` bigint NOT NULL COMMENT '租户ID',
    `corporation_ref_id` bigint NOT NULL COMMENT 'EVE军团绑定记录ID',
    `structure_id` bigint NOT NULL COMMENT '游戏月矿堡建筑ID',
    `note` varchar(500) DEFAULT NULL COMMENT '军团维护的月矿堡备注',
    `create_user` bigint NOT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    `update_time` datetime DEFAULT NULL COMMENT '修改时间',
    `deleted` bigint NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_eve_moon_structure_note` (`tenant_id`, `corporation_ref_id`, `structure_id`, `deleted`),
    INDEX `idx_eve_moon_structure_note_page` (`tenant_id`, `corporation_ref_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EVE月矿堡长期备注';

INSERT INTO `eve_moon_structure_note`
    (`tenant_id`, `corporation_ref_id`, `structure_id`, `note`, `create_user`, `create_time`, `update_user`, `update_time`, `deleted`)
SELECT extraction.`tenant_id`, extraction.`corporation_ref_id`, extraction.`structure_id`, extraction.`note`,
       extraction.`create_user`, extraction.`create_time`, extraction.`update_user`, extraction.`update_time`, 0
FROM `eve_moon_extraction` AS extraction
INNER JOIN (
    SELECT `tenant_id`, `corporation_ref_id`, `structure_id`, MAX(`id`) AS `id`
    FROM `eve_moon_extraction`
    WHERE `deleted` = 0 AND `note` IS NOT NULL AND TRIM(`note`) <> ''
    GROUP BY `tenant_id`, `corporation_ref_id`, `structure_id`
) AS latest ON latest.`id` = extraction.`id`;
