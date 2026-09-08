# 架构与技术候选

状态：技术框架已采纳，详见 [ADR-001](adr-001-continew.md)。前后端分离、模块化单体。

| 部分 | 已选方案 |
|---|---|
| 后端 | ContiNew Admin v4.1.0，Java 17、Spring Boot 3.3.12 |
| 站内认证 | Sa-Token；EVE OAuth 单独适配 |
| 前端 | ContiNew Admin UI v4.1.0，Vue 3、Arco Design、TypeScript、Vite、Pinia |
| 数据库 | MySQL 8.0.42 |
| ORM / 迁移 | MyBatis-Plus / Liquibase |
| 缓存 | Redis 7.2.8，Redisson / JetCache 沿用上游 |
| EVE 客户端 | 国服契约驱动的独立适配层；SSO、Token 与角色查询客户端已实现 |
| 调度 | 上游调度代码保留；EVE 授权定时复核已实现，业务数据同步留待后续范围 |

## 模块和数据流

浏览器访问自己的 API；Java 负责 SSO callback、游戏 API 与后台同步；页面查询本地快照，刷新按钮提交受限同步任务，不让每次打开页面都扇出数百次游戏请求。

当前模块：identity（站内用户/派生角色）、eve-auth（授权与令牌）、esi-adapter（国服契约）、corporation、permission、audit。assets、structures、mining、members 和业务数据 sync 属于后续范围。

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

重点测试跨军团越权、分页中断不覆盖完整快照、重复账本不重复累计、并发刷新、撤销/离团失效、结构字段缺失。已导入与构建的范围见 [验证记录](../VALIDATION.md)；真实登录和国服 ESI 联调仍待完成。

参考：[Spring Boot 系统要求](https://docs.spring.io/spring-boot/system-requirements.html)、[Vue 3](https://vuejs.org/guide/introduction.html)、[Vue TypeScript](https://vuejs.org/guide/typescript/overview)、[官方 SSO](https://developers.eveonline.com/docs/services/sso/)。
