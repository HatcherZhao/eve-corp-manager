-- liquibase formatted sql

-- changeset zhaoyuqing:063-official-type-detail-cache
ALTER TABLE eve_static_type_reference
    ADD COLUMN official_detail_updated_at DATETIME NULL COMMENT '最近成功读取晨曦官方类型详情的时间' AFTER official_directory;
