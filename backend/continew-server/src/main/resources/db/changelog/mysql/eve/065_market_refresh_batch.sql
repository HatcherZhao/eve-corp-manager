-- liquibase formatted sql

-- changeset zhaoyuqing:065-market-refresh-batch
CREATE TABLE IF NOT EXISTS eve_market_refresh_batch (
    batch_key VARCHAR(64) NOT NULL COMMENT '排序后的物品集合与历史需求生成的批次键',
    include_history TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否要求区域成交历史',
    type_count INT NOT NULL COMMENT '本批次物品类型数量',
    requested_at DATETIME NOT NULL COMMENT '本轮批次要求行情不早于该时间',
    completed_at DATETIME NULL COMMENT '全部物品满足本轮要求的时间',
    create_time DATETIME NOT NULL COMMENT '首次创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (batch_key),
    KEY idx_market_refresh_batch_completed (completed_at, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按需市场行情刷新批次';
