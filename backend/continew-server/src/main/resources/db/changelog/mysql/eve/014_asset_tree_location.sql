-- liquibase formatted sql

-- changeset eve-corp-manager:014-asset-tree-location
-- comment 为军团资产快照保存空间站或建筑所属星系，支持稳定构建星系至仓库的资产树
ALTER TABLE `eve_corporation_asset`
    ADD COLUMN `solar_system_id` bigint DEFAULT NULL COMMENT '顶层空间站或建筑所属星系ID' AFTER `location_name`,
    ADD COLUMN `solar_system_name` varchar(255) DEFAULT NULL COMMENT '顶层空间站或建筑所属星系名称快照' AFTER `solar_system_id`;

CREATE INDEX `idx_eve_corporation_asset_location_tree`
    ON `eve_corporation_asset` (`tenant_id`, `corporation_ref_id`, `status`, `solar_system_id`, `location_id`);
