# 参考资料目录

访问日期均为 2026-09-06。优先使用国服实际契约，其次官方国际服说明，再参考项目维护者自己的实现。历史资料不等于当前国服保证。

| 资料 | 用途 | 证据与局限 |
|---|---|---|
| [国服 Swagger](https://ali-esi.evepc.163.com/latest/swagger.json) | 核心 endpoint、scope、角色、字段、缓存和分页 | 本环境直接 HTTP 200，完整快照及哈希已保存；未实测授权资源 |
| [国服 SSO metadata](https://login.evepc.163.com/.well-known/oauth-authorization-server) | 登录/token/JWKS/撤销/PKCE | 本环境直接 HTTP 200；不证明应用注册已开放 |
| [官方 ESI 概览](https://developers.eveonline.com/docs/services/esi/overview/) | 国际服现行版本策略 | X-Compatibility-Date 不自动套用国服 |
| [官方 SSO](https://developers.eveonline.com/docs/services/sso/) | 理解授权码及验证步骤 | 域名、issuer 和实际声明使用国服值 |
| [官方限流说明](https://developers.eveonline.com/docs/services/esi/rate-limiting/) | 错误预算与限流设计参考 | 国服具体头、阈值和策略需观察 |
| [X-Pages](https://developers.eveonline.com/docs/services/esi/pagination/x-pages/) / [From-ID](https://developers.eveonline.com/docs/services/esi/pagination/from-id/) / [Cursor](https://developers.eveonline.com/docs/services/esi/pagination/cursor-based/) | 理解分页差异 | 只启用实际国服契约支持的模式 |
| [2026 缓存变化](https://developers.eveonline.com/blog/smarter-caching-when-events-drive-invalidation) | 避免永久硬编码固定刷新时长 | 国际服官方 2026-01-27 说明，不推断国服已同步 |
| [Mining Ledgers in ESI](https://developers.eveonline.com/blog/mining-ledgers-in-esi) | 观察者含义、非实时、历史归属及窗口 | 官方 2017 说明；保留期限在国服再核验 |
| [Pyfa 配置](https://github.com/pyfa-org/Pyfa/blob/master/config.py) | 国服域名独立配置案例 | 项目维护者的一手实现；不复用它的 client_id |
| [国服 Android 登录 Demo](https://github.com/zhkrb/Eve-Android-loginDemo) | 国服历史登录实现线索 | 历史实验，不作为 Web 生产授权模板 |
| [国服余额脚本](https://github.com/MCTBL/EVE_Tools/blob/master/balance.py) | 国服 token/刷新实现线索 | 历史项目，不证明本项目授权可用 |
| [SeAT 官方介绍](https://developers.eveonline.com/docs/community/seat/) / [仓库](https://github.com/eveseat/seat) | 军团管理、同步、站内 RBAC 与功能组织 | 可借鉴产品和模块，不要求改用其技术栈 |
| [aa-structures 操作手册](https://aa-structures.readthedocs.io/en/latest/operations.html) | 建筑数据、权限与通知组织 | 插件作者文档；国服兼容性未知 |
| [aa-moonstuff](https://pypi.org/project/aa-moonstuff/) | 月矿/扫描数据补全与 scope 组合 | 发布者说明；国服兼容性未知 |
| [Q.Industrialist](https://github.com/Qandra-Si/q.industrialist) | 工业、库存与角色区分 | 项目实现参考；不照搬部署 |

上游许可证逐项目核对；当前资料包没有复制上述项目的业务代码。references 中的机器接口定义仅作为上游契约快照，来源、日期、哈希独立保存，不能当作本项目原创 API。

## 尚缺的证据

国服应用自助注册/审批的一手指南；真实国服 CEO/总监授权返回；全部核心接口业务响应；国服账本实际回溯长度；国服新版兼容日期和限流演进情况；可用的国服 SDE/本地化与市场估值数据版本。公开资料不足的部分不补造结论。

用户补充的国服交互文档入口：[Swagger UI](https://ali-esi.evepc.163.com/ui/)。本次检索抓取器未能解析 UI 页面；契约证据仍来自上一轮直接取得的 JSON 快照，不能以 UI 抓取失败判断接口不可用。
