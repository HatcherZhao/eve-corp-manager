# 国服接口调研

调研日期：2026-09-06。这里区分实际取得的公开资料、参考资料和未完成的账号验证。

## 直接验证结果

| 地址 | 本次结果 | 结论 |
|---|---|---|
| https://ali-esi.evepc.163.com/latest/swagger.json | HTTP 200；885984 字节 | 已取得国服真实接口定义；host 为 ali-esi.evepc.163.com，basePath 为 /latest |
| https://login.evepc.163.com/.well-known/oauth-authorization-server | HTTP 200；652 字节 | 已取得国服授权元数据 |
| https://esi.evepc.163.com/latest/swagger.json | 本环境 HTTP 503 | 本次不可用，不能推断永久失效 |
| https://esi.evetech.net/latest/swagger.json | 本环境超时 | 未取得最新国际服完整契约，不声称完成两服逐字段差异比较 |

网页检索抓取器访问国服地址失败，但运行环境的直接 HTTPS GET 成功。以上结论来自直接请求，不把检索抓取失败等同于服务不存在。响应快照保存在 references；未调用任何需授权的军团接口。

## 国服契约确认

- Swagger 2.0；info.version=1.19。这是文档自己的版本标识，不是客户端游戏版本，也不是统一的路径版本。
- datasource 默认 serenity，枚举为 serenity / infinity。本项目只使用 serenity；不把 infinity 视为国际服。
- 国服提供 /latest 与各路由自己的 /vN 别名。生成客户端前应固定快照并记录路由版本，不能默认永久跟随 latest。
- 月矿三个路径使用 `/corporation/{corporation_id}/mining/`（单数）；资产、建筑及人员使用 `/corporations/`（复数）。
- 多个路径提供 Cache-Control、ETag、Expires、Last-Modified；分页能力逐路由检查，不全局假定每个接口都支持 page。
- 语言枚举必须按国服端点定义读取，例如建筑响应语言为 en / zh，不能把国际服语言配置照搬。

## SSO 确认及未决点

国服元数据返回 issuer 为 `login.evepc.163.com`，授权端点为 `/v2/oauth/authorize`，token 为 `/v2/oauth/token`，JWKS 为 `/oauth/jwks`，撤销为 `/v2/oauth/revoke`。声明 response_types 支持 code 与 token，PKCE 方法为 S256。Swagger 中的 implicit 描述不是国服只支持隐式流的证据；应综合 SSO 元数据，优先做授权码验证。

公开元数据不能证明自己的应用已注册，也不能证明 refresh_token 行为。仍需确认：开发者注册入口与申请条件；自有 client_id / secret；允许的 HTTPS callback；可申请 scopes；授权、刷新和撤销的真实返回；JWT aud/scp/sub/owner 等实际声明及角色换主处理。未找到足以确认国服自助注册流程的一手文档。国际服开发者后台注册的应用不能直接假设在国服有效。

## 国际服参考如何使用

国际服官方 ESI 文档可用于理解 OAuth、分页与错误处理；现行文档已引入 X-Compatibility-Date，2026 年缓存博客也说明部分缓存采用事件失效。国服当前取得的契约仍是版本路径与缓存时长描述，因此两服使用独立配置，不能直接套用国际服新的兼容日期、限流桶及缓存规则。

Pyfa 的维护者配置直接包含国服 login.evepc.163.com 和 ali-esi.evepc.163.com，是域名线索的一手项目证据；Android 登录 Demo 仅作为历史实现线索，不采用其中借用应用 ID、拦截其他应用回调等方案。

## 重要限制

军团资产不包含成员全部个人资产。月矿拉取计划不含完整矿物组成、剩余矿带量，也不能直接启动/引爆月矿。观察者账本不是全团全地图采矿监控，且可能出现外团采矿角色。成员追踪是带缓存的记录，不能包装成实时在线人数或精确在线时长。所有权限最终以国服实际调用及当前角色状态为准。

来源：[国服契约](https://ali-esi.evepc.163.com/latest/swagger.json)、[国服 SSO](https://login.evepc.163.com/.well-known/oauth-authorization-server)、[Pyfa](https://github.com/pyfa-org/Pyfa/blob/master/config.py)、[国际服 ESI](https://developers.eveonline.com/docs/services/esi/overview/)、[缓存变化](https://developers.eveonline.com/blog/smarter-caching-when-events-drive-invalidation)。
