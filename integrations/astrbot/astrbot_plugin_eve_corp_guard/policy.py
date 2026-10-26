"""与 AstrBot 解耦的军团访问策略，便于独立测试。"""

from __future__ import annotations

import copy
import json
import re
from dataclasses import dataclass
from typing import Any


PROTECTED_TOOLS = frozenset(
    {
        "eve_catalog",
        "eve_query",
        "eve_aggregate",
        "eve_read_document",
    }
)
CORPORATION_LIST_TOOL = "eve_corporations"
MAX_SIGNED_LONG = 9223372036854775807
CORPORATION_ID_PATTERN = re.compile(r"^[1-9][0-9]*$")


class PolicyConfigurationError(ValueError):
    """表示管理员配置不满足封闭授权规则。"""


class AccessDeniedError(PermissionError):
    """表示当前真实会话来源无权调用目标军团。"""


@dataclass(frozen=True)
class SessionBinding:
    """一个 AstrBot 统一会话来源对应的军团白名单。"""

    allowed_corporation_ids: frozenset[str]
    default_corporation_id: str | None


class CorpAccessPolicy:
    """按 AstrBot 统一会话来源执行默认军团注入和白名单校验。"""

    def __init__(self, bindings: dict[str, SessionBinding]) -> None:
        self._bindings = dict(bindings)

    @classmethod
    def from_json(cls, raw: str | dict[str, Any] | None) -> "CorpAccessPolicy":
        """解析管理面板中的 JSON，并拒绝含糊或越权配置。"""
        if raw is None or raw == "":
            return cls({})
        try:
            value = json.loads(raw) if isinstance(raw, str) else raw
        except json.JSONDecodeError as exc:
            raise PolicyConfigurationError(f"会话授权 JSON 无法解析：{exc.msg}") from exc
        if not isinstance(value, dict):
            raise PolicyConfigurationError("会话授权配置必须是 JSON 对象")

        bindings: dict[str, SessionBinding] = {}
        for origin, item in value.items():
            if not isinstance(origin, str) or not origin.strip() or "*" in origin:
                raise PolicyConfigurationError("会话来源必须是非空精确值，不能使用通配符")
            if not isinstance(item, dict):
                raise PolicyConfigurationError(f"会话 {origin} 的配置必须是对象")
            allowed_raw = item.get("allowedCorporationIds")
            if not isinstance(allowed_raw, list) or not allowed_raw:
                raise PolicyConfigurationError(f"会话 {origin} 至少需要一个允许的军团")
            allowed = frozenset(_corporation_id(value, origin) for value in allowed_raw)
            default_raw = item.get("defaultCorporationId")
            default_id = None if default_raw in (None, "") else _corporation_id(default_raw, origin)
            if default_id is not None and default_id not in allowed:
                raise PolicyConfigurationError(f"会话 {origin} 的默认军团不在允许列表中")
            bindings[origin] = SessionBinding(allowed, default_id)
        return cls(bindings)

    def authorize(self, origin: str, tool_name: str, arguments: dict[str, Any] | None) -> dict[str, Any]:
        """返回强制注入后的参数；未绑定或跨军团请求直接拒绝。"""
        binding = self._bindings.get(origin)
        if binding is None:
            raise AccessDeniedError("当前群或私聊未绑定 EVE 军团，已拒绝访问本站数据")
        args = dict(arguments or {})
        if tool_name == CORPORATION_LIST_TOOL:
            return args
        if tool_name not in PROTECTED_TOOLS:
            raise AccessDeniedError("该工具不属于受保护的 EVE 查询工具")

        requested = args.get("corporationId")
        if requested in (None, ""):
            target = binding.default_corporation_id
            if target is None and len(binding.allowed_corporation_ids) == 1:
                target = next(iter(binding.allowed_corporation_ids))
            if target is None:
                raise AccessDeniedError("本会话允许多个军团，请明确指定 corporationId")
        else:
            target = _corporation_id(requested, origin)
        if target not in binding.allowed_corporation_ids:
            raise AccessDeniedError("当前群或私聊无权查询指定军团")
        args["corporationId"] = target
        return args

    def filter_corporations(self, origin: str, payload: Any) -> dict[str, Any]:
        """过滤军团目录，任何未知响应结构都封闭失败而非原样泄漏。"""
        binding = self._bindings.get(origin)
        if binding is None:
            raise AccessDeniedError("当前群或私聊未绑定 EVE 军团，已拒绝访问本站数据")
        if isinstance(payload, list):
            rows = payload
            read_only = True
        elif isinstance(payload, dict):
            rows = next(
                (payload[key] for key in ("corporations", "items") if isinstance(payload.get(key), list)),
                None,
            )
            if rows is None:
                raise AccessDeniedError("军团目录响应缺少可验证的军团列表，已拒绝返回数据")
            read_only = payload.get("readOnly") is not False
        else:
            raise AccessDeniedError("军团目录响应格式异常，已拒绝返回未过滤数据")
        filtered = _filter_rows(rows, binding.allowed_corporation_ids)
        return {"readOnly": read_only, "corporations": filtered, "total": len(filtered)}


def _corporation_id(value: Any, origin: str) -> str:
    """将军团 ID 规范化为正整数字符串。"""
    if not isinstance(value, str):
        raise PolicyConfigurationError(f"会话 {origin} 包含无效的军团 ID")
    normalized = value.strip()
    if not CORPORATION_ID_PATTERN.fullmatch(normalized) or int(normalized) > MAX_SIGNED_LONG:
        raise PolicyConfigurationError(f"会话 {origin} 包含无效的军团 ID")
    return normalized


def _filter_rows(rows: list[Any], allowed: frozenset[str]) -> list[dict[str, Any]]:
    """只保留带有已授权 corporationId 的对象行。"""
    filtered: list[dict[str, Any]] = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        corporation_id = str(row.get("corporationId", "")).strip()
        if corporation_id in allowed:
            safe_row: dict[str, Any] = {"corporationId": corporation_id}
            for key in ("name", "ticker"):
                if isinstance(row.get(key), str):
                    safe_row[key] = row[key]
            filtered.append(copy.deepcopy(safe_row))
    return filtered
