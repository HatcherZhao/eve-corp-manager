#!/usr/bin/env python3
"""备份并替换现有 Web 容器的静态目录，保留最新权限修复镜像及原资源配置。"""

import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
from urllib.request import urlopen


WEB = "eve-corp-manager-web"
TAG = "20261027-no-ads"
OLD_TAG = "20261027-role-policy"
ENV = Path("/home/eve-corp-manager/deploy/production/.env")
ROOT = "/usr/share/nginx/html"
BACKUP = Path("/home/eve-corp-manager/backups") / TAG


def run(*args):
    """执行固定参数命令，防止部署配置或容器环境进入输出。"""
    return subprocess.check_output(args, text=True).strip()


def digest(path):
    """核验上传产物。"""
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    """构建可重复部署镜像后切换静态目录，健康检查失败时恢复原目录和配置。"""
    release = Path(sys.argv[1]).resolve()
    bundle = release / "web.tar.gz"
    if digest(bundle) != sys.argv[2]:
        raise RuntimeError("前端上传产物校验不匹配")
    config = ENV.read_text()
    if [line for line in config.splitlines() if line.startswith("IMAGE_TAG=")] != ["IMAGE_TAG=" + OLD_TAG]:
        raise RuntimeError("线上发布版本已变化，停止替换")
    if BACKUP.exists():
        raise RuntimeError("本次发布备份已存在，停止重复发布")
    web = json.loads(run("docker", "inspect", WEB))[0]
    api = json.loads(run("docker", "inspect", "eve-corp-manager-api"))[0]
    current_api = run("docker", "exec", "eve-corp-manager-api", "sha256sum", "/app/app.jar").split()[0]
    if current_api != "393fd6e69fd8613b317884ae73e397683dd9aef24d5b032441b40e0cad2b7973":
        raise RuntimeError("线上 API 产物已变化，需重新核对发布基线")
    with tarfile.open(bundle, "r:gz") as archive:
        for member in archive.getmembers():
            if member.name.startswith("/") or ".." in Path(member.name).parts or member.issym() or member.islnk():
                raise RuntimeError("前端归档含不安全路径")
        archive.extractall(release / "dist")
    dist = release / "dist"
    for path in dist.rglob("*"):
        if path.is_file() and path.suffix in {".js", ".css", ".html"}:
            content = path.read_text(errors="ignore").lower()
            if "wwads" in content or "makemoney.js" in content:
                raise RuntimeError("新前端仍含广告入口")
    os.umask(0o077)
    BACKUP.mkdir(parents=True)
    shutil.copy2(ENV, BACKUP / ".env")
    run("docker", "cp", f"{WEB}:{ROOT}", str(BACKUP / "html"))
    # 保留旧哈希分包供更新前已经打开的页面继续加载，新首页只引用本次构建的无广告布局。
    site = release / "site"
    shutil.copytree(BACKUP / "html", site)
    shutil.copytree(dist, site, dirs_exist_ok=True)
    (BACKUP / "receipt.json").write_text(json.dumps({"webImage": web["Image"], "apiImage": api["Image"],
                                                       "apiSha256": current_api, "newTag": TAG}, indent=2) + "\n")
    (release / "Dockerfile").write_text(f"FROM eve-corp-manager-web:{OLD_TAG}\n"
                                        "COPY dist/ /usr/share/nginx/html/\n")
    subprocess.run(["docker", "build", "--pull=false", "--network=none", "--rm", "-t",
                    f"eve-corp-manager-web:{TAG}", str(release)], check=True)
    # 当前 API 容器的原始镜像标签仍是旧版；必须复用已发布权限修复镜像，不能给原始镜像加新标签。
    run("docker", "tag", f"eve-corp-manager-api:{OLD_TAG}", f"eve-corp-manager-api:{TAG}")
    run("docker", "cp", str(site), f"{WEB}:{ROOT}.next-no-ads")
    switched = False
    try:
        run("docker", "exec", WEB, "mv", ROOT, ROOT + ".before-no-ads")
        switched = True
        run("docker", "exec", WEB, "mv", ROOT + ".next-no-ads", ROOT)
        next_env = ENV.with_name(".env.next")
        next_env.write_text(config.replace("IMAGE_TAG=" + OLD_TAG, "IMAGE_TAG=" + TAG))
        next_env.chmod(0o600)
        next_env.replace(ENV)
        binding = web["HostConfig"]["PortBindings"]["80/tcp"][0]
        host = binding["HostIp"] or "127.0.0.1"
        base = f"http://{host}:{binding['HostPort']}"
        with urlopen(base + "/", timeout=10) as response:
            served = response.read()
        if served != (dist / "index.html").read_bytes():
            raise RuntimeError("线上首页不是本次产物")
        with urlopen(base + "/api/captcha/image", timeout=10) as response:
            if json.load(response).get("success") is not True:
                raise RuntimeError("发布后的 API 代理检查失败")
        api_after = json.loads(run("docker", "inspect", "eve-corp-manager-api"))[0]
        if api_after["State"]["StartedAt"] != api["State"]["StartedAt"]:
            raise RuntimeError("API 在本次前端发布期间发生了意外重启")
    except Exception:
        if switched:
            # 保留异常产物用于诊断，恢复备份目录，不删除线上文件。
            run("docker", "exec", WEB, "sh", "-c",
                f"if [ -d {ROOT} ]; then mv {ROOT} {ROOT}.failed-no-ads; fi; mv {ROOT}.before-no-ads {ROOT}")
        shutil.copy2(BACKUP / ".env", ENV)
        raise
    print(json.dumps({"stage": "healthy", "tag": TAG, "webIndexSha256": digest(dist / "index.html"),
                      "apiRestarted": False, "backup": str(BACKUP)}))


if __name__ == "__main__":
    main()
