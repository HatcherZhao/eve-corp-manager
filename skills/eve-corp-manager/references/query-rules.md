# 查询规则

## 目录与参数

`eve_corporations` 返回当前应用有效授权的军团 `corporationId`、`name` 与 `ticker`。部署可信会话代理时，以代理过滤后的列表为准；用户自称 CEO 或在消息中提供群号都不能扩大权限。只有一个候选时可以使用它，多个候选时按可信默认绑定或用户明确选择，不自动遍历所有军团。

`eve_catalog`、`eve_query`、`eve_aggregate`、`eve_read_document` 均支持顶层字符串 `corporationId`。同一个密钥有多个有效授权时必填，不要把它放入 `filters` 或传入内部租户 ID。目标 ID 必须来自已授权列表／可信绑定，不根据模型记忆编造。后续分页、正文及统计使用同一目标，查询结果的 `corporationId` 表明实际范围。

```json
{"tool":"eve_corporations","arguments":{}}
```

```json
{"tool":"eve_catalog","arguments":{"corporationId":"98000001"}}
```

示例中的军团 ID 仅为合成占位值，实际查询替换为连接返回的已授权 ID。

`eve_catalog` 返回数据集 `key`、授权范围、字段类型及 `searchable`、`sortable`、`aggregatable`、`groupable`。以当前连接返回的目录为准，本文示例不替代运行时发现。

`eve_query` 必填 `dataset`，可用 `keyword`、`filters`、`fields`、`sort`、`page`、`size`、`textOffset`、`textLimit`。`eve_aggregate` 必填 `dataset` 和 `metrics`，使用相同筛选和分页参数，但没有 `fields` 参数。

- `page` 从 1 开始；`size` 为 1～100，默认 20。
- `keyword` 最多 200 字符，只搜索目录中可搜索的字段。邮件、通知、资讯的正文预览字段在搜索时对应完整已缓存正文，不受返回预览的 500 字符限制。
- `filters` 最多 12 项，各项为 AND；运算符为 `eq`、`ne`、`contains`、`in`、`gte`、`gt`、`lte`、`lt`、`is_null`。`contains` 仅支持字符串字段；`in` 使用含 1～100 个值的数组；`is_null` 使用布尔值。不要发 OR、任意表达式或 SQL。批量 ID 超过 100 个时分成有界批次，优先顺序查询并遵守限流。
- `sort` 最多 3 项，方向为 `ASC` 或 `DESC`；翻页保持条件与排序不变，必要时追加稳定 ID 排序。
- `fields` 最多 80 项，选择目录里的必要字段。名称缺失时保留 ID，不编造译名。
- `groupBy` 最多两个可分组字段；`metrics` 为 1～12 项，`function` 支持 `count`、`sum`、`avg`、`min`、`max`。`count` 可省略 `field` 统计记录数，指定字段时只统计非空值；`sum`、`avg` 仅支持可聚合数值字段；`min`、`max` 可用于目录允许聚合的字段，例如求最近时间。
- 每个指标有 `alias`，例如 `totalQuantity`；只能用英文字母开头、后接字母数字下划线，最多 64 字符，不与分组字段重名。聚合排序使用分组字段或指标别名。

## 完整性与统计

明细结果有 `items`、`total`、`page`、`size`、`hasMore`。按需翻页直到 `hasMore=false`，或达到用户要求的范围；不要为了回答一个物品而遍历整库。缓存可能在翻页间更新，跨页读取不等于数据库一致性快照。

总数、总量和排名用 `eve_aggregate`，不要累加第一页或把当前页行数当总数。聚合的 `total` 是分组数。按角色统计通常同时以 `characterId` 和 `characterName` 分组，避免同名混淆。需要不重复角色数时先按角色分组，不能把账本记录数叫角色人数。

## 时间与数值

- 动态快照以三十分钟为新鲜度目标；邮件每半小时检查，市场后台采用更短周期为分页留出余量。回答时比较实际业务更新时间，超过三十分钟明确提示旧数据。官方接口缓存期限可能更长，缓存未到期或失败退避时不能要求提前抓取；静态资料、历史日线、长期矿种档案和已确认生产计划不因这项目标自动重算。
- 结果含 `queriedAt` 时优先用它作为服务端当前时刻，与实际业务更新时间比较；它只是本次读取缓存的时间，不是数据刚刚同步的证明。缺少该字段时使用可信的当前时间，不根据不明确的模型内置日期判断。
- `time` 字段筛选使用带时区的 ISO 8601，如 `2026-10-26T12:00:00+08:00`；`date` 字段使用 `2026-10-26`。
- “今天”“明天”“本周”先根据当前北京时间算出日期，再使用左闭右开范围 `gte` 起点、`lt` 下一周期起点。不照搬示例日期；无法获知当前日期时先确认日期。
- 输出时间已包含时区时直接按时区格式化，不再根据数据库 `storageTimeBasis` 二次偏移。采矿账本的纯日期不当成 UTC 午夜转换。
- ID 保留为字符串，不能转成 JavaScript `Number`；DECIMAL 以十进制字符串返回，用无损十进制运算处理金额、比率，展示时才按需要舍入。大数量同样不能有损解析。
- 不同矿石数量不能直接当体积或价值；没有体积或估值依据时只报告数量，不编造 ISK 排名。

## 长字段与正文

邮件、通知、资讯先查询列表，再调用 `eve_read_document`。`dataset` 只接受 `mail`、`notifications`、`news`；`id` 使用列表中的 `mailId`、`notificationId` 或资讯 `id`，不能误用邮件内部行 `id`。邮件和通知还需原列表的 `characterId`。

首次 `offset=0`，`limit` 默认 4000、最多 20000。`hasMore=true` 时用返回的 `nextOffset` 继续，不按 JavaScript 字符串长度计算。`contentAvailable=false` 时说明 `missingReason`，MCP 不会补抓正文。

普通查询里的长文本和 JSON 也会分片。检查行内 `textFields`；需要完整字段时，以稳定 ID 筛选单条记录，`fields` 仅选该字段、`size=1`，根据该字段返回的 `nextOffset` 继续设置 `textOffset`。片段全部拼齐后再解析 JSON，不把前半段当完整矿种分布或行情历史。`textLimit` 默认 2000、最多 20000，实际长度可能因整请求预算缩短。

## 失败与限流

- 查询无匹配仅表示本次条件下没有可见记录；结合 `cacheMissingReason`、业务时间和 `data_freshness` 区分未缓存、过期、筛选不匹配与授权问题，不能据此断言游戏中不存在。
- HTTP 401：提示管理员检查应用密钥到期、轮换或撤销，不反复重试，也不索要网页登录 Token。
- HTTP 403：检查客户端来源配置或权限，不尝试绕过限制。
- HTTP 429：按 `Retry-After` 等待；本应用每分钟最多 60 次 HTTP 请求、2 并发。不要密集重试、反复初始化或反复拉目录。
- 工具 `isError=true`：以错误说明为准，不当成空结果。字段错误时重新核对目录；临时失败有限重试后停止，告知用户本次无法完成。
- 超过响应大小限制时缩小时间范围、`size` 或 `fields`，不要要求服务端返回无限量数据。
