#!/usr/bin/env python3
"""通过 AstrBot 既有 MCP 配置只读验收线上通知语义，不输出认证信息或通知正文。"""

import argparse
import json
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen


DEFAULT_CONFIG = Path("/home/astrbot/data/mcp_server.json")
SOVEREIGNTY_TYPES = ["EntosisCaptureStarted", "SovStructureReinforced"]
EVENT_START = "2026-10-28T17:00:00+08:00"
EVENT_END = "2026-10-28T19:00:00+08:00"
EXPECTED_SYSTEM_TIMES = {
    "V6-NY1": "2026-10-30T18:43:16.0071693+08:00",
    "PXF-RF": "2026-10-30T16:14:34.1245653+08:00",
    "9DQW-W": "2026-10-30T18:38:27.7355622+08:00",
}


def configured_connection(config_path, requested_url=None, server_name=None):
    """从 AstrBot 配置选择唯一 EVE MCP，认证头仅留在内存中。"""
    config = json.loads(config_path.read_text(encoding="utf-8"))
    servers = config.get("mcpServers")
    if not isinstance(servers, dict):
        raise RuntimeError("AstrBot MCP 配置缺少 mcpServers 对象")

    candidates = []
    for name, value in servers.items():
        if not isinstance(value, dict) or not isinstance(value.get("url"), str):
            continue
        parsed = urlsplit(value["url"])
        if parsed.hostname == "eve.codeagent.cc" and parsed.path.rstrip("/") == "/api/mcp":
            candidates.append((name, value))
    if server_name:
        candidates = [item for item in candidates if item[0] == server_name]
    if requested_url:
        candidates = [item for item in candidates if item[1]["url"].rstrip("/") == requested_url.rstrip("/")]
    if len(candidates) != 1:
        raise RuntimeError("无法从 AstrBot 配置唯一确定 EVE MCP，请使用 --server-name 或 --mcp-url")

    _, selected = candidates[0]
    url = selected["url"]
    if requested_url and requested_url.rstrip("/") != url.rstrip("/"):
        raise RuntimeError("--mcp-url 必须与持有认证信息的 AstrBot 配置完全一致")
    headers = selected.get("headers")
    if not isinstance(headers, dict) or not any(key.lower() == "authorization" for key in headers):
        raise RuntimeError("所选 AstrBot MCP 配置缺少认证头")
    return url, {str(key): str(value) for key, value in headers.items()}


class McpClient:
    """最小化 Streamable HTTP 客户端，只调用本站只读工具。"""

    def __init__(self, url, configured_headers):
        self.url = url
        self.headers = {
            **configured_headers,
            "Content-Type": "application/json",
            "Accept": "application/json, text/event-stream",
        }
        self.request_id = 0

    def call(self, tool_name, arguments):
        """执行一次工具调用并提取结构化结果，错误时不回显响应正文。"""
        self.request_id += 1
        payload = json.dumps({
            "jsonrpc": "2.0",
            "id": self.request_id,
            "method": "tools/call",
            "params": {"name": tool_name, "arguments": arguments},
        }).encode("utf-8")
        try:
            with urlopen(Request(self.url, data=payload, headers=self.headers), timeout=45) as response:
                body = response.read().decode("utf-8")
                content_type = response.headers.get("Content-Type", "")
        except HTTPError as error:
            raise RuntimeError(f"MCP HTTP 调用失败，状态码 {error.code}") from None
        except URLError:
            raise RuntimeError("MCP HTTP 调用失败，无法连接线上服务") from None

        message = self._parse_message(body, content_type)
        if message.get("error") is not None:
            raise RuntimeError(f"MCP 工具调用失败：{tool_name}")
        result = message.get("result")
        if not isinstance(result, dict) or result.get("isError") is True:
            raise RuntimeError(f"MCP 工具未返回成功：{tool_name}")
        structured = result.get("structuredContent")
        if isinstance(structured, dict):
            return structured
        for content in result.get("content", []):
            if isinstance(content, dict) and content.get("type") == "text":
                try:
                    parsed = json.loads(content.get("text", ""))
                except json.JSONDecodeError:
                    continue
                if isinstance(parsed, dict):
                    return parsed
        raise RuntimeError(f"MCP 工具缺少结构化结果：{tool_name}")

    def _parse_message(self, body, content_type):
        """兼容普通 JSON 与 Streamable HTTP 的 SSE data 行。"""
        if "text/event-stream" not in content_type.lower():
            return json.loads(body)
        messages = []
        for line in body.splitlines():
            if not line.startswith("data:"):
                continue
            try:
                message = json.loads(line[5:].strip())
            except json.JSONDecodeError:
                continue
            if isinstance(message, dict):
                messages.append(message)
        for message in reversed(messages):
            if message.get("id") == self.request_id:
                return message
        raise RuntimeError("MCP SSE 响应缺少当前请求结果")


def resolve_corporation(client, corporation_id):
    """从应用有效授权中选择目标军团，避免把查询落到另一个授权军团。"""
    corporations = client.call("eve_corporations", {}).get("corporations", [])
    matches = [item for item in corporations if str(item.get("corporationId")) == corporation_id]
    if len(matches) != 1:
        raise RuntimeError("无法从 MCP 有效授权中唯一确定目标军团")
    authorized_ids = {str(item.get("corporationId")) for item in corporations}
    return str(matches[0]["corporationId"]), authorized_ids


def query_notifications(client, corporation_id, *, fields, filters, size):
    """执行只读通知查询，限定稳定时间排序和返回数量。"""
    return client.call("eve_query", {
        "corporationId": corporation_id,
        "dataset": "notifications",
        "fields": fields,
        "filters": filters,
        "sort": [{"field": "sentAt", "direction": "DESC"}],
        "page": 1,
        "size": size,
    })


def verify_narrow_projection(client, corporation_id):
    """校验窄投影仍有计算字段，同时内部通知类型列不会泄漏。"""
    result = query_notifications(
        client,
        corporation_id,
        fields=["notificationId"],
        filters=sovereignty_filters(),
        size=1,
    )
    items = result.get("items", [])
    if not items:
        raise RuntimeError("线上没有可用于窄投影验收的联盟主权通知")
    item = items[0]
    if item.get("eventScope") != "ALLIANCE_SOVEREIGNTY" or not item.get("title"):
        raise RuntimeError("窄投影缺少通知标题或联盟主权事件范围")
    forbidden = {"notificationType", "__notificationType"}.intersection(item)
    if forbidden:
        raise RuntimeError("窄投影泄漏了内部通知类型字段")


def verify_catalog(client, corporation_id):
    """目录必须将通知计算字段声明为响应字段，而不是可查询 SQL 字段。"""
    catalog = client.call("eve_catalog", {"corporationId": corporation_id})
    matches = [item for item in catalog.get("datasets", []) if item.get("key") == "notifications"]
    if len(matches) != 1:
        raise RuntimeError("MCP 目录缺少唯一 notifications 数据集")
    dataset = matches[0]
    field_names = {field.get("name") for field in dataset.get("fields", [])}
    computed_names = {field.get("name") for field in dataset.get("computedResponseFields", [])}
    expected = {"title", "eventScope", "eventScopeLabel", "interpretation", "interpretationComplete"}
    if not expected.issubset(computed_names) or expected.intersection(field_names):
        raise RuntimeError("notifications 目录未正确区分计算响应字段与 SQL 字段")
    if "不代表事件建筑归属于该军团" not in str(dataset.get("scopeSemantics", "")):
        raise RuntimeError("notifications 目录缺少接收范围与资产归属的语义说明")


def sovereignty_filters():
    """固定历史验收窗口和两类已知联盟主权事件。"""
    return [
        {"field": "notificationType", "operator": "in", "value": SOVEREIGNTY_TYPES},
        {"field": "sentAt", "operator": "gte", "value": EVENT_START},
        {"field": "sentAt", "operator": "lt", "value": EVENT_END},
    ]


def contains_owner_key(value):
    """递归检查结构化解读是否凭空增加所有者字段。"""
    if isinstance(value, dict):
        return any("owner" in str(key).lower() or contains_owner_key(item) for key, item in value.items())
    if isinstance(value, list):
        return any(contains_owner_key(item) for item in value)
    return False


def verify_alliance_interpretations(client, corporation_id):
    """读取固定窗口六条联盟主权通知，逐星系核对事件阶段和节点时间。"""
    result = query_notifications(
        client,
        corporation_id,
        fields=["notificationId", "characterId", "notificationType", "sentAt"],
        filters=sovereignty_filters(),
        size=10,
    )
    items = result.get("items", [])
    if result.get("total") != 6 or len(items) != 6:
        raise RuntimeError("固定时间窗口内联盟主权通知不是且仅有六条")

    observed = set()
    for item in items:
        if item.get("eventScope") != "ALLIANCE_SOVEREIGNTY":
            raise RuntimeError("联盟主权查询混入了其他事件范围")
        document = client.call("eve_read_document", {
            "corporationId": corporation_id,
            "dataset": "notifications",
            "id": str(item["notificationId"]),
            "characterId": str(item["characterId"]),
            "offset": 0,
            "limit": 256,
        })
        interpretation = document.get("interpretation")
        if not document.get("contentAvailable") or not isinstance(interpretation, dict):
            raise RuntimeError("联盟主权通知正文不可用或缺少结构化解读")
        if document.get("interpretationComplete") is not True:
            raise RuntimeError("固定联盟主权样本没有完成全文业务解读")
        if interpretation.get("eventScope") != "ALLIANCE_SOVEREIGNTY":
            raise RuntimeError("正文结构化解读的事件范围不正确")
        system_name = interpretation.get("solarSystemName")
        notification_type = item.get("notificationType")
        if system_name not in EXPECTED_SYSTEM_TIMES:
            raise RuntimeError("固定联盟主权样本出现非预期星系")
        if interpretation.get("structureTypeName") != "主权中心":
            raise RuntimeError("联盟主权样本未识别为主权中心")
        if contains_owner_key(interpretation):
            raise RuntimeError("联盟主权解读不应包含所有者字段")
        summary = str(interpretation.get("summary", ""))
        if notification_type == "EntosisCaptureStarted":
            if interpretation.get("title") != "联盟主权建筑开始遭受侵噬" or "开始遭受侵噬" not in summary:
                raise RuntimeError("联盟侵噬事件标题或阶段表述不正确")
            if interpretation.get("commandNodeDecloakAt") is not None:
                raise RuntimeError("联盟侵噬启动事件不应虚构节点解除隐形时间")
        elif notification_type == "SovStructureReinforced":
            if "增强模式" not in summary or "增援" in summary:
                raise RuntimeError("联盟增强事件阶段表述不正确")
            if interpretation.get("commandNodeDecloakAt") != EXPECTED_SYSTEM_TIMES[system_name]:
                raise RuntimeError("联盟增强事件的节点解除隐形时间不准确")
        else:
            raise RuntimeError("固定联盟主权样本出现非预期通知类型")
        observed.add((notification_type, system_name))

        beyond_end = client.call("eve_read_document", {
            "corporationId": corporation_id,
            "dataset": "notifications",
            "id": str(item["notificationId"]),
            "characterId": str(item["characterId"]),
            "offset": int(document["totalLength"]) + 1,
            "limit": 1,
        })
        if beyond_end.get("content") != "" or beyond_end.get("interpretation") != interpretation:
            raise RuntimeError("超出正文长度的切片请求未保持完整前缀业务解读")
        if beyond_end.get("interpretationComplete") is not True:
            raise RuntimeError("超出正文长度的切片请求丢失了解读完整性标志")

    expected_pairs = {(notification_type, system) for notification_type in SOVEREIGNTY_TYPES
                      for system in EXPECTED_SYSTEM_TIMES}
    if observed != expected_pairs:
        raise RuntimeError("六条联盟主权样本未覆盖三个星系的侵噬与增强事件")


def verify_structure_fuel_scope(client, corporation_id, fuel_corporation_id, authorized_ids):
    """普通建筑燃料通知必须为 STRUCTURE；目标军团无样本时仅回退到显式授权备用军团。"""
    def read(target_id):
        return query_notifications(
            client,
            target_id,
            fields=["notificationId"],
            filters=[{"field": "notificationType", "operator": "eq", "value": "StructureFuelAlert"}],
            size=1,
        )

    result = read(corporation_id)
    if not result.get("items"):
        if not fuel_corporation_id:
            raise RuntimeError("目标军团没有燃料历史，请显式提供已授权的 --fuel-corporation-id")
        if fuel_corporation_id not in authorized_ids:
            raise RuntimeError("--fuel-corporation-id 不在 MCP 有效授权列表中")
        result = read(fuel_corporation_id)
    items = result.get("items", [])
    if not items or items[0].get("eventScope") != "STRUCTURE":
        raise RuntimeError("StructureFuelAlert 未返回 STRUCTURE 事件范围")


def main():
    """组合执行线上只读 MCP 通知语义验收。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG, help="AstrBot mcp_server.json 路径")
    parser.add_argument("--mcp-url", help="必须与配置中的 EVE MCP URL 完全一致")
    parser.add_argument("--server-name", help="AstrBot mcpServers 中的服务名称")
    parser.add_argument("--corporation-id", required=True, help="eve_corporations 返回的目标军团 ID")
    parser.add_argument("--fuel-corporation-id", help="目标军团无燃料样本时使用的已授权军团 ID")
    args = parser.parse_args()

    url, headers = configured_connection(args.config, args.mcp_url, args.server_name)
    client = McpClient(url, headers)
    corporation_id, authorized_ids = resolve_corporation(client, args.corporation_id)
    verify_catalog(client, corporation_id)
    verify_narrow_projection(client, corporation_id)
    verify_alliance_interpretations(client, corporation_id)
    verify_structure_fuel_scope(client, corporation_id, args.fuel_corporation_id, authorized_ids)
    print(json.dumps({
        "stage": "passed",
        "catalogComputedFields": True,
        "allianceEvents": 6,
        "narrowProjection": True,
        "sovereigntyHub": True,
        "commandNodeTime": True,
        "structureFuelScope": True,
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
