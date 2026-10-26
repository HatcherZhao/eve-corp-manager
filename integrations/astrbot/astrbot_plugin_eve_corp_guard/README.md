# EVE 多军团会话权限插件

此插件让一个 AstrBot、一个本站 MCP 地址和一个应用密钥服务多个已授权军团，同时在 AstrBot 的可信代码层把 QQ 会话限制到指定军团。提示词、用户消息、邮件正文和技能说明都不能扩大该白名单。

## 安全边界

- 以 `event.unified_msg_origin` 作为精确会话键，其中包含平台实例、消息类型和群号或私聊会话 ID。
- 未配置的群、私聊和其他来源默认拒绝所有 EVE MCP 工具。
- `eve_catalog`、`eve_query`、`eve_aggregate`、`eve_read_document` 的 `corporationId` 由插件校验并强制注入。
- `eve_corporations` 的结果会在进入模型上下文前过滤，群成员看不到该群白名单之外的军团。
- 插件只包装当前人格和当前请求本来就启用的 EVE MCP 工具；人格禁用的工具不会从全局工具管理器重新加入。
- 当前请求中的原始 EVE MCP 工具会被调用同一 MCP 客户端连接的受控代理替换，避免原始工具形成旁路。

不要同时向人格或其他插件暴露第二套指向本站的 EVE MCP 连接。AstrBot 管理员仍应限制谁能修改插件配置和 `mcp_server.json`。

## 兼容范围

实现按 AstrBot 官方 `master` 分支在 2026-10-26 的接口核验，并声明 `AstrBot >= 4.23.2,<5`：

- `ProviderRequest.func_tool` 为请求级 `ToolSet`；
- `MCPTool` 保存原 MCP 客户端且 `call()` 复用现有连接；
- `@filter.on_llm_request()` 可在模型看到工具前替换请求工具集；
- Agent Runner 执行时按名称从本次 `req.func_tool` 取工具，不会回到全局管理器重新解析同名工具；
- 插件配置使用 `_conf_schema.json`，元数据使用 `metadata.yaml`。

核验依据：

- [AstrBot v4.23.2 Agent Runner 从 `req.func_tool` 取执行工具](https://github.com/AstrBotDevs/AstrBot/blob/v4.23.2/astrbot/core/agent/runners/tool_loop_agent_runner.py#L917-L930)
- [AstrBot v4.23.2 `ToolSet` 的请求级增删与查找实现](https://github.com/AstrBotDevs/AstrBot/blob/v4.23.2/astrbot/core/agent/tool.py#L78-L119)
- [AstrBot v4.23.2 `MCPTool.call()` 复用其 `mcp_client`](https://github.com/AstrBotDevs/AstrBot/blob/v4.23.2/astrbot/core/agent/mcp_client.py#L596-L619)

升级 AstrBot 大版本后应重新运行仓库中的 `scripts/g020/01-test-corp-guard.py` 并核对上述接口。

## 安装

1. 将整个 `astrbot_plugin_eve_corp_guard` 目录复制到 AstrBot 的 `data/plugins/`，或压缩该目录后通过 WebUI 安装插件。
2. 在 AstrBot 的 MCP 配置中只保留一个本站服务，例如名称为 `eve`；地址和 Bearer 密钥仍按本站“外部 Agent”页面生成的配置填写。
3. 重启或重载插件，确认本站的五个工具已连接：`eve_corporations` 以及四个查询工具。
4. 在目标 QQ 群执行 `/eve_access_origin`。此命令只向 AstrBot 管理员显示精确授权键。
5. 在 WebUI 的插件配置中填写会话白名单并保存。

插件目录本身不包含 MCP 密钥、QQ 群号或真实军团 ID。

## 配置示例

以下均为虚构值。键必须使用机器人实际返回的完整字符串，不能只写群号，也不支持通配符。

```json
{
  "napcat-main:GroupMessage:100001": {
    "allowedCorporationIds": ["98000001"],
    "defaultCorporationId": "98000001"
  },
  "napcat-main:GroupMessage:100002": {
    "allowedCorporationIds": ["98000002"],
    "defaultCorporationId": "98000002"
  },
  "napcat-main:GroupMessage:100099": {
    "allowedCorporationIds": ["98000001", "98000002"]
  }
}
```

- 普通军团群设置一个允许军团和同一个默认军团，玩家无需每次写军团名。
- 管理群可允许多个军团；若不设置默认军团，模型调用时必须明确传入 `corporationId`。
- 私聊只有显式加入精确会话键后才能访问；默认始终拒绝。
- 军团 ID 从本站 `eve_corporations` 返回或本站管理页面取得，不能使用名称代替。

## 验收

安装后至少验证：

1. A 群不指定军团时只能得到 A 军团数据。
2. A 群明确要求 B 军团时返回“访问被拒绝”，且本站访问记录中没有 B 军团查询。
3. 未绑定群和私聊均被拒绝。
4. 管理群可以在允许列表内切换，但无法查询列表外军团。
5. `eve_corporations` 在每个群只显示该群允许的军团。
