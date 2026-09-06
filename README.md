# EVE Corp Manager · 国服军团管理平台

面向网易 EVE Online 国服（Serenity）的 Web 军团管理项目。通过 CEO / 总监等有权限角色的官方 SSO 授权，汇总军团资产、建筑、月矿及人员数据。

**阶段：ContiNew 前后端骨架已建立；EVE 真实角色授权与数据同步尚未实现。**

后端基于 ContiNew Admin v4.1.0（Java 17 / Spring Boot / Sa-Token / MyBatis-Plus / Liquibase），前端基于配套 ContiNew Admin UI v4.1.0（Vue 3 / Arco Design / TypeScript / Vite）。

## 启动与开发

- [本地启动说明](docs/DEVELOPMENT.md)
- [技术选型决策及固定版本](docs/design/adr-001-continew.md)
- [构建验证记录](docs/VALIDATION.md)
- [上游来源与修改说明](UPSTREAM.md)

backend/ 与 frontend/ 已包含实际源码，无需另行克隆上游项目。先生成本地配置并启动 MySQL/Redis，再构建运行后端和前端。登录使用本站管理账号；「EVE 军团 → 军团工作台」当前展示尚未开放的接入状态。

## 调研结论（2026-09-06）

- 已直接取得国服 `https://ali-esi.evepc.163.com/latest/swagger.json`，Swagger 2.0、接口文档版本 1.19、189 个路径；快照与 SHA-256 存于 references。
- 已取得国服 SSO 元数据，声明支持授权码、S256 PKCE、令牌及撤销端点。
- 核心需求均存在对应国服接口定义，但“文档存在”不代表已完成实际数据调用验证。
- 国服应用注册入口、独立 client_id / 回调注册及令牌刷新联调仍是首要待办。
- 不能只替换国际服域名：datasource、SSO issuer、接口版本、语言、缓存和可用能力均须按国服配置。

## 文档导航

1. [国服与国际服调研](docs/research/serenity-and-esi.md)
2. [国服接口权限矩阵（从快照生成）](docs/research/api-matrix.md)
3. [参考资料及可信度](docs/research/sources.md)
4. [产品范围](docs/design/product-scope.md)
5. [架构与技术候选](docs/design/architecture.md)
6. [授权、同步与验收](docs/integration/validation-plan.md)
7. [开发待办](docs/ROADMAP.md)
8. [登录交互：参考 EIMS](docs/design/login-flow.md)
9. [发布说明](docs/PUBLISH.md)

## 下一步

登录交互按用户指定的 EIMS 分步授权与回调网址粘贴方案设计，详见登录方案；先核实接入方式并完成一名角色的纵向验证，再实现真实游戏业务页面。不要借用 Swagger UI、Pyfa 或其他第三方应用的 client_id / callback。

公开仓库只保存代码、公开接口资料和虚构样例；真实军团数据、账号凭据、令牌与日志不入库。本项目为非官方工具，与网易、CCP 无隶属关系。项目源码采用 Apache-2.0，保留上游版权与许可证；依赖及引用资料保持各自权利，详见 UPSTREAM.md。
