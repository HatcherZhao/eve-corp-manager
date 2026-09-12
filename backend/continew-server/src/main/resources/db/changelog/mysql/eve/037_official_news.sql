-- liquibase formatted sql

-- changeset eve-corp-manager:037-official-news
-- comment 同步网易 EVE 国服官网的公开新闻活动、版本更新及抓取状态
CREATE TABLE `eve_official_news`
(
    `id`                      bigint       NOT NULL COMMENT 'ID',
    `source_code`             varchar(32)  NOT NULL COMMENT '官网栏目编码',
    `source_category`         varchar(64)  NOT NULL COMMENT '官网栏目名称',
    `title`                   varchar(500) NOT NULL COMMENT '资讯标题',
    `summary`                 varchar(2000) DEFAULT NULL COMMENT '官网列表摘要',
    `original_url`            varchar(1000) NOT NULL COMMENT '官网原文链接',
    `content_html`            mediumtext   DEFAULT NULL COMMENT '净化后的官网正文 HTML',
    `content_text`            mediumtext   DEFAULT NULL COMMENT '官网正文纯文本',
    `cover_url`               varchar(1000) DEFAULT NULL COMMENT '首张正文图片官网链接',
    `published_at`            datetime     DEFAULT NULL COMMENT '官网发布时间',
    `content_hash`            char(64)     DEFAULT NULL COMMENT '正文 SHA-256',
    `first_synced_at`         datetime     NOT NULL COMMENT '首次发现时间',
    `last_synced_at`          datetime     NOT NULL COMMENT '最近同步时间',
    `last_content_checked_at` datetime     NOT NULL COMMENT '最近正文校验时间',
    `create_user`             bigint       DEFAULT NULL COMMENT '创建人',
    `create_time`             datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user`             bigint       DEFAULT NULL COMMENT '修改人',
    `update_time`             datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`                 tinyint      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_eve_official_news_url` (`original_url`),
    KEY `idx_eve_official_news_category_published` (`source_code`, `published_at`, `id`),
    KEY `idx_eve_official_news_published` (`published_at`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '网易 EVE 国服官网资讯快照';

CREATE TABLE `eve_official_news_sync_state`
(
    `source_code`           varchar(32)   NOT NULL COMMENT '官网栏目编码',
    `source_url`            varchar(1000) NOT NULL COMMENT '官网栏目链接',
    `last_checked_at`       datetime      DEFAULT NULL COMMENT '最近检查时间',
    `last_successful_at`    datetime      DEFAULT NULL COMMENT '最近成功时间',
    `last_failure_at`       datetime      DEFAULT NULL COMMENT '最近失败时间',
    `failure_count`         int           NOT NULL DEFAULT 0 COMMENT '连续失败次数',
    `last_failure_message`  varchar(500)  DEFAULT NULL COMMENT '最近失败摘要',
    `last_discovered_count` int           NOT NULL DEFAULT 0 COMMENT '最近发现数量',
    `create_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`source_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '网易 EVE 国服官网资讯同步状态';

INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (20083, '游戏资讯', 20000, 1, '/eve/news-directory', 'EveOfficialNewsDirectory', 'Layout', '/eve/news', 'sound', b'0', b'0', b'0', NULL, 10, 1, 1, NOW()),
    (20084, '国服新闻活动', 20083, 2, '/eve/news', 'EveOfficialNews', 'eve/news/index', NULL, 'notification', b'0', b'0', b'0', 'eve:news:view', 1, 1, 1, NOW()),
    (20085, '版本更新', 20083, 2, '/eve/news/versions', 'EveOfficialNewsVersions', 'eve/news/index', NULL, 'file', b'0', b'0', b'0', 'eve:news:view', 2, 1, 1, NOW()),
    (20086, '检查官网资讯更新', 20083, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'eve:news:sync', 3, 1, 1, NOW());

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
VALUES (20001, 20083), (20001, 20084), (20001, 20085), (20001, 20086);

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin', 'corp_member')
  AND `role`.`deleted` = 0
  AND `menu`.`id` IN (20083, 20084, 20085)
  AND `menu`.`deleted` = 0;

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin')
  AND `role`.`deleted` = 0
  AND `menu`.`id` = 20086
  AND `menu`.`deleted` = 0;
