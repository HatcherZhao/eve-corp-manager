-- liquibase formatted sql

-- changeset zhaoyuqing:064-market-quote-expiry
ALTER TABLE eve_market_snapshot
    ADD COLUMN quote_next_request_at DATETIME NULL COMMENT '官方订单响应缓存截止时间，到期后需重新校验' AFTER quote_synchronized_at,
    ADD COLUMN quote_validated_at DATETIME NULL COMMENT '最近一次成功确认报价可供业务批次使用的时间' AFTER quote_next_request_at;
