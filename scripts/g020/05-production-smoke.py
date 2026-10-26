#!/usr/bin/env python3
"""只读验收公网发布产物与访问保护，不使用凭据或触发游戏同步。"""

import hashlib
from html.parser import HTMLParser
import json
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urljoin, urlsplit
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[2]
BASE = "https://eve.codeagent.cc:9443"
DIST = ROOT / "frontend/dist"


class EntryAssets(HTMLParser):
    """提取首页入口脚本和样式，检查实际发布内容而非仅检查状态码。"""

    def __init__(self):
        super().__init__()
        self.paths = []

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        if tag == "script" and values.get("type") == "module" and values.get("src"):
            self.paths.append(values["src"])
        if tag == "link" and values.get("rel") == "stylesheet" and values.get("href"):
            self.paths.append(values["href"])


def fetch(path, *, payload=None, headers=None):
    """获取限定同源的公共资源，并保留拒绝请求的原始状态。"""
    url = urljoin(BASE + "/", path)
    if urlsplit(url).netloc != urlsplit(BASE).netloc:
        raise ValueError("验收请求不能离开本站")
    request = Request(url, data=payload, headers=headers or {})
    try:
        with urlopen(request, timeout=30) as response:
            return response.status, response.headers, response.read()
    except HTTPError as error:
        return error.code, error.headers, error.read()


def main():
    """验证首页及入口哈希、验证码关闭、MCP 和授权接口访问保护。"""
    status, _, homepage = fetch("/")
    assert status == 200 and homepage == (DIST / "index.html").read_bytes()
    parser = EntryAssets()
    parser.feed(homepage.decode("utf-8"))
    assert parser.paths, "首页缺少入口资源"
    for path in parser.paths:
        status, _, content = fetch(path)
        relative = urlsplit(path).path.lstrip("/")
        local = (DIST / relative).resolve()
        assert local.is_relative_to(DIST.resolve())
        assert status == 200 and content == local.read_bytes(), path
    print(f"首页及 {len(parser.paths)} 个入口资源哈希一致：{hashlib.sha256(homepage).hexdigest()}")

    status, _, body = fetch("/api/captcha/image")
    data = json.loads(body)
    assert status == 200 and data["success"] and data["data"]["isEnabled"] is False
    print("登录图形验证码仍关闭")

    payload = json.dumps({"jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {}}).encode()
    headers = {"Content-Type": "application/json", "Accept": "application/json, text/event-stream"}
    status, response_headers, _ = fetch("/api/mcp", payload=payload, headers=headers)
    assert status == 401 and "Bearer" in response_headers.get("WWW-Authenticate", "")
    print("MCP 匿名访问返回 401 与 Bearer 挑战")

    invalid = "eve_agent_" + "Z" * 43
    status, _, _ = fetch("/api/mcp", payload=payload, headers={**headers, "Authorization": f"Bearer {invalid}"})
    assert status == 401
    status, _, _ = fetch("/api/mcp", payload=payload, headers={**headers, "Origin": "https://invalid.example"})
    assert status == 403
    print("MCP 无效密钥返回 401，不可信 Origin 返回 403")

    status, _, body = fetch("/api/eve/agents/grants")
    assert status == 401 or (status == 200 and str(json.loads(body).get("code")) == "401")
    print("军团授权接口拒绝匿名访问")


if __name__ == "__main__":
    main()
