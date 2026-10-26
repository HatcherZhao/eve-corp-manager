#!/usr/bin/env python3
"""校验本站技能示例和引用，生成不包含凭据及业务数据的 AstrBot 安装包。"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile


ROOT = Path(__file__).resolve().parents[2]
SKILL = ROOT / "skills/eve-corp-manager"
FILES = ("SKILL.md", "references/query-rules.md", "references/player-workflows.md")
TOOLS = {"eve_corporations", "eve_catalog", "eve_query", "eve_aggregate", "eve_read_document"}


def load_catalog(path):
    """兼容目录本体和 MCP JSON-RPC 响应，仅在内存中读取字段契约。"""
    data = json.loads(path.read_text(encoding="utf-8"))
    if "result" in data:
        data = data["result"]
    if "structuredContent" in data:
        data = data["structuredContent"]
    elif "content" in data:
        data = json.loads(next(item["text"] for item in data["content"] if item["type"] == "text"))
    return {dataset["key"]: dataset for dataset in data["datasets"]}


def check_example(example, catalog):
    """检查示例使用真实工具及目录允许的字段、排序、分组和聚合能力。"""
    tool = example["tool"]
    args = example["arguments"]
    if tool not in TOOLS or not isinstance(args, dict):
        raise ValueError("示例工具或参数结构不合法")
    if "corporationId" in args and (not isinstance(args["corporationId"], str) or not re.fullmatch(r"[1-9][0-9]{0,18}", args["corporationId"])):
        raise ValueError("军团 ID 必须为正整数字符串")
    if catalog is None or tool in {"eve_catalog", "eve_corporations"}:
        return
    dataset = catalog[args["dataset"]]
    fields = {field["name"]: field for field in dataset["fields"]}
    if tool == "eve_read_document":
        if not dataset["documentReadable"] or not re.fullmatch(r"[1-9][0-9]*", args["id"]):
            raise ValueError("正文示例的数据集或 ID 不合法")
        if dataset.get("documentCharacterIdRequired") and not re.fullmatch(r"[1-9][0-9]*", args.get("characterId", "")):
            raise ValueError("邮件或通知示例缺少角色 ID")
        return
    for name in args.get("fields", []):
        fields[name]
    for condition in args.get("filters", []):
        field = fields[condition["field"]]
        operator = condition["operator"]
        if operator not in {"eq", "ne", "contains", "in", "gte", "gt", "lte", "lt", "is_null"}:
            raise ValueError("未知筛选运算符")
        if operator == "contains" and field["type"] != "string":
            raise ValueError("contains 仅支持字符串字段")
        if operator == "in" and (not isinstance(condition.get("value"), list) or not 1 <= len(condition["value"]) <= 100):
            raise ValueError("in 必须使用 1～100 项数组")
    groups = args.get("groupBy", [])
    aliases = set()
    used_aliases = {name.lower() for name in groups}
    if tool == "eve_aggregate" and ("fields" in args or not 1 <= len(args.get("metrics", [])) <= 12):
        raise ValueError("聚合示例需要 1～12 个指标且不能指定 fields")
    for metric in args.get("metrics", []):
        if metric["function"] not in {"count", "sum", "avg", "min", "max"}:
            raise ValueError("未知聚合函数")
        if metric["function"] != "count" and "field" not in metric:
            raise ValueError("非 count 指标必须指定 field")
        if "field" in metric:
            field = fields[metric["field"]]
            if not field["aggregatable"]:
                raise ValueError("聚合示例使用不可聚合字段")
            if metric["function"] in {"sum", "avg"} and field["type"] not in {"number", "decimal"}:
                raise ValueError("sum 和 avg 仅支持数值字段")
        if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]{0,63}", metric["alias"]):
            raise ValueError("聚合别名不合法")
        if metric["alias"].lower() in used_aliases:
            raise ValueError("聚合别名重复或与分组字段冲突")
        used_aliases.add(metric["alias"].lower())
        aliases.add(metric["alias"])
    for name in groups:
        if not fields[name]["groupable"]:
            raise ValueError("示例使用不可分组字段")
    for order in args.get("sort", []):
        if tool == "eve_aggregate" and order["field"] not in set(groups) | aliases:
            raise ValueError("聚合排序必须使用分组字段或指标别名")
        if order["field"] not in aliases and not fields[order["field"]]["sortable"]:
            raise ValueError("示例使用不可排序字段")
        if order["direction"] not in {"ASC", "DESC"}:
            raise ValueError("示例排序方向不合法")
    if not 1 <= args.get("size", 20) <= 100 or len(args.get("groupBy", [])) > 2 or len(args.get("sort", [])) > 3:
        raise ValueError("示例超过分页、分组或排序限制")


def main():
    """验证相对引用与 JSON 示例，并以固定白名单打包技能文件。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, help="可选：eve_catalog 的 JSON 响应，仅用于契约校验，不放入包中")
    options = parser.parse_args()
    catalog = load_catalog(options.catalog) if options.catalog else None
    example_count = 0
    for name in FILES:
        source = SKILL / name
        if source.is_symlink() or not source.is_file():
            raise ValueError(f"技能文件缺失或为符号链接：{name}")
        content = source.read_text(encoding="utf-8")
        for target in re.findall(r"\[[^\]]+\]\(([^)]+)\)", content):
            if target.startswith("https://"):
                continue
            resolved = (source.parent / target.split("#", 1)[0]).resolve()
            if not resolved.is_relative_to(SKILL.resolve()) or not resolved.is_file():
                raise ValueError(f"引用不存在或越出技能目录：{name}: {target}")
        for block in re.findall(r"^```json\n(.*?)^```", content, flags=re.MULTILINE | re.DOTALL):
            check_example(json.loads(block), catalog)
            example_count += 1
    destination = ROOT / "output/g017/eve-corp-manager.zip"
    destination.parent.mkdir(parents=True, exist_ok=True)
    # 固定目录白名单，避免将目录响应、密钥或临时业务数据夹带到安装包。
    with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name in FILES:
            archive.write(SKILL / name, f"eve-corp-manager/{name}")
    with zipfile.ZipFile(destination) as archive:
        if archive.testzip() is not None:
            raise ValueError("ZIP 完整性检查失败")
    print(f"已验证 {len(FILES)} 个技能文件、{example_count} 个 JSON 示例及全部本地引用")
    print("目录字段契约：通过" if catalog is not None else "目录字段契约：未提供目录，已跳过")
    print(f"安装包：{destination}（{destination.stat().st_size} bytes）")
    print(f"SHA256：{hashlib.sha256(destination.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main()
