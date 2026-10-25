# 外部 Agent 数据库设计

## 新增表

迁移：`backend/continew-server/src/main/resources/db/changelog/mysql/eve/054_agent_access.sql`。

| 业务名 | 表 | 核心字段 | 来源与规则 |
| --- | --- | --- | --- |
| 外部应用授权 | `eve_agent_application` | `id`、`tenant_id`、`corporation_ref_id`、`corporation_id`、`name`、`status` | 军团 CEO/总监在“外部 Agent”页面创建；军团标识来自当前有效绑定，不接受客户端指定 |
| 凭据摘要 | 同上 | `token_hash`、`token_prefix` | 密码安全随机生成 256 位原始密钥；仅 SHA-256 摘要落库；前缀仅用于辨识；`token_hash` 唯一索引 |
| 生命周期 | 同上 | `expires_at`、`last_used_at`、`last_rotated_at`、`revoked_at` | UTC 保存，API 携带明确时区；轮换不改变到期时间；撤销后不能轮换恢复 |
| 工具及管理审计 | `eve_agent_audit` | `application_id`、`application_name`、`tenant_id`、`corporation_ref_id`、`actor_type`、`actor_user_id`、`tool_name` | 应用名称保存快照；管理事件标记 SITE_USER，工具调用标记 AGENT；不保存业务请求或结果 |
| 调用结果摘要 | 同上 | `success`、`duration_ms`、`result_count`、`occurred_at` | SDK 回调完成后记录；发生时间为 UTC，不保存原始异常 SQL、查询值或正文 |

通用 `create_time/update_time` 沿用项目现有数据库及 JVM 约定，不将这些字段当作 ESI UTC 源值重复加八小时。

索引按军团、应用和发生时间覆盖列表与审计分页。创建/轮换/撤销与管理审计在事务内完成；工具审计及最近成功使用时间在独立服务事务中完成。原始密钥不进入数据库、列表 DTO、日志或前端持久化存储。

## 现有数据来源映射

MCP 不新增一份业务镜像，也不更改现有游戏同步规则。查询注册表显式列出 SQL 字段，以下是来源分类，实际字段以 `eve_catalog` 为准。

| 来源系统/菜单 | 来源表与字段示例 | MCP 数据集/字段 | 转换与约束 |
| --- | --- | --- | --- |
| 本站/军团绑定 | `eve_corporation.corporation_id/name/status` | `corporation.corporationId/name/status` | `tenant_id` 唯一军团绑定，额外核对绑定主键及游戏 ID |
| EVE ESI/军团成员 | `eve_corporation_roster_member.character_id/character_name`；追踪表地点与登录时间 | `members.characterId/characterName/locationName/lastLogonAt` | tenant + corporation_ref_id；UTC 瞬时输出北京时间偏移 |
| 本站/角色授权 | 角色、有效授权及角色权限快照 | `characters`、`authorization_summary`、`character_role_snapshots` | 只公开安全摘要，不公开 token、owner_hash、私钥 |
| EVE ESI/军团资产与建筑 | `eve_corporation_asset.quantity/location_name`；建筑燃料与服务 | `assets.quantity/locationName`、`structures` | 当前军团缓存记录，不现场调用 ESI |
| 本站/月矿情报 | 排期、协同任务、矿种档案与矿堡备注 | `moon_extractions`、`moon_operations`、`moon_operation_tasks`、`moon_ore_profiles`、`moon_structure_notes` | 保存矿种体积比例；不新增每轮产量数据 |
| EVE ESI/采矿统计 | 观测点、采矿账本、压缩映射 | `mining_observers`、`mining_ledger`、`mining_compression_mapping` | 日期不当瞬时偏移；数量单位与压缩比例分别保留 |
| 官方接口/吉他市场 | `eve_market_snapshot` 价格、数量、历史和同步时间 | `market` | 只读快照，不触发补温或强制同步；金额为十进制字符串 |
| 本站/工业制造 | 蓝图、生产配置、设施、蓝图效率、机会/材料、计划/步骤、市场与星系输入快照、成本系数 | `industry_*` | 全局蓝图与租户业务数据区分；配置与测算假设一起保留 |
| 本站/星图 | 星系、星门、运营标注、航线和节点 | `universe_systems`、`universe_stargates`、`starmap_*` | 公共拓扑与军团内部标注分别隔离 |
| EVE ESI/游戏邮件与通知 | `eve_game_mail.mail_id/subject/body`；通知内容 | `mail`、`notifications`、`eve_read_document` | JOIN 同租户当前军团有效角色和所需 Scope；按 characterId+邮件/通知 ID 读取；不写已读状态 |
| 网易官网/游戏资讯 | `eve_official_news.title/content_text/original_url` | `news` | 返回已缓存内容与来源，不访问任意 URL |
| 本站/静态资料与同步状态 | 静态物品、地点、数据新鲜度 | `static_types`、`static_locations`、`data_freshness` | 只读公共静态字段与当前军团脱敏同步摘要 |
| 本站/权限与审计 | 当前租户角色菜单权限、Agent 审计 | `permission_summary`、`agent_audit` | 不返回用户口令、会话、IP 或原始请求内容 |

## 查询安全

表名、JOIN、字段表达式、可搜索字段与排序键来自代码白名单；过滤值全部通过 JDBC 参数绑定。SQL 中的租户和军团条件不能由外部请求覆盖。统计与正文读取使用相同隔离，不存在“列表过滤但总数泄露”的旁路。

大文本通过 MySQL `SUBSTRING`/`CHAR_LENGTH` 分段，不先读完整 MEDIUMTEXT。片段不是完整 JSON 时必须按切片标志继续读取后再解析。专用 JDBC 模板设置查询超时，不改变站内其他模块的共享模板。

## 数据生命周期

本次不改变业务数据保留策略，不自动清理审计或删除旧授权记录。撤销立即阻止后续请求，不能撤回外部应用已经接收或转发的数据。重新部署前对新增表与菜单迁移做备份和变更核对；回滚应用版本不删除授权与审计表。
