-- liquibase formatted sql

-- changeset eve-corp-manager:015-asset-tree-structure
-- comment 为军团资产快照标记自有玩家建筑；该标记只在同步时由军团建筑清单确认，供资产树与普通太空物品区分
ALTER TABLE `eve_corporation_asset`
    ADD COLUMN `corporation_structure` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否为军团自有玩家建筑' AFTER `item_name`;
