-- liquibase formatted sql

-- changeset eve-corp-manager:034-static-location-tree-indexes
-- comment 为星域、星座、星系导航树的下级位置查询建立索引，避免静态资料增长后全表扫描
ALTER TABLE `eve_static_location_reference`
    ADD KEY `idx_eve_static_location_reference_region_id` (`region_id`),
    ADD KEY `idx_eve_static_location_reference_constellation_id` (`constellation_id`),
    ADD KEY `idx_eve_static_location_reference_solar_system_id` (`solar_system_id`);
