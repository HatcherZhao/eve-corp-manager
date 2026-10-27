#!/usr/bin/env python3
"""复用指定用户现有会话验收只读接口，令牌仅在内存中使用，不输出或创建登录凭据。"""

import base64
import json
import socket
import subprocess
import sys
from urllib.error import HTTPError
from urllib.request import Request, urlopen


class RedisReader:
    """以标准 RESP 读取项目独立 Redis 库，避免将连接密码放入命令行。"""

    def __init__(self, env):
        self.connection = socket.create_connection((env["REDIS_HOST"], int(env["REDIS_PORT"])), timeout=10)
        self.stream = self.connection.makefile("rwb")
        if env.get("REDIS_PWD"):
            self.call("AUTH", env["REDIS_PWD"])
        self.call("SELECT", env["REDIS_DB"])

    def read(self):
        """解析 Redis 基本响应；认证错误不回显连接参数。"""
        prefix = self.stream.read(1)
        line = self.stream.readline().rstrip(b"\r\n")
        if prefix == b"+":
            return line
        if prefix == b":":
            return int(line)
        if prefix == b"$":
            length = int(line)
            if length == -1:
                return None
            result = self.stream.read(length)
            self.stream.read(2)
            return result
        if prefix == b"*":
            return [self.read() for _ in range(int(line))]
        raise RuntimeError("Redis 只读验收请求失败")

    def call(self, *args):
        """发送参数化命令。"""
        encoded = [str(value).encode() if not isinstance(value, bytes) else value for value in args]
        payload = b"*" + str(len(encoded)).encode() + b"\r\n"
        for value in encoded:
            payload += b"$" + str(len(value)).encode() + b"\r\n" + value + b"\r\n"
        self.stream.write(payload)
        self.stream.flush()
        return self.read()


def strings(value):
    """只在内存中遍历会话字段，寻找现有签名令牌。"""
    if isinstance(value, str):
        yield value
    elif isinstance(value, list):
        for item in value:
            yield from strings(item)
    elif isinstance(value, dict):
        for item in value.values():
            yield from strings(item)


def find_token(redis, user_id):
    """只读取该用户的会话键，校验 JWT 声明对应本人后使用。"""
    cursor = b"0"
    sessions = []
    while True:
        cursor, keys = redis.call("SCAN", cursor, "MATCH", f"*session*{user_id}*", "COUNT", 200)
        for key in keys:
            if redis.call("TYPE", key) != b"string":
                continue
            value = redis.call("GET", key)
            if value:
                try:
                    decoded = json.loads(value)
                    # Redis 编解码器可能将会话 JSON 再编码为字符串。
                    for _ in range(3):
                        if not isinstance(decoded, str):
                            break
                        decoded = json.loads(decoded)
                    sessions.append(decoded)
                except (ValueError, UnicodeDecodeError):
                    continue
        if cursor == b"0":
            break
    for session in sessions:
        for candidate in strings(session):
            if not candidate.startswith("eyJ") or candidate.count(".") != 2:
                continue
            try:
                encoded = candidate.split(".")[1]
                claims = json.loads(base64.urlsafe_b64decode(encoded + "=" * (-len(encoded) % 4)))
            except (ValueError, UnicodeDecodeError):
                continue
            if str(user_id) in [str(claims.get("loginId")), str(claims.get("sub"))]:
                return candidate
    # 仅输出结构字段名供诊断，不泄露字段值。
    shapes = [list(session.keys()) for session in sessions if isinstance(session, dict)]
    raise RuntimeError(f"指定用户没有可用的现有 JWT 会话；会话结构={shapes}")


def main():
    """验收权限、菜单和工作台三个只读数据接口，只报告结果及缺失项。"""
    user_id, tenant_id = sys.argv[1:3]
    config = json.loads(subprocess.check_output(["docker", "inspect", "eve-corp-manager-api"]))[0]
    env = dict(item.split("=", 1) for item in config["Config"]["Env"] if "=" in item)
    redis = RedisReader(env)
    token = find_token(redis, user_id)
    web = json.loads(subprocess.check_output(["docker", "inspect", "eve-corp-manager-web"]))[0]
    binding = web["HostConfig"]["PortBindings"]["80/tcp"][0]
    host = binding["HostIp"]
    if host in {"", "0.0.0.0", "::"}:
        host = "127.0.0.1"
    base = "http://" + host + ":" + binding["HostPort"] + "/api"
    headers = {"Authorization": "Bearer " + token, "X-Tenant-Id": tenant_id}
    required = {"eve:structures:view", "eve:extractions:view", "eve:mining:view"}
    required_paths = {"/eve/members", "/eve/assets", "/eve/structures", "/eve/extractions", "/eve/mining"}
    paths = ["/auth/user/info", "/auth/user/route", "/eve/structures?page=1&size=1", "/eve/extractions?page=1&size=1", "/eve/mining/summary"]
    failed = False
    for path in paths:
        request = Request(base + path, headers=headers)
        try:
            with urlopen(request, timeout=25) as response:
                status, data = response.status, json.load(response)
        except HTTPError as error:
            status, data = error.code, json.loads(error.read())
        success = status == 200 and data.get("success") is True
        report = {"path": path, "http": status, "code": data.get("code"), "success": success}
        if path.endswith("/info") and success:
            missing = sorted(required - set(data["data"].get("permissions", [])))
            report["missingPermissions"] = missing
            success = success and not missing
        if path.endswith("/route") and success:
            route_paths = set()
            def collect(routes):
                """遍历动态路由，只验证路径，不输出用户或军团业务数据。"""
                for route in routes:
                    route_paths.add(route.get("path"))
                    collect(route.get("children") or [])
            collect(data["data"])
            missing = sorted(required_paths - route_paths)
            report["rootMenus"] = len(data["data"])
            report["missingRoutes"] = missing
            success = success and not missing
        if not success:
            report["message"] = data.get("msg") or data.get("message")
            failed = True
        report["success"] = success
        print(json.dumps(report, ensure_ascii=False), flush=True)
    if failed:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
