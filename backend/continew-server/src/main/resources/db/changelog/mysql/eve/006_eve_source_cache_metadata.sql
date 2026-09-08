-- liquibase formatted sql

-- changeset eve-corp-manager:006-eve-source-cache-metadata
-- comment 记录 ESI 缓存过期时间是上游原值还是本地TTL估算值
ALTER TABLE `eve_character_role_snapshot`
    ADD COLUMN `source_expiry_estimated` bit(1) NOT NULL DEFAULT b'1'
        COMMENT '是否使用本地TTL估算缓存过期时间'
        AFTER `source_expires_at`;

