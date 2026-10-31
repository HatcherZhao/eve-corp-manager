-- liquibase formatted sql

-- changeset eve-corp-manager:060-official-type-reference
-- comment 增加晨曦官方物品目录可续跑暂存表，完整构建后原子发布类型资料
CREATE TABLE `eve_static_type_sync_state`
(
    `id`                tinyint unsigned NOT NULL COMMENT '固定主键 1',
    `phase`             varchar(32) NOT NULL COMMENT '同步阶段：TYPE_IDS、MARKET_GROUPS、NAMES、PUBLISH、READY',
    `current_page`      int unsigned NOT NULL DEFAULT 1 COMMENT '当前类型目录页码',
    `total_pages`       int unsigned NOT NULL DEFAULT 0 COMMENT '类型目录总页数',
    `started_at`        datetime NULL COMMENT '本轮开始时间',
    `completed_at`      datetime NULL COMMENT '最近完整发布时间',
    `last_error`        varchar(512) NULL COMMENT '最近失败摘要',
    `update_time`       datetime NOT NULL COMMENT '状态更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='晨曦官方物品目录同步状态';

INSERT INTO `eve_static_type_sync_state` (`id`, `phase`, `current_page`, `total_pages`, `update_time`)
VALUES (1, 'TYPE_IDS', 1, 0, NOW());

CREATE TABLE `eve_static_type_stage`
(
    `type_id`            int unsigned NOT NULL COMMENT '游戏类型 ID',
    `type_name`          varchar(255) NULL COMMENT '官方中文名称',
    `type_description`   mediumtext NULL COMMENT '按需读取的官方中文说明',
    `name_attempts`      tinyint unsigned NOT NULL DEFAULT 0 COMMENT '官方批量名称解析尝试次数',
    `market_category_l1` varchar(128) NOT NULL DEFAULT '' COMMENT '第一市场分类',
    `market_category_l2` varchar(128) NOT NULL DEFAULT '' COMMENT '第二市场分类',
    `market_category_l3` varchar(128) NOT NULL DEFAULT '' COMMENT '第三市场分类',
    `market_category_l4` varchar(128) NOT NULL DEFAULT '' COMMENT '第四市场分类',
    `market_category_l5` varchar(128) NOT NULL DEFAULT '' COMMENT '第五市场分类',
    `market_category_l6` varchar(128) NOT NULL DEFAULT '' COMMENT '第六市场分类',
    PRIMARY KEY (`type_id`),
    KEY `idx_eve_static_type_stage_name` (`type_name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='晨曦官方物品目录构建暂存';

CREATE TABLE `eve_static_market_group_stage`
(
    `market_group_id` int unsigned NOT NULL COMMENT '市场组 ID',
    `parent_group_id` int unsigned NULL COMMENT '上级市场组 ID',
    `group_name`      varchar(255) NULL COMMENT '官方中文名称',
    `type_ids_json`   mediumtext NULL COMMENT '直接归属该市场组的类型 ID',
    `loaded`          tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已读取详情',
    PRIMARY KEY (`market_group_id`),
    KEY `idx_eve_static_market_group_stage_loaded` (`loaded`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='晨曦官方市场分类构建暂存';

ALTER TABLE `eve_static_type_reference`
    ADD COLUMN `official_directory` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否存在于最近完整晨曦官方类型目录' AFTER `source_updated_at`,
    ADD KEY `idx_eve_static_type_reference_official` (`official_directory`);
