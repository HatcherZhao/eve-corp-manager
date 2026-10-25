# 外部 Agent 接入验收记录

日期：2026-10-25。代码已实现并完成以下本地验证；本记录不代表已部署生产，未创建生产应用密钥。

## 后端构建与测试

Java 17.0.18，Maven 3.9.12，在 `backend/` 执行：

```bash
rtk proxy env JAVA_HOME=/Users/yuqingzhao/.jenv/versions/17.0.18 \
  /Users/yuqingzhao/development/maven/apache-maven-3.9.12/bin/mvn \
  -B -ntp -Pfat_jar -pl continew-server -am \
  -Dspotless.apply.skip=true package
```

- 全模块构建及 fat JAR 打包成功。
- EVE 模块 306 项、Server 模块 71 项测试通过，失败、错误和跳过均为 0。
- 新增授权服务 9 项、查询服务 12 项、MCP 过滤器 6 项、真实 HTTP 协议 3 项测试通过。
- CEO/总监首次创建角色具有 Agent 管理菜单，普通成员不具有。
- 限定 Agent 文件执行 Spotless，不格式化无关历史代码；`git diff --check` 通过。
- 构建日志：`tmp/g015-backend-verification.log`。

## 真实 MySQL

显式提供本地 `G008_IT_DB_HOST/PORT/USER/PWD`，不把密码写入脚本或文档：

```bash
rtk proxy env JAVA_HOME=/Users/yuqingzhao/.jenv/versions/17.0.18 \
  /Users/yuqingzhao/development/maven/apache-maven-3.9.12/bin/mvn \
  -B -ntp -pl continew-server -am -Dspotless.apply.skip=true \
  -Dtest=EveAgentAuthorizationPersistenceIT,EveAgentQueryPersistenceIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- 授权及验证码迁移持久化 2 项、业务查询 5 项测试通过，未跳过。
- 随机独立 schema 上完整 Liquibase 63 个 changeset 执行成功，包含新增 054、055。
- 原始密钥不落库；无网页登录身份时可认证并写入脱敏审计；轮换、撤销和最近访问时间有效。
- 42 个数据集的投影与聚合 SQL 均在真实 MySQL 执行成功。
- 邮件/通知跨军团、跨租户、缺少 Scope、撤销授权均不可读取。
- 长正文搜索、数据库切片、Unicode 续读、查询文本预算及聚合隔离通过。
- 查询使用连接私有临时表，未写入业务表；授权测试只删除自己成功创建的随机 schema。结束后无测试 schema 遗留。
- 日志：`tmp/g015-mysql-verification.log`。

## 协议与整站启动

- 官方 MCP Java Client 通过真实 Undertow HTTP 完成初始化、工具发现、查询、汇总和正文读取。
- 两个军团客户端并发调用，SDK 异步工作线程上的身份与审计仍分别对应原军团。
- JSON Schema 拒绝客户端附加 `tenantId`；撤销后既有客户端不能继续查询。
- `2025-06-18` 原始 HTTP 回归验证超过 JavaScript 安全整数范围的 JSON-RPC 数字 ID 仍以数字往返。
- 不完整请求体在阻塞读取等待超时后断开，不进入业务查询。
- 使用完整 fat JAR 和独立临时库启动整站，在正式 Filter、Servlet、MyBatis、JDBC、审计链路上验证：无凭据 401；合成应用查询军团资料 200；伪造租户头不改变应用身份；目录返回 42 个只读数据集；撤销后 401；两次调用审计成功落库。
- 本地启动库仅含合成数据，管理员登录设为不可用；验证后后端停止、临时库精确删除，未保留合成凭据。

SDK 声明支持 `2025-03-26`、`2025-06-18`、`2025-11-25`；本次验证 SDK 客户端协商和后两种原始 HTTP 调用，不宣称已逐一验证所有第三方客户端。

## 前端

- Node.js 22.23.2，`pnpm lint` 通过，保留既有 `useMultipartUploader.ts:40` JSDoc 警告。
- `pnpm build` 内的 `vue-tsc --noEmit` 与 Vite 生产构建通过；保留既有 Vite CJS 与大 chunk 提示。
- 真实 Vue 组件配合合成接口数据验证创建确认、一次性密钥遮罩、复制、更换、撤销、审计、错误态与重试。
- 关闭凭据弹窗后密钥从 DOM 移除；localStorage/sessionStorage 不保存密钥。
- 320、375、768、1440 像素宽度无页面横向溢出；已查看窄屏凭据弹窗和桌面列表截图。
- 截图：`tmp/g015-agent-modal-320.png`、`tmp/g015-agent-list-1440.png`。
- 验收 fixture：`scripts/g015/01-agent-ui-check.html`，不放入前端生产目录。

## 边界与遗留风险

- 本地既有业务库存在历史 `047_industry.sql` checksum 不一致，默认本地后端启动受阻。本次没有修改历史 changeset、清 checksum 或绕过迁移；独立临时库验证已通过。不能把本次结果等同于该旧库已修复。
- 本地前端可在 `http://127.0.0.1:5173/` 打开，但上述旧库问题解决前，不提供连到该业务库的完整登录验收。
- 邮件仅限当前军团有效授权角色的已缓存资料；不补抓缺失正文、不标已读、不发送邮件。
- 已按用户指定的 AstrBot 官方文档补充 Streamable HTTP 配置和人格能力设置说明；未修改线上 AstrBot，也未进行真实 QQ 群转发测试。机器人应在可信代码中控制群成员及邮件转发权限。
- 凭据模式不是完整 OAuth 授权服务器；多实例扩容前需共享限流；分页不提供跨请求快照一致性。
- 尚未进行生产反代、HTTPS、真实军团账号及线上权限菜单验收。

## 登录验证码关闭回归

- 新增 055 迁移，同时将 `LOGIN_CAPTCHA_ENABLED` 当前值和默认值设为 `0`，不修改历史迁移。
- 启动时仅失效 `OPTION:LOGIN_CAPTCHA_ENABLED`，保留其他配置、会话和共享 Redis 数据。已有菜单缓存失效任务也有针对性回归测试。
- 后端 6 项回归验证关闭时不生成验证码、不要求验证码参数，重新开启后仍要求验证码，错误密码仍计数并拒绝登录。
- 真实登录 Vue 组件配合合成接口验证：关闭时空验证码可提交；启用时空验证码阻止提交；再次关闭清除旧 UUID、验证码和定时器。未创建真实登录会话。
- 375 像素宽度无横向溢出；验收 fixture 位于 `scripts/g016/01-login-ui-check.html`，临时副本不进入生产构建。
- 最新日志：`tmp/g016-captcha-tests.log`、`tmp/g016-mysql-it.log`、`tmp/g016-backend-build.log`。
