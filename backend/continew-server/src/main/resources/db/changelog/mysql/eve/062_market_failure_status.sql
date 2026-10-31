-- liquibase formatted sql

-- changeset zhaoyuqing:062-market-failure-status
ALTER TABLE eve_market_snapshot
    ADD COLUMN order_failure_message VARCHAR(500) NULL COMMENT '最近订单查询失败摘要' AFTER last_failure_message,
    ADD COLUMN order_failure_at DATETIME NULL COMMENT '最近订单查询失败时间' AFTER order_failure_message,
    ADD COLUMN history_failure_message VARCHAR(500) NULL COMMENT '最近历史查询失败摘要' AFTER order_failure_at,
    ADD COLUMN history_failure_at DATETIME NULL COMMENT '最近历史查询失败时间' AFTER history_failure_message;
