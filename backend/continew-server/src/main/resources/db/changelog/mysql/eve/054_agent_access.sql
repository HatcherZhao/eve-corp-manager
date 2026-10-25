-- liquibase formatted sql

-- changeset eve-corp-manager:054-agent-access
-- comment 新增军团级外部 Agent 只读授权、脱敏审计和管理权限
CREATE TABLE `eve_agent_application`
(
    `id`                 bigint      NOT NULL COMMENT '应用 ID',
    `tenant_id`          bigint      NOT NULL COMMENT '租户 ID',
    `corporation_ref_id` bigint      NOT NULL COMMENT '军团绑定记录 ID',
    `corporation_id`     bigint      NOT NULL COMMENT '游戏军团 ID',
    `name`               varchar(80) NOT NULL COMMENT '应用名称',
    `token_hash`         char(64)    NOT NULL COMMENT '凭据 SHA-256 摘要',
    `token_prefix`       varchar(32) NOT NULL COMMENT '凭据展示前缀',
    `status`             varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE 或 REVOKED',
    `expires_at`         datetime    NOT NULL COMMENT '凭据过期时间（UTC）',
    `last_used_at`       datetime    DEFAULT NULL COMMENT '最近成功调用时间（UTC）',
    `last_rotated_at`    datetime    NOT NULL COMMENT '最近轮换时间（UTC）',
    `revoked_at`         datetime    DEFAULT NULL COMMENT '撤销时间（UTC）',
    `create_user`        bigint      DEFAULT NULL COMMENT '创建人',
    `create_time`        datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user`        bigint      DEFAULT NULL COMMENT '修改人',
    `update_time`        datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`            bigint      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_eve_agent_application_token` (`token_hash`),
    KEY `idx_eve_agent_application_corp_status` (`tenant_id`, `corporation_ref_id`, `status`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'EVE 军团外部 Agent 应用';

CREATE TABLE `eve_agent_audit`
(
    `id`                 bigint       NOT NULL COMMENT '审计 ID',
    `tenant_id`          bigint       NOT NULL COMMENT '租户 ID',
    `application_id`     bigint       NOT NULL COMMENT '应用 ID',
    `application_name`   varchar(80)  NOT NULL COMMENT '应用名称快照',
    `corporation_ref_id` bigint       NOT NULL COMMENT '军团绑定记录 ID',
    `actor_type`         varchar(16)  NOT NULL COMMENT 'SITE_USER 或 AGENT',
    `actor_user_id`      bigint       DEFAULT NULL COMMENT '站内操作者 ID',
    `tool_name`          varchar(96)  NOT NULL COMMENT '工具名或管理事件',
    `success`            tinyint      NOT NULL DEFAULT 0 COMMENT '是否成功',
    `duration_ms`        bigint       NOT NULL DEFAULT 0 COMMENT '调用耗时毫秒',
    `result_count`       int          NOT NULL DEFAULT 0 COMMENT '返回记录数量',
    `occurred_at`        datetime     NOT NULL COMMENT '事件发生时间（UTC）',
    `create_user`        bigint       DEFAULT NULL COMMENT '创建人',
    `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user`        bigint       DEFAULT NULL COMMENT '修改人',
    `update_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`            bigint       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_eve_agent_audit_corp_time` (`tenant_id`, `corporation_ref_id`, `occurred_at`, `id`),
    KEY `idx_eve_agent_audit_application_time` (`tenant_id`, `application_id`, `occurred_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'EVE 外部 Agent 脱敏审计';

INSERT IGNORE INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (20200, '外部 Agent', 0, 2, '/eve/agents', 'EveAgents', 'eve/agents/index', 'tool', b'0', b'0', b'0', 'eve:agents:manage', 14, 1, 1, NOW());

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
VALUES (20001, 20200);

INSERT IGNORE INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT `role`.`tenant_id`, `role`.`id`, `menu`.`id`
FROM `sys_role` AS `role`
CROSS JOIN `sys_menu` AS `menu`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin')
  AND `role`.`deleted` = 0 AND `menu`.`id` = 20200 AND `menu`.`deleted` = 0;
