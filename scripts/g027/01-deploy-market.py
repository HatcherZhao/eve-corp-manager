#!/usr/bin/env python3
"""发布晨曦市场改造到现有容器，并保留可回退的应用产物。"""

import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import time
from urllib.error import URLError
from urllib.request import urlopen

API = "eve-corp-manager-api"
WEB = "eve-corp-manager-web"
ENV = Path("/home/eve-corp-manager/deploy/production/.env")
WEB_ROOT = "/usr/share/nginx/html"


def run(*args):
    """执行命令，不输出容器配置或环境变量。"""
    result = subprocess.run(args, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(f"命令失败：{args[0]} {args[1]}，退出码 {result.returncode}")
    return result.stdout.strip()


def inspect(name):
    """读取容器配置供发布前后校验。"""
    return json.loads(run("docker", "inspect", name))[0]


def digest(path):
    """计算文件摘要。"""
    value = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def resources(container):
    """保留资源、网络、卷与重启策略的发布基线。"""
    host = container["HostConfig"]
    return {key: host.get(key) for key in ("NanoCpus", "Memory", "PortBindings", "Binds", "NetworkMode", "RestartPolicy")}


def web_url(container):
    """从已有容器读取 Web 绑定地址。"""
    binding = container["HostConfig"]["PortBindings"]["80/tcp"][0]
    host = binding["HostIp"]
    if host in ("", "0.0.0.0", "::"):
        host = "127.0.0.1"
    return f"http://{host}:{binding['HostPort']}"


def wait_healthy(base):
    """同时检查应用启动日志和真实 API 响应。"""
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        state = inspect(API)["State"]
        if state["Status"] == "running":
            logs = subprocess.run(["docker", "logs", "--since", state["StartedAt"], API],
                                  text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=True).stdout
            if "Started ContiNewAdminApplication" in logs:
                try:
                    with urlopen(base + "/api/captcha/image", timeout=3) as response:
                        if response.status == 200 and json.load(response).get("success") is True:
                            return
                except (URLError, ValueError, TimeoutError):
                    pass
        time.sleep(2)
    raise RuntimeError("API 启动日志与健康检查未同时通过")


def main():
    """校验基线与产物，构建持久镜像并替换现有容器内文件。"""
    release = Path(sys.argv[1]).resolve()
    manifest = json.loads((release / "manifest.json").read_text())
    tag, base_tag = manifest["tag"], manifest["baseTag"]
    jar, bundle = release / "app.jar", release / "web.tar.gz"
    if digest(jar) != manifest["apiSha256"] or digest(bundle) != manifest["webSha256"]:
        raise RuntimeError("上传产物摘要不匹配")
    config = ENV.read_text()
    if [line for line in config.splitlines() if line.startswith("IMAGE_TAG=")] != ["IMAGE_TAG=" + base_tag]:
        raise RuntimeError("线上发布标签已变化")
    api, web = inspect(API), inspect(WEB)
    if run("docker", "exec", API, "sha256sum", "/app/app.jar").split()[0] != manifest["previousApiSha256"]:
        raise RuntimeError("线上 API 基线已变化")
    if run("docker", "exec", WEB, "sha256sum", WEB_ROOT + "/index.html").split()[0] != manifest["previousIndexSha256"]:
        raise RuntimeError("线上 Web 基线已变化")
    run("docker", "exec", WEB, "sh", "-c", f"test ! -e {WEB_ROOT}.next-g027 && test ! -e {WEB_ROOT}.before-g027")
    run("docker", "exec", API, "sh", "-c", "test ! -e /app/app.jar.next-g027")
    backup = Path("/home/eve-corp-manager/backups") / tag
    if backup.exists():
        raise RuntimeError("发布备份已存在")
    with tarfile.open(bundle, "r:gz") as archive:
        for member in archive.getmembers():
            if member.name.startswith("/") or ".." in Path(member.name).parts or member.issym() or member.islnk():
                raise RuntimeError("前端归档包含不安全路径")
        archive.extractall(release / "dist")
    os.umask(0o077)
    backup.mkdir(parents=True)
    shutil.copy2(ENV, backup / ".env")
    run("docker", "cp", API + ":/app/app.jar", str(backup / "app.jar"))
    run("docker", "cp", WEB + ":" + WEB_ROOT, str(backup / "html"))
    site = release / "site"
    shutil.copytree(backup / "html", site)
    shutil.copytree(release / "dist", site, dirs_exist_ok=True)
    for kind, content in {
        "api": "COPY --chown=10001:0 app.jar /app/app.jar\n",
        "web": "COPY site/ /usr/share/nginx/html/\n",
    }.items():
        dockerfile = release / f"Dockerfile.{kind}"
        dockerfile.write_text(f"FROM eve-corp-manager-{kind}:{base_tag}\n"
                              f'LABEL org.opencontainers.image.revision="{manifest["revision"]}"\n' + content)
        run("docker", "build", "--pull=false", "--network=none", "--rm", "-f", str(dockerfile),
            "-t", f"eve-corp-manager-{kind}:{tag}", str(release))
    before = {API: resources(api), WEB: resources(web)}
    base = web_url(web)
    switched_web = False
    try:
        run("docker", "cp", str(site), WEB + ":" + WEB_ROOT + ".next-g027")
        run("docker", "cp", str(jar), API + ":/app/app.jar.next-g027")
        run("docker", "exec", API, "mv", "/app/app.jar.next-g027", "/app/app.jar")
        if run("docker", "exec", API, "sha256sum", "/app/app.jar").split()[0] != manifest["apiSha256"]:
            raise RuntimeError("容器内 API 摘要不一致")
        run("docker", "restart", API)
        wait_healthy(base)
        run("docker", "exec", WEB, "mv", WEB_ROOT, WEB_ROOT + ".before-g027")
        switched_web = True
        run("docker", "exec", WEB, "mv", WEB_ROOT + ".next-g027", WEB_ROOT)
        with urlopen(base + "/", timeout=10) as response:
            if hashlib.sha256(response.read()).hexdigest() != manifest["indexSha256"]:
                raise RuntimeError("线上首页摘要不一致")
        for name in (API, WEB):
            if resources(inspect(name)) != before[name]:
                raise RuntimeError("容器资源配置发生变化")
        next_env = ENV.with_name(".env.next-g027")
        next_env.write_text(config.replace("IMAGE_TAG=" + base_tag, "IMAGE_TAG=" + tag))
        next_env.chmod(0o600)
        next_env.replace(ENV)
    except Exception:
        run("docker", "stop", "-t", "15", API)
        run("docker", "cp", str(backup / "app.jar"), API + ":/app/app.jar")
        if switched_web:
            run("docker", "exec", WEB, "sh", "-c", f"if [ -d {WEB_ROOT} ]; then mv {WEB_ROOT} {WEB_ROOT}.failed-g027; fi; mv {WEB_ROOT}.before-g027 {WEB_ROOT}")
        shutil.copy2(backup / ".env", ENV)
        run("docker", "start", API)
        wait_healthy(base)
        raise
    receipt = {"tag": tag, "revision": manifest["revision"], "apiSha256": manifest["apiSha256"],
               "indexSha256": manifest["indexSha256"], "previousTag": base_tag, "backup": str(backup),
               "resources": before, "stage": "healthy"}
    (release / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print(json.dumps(receipt))


if __name__ == "__main__":
    main()
