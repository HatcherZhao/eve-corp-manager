-- liquibase formatted sql

-- changeset eve-corp-manager:047-industry
-- comment 新增工业制造配置、蓝图静态数据与利润机会分析
CREATE TABLE `eve_industry_static_version`
(
    `id`               bigint       NOT NULL COMMENT '静态工业数据版本 ID',
    `source`           varchar(32)  NOT NULL COMMENT 'SERENITY_EXPORT、TRANQUILITY_SDE 或 CEVE_INDUSTRY_PUBLIC',
    `server_version`   varchar(32)  DEFAULT NULL COMMENT '国服版本号',
    `build_number`     bigint       DEFAULT NULL COMMENT '静态数据构建号',
    `downloaded_at`    datetime     NOT NULL COMMENT '导入时间',
    `sha256`           varchar(64)  DEFAULT NULL COMMENT '原始文件 SHA-256',
    `schema_version`   varchar(32)  DEFAULT NULL COMMENT '导入结构版本',
    `validation_status` varchar(24) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING、VALIDATED、REJECTED',
    `validation_note`  varchar(500) DEFAULT NULL COMMENT '校验说明',
    `create_user`      bigint       DEFAULT NULL,
    `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_user`      bigint       DEFAULT NULL,
    `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`          bigint       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_eve_industry_static_version_status` (`source`, `validation_status`, `downloaded_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '工业静态数据版本';

CREATE TABLE `eve_industry_blueprint`
(
    `id`                bigint         NOT NULL COMMENT '蓝图记录 ID',
    `static_version_id` bigint         NOT NULL COMMENT '静态数据版本 ID',
    `blueprint_type_id` int            NOT NULL COMMENT '蓝图类型 ID',
    `blueprint_name`    varchar(255)   NOT NULL COMMENT '蓝图名称',
    `activity`          varchar(32)    NOT NULL COMMENT 'MANUFACTURING、REACTION、INVENTION 等',
    `product_type_id`   int            NOT NULL COMMENT '主产物类型 ID',
    `product_name`      varchar(255)   NOT NULL COMMENT '主产物名称',
    `product_quantity`  decimal(20,6)  NOT NULL DEFAULT 1 COMMENT '每次活动产物数量',
    `base_time_seconds` decimal(20,6)  NOT NULL DEFAULT 0 COMMENT '每次活动基础秒数',
    `probability`       decimal(12,8)  DEFAULT NULL COMMENT '活动成功概率',
    `materials_json`    mediumtext     NOT NULL COMMENT '原材料 JSON',
    `products_json`     mediumtext     DEFAULT NULL COMMENT '产物 JSON',
    `source`            varchar(32)    NOT NULL COMMENT 'SERENITY_EXPORT、TRANQUILITY_SDE 或 CEVE_INDUSTRY_PUBLIC',
    `validation_status` varchar(24)    NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING、VALIDATED、REJECTED',
    `supported`         tinyint        NOT NULL DEFAULT 0 COMMENT '是否进入正式可执行计算',
    `create_user`       bigint         DEFAULT NULL,
    `create_time`       datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_user`       bigint         DEFAULT NULL,
    `update_time`       datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`           bigint         NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_eve_industry_blueprint_version_type_activity` (`static_version_id`, `blueprint_type_id`, `activity`, `deleted`),
    KEY `idx_eve_industry_blueprint_product` (`product_type_id`, `activity`, `supported`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '工业蓝图活动主数据';

CREATE TABLE `eve_industry_config`
(
    `id`                  bigint         NOT NULL COMMENT '生产配置 ID',
    `tenant_id`           bigint         NOT NULL COMMENT '租户 ID',
    `name`                varchar(80)    NOT NULL COMMENT '配置名称',
    `production_skill_json` mediumtext   NOT NULL COMMENT '生产技能等级 JSON',
    `market_fee_json`     mediumtext     NOT NULL COMMENT '市场费用 JSON',
    `target_days`         decimal(12,4)  NOT NULL DEFAULT 1 COMMENT '目标生产周期天数',
    `market_share_limit`  decimal(8,4)   NOT NULL DEFAULT 0.10 COMMENT '安全市场占比上限',
    `freshness_policy_json` mediumtext   DEFAULT NULL COMMENT '数据新鲜度门槛 JSON',
    `version`             int            NOT NULL DEFAULT 1 COMMENT '配置版本',
    `enabled`             tinyint        NOT NULL DEFAULT 1 COMMENT '是否启用',
    `create_user`         bigint         DEFAULT NULL,
    `create_time`         datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_user`         bigint         DEFAULT NULL,
    `update_time`         datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`             bigint         NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_eve_industry_config_name` (`tenant_id`, `name`, `deleted`),
    KEY `idx_eve_industry_config_enabled` (`tenant_id`, `enabled`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '工业生产配置';

CREATE TABLE `eve_industry_facility_profile`
(
    `id`                      bigint         NOT NULL COMMENT '设施配置 ID',
    `tenant_id`               bigint         NOT NULL COMMENT '租户 ID',
    `name`                    varchar(80)    NOT NULL COMMENT '设施配置名称',
    `reality_type`            varchar(16)    NOT NULL DEFAULT 'HYPOTHETICAL' COMMENT 'ACTUAL 或 HYPOTHETICAL',
    `system_id`               bigint         NOT NULL COMMENT '生产星系 ID',
    `system_name`             varchar(255)   DEFAULT NULL COMMENT '生产星系名称',
    `security_status`         decimal(6,3)   DEFAULT NULL COMMENT '生产星系安等',
    `facility_type_id`        int            DEFAULT NULL COMMENT '建筑或空间站类型 ID',
    `facility_name`           varchar(255)   DEFAULT NULL COMMENT '建筑名称',
    `supported_activities_json` mediumtext   NOT NULL COMMENT '支持的工业活动 JSON',
    `online_services_json`    mediumtext    DEFAULT NULL COMMENT '在线服务 JSON',
    `rig_type_ids_json`       mediumtext    DEFAULT NULL COMMENT 'Rig 类型 ID JSON',
    `facility_tax`            decimal(12,8)  NOT NULL DEFAULT 0 COMMENT '设施税率',
    `material_multiplier`     decimal(12,8)  NOT NULL DEFAULT 1 COMMENT '材料乘数',
    `time_multiplier`         decimal(12,8)  NOT NULL DEFAULT 1 COMMENT '时间乘数',
    `cost_multiplier`         decimal(12,8)  NOT NULL DEFAULT 1 COMMENT 'SCI 成本乘数',
    `manufacturing_sci`       decimal(12,8)  DEFAULT NULL COMMENT '制造 SCI',
    `reaction_sci`            decimal(12,8)  DEFAULT NULL COMMENT '反应 SCI',
    `source`                  varchar(24)    NOT NULL DEFAULT 'MANUAL' COMMENT 'ESI 或 MANUAL',
    `confirmed_at`            datetime       DEFAULT NULL COMMENT '人工确认时间',
    `enabled`                 tinyint        NOT NULL DEFAULT 1,
    `create_user`             bigint         DEFAULT NULL,
    `create_time`             datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_user`             bigint         DEFAULT NULL,
    `update_time`             datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`                 bigint         NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_eve_industry_facility_name` (`tenant_id`, `name`, `deleted`),
    KEY `idx_eve_industry_facility_system` (`tenant_id`, `system_id`, `enabled`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '工业生产设施配置';

CREATE TABLE `eve_industry_blueprint_override`
(
    `id`                  bigint         NOT NULL COMMENT '蓝图覆盖 ID',
    `tenant_id`           bigint         NOT NULL COMMENT '租户 ID',
    `blueprint_type_id`   int            NOT NULL COMMENT '蓝图类型 ID',
    `blueprint_kind`      varchar(8)     NOT NULL DEFAULT 'BPO' COMMENT 'BPO 或 BPC',
    `material_efficiency` int            NOT NULL DEFAULT 0 COMMENT 'ME 等级',
    `time_efficiency`     int            NOT NULL DEFAULT 0 COMMENT 'TE 等级',
    `remaining_runs`      int            DEFAULT NULL COMMENT 'BPC 剩余流程数，BPO 为 NULL',
    `source`              varchar(24)    NOT NULL DEFAULT 'MANUAL',
    `version`             int            NOT NULL DEFAULT 1,
    `create_user`         bigint         DEFAULT NULL,
    `create_time`         datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_user`         bigint         DEFAULT NULL,
    `update_time`         datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`             bigint         NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_eve_industry_blueprint_override` (`tenant_id`, `blueprint_type_id`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '租户蓝图研究等级覆盖';

CREATE TABLE `eve_industry_opportunity_snapshot`
(
    `id`                    bigint         NOT NULL COMMENT '机会快照 ID',
    `tenant_id`             bigint         NOT NULL COMMENT '租户 ID',
    `scan_id`               varchar(64)    NOT NULL COMMENT '扫描批次 ID',
    `blueprint_id`          bigint         NOT NULL COMMENT '蓝图 ID',
    `product_type_id`       int            NOT NULL COMMENT '产品类型 ID',
    `product_name`          varchar(255)   NOT NULL COMMENT '产品名称',
    `config_id`             bigint         NOT NULL COMMENT '生产配置 ID',
    `facility_id`           bigint         NOT NULL COMMENT '设施配置 ID',
    `status`                varchar(24)    NOT NULL COMMENT 'EXECUTABLE、TRIAL、UNAVAILABLE',
    `recommended_runs`      int            NOT NULL DEFAULT 0,
    `recommended_quantity`  bigint         NOT NULL DEFAULT 0,
    `material_cost`         decimal(20,2)  NOT NULL DEFAULT 0,
    `install_cost`          decimal(20,2)  NOT NULL DEFAULT 0,
    `immediate_revenue`     decimal(20,2)  NOT NULL DEFAULT 0,
    `sell_order_revenue`    decimal(20,2)  NOT NULL DEFAULT 0,
    `immediate_profit`      decimal(20,2)  NOT NULL DEFAULT 0,
    `sell_order_profit`     decimal(20,2)  NOT NULL DEFAULT 0,
    `margin`                decimal(12,6)  NOT NULL DEFAULT 0,
    `hourly_profit`         decimal(20,2)  NOT NULL DEFAULT 0,
    `daily_profit`          decimal(20,2)  NOT NULL DEFAULT 0,
    `liquidity_score`       decimal(8,4)   NOT NULL DEFAULT 0,
    `risk_score`            decimal(8,4)   NOT NULL DEFAULT 0,
    `risk_tags_json`        mediumtext     DEFAULT NULL,
    `assumptions_json`      mediumtext     DEFAULT NULL,
    `calculated_at`         datetime       NOT NULL,
    `create_user`           bigint         DEFAULT NULL,
    `create_time`           datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_user`           bigint         DEFAULT NULL,
    `update_time`           datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`               bigint         NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_eve_industry_opportunity_list` (`tenant_id`, `scan_id`, `status`, `daily_profit`, `deleted`),
    KEY `idx_eve_industry_opportunity_product` (`tenant_id`, `product_type_id`, `calculated_at`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '工业利润机会快照';

CREATE TABLE `eve_industry_opportunity_material`
(
    `id`                 bigint         NOT NULL COMMENT '机会材料 ID',
    `tenant_id`          bigint         NOT NULL COMMENT '租户 ID',
    `opportunity_id`     bigint         NOT NULL COMMENT '机会 ID',
    `material_type_id`   int            NOT NULL COMMENT '材料类型 ID',
    `material_name`      varchar(255)   DEFAULT NULL COMMENT '材料名称',
    `required_quantity`  bigint         NOT NULL DEFAULT 0,
    `purchase_cost`     decimal(20,2)  NOT NULL DEFAULT 0,
    `average_price`     decimal(20,6)  DEFAULT NULL,
    `create_time`       datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`       datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`           bigint         NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_eve_industry_opportunity_material` (`tenant_id`, `opportunity_id`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '工业机会材料采购明细';

INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (20100, '工业制造', 0, 1, '/eve/industry-directory', 'EveIndustryDirectory', 'Layout', '/eve/industry', 'tool', b'0', b'0', b'0', NULL, 13, 1, 1, NOW()),
    (20101, '生产机会', 20100, 2, '/eve/industry', 'EveIndustry', 'eve/industry/index', NULL, 'tool', b'0', b'0', b'0', 'eve:industry:view', 1, 1, 1, NOW()),
    (20102, '维护工业配置', 20100, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:industry:config:manage', 2, 1, 1, NOW()),
    (20103, '维护工业数据', 20100, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:industry:data:manage', 3, 1, 1, NOW());

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
VALUES (20001, 20100), (20001, 20101), (20001, 20102), (20001, 20103);

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin', 'corp_member')
  AND `role`.`deleted` = 0 AND `menu`.`id` IN (20100, 20101) AND `menu`.`deleted` = 0;

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin')
  AND `role`.`deleted` = 0 AND `menu`.`id` IN (20102, 20103) AND `menu`.`deleted` = 0;
