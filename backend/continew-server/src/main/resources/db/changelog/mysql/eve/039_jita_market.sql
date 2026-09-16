-- liquibase formatted sql

-- changeset eve-corp-manager:039-jita-market
-- comment 新增以吉他贸易中心为固定口径的公开行情缓存与市场菜单
CREATE TABLE `eve_market_snapshot`
(
    `type_id`                int            NOT NULL COMMENT '游戏物品类型ID',
    `highest_buy_price`      decimal(20, 2) DEFAULT NULL COMMENT '吉他最高求购价',
    `lowest_sell_price`      decimal(20, 2) DEFAULT NULL COMMENT '吉他最低卖出价',
    `buy_volume`             bigint         DEFAULT NULL COMMENT '吉他求购总量',
    `sell_volume`            bigint         DEFAULT NULL COMMENT '吉他卖出总量',
    `source_updated_at`      datetime       DEFAULT NULL COMMENT '上游报价读取时间',
    `quote_synchronized_at`  datetime       DEFAULT NULL COMMENT '最近成功刷新报价时间',
    `detail_synchronized_at` datetime       DEFAULT NULL COMMENT '最近成功刷新订单和历史时间',
    `buy_orders_json`        mediumtext     DEFAULT NULL COMMENT '最近买单快照JSON',
    `sell_orders_json`       mediumtext     DEFAULT NULL COMMENT '最近卖单快照JSON',
    `history_json`           mediumtext     DEFAULT NULL COMMENT '最近每日价格历史JSON',
    `last_failure_message`   varchar(500)   DEFAULT NULL COMMENT '最近上游失败摘要',
    `last_accessed_at`       datetime       DEFAULT NULL COMMENT '最近一次站内查看详情时间',
    `create_time`            datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`            datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`type_id`),
    KEY `idx_eve_market_snapshot_recent` (`last_accessed_at`, `detail_synchronized_at`),
    KEY `idx_eve_market_snapshot_quote` (`quote_synchronized_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '吉他贸易中心公开市场缓存快照';

-- 将市场作为独立一级菜单组，成员可查看，CEO和总监可主动刷新单品详情。
INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (20090, '市场与估价', 0, 1, '/eve/market-directory', 'EveMarketDirectory', 'Layout', '/eve/market', 'shopping', b'0', b'0', b'0', NULL, 11, 1, 1, NOW()),
    (20091, '吉他市场', 20090, 2, '/eve/market', 'EveMarket', 'eve/market/index', NULL, 'shopping', b'0', b'0', b'0', 'eve:market:view', 1, 1, 1, NOW()),
    (20092, '刷新吉他行情', 20090, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:market:sync', 2, 1, 1, NOW());

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
VALUES (20001, 20090), (20001, 20091), (20001, 20092);

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin', 'corp_member')
  AND `role`.`deleted` = 0
  AND `menu`.`id` IN (20090, 20091)
  AND `menu`.`deleted` = 0;

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin')
  AND `role`.`deleted` = 0
  AND `menu`.`id` = 20092
  AND `menu`.`deleted` = 0;
