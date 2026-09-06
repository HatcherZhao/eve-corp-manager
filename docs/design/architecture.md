# 架构与技术候选

状态：建议，未最终选型。前后端分离；首期模块化单体，避免在数据规模未知时引入微服务。

| 部分 | 候选 | 理由与待决事项 |
|---|---|---|
| 后端 | Java 21 或 25 LTS + Spring Boot + Spring Security | OAuth、任务、权限和持久化生态成熟；启动时再锁定受支持版本及依赖兼容矩阵 |
| 前端 | Vue 3 + TypeScript + Vite + Router + Pinia | 管理台、表格、筛选、权限路由；应用不直接依赖 ESI 返回结构 |
| UI | Arco Design Vue / Element Plus 等候选 | 以表格、日期日历、树形资产、中文、维护状况做验证，尚不锁定框架 |
| 数据库 | PostgreSQL + Flyway | 关系约束、批次快照、聚合与少量 JSON 扩展；MySQL 亦可评估 |
| ORM | JPA 或 MyBatis | 根据批量 upsert、报表查询和维护偏好做小型验证，不同时引入两套 |
| ESI 客户端 | 手写必要适配层 + Jackson；评估 OpenAPI Generator | 以国服契约为输入；生成 DTO 不直接暴露前端，不盲目套用国际服 SDK |
| 调度 | 持久化任务表 + Spring 调度 | 单实例先用数据库锁防重；扩容后再决定 Quartz / Redis |
| 部署 | 反向代理 + 前端静态资源 + Java 服务 + 数据库 | 前后端开发分离，生产可由同域代理减少跨域与 Cookie 配置复杂度 |

## 模块和数据流

浏览器访问自己的 API；Java 负责 SSO callback、游戏 API 与后台同步；页面查询本地快照，刷新按钮提交受限同步任务，不让每次打开页面都扇出数百次游戏请求。

模块：identity（站内用户/角色）、eve-auth（授权与令牌）、esi-adapter（国服契约）、corporation、assets、structures、mining、members、sync、audit。

## 最小数据模型

| 实体 | 核心键与内容 |
|---|---|
| user / role / user_corporation_role | 站内身份和军团级访问范围 |
| eve_character / eve_authorization | server + character_id；corporation_id、scopes、加密令牌、失效状态、角色检查时间 |
| corporation | server + corporation_id；公开字段、授权状态 |
| sync_run / sync_page | 军团、资源、批次、状态、页数、错误、响应缓存元数据 |
| asset_snapshot / asset | server + corporation_id + batch_id + item_id；location_id、flag、type、quantity |
| structure / structure_service | server + corporation_id + structure_id；状态及可空时间字段 |
| moon_extraction | server + corporation_id + structure_id + extraction_start_time；moon_id 与到达/碎裂时间 |
| mining_observer / mining_record | 记录候选键：server、corporation_id、observer_id、last_updated、character_id、recorded_corporation_id、type_id；联调确认后固定 |
| corporation_member_snapshot | 批次 + character_id；追踪与角色字段分区授权 |
| universe_type / universe_location | 按 server + 版本组织的物品与位置字典；保留未知 ID |

业务 ID 在 Java 使用 Long，前端 API 统一以字符串传输标识符，避免 int64 精度风险。数量整数，金额 BigDecimal/数据库 numeric；游戏日期使用 UTC，前端默认 Asia/Shanghai，并明确时区。SSO 身份不能只以角色名识别。

## 访问控制与同步

每个资源请求同时验证站内角色、军团隔离、授权 scopes 和 EVE 当前角色资格。客户端传入 corporation_id 只是查询参数，服务端必须核验访问权。总监离团或权限被撤销后，停用其同步凭据，及时撤销对应站内敏感访问，不把过往管理员资格永久保留。

令牌加密保存，密钥由运行环境注入；Cookie 为 HttpOnly/Secure，state 一次性绑定浏览器会话。刷新按授权加锁，新 refresh_token 如返回则原子替换；日志只记脱敏错误码，绝不记录 token/code。已有元数据的 issuer 原样配置，不硬编码国际服 issuer，不仅解码 JWT 而不验签。

国际服与国服采用独立 ServerProfile：ESI base、datasource、SSO metadata、issuer、语言、路由版本、兼容日期策略、分页和限流能力。安全配置不接受用户随意填写任意地址作为令牌发送目标。

## 关键验收风险

重点测试跨军团越权、分页中断不覆盖完整快照、重复账本不重复累计、并发刷新、撤销/离团失效、结构字段缺失。版本锁定前验证 Java/Spring/数据库驱动/UI 的实际组合；这里没有声称已构建或运行。

参考：[Spring Boot 系统要求](https://docs.spring.io/spring-boot/system-requirements.html)、[Vue 3](https://vuejs.org/guide/introduction.html)、[Vue TypeScript](https://vuejs.org/guide/typescript/overview)、[官方 SSO](https://developers.eveonline.com/docs/services/sso/)。
