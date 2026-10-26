"""在 AstrBot 工具层强制执行 QQ 会话到 EVE 军团的授权边界。"""

from __future__ import annotations

import json
from typing import Any

import mcp.types

from astrbot.api import AstrBotConfig, FunctionTool
from astrbot.api.event import AstrMessageEvent, filter
from astrbot.api.provider import ProviderRequest
from astrbot.api.star import Context, Star, register
from astrbot.core.agent.mcp_client import MCPTool

from .policy import (
    CORPORATION_LIST_TOOL,
    PROTECTED_TOOLS,
    AccessDeniedError,
    CorpAccessPolicy,
    PolicyConfigurationError,
)


EVE_TOOLS = PROTECTED_TOOLS | {CORPORATION_LIST_TOOL}


class GuardedEveMcpTool(FunctionTool):
    """保留原 MCP 连接，但在调用前后强制应用当前消息的军团策略。"""

    def __init__(self, plugin: "EveCorpGuardPlugin", upstream: FunctionTool, source: MCPTool) -> None:
        super().__init__(
            name=upstream.name,
            description=upstream.description,
            parameters=upstream.parameters,
        )
        self._plugin = plugin
        self._upstream = upstream
        self._source = source

    async def call(self, context, **kwargs):
        """使用执行上下文中的真实会话来源授权，而不是相信提示词。"""
        event = context.context.event
        origin = event.unified_msg_origin
        try:
            policy = self._plugin.current_policy()
            arguments = policy.authorize(origin, self.name, kwargs)
        except (PolicyConfigurationError, AccessDeniedError) as exc:
            return _error_result(str(exc))

        result = await self._upstream.call(context, **arguments)
        if self.name != CORPORATION_LIST_TOOL or result.isError:
            return result
        try:
            payload = _extract_structured_payload(result)
            filtered = policy.filter_corporations(origin, payload)
        except (ValueError, AccessDeniedError) as exc:
            return _error_result(str(exc))
        return mcp.types.CallToolResult(
            content=[
                mcp.types.TextContent(
                    type="text",
                    text=json.dumps(filtered, ensure_ascii=False, separators=(",", ":")),
                )
            ],
            isError=False,
        )


@register(
    "astrbot_plugin_eve_corp_guard",
    "zhaoyuqing",
    "按真实 QQ 会话来源隔离同一 EVE MCP 应用的多军团查询",
    "1.0.0",
)
class EveCorpGuardPlugin(Star):
    """用请求级工具替换避免模型或正文绕过军团白名单。"""

    def __init__(self, context: Context, config: AstrBotConfig) -> None:
        super().__init__(context)
        self.context = context
        self.config = config

    def current_policy(self) -> CorpAccessPolicy:
        """每次工具调用读取当前配置，使 WebUI 保存后立即生效。"""
        return CorpAccessPolicy.from_json(self.config.get("session_bindings_json", "{}"))

    @filter.on_llm_request()
    async def replace_eve_tools(self, event: AstrMessageEvent, request: ProviderRequest) -> None:
        """在每次 LLM 请求中移除原始 EVE MCP 工具并换成受控代理。"""
        if request.func_tool is None:
            return
        server_name = str(self.config.get("mcp_server_name", "eve")).strip()
        candidates = list(request.func_tool.tools)
        # 先从本次请求移除原始工具；后续发现或包装失败时也保持封闭拒绝。
        for name in EVE_TOOLS:
            request.func_tool.remove_tool(name)
        for tool in candidates:
            if tool.name not in EVE_TOOLS:
                continue
            if isinstance(tool, GuardedEveMcpTool):
                source = tool._source
            else:
                source = _find_source_mcp_tool(tool)
            if source is None or source.mcp_server_name != server_name:
                continue
            wrapper = tool if isinstance(tool, GuardedEveMcpTool) else GuardedEveMcpTool(self, tool, source)
            request.func_tool.add_tool(wrapper)

    @filter.command("eve_access_origin")
    async def show_access_origin(self, event: AstrMessageEvent):
        """显示当前会话的精确授权键，仅 AstrBot 管理员可用。"""
        if not event.is_admin():
            yield event.plain_result("仅 AstrBot 管理员可以查看 EVE 会话授权键。")
            return
        yield event.plain_result(f"当前会话授权键：{event.unified_msg_origin}")


def _extract_structured_payload(result: mcp.types.CallToolResult) -> Any:
    """优先读取结构化结果，文本只接受完整 JSON。"""
    structured = getattr(result, "structuredContent", None)
    if structured is not None:
        return structured
    text_parts = [item.text for item in result.content if isinstance(item, mcp.types.TextContent)]
    if not text_parts:
        raise ValueError("军团目录没有可验证的 JSON 数据，已拒绝返回")
    try:
        return json.loads("".join(text_parts))
    except json.JSONDecodeError as exc:
        raise ValueError("军团目录不是有效 JSON，已拒绝返回") from exc


def _find_source_mcp_tool(tool: FunctionTool) -> MCPTool | None:
    """只沿当前请求工具的透明包装链查找原始 MCP 工具。"""
    current: Any = tool
    visited: set[int] = set()
    while id(current) not in visited:
        visited.add(id(current))
        if isinstance(current, MCPTool):
            return current
        current = getattr(current, "_wrapped", None)
        if current is None:
            return None
    return None


def _error_result(message: str) -> mcp.types.CallToolResult:
    """构造不会包含上游数据的封闭拒绝结果。"""
    return mcp.types.CallToolResult(
        content=[mcp.types.TextContent(type="text", text=f"访问被拒绝：{message}")],
        isError=True,
    )
