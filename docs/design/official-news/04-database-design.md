# 国服官网资讯：数据库设计

## 来源映射

| 来源 | 来源字段 / 结构 | 目标表.字段 | 转换规则 |
| --- | --- | --- | --- |
| 官网栏目页 | 链接 `title` | `eve_official_news.title` | HTML 实体解码、去标签 |
| 官网栏目页 | 链接 `href` | `original_url` | 相对地址补全，固定域名与路径校验，唯一索引去重 |
| 官网栏目页 | `kindname`、`comment` | `source_category`、`summary` | 缺省时使用固定栏目名称 |
| 官网文章页 | `artTitle`、`artDate`、`artText` | `title`、`published_at`、`content_html` / `content_text` | 日期解析；正文白名单净化并提取纯文本 |
| 系统同步 | 本次检查结果 | `eve_official_news_sync_state` | 按栏目保存成功、失败、新发现数量 |

## 表与索引

`eve_official_news` 使用长整型主键，`original_url` 唯一；`(source_code, published_at, id)` 支持版本/栏目分页，`(published_at, id)` 支持新闻时间倒序。`content_hash` 用于识别官网修订，正文使用 `MEDIUMTEXT`，不放入前端列表响应。

`eve_official_news_sync_state` 以 `source_code` 为主键，一栏目一行。两表均没有 `tenant_id`，Mapper 忽略租户拦截器；公开内容跨租户共享，菜单权限仍按当前租户角色控制。
