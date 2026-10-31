-- liquibase formatted sql

-- changeset zhaoyuqing:061-market-refresh-queue
CREATE TABLE IF NOT EXISTS eve_market_refresh_queue (
    type_id INT NOT NULL COMMENT '物品类型 ID',
    include_history TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否同时刷新官方区域历史',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT '连续失败次数',
    task_version BIGINT NOT NULL DEFAULT 0 COMMENT '任务版本，防止处理期间升级请求被删除',
    next_attempt_at DATETIME NOT NULL COMMENT '下次允许执行时间',
    last_error VARCHAR(255) NULL COMMENT '最近失败摘要',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (type_id),
    KEY idx_market_refresh_due (next_attempt_at, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按需市场行情刷新队列';
