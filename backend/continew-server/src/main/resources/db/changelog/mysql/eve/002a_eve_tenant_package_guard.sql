-- liquibase formatted sql

-- changeset eve-corp-manager:002a-eve-tenant-package-guard
-- comment 在固定套餐数据写入前阻止 ID 或业务名称冲突；缺失与内容完全一致均允许继续
-- preconditions onFail:HALT onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM `tenant_package` WHERE `id` = 20001 AND NOT (`name` = 'EVE军团套餐' AND `sort` = 1 AND `menu_check_strictly` = b'1' AND `description` = 'EVE军团认领流程专用套餐' AND `status` = 1 AND `create_user` = 1 AND `update_user` IS NULL AND `update_time` IS NULL AND `deleted` = 0)
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM `tenant_package` WHERE `name` = 'EVE军团套餐' AND `id` <> 20001
SELECT 1;
