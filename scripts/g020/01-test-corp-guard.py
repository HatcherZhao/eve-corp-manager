#!/usr/bin/env python3
"""验证 AstrBot 多军团会话策略的封闭授权和并发隔离。"""

from __future__ import annotations

import asyncio
import importlib.util
import json
import sys
import types
import unittest
from pathlib import Path

import mcp.types


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
POLICY_FILE = ROOT / "integrations/astrbot/astrbot_plugin_eve_corp_guard/policy.py"
SPEC = importlib.util.spec_from_file_location("eve_corp_guard_policy", POLICY_FILE)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("无法加载军团访问策略")
POLICY = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = POLICY
SPEC.loader.exec_module(POLICY)


def _install_astrbot_test_doubles() -> None:
    """提供最小 AstrBot 接口替身，验证插件逻辑而不安装完整机器人。"""

    class FakeFunctionTool:
        def __init__(self, name, description, parameters, **_kwargs):
            self.name = name
            self.description = description
            self.parameters = parameters
            self.handler = None
            self.active = True

    class FakeMcpTool(FakeFunctionTool):
        def __init__(self, name, result, server_name="eve"):
            super().__init__(name, "测试工具", {"type": "object", "properties": {}})
            self.mcp_server_name = server_name
            self.result = result
            self.calls = []

        async def call(self, _context, **kwargs):
            self.calls.append(kwargs)
            return self.result

    class FakeToolSet:
        def __init__(self, tools):
            self.tools = list(tools)

        def remove_tool(self, name):
            self.tools = [tool for tool in self.tools if tool.name != name]

        def add_tool(self, tool):
            self.remove_tool(tool.name)
            self.tools.append(tool)

        def get_tool(self, name):
            return next((tool for tool in self.tools if tool.name == name), None)

    class FakePermissionWrapper(FakeFunctionTool):
        def __init__(self, wrapped):
            super().__init__(wrapped.name, wrapped.description, wrapped.parameters)
            self._wrapped = wrapped

        async def call(self, context, **kwargs):
            return await self._wrapped.call(context, **kwargs)

    class FakeStar:
        def __init__(self, context):
            self.context = context

    class FakeFilter:
        @staticmethod
        def on_llm_request():
            return lambda func: func

        @staticmethod
        def command(_name):
            return lambda func: func

    api = types.ModuleType("astrbot.api")
    api.AstrBotConfig = dict
    api.FunctionTool = FakeFunctionTool
    api.logger = types.SimpleNamespace(warning=lambda *_args, **_kwargs: None)
    event = types.ModuleType("astrbot.api.event")
    event.AstrMessageEvent = object
    event.filter = FakeFilter
    provider = types.ModuleType("astrbot.api.provider")
    provider.ProviderRequest = object
    star = types.ModuleType("astrbot.api.star")
    star.Context = object
    star.Star = FakeStar
    star.register = lambda *_args, **_kwargs: (lambda cls: cls)
    mcp_client = types.ModuleType("astrbot.core.agent.mcp_client")
    mcp_client.MCPTool = FakeMcpTool
    modules = {
        "astrbot": types.ModuleType("astrbot"),
        "astrbot.api": api,
        "astrbot.api.event": event,
        "astrbot.api.provider": provider,
        "astrbot.api.star": star,
        "astrbot.core": types.ModuleType("astrbot.core"),
        "astrbot.core.agent": types.ModuleType("astrbot.core.agent"),
        "astrbot.core.agent.mcp_client": mcp_client,
    }
    sys.modules.update(modules)
    globals().update(
        FakeMcpTool=FakeMcpTool,
        FakePermissionWrapper=FakePermissionWrapper,
        FakeToolSet=FakeToolSet,
        FakeFunctionTool=FakeFunctionTool,
    )


_install_astrbot_test_doubles()
PLUGIN_SPEC = importlib.util.spec_from_file_location(
    "integrations.astrbot.astrbot_plugin_eve_corp_guard.main",
    ROOT / "integrations/astrbot/astrbot_plugin_eve_corp_guard/main.py",
)
if PLUGIN_SPEC is None or PLUGIN_SPEC.loader is None:
    raise RuntimeError("无法加载 AstrBot 军团权限插件")
PLUGIN = importlib.util.module_from_spec(PLUGIN_SPEC)
sys.modules[PLUGIN_SPEC.name] = PLUGIN
PLUGIN_SPEC.loader.exec_module(PLUGIN)


class CorpAccessPolicyTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self) -> None:
        self.policy = POLICY.CorpAccessPolicy.from_json(
            {
                "qq:GroupMessage:A": {
                    "allowedCorporationIds": ["1001"],
                    "defaultCorporationId": "1001",
                },
                "qq:GroupMessage:B": {
                    "allowedCorporationIds": ["2002"],
                    "defaultCorporationId": "2002",
                },
                "qq:GroupMessage:OPS": {
                    "allowedCorporationIds": ["1001", "2002"],
                },
            }
        )

    def test_default_corporation_is_forced_into_query(self) -> None:
        args = self.policy.authorize("qq:GroupMessage:A", "eve_query", {"dataset": "mail"})
        self.assertEqual("1001", args["corporationId"])

    def test_cross_corporation_request_is_denied(self) -> None:
        with self.assertRaises(POLICY.AccessDeniedError):
            self.policy.authorize(
                "qq:GroupMessage:A",
                "eve_read_document",
                {"dataset": "mail", "id": "7", "corporationId": "2002"},
            )

    def test_unknown_group_and_private_chat_are_denied(self) -> None:
        for origin in ("qq:GroupMessage:UNKNOWN", "qq:FriendMessage:123"):
            with self.subTest(origin=origin), self.assertRaises(POLICY.AccessDeniedError):
                self.policy.authorize(origin, "eve_catalog", {})

    def test_management_group_must_select_corporation(self) -> None:
        with self.assertRaises(POLICY.AccessDeniedError):
            self.policy.authorize("qq:GroupMessage:OPS", "eve_aggregate", {"dataset": "assets"})
        args = self.policy.authorize(
            "qq:GroupMessage:OPS",
            "eve_aggregate",
            {"dataset": "assets", "corporationId": "2002"},
        )
        self.assertEqual("2002", args["corporationId"])

    def test_corporation_list_is_filtered_without_mutating_source(self) -> None:
        source = {
            "corporations": [
                {"corporationId": "1001", "name": "甲", "ticker": "A", "secret": "不可泄漏"},
                {"corporationId": "2002", "name": "乙"},
                {"name": "缺少 ID"},
            ],
            "total": 3,
            "ownerTenantId": "不可泄漏",
            "readOnly": True,
        }
        result = self.policy.filter_corporations("qq:GroupMessage:A", source)
        self.assertEqual(
            [{"corporationId": "1001", "name": "甲", "ticker": "A"}],
            result["corporations"],
        )
        self.assertEqual(1, result["total"])
        self.assertEqual({"readOnly", "corporations", "total"}, set(result))
        self.assertNotIn("secret", result["corporations"][0])
        self.assertEqual(3, source["total"])

    def test_unknown_list_shape_fails_closed(self) -> None:
        with self.assertRaises(POLICY.AccessDeniedError):
            self.policy.filter_corporations("qq:GroupMessage:A", {"data": "not-a-list"})

    async def test_concurrent_sessions_do_not_share_corporation_context(self) -> None:
        async def resolve(origin: str, expected: str) -> None:
            for _ in range(100):
                await asyncio.sleep(0)
                args = self.policy.authorize(origin, "eve_query", {"dataset": "structures"})
                self.assertEqual(expected, args["corporationId"])

        await asyncio.gather(
            resolve("qq:GroupMessage:A", "1001"),
            resolve("qq:GroupMessage:B", "2002"),
        )

    def test_invalid_default_and_wildcard_are_rejected(self) -> None:
        invalid_values = (
            {"qq:*": {"allowedCorporationIds": ["1001"]}},
            {"qq:GroupMessage:A": {"allowedCorporationIds": ["１２３"]}},
            {"qq:GroupMessage:A": {"allowedCorporationIds": ["01"]}},
            {"qq:GroupMessage:A": {"allowedCorporationIds": ["9223372036854775808"]}},
            {"qq:GroupMessage:A": {"allowedCorporationIds": [1001]}},
            {
                "qq:GroupMessage:A": {
                    "allowedCorporationIds": ["1001"],
                    "defaultCorporationId": "2002",
                }
            },
        )
        for value in invalid_values:
            with self.subTest(value=value), self.assertRaises(POLICY.PolicyConfigurationError):
                POLICY.CorpAccessPolicy.from_json(value)


class GuardedToolTest(unittest.IsolatedAsyncioTestCase):
    def _plugin(self):
        manager = types.SimpleNamespace(func_list=[])
        context = types.SimpleNamespace(get_llm_tool_manager=lambda: manager)
        config = {
            "mcp_server_name": "eve",
            "session_bindings_json": json.dumps(
                {
                    "qq:GroupMessage:A": {
                        "allowedCorporationIds": ["1001"],
                        "defaultCorporationId": "1001",
                    }
                }
            ),
        }
        return PLUGIN.EveCorpGuardPlugin(context, config), manager

    @staticmethod
    def _context(origin):
        event = types.SimpleNamespace(unified_msg_origin=origin)
        return types.SimpleNamespace(context=types.SimpleNamespace(event=event))

    async def test_guarded_tool_injects_default_before_remote_call(self) -> None:
        plugin, _manager = self._plugin()
        result = mcp.types.CallToolResult(
            content=[mcp.types.TextContent(type="text", text="ok")]
        )
        upstream = FakeMcpTool("eve_query", result)
        guarded = PLUGIN.GuardedEveMcpTool(plugin, upstream, upstream)
        returned = await guarded.call(self._context("qq:GroupMessage:A"), dataset="mail")
        self.assertIs(result, returned)
        self.assertEqual("1001", upstream.calls[0]["corporationId"])

    async def test_guarded_tool_denies_cross_corporation_without_remote_call(self) -> None:
        plugin, _manager = self._plugin()
        upstream = FakeMcpTool(
            "eve_query",
            mcp.types.CallToolResult(content=[]),
        )
        guarded = PLUGIN.GuardedEveMcpTool(plugin, upstream, upstream)
        result = await guarded.call(
            self._context("qq:GroupMessage:A"),
            dataset="mail",
            corporationId="2002",
        )
        self.assertTrue(result.isError)
        self.assertEqual([], upstream.calls)

    async def test_guarded_corporation_list_filters_before_model_context(self) -> None:
        plugin, _manager = self._plugin()
        payload = {
            "corporations": [
                {"corporationId": "1001", "name": "甲"},
                {"corporationId": "2002", "name": "乙"},
            ]
        }
        upstream = FakeMcpTool(
            "eve_corporations",
            mcp.types.CallToolResult(
                content=[mcp.types.TextContent(type="text", text=json.dumps(payload))],
                structuredContent=payload,
            ),
        )
        guarded = PLUGIN.GuardedEveMcpTool(plugin, upstream, upstream)
        result = await guarded.call(self._context("qq:GroupMessage:A"))
        self.assertFalse(result.isError)
        filtered = json.loads(result.content[0].text)
        self.assertEqual(
            [{"corporationId": "1001", "name": "甲"}],
            filtered["corporations"],
        )
        self.assertNotIn("2002", result.content[0].text)

    async def test_llm_request_replaces_raw_mcp_tool_with_guard(self) -> None:
        plugin, manager = self._plugin()
        raw = FakeMcpTool("eve_query", mcp.types.CallToolResult(content=[]))
        manager.func_list = [raw]
        request = types.SimpleNamespace(func_tool=FakeToolSet([raw]))
        await plugin.replace_eve_tools(types.SimpleNamespace(), request)
        selected = request.func_tool.get_tool("eve_query")
        self.assertIsInstance(selected, PLUGIN.GuardedEveMcpTool)
        self.assertNotIsInstance(selected, FakeMcpTool)

    async def test_persona_disabled_tool_is_not_reintroduced_from_global_manager(self) -> None:
        plugin, manager = self._plugin()
        manager.func_list = [FakeMcpTool("eve_query", mcp.types.CallToolResult(content=[]))]
        unrelated = FakeFunctionTool("calculator", "计算", {"type": "object", "properties": {}})
        request = types.SimpleNamespace(func_tool=FakeToolSet([unrelated]))
        await plugin.replace_eve_tools(types.SimpleNamespace(), request)
        self.assertIsNone(request.func_tool.get_tool("eve_query"))
        self.assertIs(unrelated, request.func_tool.get_tool("calculator"))

    async def test_server_name_change_drops_wrapper_from_previous_request(self) -> None:
        plugin, _manager = self._plugin()
        raw = FakeMcpTool("eve_query", mcp.types.CallToolResult(content=[]), server_name="eve")
        first_request = types.SimpleNamespace(func_tool=FakeToolSet([raw]))
        await plugin.replace_eve_tools(types.SimpleNamespace(), first_request)
        cached = first_request.func_tool.get_tool("eve_query")
        self.assertIsInstance(cached, PLUGIN.GuardedEveMcpTool)

        plugin.config["mcp_server_name"] = "another-eve"
        second_request = types.SimpleNamespace(func_tool=FakeToolSet([cached]))
        await plugin.replace_eve_tools(types.SimpleNamespace(), second_request)
        self.assertIsNone(second_request.func_tool.get_tool("eve_query"))

    async def test_current_request_permission_wrapper_is_preserved_as_upstream(self) -> None:
        plugin, _manager = self._plugin()
        raw = FakeMcpTool("eve_query", mcp.types.CallToolResult(content=[]))
        permission_wrapper = FakePermissionWrapper(raw)
        request = types.SimpleNamespace(func_tool=FakeToolSet([permission_wrapper]))
        await plugin.replace_eve_tools(types.SimpleNamespace(), request)
        guarded = request.func_tool.get_tool("eve_query")
        self.assertIsInstance(guarded, PLUGIN.GuardedEveMcpTool)
        self.assertIs(permission_wrapper, guarded._upstream)


if __name__ == "__main__":
    unittest.main(verbosity=2)
