# EVE Corp Manager · 国服军团管理平台

面向网易 EVE Online 国服（Serenity）的 Web 军团管理项目。通过 CEO / 总监等有权限角色的官方 SSO 授权，汇总军团资产、建筑、月矿及人员数据。

**阶段：接口调研与产品设计；尚未实现应用，尚未完成真实角色授权联调。** 前后端分离已确定；后端倾向 Java，前端 Vue 3，具体组件版本未定。

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

登录交互按用户指定的 EIMS 分步授权与回调网址粘贴方案设计，详见登录方案；先核实接入方式并完成一名角色的纵向验证，再实现 Web 页面。不要借用 Swagger UI、Pyfa 或其他第三方应用的 client_id / callback。

公开仓库只保存代码、公开接口资料和虚构样例；真实军团数据、账号凭据、令牌与日志不入库。本项目为非官方工具，与网易、CCP 无隶属关系。项目许可证待维护者选择；公开仓库不等于自动获得开源使用许可，引用的上游资料保持各自权利。
