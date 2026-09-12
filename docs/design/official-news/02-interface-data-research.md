# 国服官网资讯：接口与页面资料

> 资料来源：`https://evepc.163.com/news/`（2026-09-12 实测）

官网没有公开新闻 API；栏目页与文章页为服务端渲染 HTML，可由普通 HTTPS 客户端获取。

| 栏目 | 地址 | 解析结构 |
| --- | --- | --- |
| 最新 | `/news/` | `ul.list` 下的文章链接 |
| 新闻 | `/news/official/` | 链接标题、`kindname`、`comment` |
| 维护 | `/news/update/` | 同上 |
| 更新通知 | `/news/meiti/` | 同上 |
| 版本更新 | `/updates/` | 同上 |

文章页字段：标题为 `h1.artTitle`，发布日期为 `span.artDate`，正文容器为 `div.artText`。文章链接仅接受 `https://evepc.163.com/news/...` 或 `https://evepc.163.com/updates/...`；正文图片仅允许 `evepc.163.com`、`nie.res.netease.com`、`xz.res.netease.com` 的 HTTPS 地址。

示例文章：`https://evepc.163.com/news/20260827/38679_1312460.html`。
