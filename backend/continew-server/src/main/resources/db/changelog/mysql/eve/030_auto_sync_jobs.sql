-- liquibase formatted sql

-- changeset eve-corp-manager:030-auto-sync-jobs
-- comment 持久化自动同步任务、领取租约、上游冷却和失败退避状态
CREATE TABLE `eve_sync_job`
(
    `id`                   bigint       NOT NULL COMMENT 'ID',
    `tenant_id`            bigint       NOT NULL COMMENT '租户 ID',
    `target_type`          varchar(20)  NOT NULL COMMENT '同步目标类型：CORPORATION、CHARACTER',
    `target_ref_id`        bigint       NOT NULL COMMENT '同步目标绑定记录 ID',
    `module`               varchar(40)  NOT NULL COMMENT '数据模块代码',
    `state`                varchar(20)  NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING、RUNNING、PAUSED',
    `next_run_at`          datetime     NOT NULL COMMENT '下次允许调度时间',
    `cooldown_until`       datetime     DEFAULT NULL COMMENT '手动同步也不得绕过的上游或失败冷却时间',
    `last_started_at`      datetime     DEFAULT NULL COMMENT '最近开始执行时间',
    `last_finished_at`     datetime     DEFAULT NULL COMMENT '最近结束执行时间',
    `consecutive_failures` int          NOT NULL DEFAULT 0 COMMENT '连续失败次数',
    `last_failure_code`    varchar(64)  DEFAULT NULL COMMENT '最近脱敏失败分类',
    `claim_token`          varchar(64)  DEFAULT NULL COMMENT '当前执行租约令牌',
    `claim_expires_at`     datetime     DEFAULT NULL COMMENT '当前执行租约过期时间',
    `create_user`          bigint       DEFAULT NULL COMMENT '创建人',
    `create_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user`          bigint       DEFAULT NULL COMMENT '修改人',
    `update_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`              tinyint      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_eve_sync_job_target_module` (`tenant_id`, `target_type`, `target_ref_id`, `module`),
    KEY `idx_eve_sync_job_due` (`state`, `next_run_at`, `id`),
    KEY `idx_eve_sync_job_expired_claim` (`state`, `claim_expires_at`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'EVE 自动同步任务';
