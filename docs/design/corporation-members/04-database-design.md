# 军团人员模块：数据库与来源字段设计

> 状态：讨论稿 v0.1（2026-09-08）  
> 原则：新增名册快照表，不复用 `eve_corporation_member`。后者仅表示“本站用户绑定的游戏角色在军团中的关系”，不能表达未注册的全体游戏成员。

## 1. 新增表概览

| 表名 | 用途 | 主键 / 唯一约束 |
| --- | --- | --- |
| `eve_member_sync_run` | 每次各资源同步的批次与可观测性 | `id`；`tenant_id + corporation_ref_id + resource + started_at` 索引 |
| `eve_corporation_roster_member` | 当前名册的角色级快照 | `tenant_id + corporation_ref_id + character_id + deleted` 唯一 |
| `eve_corporation_member_tracking` | 成员运营追踪快照 | `roster_member_id` 唯一；不与基础名册混表 |
| `eve_corporation_member_game_role` | 四范围游戏职位快照 | `roster_member_id + role_scope + role_code + deleted` 唯一 |
| `eve_corporation_member_title` | 成员头衔分配 | `roster_member_id + title_id + deleted` 唯一 |
| `eve_corporation_title` | 军团头衔定义及权限 | `tenant_id + corporation_ref_id + title_id + deleted` 唯一 |
| `eve_corporation_member_role_history` | 上游职位变更历史 | `tenant_id + corporation_ref_id + character_id + changed_at + role_type + deleted` 唯一 |
| `eve_corporation_member_operations` | 本站标签、备注、负责人 | `roster_member_id` 唯一 |
| `eve_member_tracking_access_audit` | 敏感追踪数据查看审计 | `tenant_id + roster_member_id + occurred_at` 索引 |

所有表延续项目基类字段：`id`、`tenant_id`、`create_user`、`create_time`、`update_user`、`update_time`、`deleted`。游戏 ID 使用 `bigint`，时间统一以 UTC 入库、前端转换为 Asia/Shanghai 展示。

## 2. 关键表字段

### 2.1 `eve_member_sync_run`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `corporation_ref_id` | bigint | 关联 `eve_corporation.id` |
| `authorization_id` | bigint | 实际数据源授权；不得存令牌副本 |
| `resource` | varchar(40) | `ROSTER`、`TRACKING`、`ROLES`、`TITLES`、`ROLE_HISTORY`、`NAME_RESOLUTION` |
| `trigger_type` | varchar(20) | `SCHEDULED`、`MANUAL`、`RECOVERY` |
| `status` | varchar(30) | `RUNNING`、`SUCCESS`、`PARTIAL_FAILURE`、`FAILED` |
| `source_etag` | varchar(255) | 上游 ETag，允许空 |
| `source_expires_at` | datetime | 上游 `Expires`，允许空 |
| `expected_count` / `processed_count` | int | 源与成功处理数量 |
| `error_code` | varchar(100) | 脱敏错误分类，不存上游原始正文 |
| `started_at` / `finished_at` | datetime | 任务耗时与时间线 |

### 2.2 `eve_corporation_roster_member`

| 字段 | 类型 | 来源 | 说明 |
| --- | --- | --- | --- |
| `corporation_ref_id` | bigint | 本站 | 军团绑定记录 |
| `server` | varchar(20) | 配置 | 当前为 `SERENITY` |
| `corporation_id` | bigint | 绑定军团 | 冗余校验与查询分区 |
| `character_id` | bigint | `members[]` | 游戏角色唯一标识 |
| `character_name` | varchar(100) | `/universe/names/` 或 `/characters/{id}/` | 展示名；可为空待补偿 |
| `alliance_id` | bigint | `/characters/{id}/` | 公开资料，可空 |
| `security_status` | decimal(8,4) | `/characters/{id}/` | 公开资料，可空 |
| `membership_status` | varchar(20) | 完整名册差异 | `ACTIVE`、`LEFT`、`UNKNOWN` |
| `joined_at` | datetime | `membertracking.start_date` | 上游未提供时为空 |
| `first_seen_at` / `last_seen_at` | datetime | 本站同步 | 必须与真实入团时间区分 |
| `last_roster_sync_run_id` | bigint | 本站 | 发布该成员状态的完整批次 |

### 2.3 `eve_corporation_member_tracking`

| 字段 | 类型 | 来源字段 | 说明 |
| --- | --- | --- | --- |
| `roster_member_id` | bigint | 本站 | 关联当前名册成员 |
| `base_id` | bigint | `base_id` | 可空 |
| `location_id` | bigint | `location_id` | 可空；不自动推断位置名 |
| `ship_type_id` | bigint | `ship_type_id` | 可空；名称按字典补偿 |
| `last_logon_at` | datetime | `logon_date` | 上游记录时间，非持续在线状态 |
| `last_logoff_at` | datetime | `logoff_date` | 上游记录时间 |
| `source_observed_at` | datetime | 同步时间 | 上游未给单条采集时间时使用批次完成时间并明确标记 |
| `last_sync_run_id` | bigint | 本站 | 可追溯数据源 |

### 2.4 角色、头衔与内部资料

| 表 | 必要字段 | 转换规则 |
| --- | --- | --- |
| `eve_corporation_member_game_role` | `roster_member_id`、`role_scope`、`role_code`、`is_grantable`、`last_sync_run_id` | `roles*` 写入 `is_grantable=0`；四组 `grantable_roles*` 写入 `is_grantable=1`；`role_scope` 为 `GLOBAL/HQ/BASE/OTHER` |
| `eve_corporation_member_title` | `roster_member_id`、`title_id`、`last_sync_run_id` | 每个 `titles[]` ID 一行；名称通过头衔定义表关联 |
| `eve_corporation_title` | `corporation_ref_id`、`title_id`、`name`、四范围角色 JSON、四范围可授予角色 JSON | 保留国服原始权限数组，页面再格式化 |
| `eve_corporation_member_role_history` | `character_id`、`changed_at`、`issuer_id`、`role_type`、`old_roles` JSON、`new_roles` JSON、`last_sync_run_id` | 仅完整分页批次发布；原始数组不可覆盖为当前角色 |
| `eve_corporation_member_operations` | `roster_member_id`、`tags` JSON、`note`、`owner_user_id`、`version` | 纯本站运营数据，更新需审计并乐观锁 |

## 3. 来源字段映射

| 来源系统 | 来源接口 / 字段 | 目标表 / 字段 | 转换与同步规则 |
| --- | --- | --- | --- |
| EVE 国服 ESI | `members[]` | `eve_corporation_roster_member.character_id` | 当前成员全量集合；完整成功后 upsert，在旧集合中不存在者标 `LEFT` |
| EVE 国服 ESI | `/universe/names/`: `id,name,category` | `eve_corporation_roster_member.character_name` | 每批最多 1000；仅接受角色类别；失败可延迟补偿 |
| EVE 国服 ESI | `/characters/{id}/`: `alliance_id,security_status,corporation_id` | 名册成员 `alliance_id,security_status` | 公开资料补偿；`corporation_id` 与目标军团不一致时记录待复核，不直接改离团状态 |
| EVE 国服 ESI | `membertracking`: `start_date` | 名册成员 `joined_at` | 仅在非空时更新；不以本站首次发现时间代替 |
| EVE 国服 ESI | `membertracking`: 位置、舰船、登录登出、基地 | `eve_corporation_member_tracking` 同名语义字段 | 独立资源同步与权限；空值覆盖策略需记录来源时间 |
| EVE 国服 ESI | `roles*` / `grantable_roles*` | `eve_corporation_member_game_role` | 按四范围拆行；角色代码原样保存 |
| EVE 国服 ESI | `members/titles`: `character_id,titles[]` | `eve_corporation_member_title` | 每个头衔 ID 拆行；完整资源成功后替换当前分配 |
| EVE 国服 ESI | `titles`: `title_id,name,roles*` | `eve_corporation_title` | 按军团维度 upsert，保留原始 JSON |
| EVE 国服 ESI | `roles/history` | `eve_corporation_member_role_history` | 以自然键幂等写入，分页成功后标记可见 |
| 本站人员管理员 | 标签、备注、负责人 | `eve_corporation_member_operations` | 仅当前租户；写审计；绝不回写国服 |

## 4. 数据一致性与迁移要求

1. 迁移新增表、索引、字典 / 权限种子和资源枚举；不修改已应用的 Liquibase changeset。
2. 名册发布使用批次号或“同步成功后再状态切换”的事务策略，防止读到半完成名册。
3. 删除或离团使用逻辑状态，不物理删除历史角色、头衔、变更或审计。
4. 拥有 `eve:members:track:view` 时，成员列表 SQL 可联接追踪表并展示追踪列；无该权限时服务端不得查询或返回追踪字段。导出 API 同样二次校验该权限。
5. 所有查询条件必须带 `tenant_id`；即使 `character_id` 为全球唯一，也不得省略租户条件。
