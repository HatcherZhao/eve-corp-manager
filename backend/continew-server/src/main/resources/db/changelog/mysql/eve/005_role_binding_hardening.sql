-- liquibase formatted sql

-- changeset eve-corp-manager:005-role-binding-hardening
-- comment 分离 EVE 派生身份与业务权限，并约束每个租户用户只能有一个未删除主角色
-- preconditions onFail:HALT onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM (SELECT `tenant_id`, `user_id` FROM `eve_character` WHERE `deleted` = 0 AND `is_primary` = b'1' GROUP BY `tenant_id`, `user_id` HAVING COUNT(*) > 1) AS `duplicate_primary_character`

DELETE `role_menu`
FROM `sys_role_menu` AS `role_menu`
INNER JOIN `sys_role` AS `role`
    ON `role`.`id` = `role_menu`.`role_id`
    AND `role`.`tenant_id` = `role_menu`.`tenant_id`
WHERE `role`.`code` IN ('corp_owner', 'corp_admin', 'corp_member')
  AND `role`.`deleted` = 0
  AND `role_menu`.`menu_id` IN (20020, 20021, 20022, 20023, 20024);

ALTER TABLE `eve_character`
    ADD COLUMN `active_primary_user_id` bigint
        GENERATED ALWAYS AS (
            CASE
                WHEN `deleted` = 0 AND `is_primary` = b'1' THEN `user_id`
                ELSE NULL
            END
        ) STORED COMMENT '未删除主角色对应的本站用户ID',
    ADD UNIQUE INDEX `uk_eve_character_tenant_primary_user` (`tenant_id`, `active_primary_user_id`);
