-- liquibase formatted sql

-- changeset eve-corp-manager:050-industry-snapshots
-- comment 固化工业计算使用的市场订单、星系 SCI 与设施事实快照
ALTER TABLE `eve_industry_opportunity_snapshot`
    ADD COLUMN `static_version_id` bigint DEFAULT NULL COMMENT '静态工业数据版本 ID' AFTER `blueprint_id`,
    ADD COLUMN `config_version` int DEFAULT NULL COMMENT '生产配置版本' AFTER `config_id`,
    ADD COLUMN `blueprint_override_version` int DEFAULT NULL COMMENT '蓝图研究覆盖版本' AFTER `facility_id`,
    ADD COLUMN `market_snapshot_id` bigint DEFAULT NULL COMMENT '市场输入快照 ID' AFTER `blueprint_override_version`,
    ADD COLUMN `system_snapshot_id` bigint DEFAULT NULL COMMENT '星系事实快照 ID' AFTER `market_snapshot_id`,
    ADD COLUMN `rule_version` varchar(32) DEFAULT NULL COMMENT '计算规则版本' AFTER `system_snapshot_id`,
    ADD COLUMN `input_snapshot_json` mediumtext DEFAULT NULL COMMENT '工业计算输入 JSON' AFTER `assumptions_json`;

CREATE TABLE `eve_industry_market_input_snapshot`
(
    `id` bigint NOT NULL COMMENT '市场输入快照 ID',
    `tenant_id` bigint NOT NULL COMMENT '租户 ID',
    `snapshot_key` varchar(64) NOT NULL COMMENT '快照批次或计算上下文标识',
    `market_region_id` bigint NOT NULL DEFAULT 10000002 COMMENT '市场区域 ID，国服吉他海四',
    `market_station_id` bigint NOT NULL DEFAULT 60003760 COMMENT '市场空间站 ID，吉他 4-4',
    `captured_at` datetime NOT NULL COMMENT '快照采集时间',
    `source_updated_at` datetime DEFAULT NULL COMMENT '上游报价时间',
    `source` varchar(32) NOT NULL DEFAULT 'JITA_4_4' COMMENT '市场来源',
    `quote_json` mediumtext NOT NULL COMMENT '报价与成交统计 JSON',
    `freshness_status` varchar(16) NOT NULL DEFAULT 'FRESH' COMMENT 'FRESH、STALE、FAILED',
    `rule_version` varchar(32) DEFAULT NULL COMMENT '计算规则版本',
    `create_user` bigint DEFAULT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_user` bigint DEFAULT NULL,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_eve_industry_market_input_snapshot` (`tenant_id`, `snapshot_key`, `deleted`),
    KEY `idx_eve_industry_market_input_time` (`tenant_id`, `captured_at`, `freshness_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工业计算市场输入快照';

CREATE TABLE `eve_industry_market_order_slice`
(
    `id` bigint NOT NULL COMMENT '订单档位快照 ID',
    `tenant_id` bigint NOT NULL COMMENT '租户 ID',
    `snapshot_id` bigint NOT NULL COMMENT '市场输入快照 ID',
    `type_id` int NOT NULL COMMENT '物品类型 ID',
    `side` varchar(8) NOT NULL COMMENT 'BUY 或 SELL',
    `price` decimal(20,6) NOT NULL COMMENT '订单单价',
    `quantity` bigint NOT NULL COMMENT '该档可用数量',
    `cumulative_quantity` bigint NOT NULL DEFAULT 0 COMMENT '累计可用数量',
    `source_order_id` bigint DEFAULT NULL COMMENT '上游订单 ID',
    `source_updated_at` datetime DEFAULT NULL COMMENT '订单更新时间',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_eve_industry_market_slice` (`tenant_id`, `snapshot_id`, `type_id`, `side`, `price`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工业计算市场订单档位切片';

CREATE TABLE `eve_industry_system_snapshot`
(
    `id` bigint NOT NULL COMMENT '星系事实快照 ID',
    `tenant_id` bigint NOT NULL COMMENT '租户 ID',
    `snapshot_key` varchar(64) NOT NULL COMMENT '快照批次或计算上下文标识',
    `system_id` bigint NOT NULL COMMENT '太阳系 ID',
    `system_name` varchar(255) DEFAULT NULL COMMENT '太阳系名称',
    `security_status` decimal(6,3) DEFAULT NULL COMMENT '安等',
    `manufacturing_sci` decimal(12,8) DEFAULT NULL COMMENT '制造 SCI',
    `reaction_sci` decimal(12,8) DEFAULT NULL COMMENT '反应 SCI',
    `facility_json` mediumtext DEFAULT NULL COMMENT '设施服务与 Rig 快照 JSON',
    `source` varchar(24) NOT NULL DEFAULT 'SERENITY_ESI',
    `captured_at` datetime NOT NULL COMMENT '事实采集时间',
    `expires_at` datetime DEFAULT NULL COMMENT '事实过期时间',
    `create_user` bigint DEFAULT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_user` bigint DEFAULT NULL,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_eve_industry_system_snapshot` (`tenant_id`, `snapshot_key`, `system_id`, `deleted`),
    KEY `idx_eve_industry_system_snapshot_freshness` (`tenant_id`, `system_id`, `captured_at`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工业计算星系与设施事实快照';
