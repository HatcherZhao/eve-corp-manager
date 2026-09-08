-- liquibase formatted sql

-- changeset eve-corp-manager:007-eve-review-fairness
-- comment 为国服权限后台复核增加任务认领与有限退避，避免持续失败记录饿死后续授权
ALTER TABLE `eve_authorization`
    ADD COLUMN `last_attempt_at` datetime DEFAULT NULL COMMENT '最近一次后台复核尝试时间' AFTER `last_verified_at`,
    ADD COLUMN `next_review_at` datetime DEFAULT NULL COMMENT '下一次允许后台复核时间' AFTER `last_attempt_at`,
    ADD INDEX `idx_eve_authorization_review` (`status`, `deleted`, `next_review_at`, `last_verified_at`, `id`),
    ALGORITHM=INPLACE,
    LOCK=NONE;
