-- liquibase formatted sql

-- changeset eve-corp-manager:057-agent-multi-corporation-grants
-- comment 将外部 Agent 应用密钥与军团授权分离，并迁移现有有效应用授权
ALTER TABLE `eve_agent_application`
    ADD COLUMN `invitation_code` char(32) DEFAULT NULL COMMENT '不含密钥的军团授权邀请码' AFTER `name`;

UPDATE `eve_agent_application`
SET `invitation_code` = LOWER(SUBSTRING(SHA2(CONCAT('eve-agent-invitation:', `id`, ':', `token_hash`), 256), 1, 32))
WHERE `invitation_code` IS NULL;

ALTER TABLE `eve_agent_application`
    MODIFY COLUMN `invitation_code` char(32) NOT NULL COMMENT '不含密钥的军团授权邀请码',
    ADD UNIQUE KEY `uk_eve_agent_application_invitation` (`invitation_code`);

CREATE TABLE `eve_agent_grant`
(
    `id`                 bigint      NOT NULL COMMENT '授权 ID',
    `tenant_id`          bigint      NOT NULL COMMENT '获授权军团租户 ID',
    `application_id`     bigint      NOT NULL COMMENT '外部应用 ID',
    `corporation_ref_id` bigint      NOT NULL COMMENT '获授权军团绑定记录 ID',
    `corporation_id`     bigint      NOT NULL COMMENT '获授权游戏军团 ID',
    `status`             varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE 或 REVOKED',
    `granted_at`         datetime    NOT NULL COMMENT '授权时间（UTC）',
    `granted_by`         bigint      DEFAULT NULL COMMENT '授权操作者',
    `revoked_at`         datetime    DEFAULT NULL COMMENT '撤销时间（UTC）',
    `revoked_by`         bigint      DEFAULT NULL COMMENT '撤销操作者',
    `create_user`        bigint      DEFAULT NULL COMMENT '创建人',
    `create_time`        datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user`        bigint      DEFAULT NULL COMMENT '修改人',
    `update_time`        datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`            bigint      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_eve_agent_grant_application_corp` (`application_id`, `corporation_ref_id`),
    KEY `idx_eve_agent_grant_corp_status` (`tenant_id`, `corporation_ref_id`, `status`, `granted_at`),
    KEY `idx_eve_agent_grant_application_status` (`application_id`, `status`, `corporation_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'EVE 外部 Agent 多军团授权';

INSERT INTO `eve_agent_grant`
(`id`, `tenant_id`, `application_id`, `corporation_ref_id`, `corporation_id`, `status`, `granted_at`,
 `granted_by`, `create_user`, `create_time`, `deleted`)
SELECT `id`, `tenant_id`, `id`, `corporation_ref_id`, `corporation_id`, 'ACTIVE',
       CONVERT_TZ(`create_time`, '+08:00', '+00:00'), `create_user`, `create_user`, `create_time`, 0
FROM `eve_agent_application`
WHERE `status` = 'ACTIVE' AND `expires_at` > UTC_TIMESTAMP() AND `deleted` = 0;
