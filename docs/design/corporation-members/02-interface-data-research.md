# 军团人员模块：国服接口与数据调研

> 证据来源：`references/serenity-swagger-2026-09-06.json`、`references/endpoint-index.json`、`docs/research/api-matrix.md`。  
> 证据等级：接口契约已从国服 Swagger 快照提取；每个接口仍需使用真实国服授权角色完成联调验收。

## 接口结论

| 数据域 | 国服接口 | Scope | 游戏资格 / 条件 | 缓存契约 | 可用字段 | 设计结论 |
| --- | --- | --- | --- | --- | --- | --- |
| 当前成员名册 | `GET /corporations/{corporation_id}/members/` | `esi-corporations.read_corporation_membership.v1` | 授权角色必须属于该军团 | 3600 秒 | `character_id[]` | 全量名册事实来源；接口本身不返回角色名或入团时间 |
| 成员追踪 | `GET /corporations/{corporation_id}/membertracking/` | `esi-corporations.track_members.v1` | Director | 3600 秒 | `character_id`、`start_date`、`logon_date`、`logoff_date`、`location_id`、`ship_type_id`、`base_id` | 获本站追踪查看权限的用户可直接查看；不是实时定位或在线时长接口 |
| 成员游戏职位 | `GET /corporations/{corporation_id}/roles/` | `esi-corporations.read_corporation_membership.v1` | Personnel Manager 或任一可授予角色，实际以国服响应为准 | 3600 秒 | `roles`、`roles_at_hq`、`roles_at_base`、`roles_at_other` 与四类 `grantable_roles` | 用于人员权限展示；四个范围不得合并丢失 |
| 职位变更历史 | `GET /corporations/{corporation_id}/roles/history/` | `esi-corporations.read_corporation_membership.v1` | Director | 3600 秒，分页 | `changed_at`、`character_id`、`issuer_id`、`old_roles`、`new_roles`、`role_type` | 作为审计补充；分页全成功后发布批次 |
| 成员头衔分配 | `GET /corporations/{corporation_id}/members/titles/` | `esi-corporations.read_titles.v1` | Director | 3600 秒 | `character_id`、`titles[]` | 仅存标题 ID，需连接头衔定义 |
| 头衔定义 | `GET /corporations/{corporation_id}/titles/` | `esi-corporations.read_titles.v1` | Director | 3600 秒 | `title_id`、`name` 与四范围角色 / 可授予角色 | 提供头衔名称和其权限含义 |
| 角色公开资料 | `GET /characters/{character_id}/` | 无 | 公开 | 604800 秒 | `name`、`corporation_id`、`alliance_id`、`security_status`、`title` 等 | 只用于公开展示信息与成员 ID 一致性校验 |
| 批量名称解析 | `POST /universe/names/` | 无 | 公开 | 未声明 | `id`、`name`、`category` | 单批 1–1000 ID；用于角色、位置、舰船等展示名称解析；POST 是读取，不是写入 |

## 最小授权组合

| 功能级别 | 必须 Scope | 数据源角色 | 说明 |
| --- | --- | --- | --- |
| 基础名册 | `esi-corporations.read_corporation_membership.v1` | 在该军团的有效授权角色 | 可取得成员 ID 和成员游戏职位；是否可访问仍以真实国服响应为准 |
| 追踪信息 | `esi-corporations.track_members.v1` | Director | 才能取成员追踪和成员上限；须同步拥有本站敏感查看资格 |
| 头衔 | `esi-corporations.read_titles.v1` | Director | 同步头衔分配和头衔定义 |
| 角色变更历史 | `esi-corporations.read_corporation_membership.v1` | Director | 同步职位变更历史 |

现有 `EveCapability.MEMBERS` 将成员模块整体映射到 `track_members` + Director，只能用作“全能力可用”提示；实施时必须拆成“基础名册、头衔、追踪、角色历史”四个数据资格，避免因一个敏感接口不可用而隐藏整个名册。

## 字段分层与质量规则

| 层级 | 字段 | 质量与展示规则 |
| --- | --- | --- |
| 名册事实 | `character_id`、当前在团状态、批次 ID | 名单完整成功才更新 `ACTIVE/LEFT`；不能根据一次超时把成员标记离团 |
| 展示资料 | 角色名、公开头衔、安全等级、联盟 | 角色 ID 为真值；名称解析失败可延迟补偿，展示待解析状态 |
| 权限资料 | 四范围角色、可授予角色、游戏头衔 | JSON 保留原始范围；页面按全局 / 总部 / 基地 / 其他地点分组 |
| 运营追踪 | 入团、上下线、位置、舰船、基地 | 分表存储；获本站追踪查看权限的用户在名册和详情直接查看，记录访问审计；空值展示“上游未提供” |
| 历史审计 | 职位变更、变更发起人、变更时间 | 保留上游原始角色数组与解析后的差异；历史归属不被当前名册覆盖 |

## 同步策略

1. 每个资源尊重响应的 `Expires`、`ETag`、`X-Pages`；Swagger 的 3600 秒仅是默认计划参考。
2. 名册同步先取完整 `members` 列表，计算候选差异；角色名分批（最多 1000 ID）解析；全部关键页成功后才在事务中发布当前状态。
3. `membertracking`、角色、头衔、角色历史是独立资源批次。追踪失败不得影响基础名册状态，只降低追踪区的数据新鲜度。
4. 401 仅可控刷新令牌一次后重试；403 记录“缺少 Scope / 游戏角色 / 对象权限”的归类并停止该资源；420/429/5xx 退避重试且保留旧快照。
5. 同一军团、同一资源加分布式锁；手动同步立即执行，定时同步继续按自身队列节奏运行，二者不并发扇出。

## 已知限制

- 没有接口保证返回真实在线状态或精确在线时长；`logon_date` / `logoff_date` 仅是上游追踪快照字段。
- `location_id` 和 `ship_type_id` 可能缺失或因访问限制无法解析，不得推断成员故意隐藏信息。
- 成员角色、头衔和角色历史的实际国服资格需真实 Director 授权联调，不以国际服经验替代。
- 成员名称、当前军团和公开资料可能在不同响应时刻变化；批次必须记录来源时间，页面展示快照时间。
