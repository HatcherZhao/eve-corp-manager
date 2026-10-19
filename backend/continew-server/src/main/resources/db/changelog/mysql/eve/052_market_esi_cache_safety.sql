-- liquibase formatted sql

-- changeset eve-corp-manager:052-market-esi-cache-safety
-- comment 标记区域历史来源并保存官方缓存与失败退避期限，旧历史保留但不按官方日均价展示
ALTER TABLE `eve_market_snapshot`
    ADD COLUMN `history_source` varchar(64) DEFAULT NULL COMMENT '区域历史来源及版本',
    ADD COLUMN `history_next_request_at` datetime DEFAULT NULL COMMENT '历史下次允许请求时间';
