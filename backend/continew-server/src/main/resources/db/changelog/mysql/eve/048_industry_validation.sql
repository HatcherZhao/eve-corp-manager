-- liquibase formatted sql

-- changeset eve-corp-manager:048-industry-validation
-- comment 新增工业静态数据校验、Golden Case 与星系成本指数缓存
ALTER TABLE `eve_industry_facility_profile`
    ADD COLUMN `versioned_surcharge` decimal(12,8) NOT NULL DEFAULT 0 COMMENT '版本化工业附加费率' AFTER `facility_tax`;

CREATE TABLE `eve_industry_system_cost_index`
(
    `id`             bigint         NOT NULL COMMENT '成本指数快照 ID',
    `system_id`      bigint         NOT NULL COMMENT '太阳系 ID',
    `activity`       varchar(32)    NOT NULL COMMENT '工业活动类型',
    `cost_index`     decimal(12,8)  NOT NULL COMMENT '星系活动成本指数',
    `source`         varchar(24)    NOT NULL DEFAULT 'SERENITY_ESI' COMMENT '数据来源',
    `etag`           varchar(255)   DEFAULT NULL COMMENT 'ESI ETag',
    `fetched_at`     datetime       NOT NULL COMMENT '抓取时间',
    `expires_at`     datetime       DEFAULT NULL COMMENT 'ESI 建议过期时间',
    `create_user`    bigint         DEFAULT NULL,
    `create_time`    datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_user`    bigint         DEFAULT NULL,
    `update_time`    datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        bigint         NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_eve_industry_sci_system_activity` (`system_id`, `activity`, `deleted`),
    KEY `idx_eve_industry_sci_freshness` (`fetched_at`, `expires_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '国服星系工业成本指数缓存';

CREATE TABLE `eve_industry_golden_case`
(
    `id`                        bigint         NOT NULL COMMENT 'Golden Case ID',
    `tenant_id`                 bigint         NOT NULL COMMENT '租户 ID',
    `name`                      varchar(120)   NOT NULL COMMENT '样例名称',
    `blueprint_id`              bigint         NOT NULL COMMENT '蓝图活动记录 ID',
    `static_version_id`         bigint         NOT NULL COMMENT '静态工业数据版本 ID',
    `config_id`                 bigint         NOT NULL COMMENT '生产技能与费用配置 ID',
    `facility_id`               bigint         NOT NULL COMMENT '生产设施配置 ID',
    `runs`                      int            NOT NULL COMMENT '任务流程数',
    `material_efficiency`       int            NOT NULL DEFAULT 0 COMMENT '蓝图 ME',
    `time_efficiency`           int            NOT NULL DEFAULT 0 COMMENT '蓝图 TE',
    `input_snapshot_json`       mediumtext     NOT NULL COMMENT '技能、设施、SCI、调整价格等输入快照',
    `expected_materials_json`   mediumtext     NOT NULL COMMENT '游戏内实际材料数量 JSON',
    `expected_time_seconds`     decimal(20,6)  NOT NULL COMMENT '游戏内实际任务时长秒数',
    `expected_install_cost`     decimal(20,2)  NOT NULL COMMENT '游戏内实际安装费',
    `calculated_materials_json` mediumtext     DEFAULT NULL COMMENT '系统计算材料数量 JSON',
    `calculated_time_seconds`   decimal(20,6)  DEFAULT NULL COMMENT '系统计算任务时长秒数',
    `calculated_install_cost`   decimal(20,2)  DEFAULT NULL COMMENT '系统计算安装费',
    `material_tolerance`        bigint         NOT NULL DEFAULT 0 COMMENT '单项材料数量容差',
    `time_tolerance_seconds`    decimal(20,6)  NOT NULL DEFAULT 1 COMMENT '时间容差秒数',
    `install_cost_tolerance`    decimal(20,2)  NOT NULL DEFAULT 1 COMMENT '安装费容差',
    `validation_status`         varchar(16)    NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING、PASSED、FAILED',
    `validation_detail_json`    mediumtext     DEFAULT NULL COMMENT '逐项差异与失败原因',
    `validated_at`              datetime       DEFAULT NULL COMMENT '最近校验时间',
    `create_user`               bigint         DEFAULT NULL,
    `create_time`               datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_user`               bigint         DEFAULT NULL,
    `update_time`               datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`                   bigint         NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_eve_industry_golden_case_blueprint` (`tenant_id`, `blueprint_id`, `validation_status`, `deleted`),
    KEY `idx_eve_industry_golden_case_version` (`static_version_id`, `validation_status`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '国服工业 Golden Case';

