#!/usr/bin/env python3
"""只检查公开接口；不接受或读取游戏凭据，不输出响应正文。"""
import concurrent.futures
import hashlib
import json
import urllib.error
import urllib.request

URLS = [
    "https://ali-esi.evepc.163.com/latest/swagger.json",
    "https://login.evepc.163.com/.well-known/oauth-authorization-server",
    "https://esi.evepc.163.com/latest/swagger.json",
]

def probe(url):
    request = urllib.request.Request(url, headers={"User-Agent": "eve-corp-manager-research/0.1", "Accept": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            body = response.read(4 * 1024 * 1024 + 1)
            if len(body) > 4 * 1024 * 1024:
                return {"url": url, "error": "response exceeds 4 MiB limit"}
            payload = json.loads(body)
            return {"url": url, "status": response.status, "bytes": len(body), "sha256": hashlib.sha256(body).hexdigest(), "paths": len(payload.get("paths", {})), "issuer": payload.get("issuer")}
    except urllib.error.HTTPError as error:
        return {"url": url, "status": error.code}
    except Exception as error:
        return {"url": url, "error_type": type(error).__name__}

if __name__ == "__main__":
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        print(json.dumps(list(pool.map(probe, URLS)), ensure_ascii=False, indent=2))
