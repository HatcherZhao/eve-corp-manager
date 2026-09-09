-- liquibase formatted sql

-- changeset eve-corp-manager:011-preserve-identity-during-reauthorization
-- comment 恢复因授权范围升级被误移除的军团派生角色；仅采用当前有效成员关系与最近一次已验证游戏身份
INSERT IGNORE INTO `sys_user_role` (`tenant_id`, `user_id`, `role_id`)
SELECT `character`.`tenant_id`, `character`.`user_id`, `role`.`id`
FROM `eve_character` AS `character`
INNER JOIN `eve_corporation_member` AS `member`
    ON `member`.`tenant_id` = `character`.`tenant_id`
    AND `member`.`character_ref_id` = `character`.`id`
    AND `member`.`user_id` = `character`.`user_id`
    AND `member`.`status` = 'ACTIVE'
    AND `member`.`deleted` = 0
INNER JOIN `sys_role` AS `role`
    ON `role`.`tenant_id` = `character`.`tenant_id`
    AND `role`.`code` = 'corp_member'
    AND `role`.`deleted` = 0
WHERE `character`.`status` = 'ACTIVE'
  AND `character`.`deleted` = 0;

INSERT IGNORE INTO `sys_user_role` (`tenant_id`, `user_id`, `role_id`)
SELECT `character`.`tenant_id`, `character`.`user_id`, `role`.`id`
FROM `eve_character` AS `character`
INNER JOIN `eve_corporation_member` AS `member`
    ON `member`.`tenant_id` = `character`.`tenant_id`
    AND `member`.`character_ref_id` = `character`.`id`
    AND `member`.`user_id` = `character`.`user_id`
    AND `member`.`status` = 'ACTIVE'
    AND `member`.`deleted` = 0
INNER JOIN `eve_character_role_snapshot` AS `snapshot`
    ON `snapshot`.`tenant_id` = `character`.`tenant_id`
    AND `snapshot`.`character_ref_id` = `character`.`id`
    AND `snapshot`.`deleted` = 0
    AND NOT EXISTS (
        SELECT 1
        FROM `eve_character_role_snapshot` AS `newer_snapshot`
        WHERE `newer_snapshot`.`tenant_id` = `snapshot`.`tenant_id`
          AND `newer_snapshot`.`character_ref_id` = `snapshot`.`character_ref_id`
          AND `newer_snapshot`.`deleted` = 0
          AND (`newer_snapshot`.`captured_at` > `snapshot`.`captured_at`
              OR (`newer_snapshot`.`captured_at` = `snapshot`.`captured_at`
                  AND `newer_snapshot`.`id` > `snapshot`.`id`))
    )
INNER JOIN `sys_role` AS `role`
    ON `role`.`tenant_id` = `character`.`tenant_id`
    AND `role`.`code` = CASE
        WHEN `snapshot`.`is_ceo` = b'1' THEN 'corp_owner'
        ELSE 'corp_admin'
    END
    AND `role`.`deleted` = 0
WHERE `character`.`status` = 'ACTIVE'
  AND `character`.`deleted` = 0
  AND (`snapshot`.`is_ceo` = b'1'
      OR JSON_CONTAINS(`snapshot`.`roles`, JSON_QUOTE('Director')));
