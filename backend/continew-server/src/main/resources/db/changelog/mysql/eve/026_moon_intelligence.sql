-- liquibase formatted sql

-- changeset eve-corp-manager:026-moon-intelligence
-- comment 将月矿模块收敛为国服时间线与本站备注，保留历史排产数据但停止使用
ALTER TABLE `eve_moon_extraction`
    ADD COLUMN `note` varchar(500) DEFAULT NULL COMMENT '月矿情报备注' AFTER `natural_decay_at`;

UPDATE `sys_menu`
SET `title` = '月矿情报'
WHERE `id` = 20022
  AND `path` = '/eve/extractions'
  AND `name` = 'EveExtractions'
  AND `component` = 'eve/extractions/index'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `title` = '查看月矿情报'
WHERE `id` = 20065
  AND `permission` = 'eve:extractions:view'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `title` = '维护月矿备注'
WHERE `id` = 20064
  AND `permission` = 'eve:extractions:manage'
  AND `deleted` = 0;
