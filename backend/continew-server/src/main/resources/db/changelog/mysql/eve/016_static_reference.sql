-- liquibase formatted sql

-- changeset eve-corp-manager:016-static-reference
-- comment 将 evedata.xlsx 的类型、星系、空间站和公开建筑资料持久化，供名称解析与资产分类统一使用
CREATE TABLE `eve_static_type_reference`
(
    `id`                 bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `type_id`            int unsigned NOT NULL COMMENT '游戏类型 ID',
    `type_name`          varchar(255) NOT NULL COMMENT '中文类型名称',
    `type_description`   mediumtext NULL COMMENT '类型说明',
    `market_category_l1` varchar(128) NOT NULL DEFAULT '' COMMENT '第一市场分类',
    `market_category_l2` varchar(128) NOT NULL DEFAULT '' COMMENT '第二市场分类',
    `market_category_l3` varchar(128) NOT NULL DEFAULT '' COMMENT '第三市场分类',
    `market_category_l4` varchar(128) NOT NULL DEFAULT '' COMMENT '第四市场分类',
    `market_category_l5` varchar(128) NOT NULL DEFAULT '' COMMENT '第五市场分类',
    `market_category_l6` varchar(128) NOT NULL DEFAULT '' COMMENT '第六市场分类',
    `source_updated_at`  datetime NOT NULL COMMENT '来源资料更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_eve_static_type_reference_type_id` (`type_id`),
    KEY `idx_eve_static_type_reference_market_l1` (`market_category_l1`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='EVE 静态类型资料';

CREATE TABLE `eve_static_location_reference`
(
    `id`                 bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `reference_type`     varchar(32)  NOT NULL COMMENT '资料类型：REGION、CONSTELLATION、SOLAR_SYSTEM、NPC_STATION、PUBLIC_STRUCTURE',
    `reference_id`       bigint unsigned NOT NULL COMMENT '游戏位置 ID',
    `reference_name`     varchar(512) NOT NULL COMMENT '中文位置名称',
    `solar_system_id`    bigint unsigned NULL COMMENT '所属星系 ID',
    `constellation_id`   bigint unsigned NULL COMMENT '所属星座 ID',
    `region_id`          bigint unsigned NULL COMMENT '所属星域 ID',
    `security_status`    decimal(6, 3) NULL COMMENT '安全等级',
    `source_updated_at`  datetime NOT NULL COMMENT '来源资料更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_eve_static_location_reference_kind_id` (`reference_type`, `reference_id`),
    KEY `idx_eve_static_location_reference_id` (`reference_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='EVE 静态位置资料';

CREATE TABLE `eve_static_reference_import`
(
    `reference_name`     varchar(64)  NOT NULL COMMENT '资料集名称',
    `source_file_name`   varchar(255) NOT NULL COMMENT '来源文件名',
    `source_sha256`      char(64)     NOT NULL COMMENT '来源文件 SHA-256',
    `source_updated_at`  datetime     NOT NULL COMMENT '来源文件修改时间',
    `imported_at`        datetime     NOT NULL COMMENT '导入完成时间',
    `record_count`       int unsigned NOT NULL COMMENT '本次写入记录数',
    PRIMARY KEY (`reference_name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='EVE 静态资料导入记录';
