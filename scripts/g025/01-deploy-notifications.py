#!/usr/bin/env python3
"""更新现有 API 与 Web 产物，校验版本、保留备份并在健康检查失败时恢复。"""

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
TAG = "20261028-notification-presentation"
BASE_TAG = "20261027-layout-options-r2"
ENV = Path("/home/eve-corp-manager/deploy/production/.env")
ROOT = "/usr/share/nginx/html"
BACKUP = Path("/home/eve-corp-manager/backups") / TAG
OLD_API_SHA = "393fd6e69fd8613b317884ae73e397683dd9aef24d5b032441b40e0cad2b7973"
OLD_INDEX_SHA = "2bb156522a685c306461c3ed5e01b207b140f8e0fa7a54a3255af434c7bba75c"


def run(*args):
    """捕获命令输出，避免配置和容器环境进入部署日志。"""
    result = subprocess.run(args, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(f"部署命令失败：{args[0]} {args[1]}，退出码 {result.returncode}")
    return result.stdout.strip()


def inspect(name):
    """只在内存读取容器配置。"""
    return json.loads(run("docker", "inspect", name))[0]


def sha(path):
    """以分块方式校验上传产物。"""
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def base_url(web):
    """沿用现有 Web 绑定地址和端口。"""
    binding = web["HostConfig"]["PortBindings"]["80/tcp"][0]
    host = binding["HostIp"]
    if host in {"", "0.0.0.0", "::"}:
        host = "127.0.0.1"
    return f"http://{host}:{binding['HostPort']}"


def wait_healthy(base):
    """启动日志和真实 API 同时成功才认定应用恢复。"""
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        state = inspect(API)["State"]
        if state["Status"] == "running":
            result = subprocess.run(["docker", "logs", "--since", state["StartedAt"], API],
                                    text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=True)
            if "Started ContiNewAdminApplication" in result.stdout:
                try:
                    with urlopen(base + "/api/captcha/image", timeout=3) as response:
                        if response.status == 200 and json.load(response).get("success") is True:
                            return
                except (URLError, ValueError, TimeoutError):
                    pass
        time.sleep(2)
    raise RuntimeError("应用启动日志与 API 健康检查未同时通过")


def resources(container):
    """记录并核对既有资源、网络及卷配置。"""
    host = container["HostConfig"]
    return {key: host.get(key) for key in ["NanoCpus", "Memory", "PortBindings", "Binds", "NetworkMode", "RestartPolicy"]}


def main():
    """先构建可重复部署镜像，再替换当前容器产物并完成健康检查。"""
    release = Path(sys.argv[1]).resolve()
    expected_jar, expected_web, revision = sys.argv[2:5]
    jar, bundle = release / "app.jar", release / "web.tar.gz"
    if sha(jar) != expected_jar or sha(bundle) != expected_web:
        raise RuntimeError("上传产物摘要不匹配")
    config = ENV.read_text()
    if [line for line in config.splitlines() if line.startswith("IMAGE_TAG=")] != ["IMAGE_TAG=" + BASE_TAG]:
        raise RuntimeError("发布版本已变化，停止部署")
    api, web = inspect(API), inspect(WEB)
    if run("docker", "exec", API, "sha256sum", "/app/app.jar").split()[0] != OLD_API_SHA:
        raise RuntimeError("当前 API 与已核对基线不一致")
    if run("docker", "exec", WEB, "sha256sum", ROOT + "/index.html").split()[0] != OLD_INDEX_SHA:
        raise RuntimeError("当前 Web 与已核对基线不一致")
    if BACKUP.exists():
        raise RuntimeError("发布备份已存在，停止重复执行")
    with tarfile.open(bundle, "r:gz") as archive:
        for member in archive.getmembers():
            if member.name.startswith("/") or ".." in Path(member.name).parts or member.issym() or member.islnk():
                raise RuntimeError("前端归档路径无效")
        archive.extractall(release / "dist")
    dist = release / "dist"
    os.umask(0o077)
    BACKUP.mkdir(parents=True)
    shutil.copy2(ENV, BACKUP / ".env")
    run("docker", "cp", f"{API}:/app/app.jar", str(BACKUP / "app.jar"))
    run("docker", "cp", f"{WEB}:{ROOT}", str(BACKUP / "html"))
    # 合并旧哈希资源，已打开页面在发布期间仍能加载原有分包。
    site = release / "site"
    shutil.copytree(BACKUP / "html", site)
    shutil.copytree(dist, site, dirs_exist_ok=True)
    for kind, body in {
        "api": "COPY --chown=10001:0 app.jar /app/app.jar\n",
        "web": "COPY site/ /usr/share/nginx/html/\n",
    }.items():
        dockerfile = release / f"Dockerfile.{kind}"
        dockerfile.write_text(f"FROM eve-corp-manager-{kind}:{BASE_TAG}\n"
                              f'LABEL org.opencontainers.image.revision="{revision}"\n' + body)
        # 既有已发布镜像包含权限和布局修复，不能取旧容器原始镜像作为基线。
        run("docker", "build", "--pull=false", "--network=none", "--rm", "-f", str(dockerfile),
            "-t", f"eve-corp-manager-{kind}:{TAG}", str(release))
    receipt = {"tag": TAG, "gitRevision": revision, "apiSha256": expected_jar,
               "webBundleSha256": expected_web, "indexSha256": sha(dist / "index.html"),
               "previousApiSha256": OLD_API_SHA, "previousIndexSha256": OLD_INDEX_SHA,
               "resources": {API: resources(api), WEB: resources(web)}, "backup": str(BACKUP)}
    (BACKUP / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    switched = False
    try:
        run("docker", "cp", str(site), f"{WEB}:{ROOT}.next-g025")
        run("docker", "cp", str(jar), f"{API}:/app/app.jar.next-g025")
        run("docker", "exec", API, "mv", "/app/app.jar.next-g025", "/app/app.jar")
        if run("docker", "exec", API, "sha256sum", "/app/app.jar").split()[0] != expected_jar:
            raise RuntimeError("容器内 JAR 摘要不一致")
        run("docker", "restart", API)
        base = base_url(web)
        wait_healthy(base)
        run("docker", "exec", WEB, "mv", ROOT, ROOT + ".before-g025")
        switched = True
        run("docker", "exec", WEB, "mv", ROOT + ".next-g025", ROOT)
        next_env = ENV.with_name(".env.next-g025")
        next_env.write_text(config.replace("IMAGE_TAG=" + BASE_TAG, "IMAGE_TAG=" + TAG))
        next_env.chmod(0o600)
        next_env.replace(ENV)
        with urlopen(base + "/", timeout=10) as response:
            if hashlib.sha256(response.read()).hexdigest() != receipt["indexSha256"]:
                raise RuntimeError("线上首页与发布产物不一致")
        for name, before in [(API, api), (WEB, web)]:
            if resources(inspect(name)) != resources(before):
                raise RuntimeError("容器资源配置发生意外变化")
    except Exception:
        run("docker", "stop", "-t", "15", API)
        run("docker", "cp", str(BACKUP / "app.jar"), f"{API}:/app/app.jar")
        if switched:
            run("docker", "exec", WEB, "sh", "-c",
                f"if [ -d {ROOT} ]; then mv {ROOT} {ROOT}.failed-g025; fi; mv {ROOT}.before-g025 {ROOT}")
        shutil.copy2(BACKUP / ".env", ENV)
        run("docker", "start", API)
        wait_healthy(base_url(web))
        raise
    receipt["stage"] = "healthy"
    (release / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print(json.dumps(receipt))


if __name__ == "__main__":
    main()
