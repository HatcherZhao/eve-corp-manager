-- liquibase formatted sql

-- changeset eve-corp-manager:044-star-map
-- comment 新增国服宇宙星图拓扑、军团运营标注与航线
CREATE TABLE `eve_universe_system`
(
    `system_id`          bigint       NOT NULL COMMENT '游戏星系 ID',
    `constellation_id`   bigint       DEFAULT NULL COMMENT '所属星座 ID',
    `region_id`          bigint       DEFAULT NULL COMMENT '所属星域 ID，由静态位置资料关联',
    `security_status`    decimal(6,3) DEFAULT NULL COMMENT '国服安全等级',
    `position_x`         decimal(30,3) DEFAULT NULL COMMENT '官方三维 X 坐标',
    `position_y`         decimal(30,3) DEFAULT NULL COMMENT '官方三维 Y 坐标',
    `position_z`         decimal(30,3) DEFAULT NULL COMMENT '官方三维 Z 坐标',
    `stargate_count`     int          NOT NULL DEFAULT 0 COMMENT '该星系声明的星门数量',
    `source_expires_at`  datetime     DEFAULT NULL COMMENT '国服缓存过期时间',
    `synchronized_at`    datetime     DEFAULT NULL COMMENT '最近成功同步时间',
    `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`system_id`),
    KEY `idx_eve_universe_system_region` (`region_id`, `constellation_id`),
    KEY `idx_eve_universe_system_sync` (`synchronized_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'EVE 国服公开星系星图快照';

CREATE TABLE `eve_universe_stargate`
(
    `stargate_id`             bigint       NOT NULL COMMENT '游戏星门 ID',
    `system_id`               bigint       NOT NULL COMMENT '星门所属星系 ID',
    `destination_stargate_id` bigint       NOT NULL COMMENT '对端星门 ID',
    `destination_system_id`   bigint       NOT NULL COMMENT '对端星系 ID',
    `type_id`                 int          DEFAULT NULL COMMENT '星门类型 ID',
    `position_x`              decimal(30,3) DEFAULT NULL COMMENT '官方星门 X 坐标',
    `position_y`              decimal(30,3) DEFAULT NULL COMMENT '官方星门 Y 坐标',
    `position_z`              decimal(30,3) DEFAULT NULL COMMENT '官方星门 Z 坐标',
    `source_expires_at`       datetime     DEFAULT NULL COMMENT '国服缓存过期时间',
    `synchronized_at`         datetime     NOT NULL COMMENT '最近成功同步时间',
    `create_time`             datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`             datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`stargate_id`),
    KEY `idx_eve_universe_stargate_system` (`system_id`, `destination_system_id`),
    KEY `idx_eve_universe_stargate_destination` (`destination_system_id`, `system_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'EVE 国服公开星门拓扑快照';

CREATE TABLE `eve_universe_sync_state`
(
    `sync_key`          varchar(32)  NOT NULL COMMENT '固定同步状态键',
    `expected_count`    int          NOT NULL DEFAULT 0 COMMENT '目录声明的总数量',
    `completed_count`   int          NOT NULL DEFAULT 0 COMMENT '已成功同步数量',
    `last_success_at`   datetime     DEFAULT NULL COMMENT '最近成功时间',
    `last_failure_at`   datetime     DEFAULT NULL COMMENT '最近失败时间',
    `failure_code`      varchar(64)  DEFAULT NULL COMMENT '脱敏失败分类',
    `next_retry_at`     datetime     DEFAULT NULL COMMENT '下次可重试时间',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`sync_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'EVE 公开宇宙拓扑同步状态';

CREATE TABLE `eve_starmap_annotation`
(
    `id`              bigint       NOT NULL COMMENT 'ID',
    `tenant_id`       bigint       NOT NULL COMMENT '租户 ID',
    `solar_system_id` bigint       NOT NULL COMMENT '标注所在星系 ID',
    `category`        varchar(32) NOT NULL COMMENT '标注分类',
    `title`           varchar(80) NOT NULL COMMENT '标注标题',
    `note`            varchar(1000) DEFAULT NULL COMMENT '标注说明',
    `color_key`       varchar(24) NOT NULL DEFAULT 'blue' COMMENT '预设颜色键',
    `expires_at`      datetime     DEFAULT NULL COMMENT '到期隐藏时间',
    `archived`        tinyint      NOT NULL DEFAULT 0 COMMENT '是否归档',
    `create_user`     bigint       NOT NULL COMMENT '创建人',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user`     bigint       DEFAULT NULL COMMENT '修改人',
    `update_time`     datetime     DEFAULT NULL COMMENT '修改时间',
    `deleted`         bigint       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_eve_starmap_annotation` (`tenant_id`, `solar_system_id`, `title`, `deleted`),
    KEY `idx_eve_starmap_annotation_active` (`tenant_id`, `archived`, `expires_at`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'EVE 军团星图运营标注';

CREATE TABLE `eve_starmap_route`
(
    `id`                    bigint       NOT NULL COMMENT 'ID',
    `tenant_id`             bigint       NOT NULL COMMENT '租户 ID',
    `title`                 varchar(80)  NOT NULL COMMENT '航线名称',
    `description`           varchar(1000) DEFAULT NULL COMMENT '航线说明',
    `origin_system_id`      bigint       NOT NULL COMMENT '起点星系 ID',
    `destination_system_id` bigint       NOT NULL COMMENT '终点星系 ID',
    `jump_count`            int          NOT NULL COMMENT '国服计算跳数',
    `validated_at`          datetime     NOT NULL COMMENT '最近按国服路线验证时间',
    `archived`              tinyint      NOT NULL DEFAULT 0 COMMENT '是否归档',
    `create_user`           bigint       NOT NULL COMMENT '创建人',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user`           bigint       DEFAULT NULL COMMENT '修改人',
    `update_time`           datetime     DEFAULT NULL COMMENT '修改时间',
    `deleted`               bigint       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_eve_starmap_route_title` (`tenant_id`, `title`, `deleted`),
    KEY `idx_eve_starmap_route_list` (`tenant_id`, `archived`, `validated_at`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'EVE 军团保存航线';

CREATE TABLE `eve_starmap_route_point`
(
    `id`              bigint       NOT NULL COMMENT 'ID',
    `tenant_id`       bigint       NOT NULL COMMENT '租户 ID',
    `route_id`        bigint       NOT NULL COMMENT '航线 ID',
    `point_order`     int          NOT NULL COMMENT '在最终官方路线中的顺序',
    `solar_system_id` bigint       NOT NULL COMMENT '星系 ID',
    `point_kind`      varchar(16) NOT NULL COMMENT 'ORIGIN、VIA、DESTINATION 或 COMPUTED',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`         bigint       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_eve_starmap_route_point` (`route_id`, `point_order`, `deleted`),
    KEY `idx_eve_starmap_route_point_system` (`tenant_id`, `solar_system_id`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'EVE 军团航线节点';

INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (20093, '星图与导航', 0, 1, '/eve/starmap-directory', 'EveStarMapDirectory', 'Layout', '/eve/starmap', 'public', b'0', b'0', b'0', NULL, 12, 1, 1, NOW()),
    (20094, '军团星图', 20093, 2, '/eve/starmap', 'EveStarMap', 'eve/starmap/index', NULL, 'public', b'0', b'0', b'0', 'eve:starmap:view', 1, 1, 1, NOW()),
    (20095, '航线库', 20093, 2, '/eve/starmap/routes', 'EveStarMapRoutes', 'eve/starmap/index', NULL, 'route', b'0', b'0', b'0', 'eve:starmap:view', 2, 1, 1, NOW()),
    (20096, '维护星图运营标注', 20093, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:starmap:annotation:manage', 3, 1, 1, NOW()),
    (20097, '维护军团航线', 20093, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:starmap:route:manage', 4, 1, 1, NOW());

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
VALUES (20001, 20093), (20001, 20094), (20001, 20095), (20001, 20096), (20001, 20097);

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin', 'corp_member')
  AND `role`.`deleted` = 0 AND `menu`.`id` IN (20093, 20094, 20095) AND `menu`.`deleted` = 0;

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin')
  AND `role`.`deleted` = 0 AND `menu`.`id` IN (20096, 20097) AND `menu`.`deleted` = 0;
