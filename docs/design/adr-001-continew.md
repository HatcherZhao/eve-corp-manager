# ADR-001：采用 ContiNew 前后端框架

状态：已采纳。日期：2026-09-06。决策来源：用户明确指定 continew-admin 与 continew-admin-ui。

## 固定基线

| 层 | 选择 |
|---|---|
| 后端源码 | continew-org/continew-admin v4.1.0，提交 e0e3294b3d56c114e2afbc36aac38506fdcd0d0d |
| 前端源码 | continew-org/continew-admin-ui v4.1.0，提交 ecabeac07e5e0dec8d2f5aae52ad4273d830c2fb |
| Java / 构建 | Java 17；Maven 3.9.11，采用上游 fat_jar profile |
| Spring Boot / Starter | Spring Boot 3.3.12；ContiNew Starter 2.14.0（以解析的 Maven BOM 为准） |
| 站内身份 | Sa-Token 1.44.0，沿用用户、角色、权限与菜单实现 |
| 持久化 / 迁移 | MyBatis-Plus 3.5.12 + Liquibase，不引入 JPA/Flyway |
| 数据库 / 缓存 | MySQL 8.0.42 + Redis 7.2.8，沿用上游示例版本；Redisson、JetCache 保持框架管理版本 |
| 前端 | Vue 3.5.12、Arco Design 2.57.0、TypeScript 5.0.4、Vite 5.2.11、Pinia 2.1.7；精确依赖由 pnpm-lock.yaml 固定 |
| 前端运行时 | Node.js 22、pnpm 9.15.9 |
| 仓库组织 | backend/、frontend/、docs/、deploy/、scripts/ 的单仓库，前后端独立构建 |

采用成对发布标签，不使用两个仓库的 dev/SNAPSHOT。源码直接纳入本仓库，开发无需初始化 submodule，也不要求另克隆两个基础框架。保留 top.continew.admin 包名和原模块名称以降低上游合并成本；项目显示名称改为 EVE Corp Manager。

## 框架复用与业务隔离

复用账号登录、RBAC、动态菜单、字典、参数配置、日志、文件、代码生成等能力。新增 continew-eve 模块承载 EVE 领域服务与模型，Controller 在 continew-server，前端业务目录为 src/apis/eve 和 src/views/eve。

`eve:workspace:view` 等站内权限只决定页面和功能访问；EVE 能力还会同时校验服务端确定的军团租户、授权 Scope 与当前游戏角色，不能把系统角色、ContiNew 租户与 EVE 军团混为一类。local profile 已启用租户隔离；请求头篡改、跨租户绑定和授权归属已有自动化测试，真实数据库集成测试验证了租户字段、Token 密文和 Scope 持久化。

EVE OAuth 与站内 Sa-Token 分开：验证网易角色后关联本站身份，再签发本站令牌；EVE access_token/refresh_token 不用作站内通行令牌。授权回调、Token 生命周期和手工导入流程已有自动化覆盖，但真实国服应用凭据及账号场景仍待上线前联调；未配置凭据时不会产生虚假的 SSO 成功路径。

## 已完成的项目化调整

- EVE 工作台接口、Vue 页面、菜单/权限及站点名称 Liquibase 增量迁移。
- EVE 国服注册、绑定、找回密码、主动刷新、重新授权、派生角色、权限树及授权定时复核。
- local 配置、仅绑定回环地址的数据库/Redis Compose、Python 初始化/启动脚本。
- 运行环境注入随机管理员初始密码、数据库、Redis、JWT、字段加密/RSA 密钥；前端独立 public key 配置。
- 移除上游网页统计脚本、演示数据重置任务、示例第三方 OAuth 配置；生产 API 改用同源 /api，不再调用上游演示服务。
- 保留版权及许可证，记录来源；不复制上游发布流水线及演示部署脚本。
- GitHub Actions 前后端构建检查。

本决策取代 architecture.md 中尚未确定的 Spring Security、JPA、Flyway、PostgreSQL 等候选项。后续升级需同时验证前后端 API、ContiNew Starter、数据库迁移及锁文件。

来源：[后端标签](https://github.com/continew-org/continew-admin/tree/v4.1.0)、[前端标签](https://github.com/continew-org/continew-admin-ui/tree/v4.1.0)、[Starter BOM](https://repo.maven.apache.org/maven2/top/continew/starter/continew-starter-dependencies/2.14.0/continew-starter-dependencies-2.14.0.pom)。
