#!/usr/bin/env python3
"""在现有 API 容器内原子替换已验证产物，保留发布镜像、配置备份和原容器资源限制。"""

import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
from urllib.error import URLError
from urllib.request import urlopen


CONTAINER = "eve-corp-manager-api"
TAG = "20261027-role-policy"
EXPECTED_IMAGE = "eve-corp-manager-api:20261026-2079317"
DEPLOY = Path("/home/eve-corp-manager/deploy/production")
BACKUP = Path("/home/eve-corp-manager/backups") / TAG


def run(*args, capture=False):
    """执行确定的本机命令，只有明确需要的非敏感结果才返回调用方。"""
    result = subprocess.run(args, check=True, text=True, capture_output=capture)
    return result.stdout.strip() if capture else None


def sha(path):
    """计算产物完整性摘要。"""
    with path.open("rb") as source:
        digest = hashlib.sha256()
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
        return digest.hexdigest()


def wait_healthy():
    """确认本次启动日志和公开 API 均正常，避免将重启命令成功误判为发布成功。"""
    web = json.loads(run("docker", "inspect", "eve-corp-manager-web", capture=True))[0]
    binding = web["HostConfig"]["PortBindings"]["80/tcp"][0]
    host = binding["HostIp"]
    if host in {"", "0.0.0.0", "::"}:
        host = "127.0.0.1"
    url = "http://" + host + ":" + binding["HostPort"] + "/api/captcha/image"
    deadline = time.monotonic() + 100
    while time.monotonic() < deadline:
        state = json.loads(run("docker", "inspect", CONTAINER, capture=True))[0]["State"]
        if state["Status"] == "running":
            logs = subprocess.run(["docker", "logs", "--since", state["StartedAt"], CONTAINER],
                                  text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=True).stdout
            if "Started ContiNewAdminApplication" in logs:
                try:
                    with urlopen(url, timeout=3) as response:
                        if response.status == 200 and json.load(response).get("success") is True:
                            return
                except (URLError, ValueError, TimeoutError):
                    pass
        time.sleep(2)
    raise RuntimeError("启动日志与 API 健康检查未在时限内同时通过")


def main():
    """构建可重复部署的镜像，再替换并重启当前容器；任何异常恢复旧产物和配置。"""
    release = Path(sys.argv[1]).resolve()
    jar = release / "app.jar"
    expected_sha = sys.argv[2]
    if sha(jar) != expected_sha:
        raise RuntimeError("上传的 JAR 校验不匹配")
    image = run("docker", "inspect", CONTAINER, "--format", "{{.Config.Image}}", capture=True)
    if image != EXPECTED_IMAGE:
        raise RuntimeError("线上容器版本已变化，停止替换")
    if BACKUP.exists():
        raise RuntimeError("本次发布备份目录已存在，停止重复发布")
    os.umask(0o077)
    BACKUP.mkdir(parents=True)
    run("docker", "cp", f"{CONTAINER}:/app/app.jar", str(BACKUP / "app.jar"))
    shutil.copy2(DEPLOY / ".env", BACKUP / ".env")
    receipt = {"oldImage": image, "oldSha256": sha(BACKUP / "app.jar"), "newSha256": expected_sha, "newTag": TAG}
    (BACKUP / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    (release / "Dockerfile").write_text(
        f"FROM {EXPECTED_IMAGE}\n"
        f"LABEL org.opencontainers.image.version=\"{TAG}\"\n"
        "COPY --chown=10001:0 app.jar /app/app.jar\n"
    )
    run("docker", "build", "--pull=false", "--network=none", "--rm", "-t", f"eve-corp-manager-api:{TAG}", str(release))
    # Compose 共用版本标签，Web 继续指向发布前已验证的相同镜像内容。
    web_image = run("docker", "inspect", "eve-corp-manager-web", "--format", "{{.Image}}", capture=True)
    run("docker", "tag", web_image, f"eve-corp-manager-web:{TAG}")
    try:
        run("docker", "cp", str(jar), f"{CONTAINER}:/app/app.jar.next")
        run("docker", "exec", CONTAINER, "mv", "/app/app.jar.next", "/app/app.jar")
        current_sha = run("docker", "exec", CONTAINER, "sha256sum", "/app/app.jar", capture=True).split()[0]
        if current_sha != expected_sha:
            raise RuntimeError("容器内产物摘要不匹配")
        env_file = DEPLOY / ".env"
        content = env_file.read_text()
        lines = content.splitlines()
        if sum(line.startswith("IMAGE_TAG=") for line in lines) != 1:
            raise RuntimeError("部署配置版本字段不唯一")
        content = "\n".join(f"IMAGE_TAG={TAG}" if line.startswith("IMAGE_TAG=") else line for line in lines) + "\n"
        next_env = DEPLOY / ".env.next"
        next_env.write_text(content)
        next_env.chmod(0o600)
        next_env.replace(env_file)
        run("docker", "restart", CONTAINER)
        wait_healthy()
    except Exception:
        run("docker", "stop", "-t", "15", CONTAINER)
        run("docker", "cp", str(BACKUP / "app.jar"), f"{CONTAINER}:/app/app.jar")
        shutil.copy2(BACKUP / ".env", DEPLOY / ".env")
        run("docker", "start", CONTAINER)
        wait_healthy()
        raise
    print(json.dumps({"stage": "healthy", "sha256": expected_sha, "tag": TAG, "backup": str(BACKUP)}))


if __name__ == "__main__":
    main()
