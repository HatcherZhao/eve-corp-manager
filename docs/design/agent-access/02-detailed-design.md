# 外部 Agent 接入详细设计

## 已确认范围

2026-10-25 用户确认“按军团授权，全部的数据，包括邮件”。一个应用凭据固定绑定一个军团，读取该军团在本站保存的全部业务资料，包括已授权角色的邮件、成员追踪、内部备注。密码、访问令牌、刷新令牌、角色所有者校验摘要和私钥不属于业务查询数据。

本功能不收发 QQ 消息，不发送游戏邮件，不改变邮件已读状态，不修改生产计划，也不在查询中调用游戏接口。QQ 机器人必须具备 MCP Client 能力，或由其可信适配层调用 MCP；群成员权限与转发控制由机器人侧负责。

## 请求链路

```text
QQ 机器人 / 外部 MCP Client
  -> HTTPS /api/mcp，Authorization: Bearer <应用密钥>
  -> 原有 Nginx /api/ 反代，后端 /mcp
  -> EveAgentMcpFilter：来源、密钥、体积、频率、并发
  -> 官方 HttpServletStatelessServerTransport
  -> 四类只读工具：目录 / 查询 / 汇总 / 缓存正文
  -> EveAgentQueryService：字段白名单 + 参数化 SQL + 军团隔离
  -> 本地 MySQL 缓存
  -> 脱敏审计成功写入后才返回数据
```

使用官方 Java SDK `mcp-core:2.1.0` 与 `mcp-json-jackson2:2.1.0`，不升级现有 Java 17、Spring Boot 3.3 或 Jackson 2。独立 Servlet 避免站内 MVC 响应包装破坏 JSON-RPC。SDK 支持 `2025-03-26`、`2025-06-18`、`2025-11-25` 协议；实际以客户端协商结果为准，不宣称支持尚未验证的新规范。

无状态 HTTP 不保存授权会话。每次请求重新从数据库验证密钥；协议初始化或旧连接不能绕过到期、撤销和轮换。身份在 SDK transport context 中显式传递，不依赖异步线程上的网页登录上下文。

## 授权与隔离

- 仅当前军团的 CEO、总监且具备 `eve:agents:manage` 权限可管理凭据，普通成员及外部 MCP 凭据不能创建应用。
- 256 位随机密钥仅在创建或轮换时返回一次，数据库保存 SHA-256；查询列表不返回摘要或原始密钥。1 至 365 天有效期，到期后需重新创建。
- 应用绑定本站 `tenantId`、军团绑定主键 `corporationRefId` 和游戏 `corporationId`，三者来自服务端，不接受调用者指定。
- 现有 `uk_eve_corporation_tenant(tenant_id, deleted)` 保证一个未删除军团绑定对应唯一租户。工业、星图等只有租户字段的业务表以该不变量实现军团隔离；具有军团字段的数据集额外限制军团主键。
- 邮件、通知同时约束记录租户、角色当前军团、角色 ACTIVE、有效 ACTIVE 授权和所需 Scope。军团应用密钥不能替代 EVE 角色的 `esi-mail.read_mail.v1` 等游戏授权。
- 邮件/通知 ID 可能在多个角色邮箱重复。读取正文必须同时提供列表返回的 `characterId` 与邮件/通知 ID。
- 公共静态资料、国服吉他行情和官方资讯可在认证后按全局数据集查询；不读取其他租户的内部资料。

## 查询契约

工具具备中文说明、JSON Schema、结构化结果和只读提示。`eve_catalog` 返回实际注册的 42 个数据集及字段，客户端不能猜表名或执行 SQL。

`eve_query` 支持 keyword、类型化 filters、fields、sort、page、size。每页最多 100 条，最多 12 个过滤条件，`in` 最多 100 个值。时间必须携带 `Z` 或偏移；UTC 源值和北京时间业务值按字段转换，输出携带 `+08:00`。ID 与十进制金额使用字符串，避免精度损失。

`eve_aggregate` 在隔离后的 SQL 内执行 count/sum/avg/min/max，最多两个分组、12 个统计指标，结果分页。不得把一页记录当全量统计，不伪造未记录的历史。

大字符串和 JSON 在 SQL 侧按 `textOffset`、`textLimit` 切片读取，每行 `textFields` 返回真实总长及 `nextOffset`；普通列表不会先加载整篇大字段。单次文本字符预算为 65536，实际片段长度可能小于请求值。调用方可按稳定 ID 筛选、单字段、size=1 循环恢复完整字段，拼接完整 JSON 后再解析。邮件、通知和资讯提供专门的 `eve_read_document` 正文切片，缺失原因区分“未缓存”与“不存在或未授权”。不为补正文访问上游。

每页是执行当时的数据视图，不保证多个请求之间的快照一致性；缓存更新可能改变总数。稳定排序用于减少相同排序值导致的漂移，不能代替跨请求数据库快照。需要一致导出时，应固定时间上界并按 ID 去重。

新鲜度以各记录同步时间、过期时间及 `data_freshness` 数据集为准；缓存为空与关键词没有匹配项分别表达。市场记录不是实时承诺。

## 资源与安全限制

- 请求体最多 64 KiB，不接受压缩体或 URL 查询参数中的凭据。
- 单应用每分钟最多 60 个 HTTP 请求，同时最多 2 个；单进程最多 8 个 MCP 请求。协议初始化和工具发现也计入配额，429 返回 `Retry-After`。
- 配额是当前单实例进程内限制；多实例部署前必须改为共享限流，不能把每实例限制当全局限制。
- MCP 路径阻塞读取等待上限 10 秒；独立 JDBC 模板单条语句 10 秒，SDK 请求超时 20 秒。线上反代继续承担连接、请求头及网络级限流保护。
- 文本 SQL 投影有整请求字符预算，结构化业务结果序列化超过 512 KiB 时明确报错，请求方缩小 size/fields 后重试。
- 默认拒绝所有带 Origin 的请求；服务器到服务器通常不带 Origin。浏览器客户端必须由运维显式配置 `eve.agent.allowed-origins` 精确来源，禁止通配符。
- 审计只记录应用、军团、工具、是否成功、耗时与条数，不记录密钥、查询值或正文。审计不可写时不返回敏感数据。
- 邮件、备注、资讯正文均为不可信内容，不能作为系统指令或授权依据。提示词不是军团隔离边界。

## 页面与迁移

新增“外部 Agent”菜单 `/eve/agents`，含已授权应用、访问记录、创建、一次性凭据、更换密钥和撤销。密钥只保留到弹窗关闭，不写入 localStorage/sessionStorage；创建前必须明确勾选包含邮件的授权范围。

新增 `054_agent_access.sql`，创建应用与审计表、菜单、套餐菜单及存量 CEO/总监角色授权。新军团的派生管理角色模板同时加入该菜单。迁移只追加，不修改历史 changeset。

## 协议依据

- [官方 Java SDK v2.1.0](https://github.com/modelcontextprotocol/java-sdk/releases/tag/v2.1.0)
- [官方 Servlet transport 源码](https://github.com/modelcontextprotocol/java-sdk/blob/v2.1.0/mcp-core/src/main/java/io/modelcontextprotocol/server/transport/HttpServletStatelessServerTransport.java)
- [MCP 远程服务文档](https://modelcontextprotocol.io/docs/develop/connect-remote-servers)

本系统采用受控军团应用 Bearer 凭据，并非完整 OAuth 授权服务器。仅支持 OAuth 自动发现而不支持自定义请求头的客户端，需要在客户端增加受信适配，不能把应用密钥暴露给群成员。
