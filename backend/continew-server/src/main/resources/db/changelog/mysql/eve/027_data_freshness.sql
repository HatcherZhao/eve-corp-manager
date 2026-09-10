-- liquibase formatted sql

-- changeset eve-corp-manager:027-data-freshness
-- comment 统一记录军团数据模块的最近成功同步、上游有效期与脱敏失败分类
CREATE TABLE `eve_data_sync_status`
(
    `id`                 bigint       NOT NULL COMMENT 'ID',
    `tenant_id`          bigint       NOT NULL COMMENT '租户 ID',
    `corporation_ref_id` bigint       NOT NULL COMMENT '军团绑定记录 ID',
    `module`             varchar(40)  NOT NULL COMMENT '数据模块代码',
    `last_successful_at` datetime     DEFAULT NULL COMMENT '最近成功发布完整快照时间',
    `source_expires_at`  datetime     DEFAULT NULL COMMENT '上游缓存过期时间',
    `last_failure_at`    datetime     DEFAULT NULL COMMENT '最近同步失败时间',
    `failure_code`       varchar(64)  DEFAULT NULL COMMENT '脱敏失败分类',
    `create_user`        bigint       DEFAULT NULL COMMENT '创建人',
    `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user`        bigint       DEFAULT NULL COMMENT '修改人',
    `update_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`            tinyint      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_eve_data_sync_status_corporation_module` (`corporation_ref_id`, `module`),
    KEY `idx_eve_data_sync_status_tenant_corporation` (`tenant_id`, `corporation_ref_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'EVE 数据模块同步健康状态';
