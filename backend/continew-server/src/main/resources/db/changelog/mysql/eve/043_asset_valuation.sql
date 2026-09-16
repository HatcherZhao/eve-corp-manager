-- liquibase formatted sql

-- changeset eve-corp-manager:043-asset-valuation
-- comment 为资产估值与市场报价优先级查询增加活动资产类型索引
CREATE INDEX `idx_eve_corporation_asset_quote_priority`
    ON `eve_corporation_asset` (`status`, `deleted`, `type_id`);
