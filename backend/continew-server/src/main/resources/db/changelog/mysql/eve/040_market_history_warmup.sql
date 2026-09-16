-- liquibase formatted sql

-- changeset eve-corp-manager:040-market-history-warmup
-- comment 为吉他市场历史日线增加独立同步时间，支持后台渐进补齐
ALTER TABLE `eve_market_snapshot`
    ADD COLUMN `history_synchronized_at` datetime DEFAULT NULL COMMENT '最近成功刷新价格历史时间' AFTER `detail_synchronized_at`,
    ADD KEY `idx_eve_market_snapshot_history` (`history_synchronized_at`);
