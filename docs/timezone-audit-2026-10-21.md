# 系统时间专项检查（2026-10-21）

## 结论与检查边界

初次检查为只读审计；用户随后要求修复，已完成下述代码修复和本地验证。修复不需要修改数据库数据或重新同步官方接口。发布时保持现有容器资源、卷、端口及运行配置，保留旧镜像以便回滚。
检查覆盖生产主机、应用容器、JVM 启动参数、数据库会话，以及前后端时间读写、显示、查询、缓存和任务调度代码。
没有逐页登录验收所有线上页面；下文区分生产实证、代码确认及潜在风险。

## 修复结果

- EVE 响应的 43 个 DTO 按字段明确时间来源：UTC 输出 `Z`，北京时间输出 `+08:00`；同一权限响应中的不同来源分别标注。不更改数据库已存时刻，也不注册影响上游解析和内部缓存的全局序列化器。
- 前端新增 `eveTime` 工具，月矿、工作台、建筑、资产、成员、采矿、邮件、通知、市场、工业、新闻、权限和星图统一按 `Asia/Shanghai` 展示和计算。纯日期不平移。
- 月矿 `04:00Z` 显示为北京时间 `12:00`；星期、跨日、待到达判断与剩余时间使用同一瞬时。
- 星图日期输入携带偏移量，后端转换成北京时间存储并兼容旧裸输入；到期过滤也使用北京时间，不迁移旧标注。
- 权限刷新、注册和绑定的估算过期时间统一 UTC；刷新冷却也统一 UTC。授权失效审计的 `last_verified_at` 与成功验证路径统一北京时间，令牌过期及撤销时刻仍为 UTC。未批量推测修正旧的混合来源审计记录。
- 市场历史补温与成员名册审计更新不再依赖数据库 `NOW()`；由调用方传入对应存储口径的当前时间。
- 公共日期区间快捷项按北京日历生成字符串，避免 Date 对象被控件再次按浏览器时区转换；调度预览按北京时间格式化。

### 验证与发布边界

- 后端完整 Maven fat JAR 构建及单元测试通过；新增响应协议、真实月矿 DTO、星图请求和权限 TTL 回归测试。
- 前端 `lint`、`typecheck`、`build` 通过；lint 仅有既有 `useMultipartUploader.ts` JSDoc warning，构建保留既有大包提醒。
- `node scripts/g011/01-timezone-regression.mjs` 在 UTC、上海、纽约三个运行时区通过，覆盖 04:00Z 到 12:00、跨午夜、星期、时间差、纯日期、带偏移输入及非法值。
- 变更 Java 文件已定向执行 Spotless；未对无关文件做全库格式化。
- 前后端必须配套发布，旧版后端的裸 UTC 时间无法仅靠新版前端推断来源。部署后另行检查启动日志、页面入口与真实接口的时间偏移量；发布和验收结果以本次交付记录为准。

月矿问题不是服务器时钟慢了 8 小时，而是 UTC 业务时间丢失时区标记后，被浏览器作为北京时间解释。
现有数据同时包含 UTC 和北京时间，不能全库加 8 小时，也不能全局给所有时间字符串补 Z。

## 生产实证

- vm-app、vm-common 主机均为 `+08:00`，应用容器也是 `+08:00`。
- 当前 API 镜像为 `20261019-f6a74a0`，启动参数包含 `-Duser.timezone=Asia/Shanghai`。
- MySQL 管理会话 `global.time_zone=SYSTEM`、`session.time_zone=SYSTEM`、`system_time_zone=UTC`。
- `performance_schema.variables_by_thread` 中当前业务库连接的 `time_zone` 均为 `SYSTEM`，也就是 UTC。
- 业务库有 253 个 DATETIME 字段，没有 TIMESTAMP 字段；DATETIME 本身不携带时区。
- 当前 7 条有效月矿时间线的矿块到达小时均为 `04`。同一批记录 `update_time` 与 `last_seen_at` 相差 480 分钟，印证本地审计时间与 UTC 同步时间并存。
- 使用项目现有 dayjs，在 `TZ=Asia/Shanghai` 下复现：`2026-10-21T04:00:00` 显示 `04:00`，而 `2026-10-21T04:00:00Z` 显示 `12:00`，瞬时时间也相差 8 小时。

## 已确认问题

### 1. 月矿时间显示和前端到达判断错误

链路：

1. 官方月矿时间按 UTC 解析到 `LocalDateTime`，现有客户端测试也明确断言保留 UTC 时分。
2. 月矿服务把这些 UTC 值写入 DATETIME，并按 UTC 查询待到达/已到达状态，这部分口径一致。
3. `EveMoonExtractionResp` 仍使用无时区的 `LocalDateTime`，没有给前端声明 UTC。
4. 月矿页直接 `dayjs(value)` 格式化、计算星期和判断到达状态；工作台倒计时也使用相同方式。

结果不仅是 12 点显示为 04 点，前端还可能提前 8 小时判断“已到达”，午夜附近的日期和星期也可能出错；后台筛选与页面标签可能不一致。

定位：

- `backend/continew-eve/src/main/java/top/continew/admin/eve/model/serenity/SerenityCorporationMoonExtractionResponse.java`
- `backend/continew-eve/src/main/java/top/continew/admin/eve/service/EveMoonExtractionService.java`
- `backend/continew-eve/src/main/java/top/continew/admin/eve/model/EveMoonExtractionResp.java`
- `frontend/src/views/eve/extractions/index.vue`
- `frontend/src/views/eve/workspace/index.vue`

### 2. 其他 UTC 业务时间也没有明确的显示协议

建筑燃料到期、建筑状态计时、邮件/通知时间、成员追踪、资产/采矿同步时间、星图同步和数据新鲜度等，多处以 UTC LocalDateTime 保存后直接交给 `dayjs(value)`。
这些代码存在同类偏移；本次没有逐条比对每个线上业务事实，不能据此断言所有记录都已偏移。

市场报价和工业计算则大量使用上海 JVM 的 `LocalDateTime.now()`，本身是北京时间，不能与上述字段一起加 8 小时。
市场详情还包含不应做时区平移的交易日期；当前官方订单映射把 `reportedAt` 置空，不把这个预留字段误报为已经发生的 UTC 显示问题。

### 3. 市场历史补温 SQL 使用了不同的时钟口径

`EveMarketService.refreshHistory()` 按生产 JVM 的北京时间写入 `history_synchronized_at` 和 `history_next_request_at`。
但 `EveStaticTypeReferenceMapper.selectHistoryWarmupTypeIds()` 使用 MySQL 会话的 UTC `NOW()` 进行到期筛选和最近访问排序。

由时间门槛本身可造成约 8 小时的补温延迟。检查当时 453 条带下次历史请求时间的快照，仅比较到期时间条件，数据库时钟下为 138 条到期，北京时间时钟下为 217 条到期；这些数字未叠加完整补温查询的其他条件，不等于最终入选数量。

同 Mapper 的旧报价轮转方法也含有 `NOW()`，但当前代码没有调用它，不能把它误报为当前订单全量同步的故障。

### 4. 个别手写更新 SQL 与审计字段时间不一致

MyBatis 公共审计字段处理器使用 JVM 本地时间，即北京时间。
`EveCorporationRosterMemberMapper` 手写更新却使用数据库 `NOW()`，线上会话实际为 UTC。
数据库默认 `CURRENT_TIMESTAMP` 的表也需逐项核对写入路径，不能假设默认时间与应用填充值一致。

## 需修复的潜在风险

- 权限快照过期时间：有官方 Expires 时按 UTC 保存；缺少 Expires 的回退路径却使用北京时间的 `checkedAt + TTL`。读取方按 UTC 判断，回退路径可能使有效期延长 8 小时。绑定授权的初始快照也有同类写法。检查时生产库没有 `source_expiry_estimated=1` 的未删除快照，未证明发生了线上权限超期事故。
- 星图运营标注：日期选择器提交无偏移时间，服务直接存储；标注可见性使用 UTC 当前时间过滤。北京时间录入的到期值可能被当作 UTC，晚隐藏 8 小时。
- 新闻发布日期：官网只有日期，后端转为当天零点；这是日期事实，不应当作 UTC 瞬时再统一转换。
- 系统管理日志、公告时间、日期区间组件也缺乏统一的带偏移协议，但主要是本地时间路径，不能一概认定当前都慢 8 小时。

## 本次未发现同类问题的路径

- OAuth 事务有效期使用 `Instant`，不是依赖墙上时钟的裸字符串。
- 订单同步共享门禁使用 epoch 毫秒；官方 Expires 明确按 UTC 转换为 epoch。
- EVE 自动同步、新闻、市场等任务主要使用 fixedDelay/initialDelay，不会因为时区不同直接增加 8 小时执行间隔；其中任务内部的时间筛选仍需单独核对。
- 市场历史日期、采矿账本日期等 LocalDate 字段是日历日期，不应加减 8 小时。

## 完整修复口径

1. 按字段确认既有存储语义，保留正确的 UTC 原始事实，不做全库时间平移。
2. API 对绝对时刻返回明确的 ISO-8601 偏移，例如 UTC 的 `04:00Z` 或北京时间的 `12:00+08:00`；区分同一响应内的不同来源字段。
3. 前端统一按 `Asia/Shanghai` 展示，倒计时、状态和星期计算使用同一绝对时刻；不能只改格式化文本。
4. 日期输入明确北京时间，提交时携带偏移并在服务端规范化；既有标注先核对来源再决定是否转换。
5. 权限 TTL 回退统一 UTC；市场历史补温及手写审计 SQL 显式传入与对应字段一致的当前时间，不修改共享 MySQL 的全局时区。
6. 纯日期保持原样。数据库既有混合口径字段先明确转换边界和兼容规则，避免静态资料、工业计算、审计日志被重复加 8 小时。
7. 回归覆盖 UTC 04:00 到北京时间 12:00、跨午夜/星期、到达前后、燃料剩余时间、权限回退 TTL、标注到期、历史补温，以及 UTC/上海/其他浏览器时区下的一致显示。
