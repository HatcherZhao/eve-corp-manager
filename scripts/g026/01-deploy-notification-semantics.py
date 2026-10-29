#!/usr/bin/env python3
"""更新现有 API 和机器人通知技能，保留资源配置并在失败时恢复。"""

import hashlib
import json
import os
from pathlib import Path
import runpy
import shutil
import sys


TAG = "20261029-notification-semantics"
BASE_TAG = "20261028-notification-presentation"
OLD_API_SHA = "a492cc4a8fb8b44c7ddca55d1da62fd89a4550377793516d4dde31e9f853d064"
OLD_INDEX_SHA = "d253918954f94397671bec722bbbb3dffa3816eb18e0d470a752d80a1478ac1f"
SKILL = Path("/home/astrbot/data/skills/eve-corp-manager")
SKILL_BASE = {
    "SKILL.md": "251474e6bdc2dccac29df566465f1370e6f0dd43c68a0a1afba9ab41616392ea",
    "references/player-workflows.md": "c0a76d0d9540ab68d916d56d79c2dde596c4ca1a6a757f7f9d34fc81c0204730",
    "references/corporation-scope.md": "71ac8970bf754aaf8280c74740872dce61d44ac499cf63aa283a7dafd515c84b",
}


def main():
    """校验基线、备份及构建后，替换 API 和三个技能文件并核验。"""
    release = Path(sys.argv[1]).resolve()
    manifest = json.loads((release / "manifest.json").read_text())
    helper = runpy.run_path(str(release / "deploy-helper.py"))
    run, inspect, sha = [helper[key] for key in ["run", "inspect", "sha"]]
    api_name, web_name = helper["API"], helper["WEB"]
    env_path = helper["ENV"]
    backup = Path("/home/eve-corp-manager/backups") / TAG
    config = env_path.read_text()
    if [line for line in config.splitlines() if line.startswith("IMAGE_TAG=")] != ["IMAGE_TAG=" + BASE_TAG]:
        raise RuntimeError("当前发布标签与基线不同")
    if backup.exists():
        raise RuntimeError("备份目录已存在，禁止重复执行")
    for relative, expected in manifest["files"].items():
        if sha(release / relative) != expected:
            raise RuntimeError("上传文件校验失败：" + relative)
    api, web = inspect(api_name), inspect(web_name)
    if run("docker", "exec", api_name, "sha256sum", "/app/app.jar").split()[0] != OLD_API_SHA:
        raise RuntimeError("线上 API 基线已变化")
    if run("docker", "exec", web_name, "sha256sum", helper["ROOT"] + "/index.html").split()[0] != OLD_INDEX_SHA:
        raise RuntimeError("线上前端基线已变化")
    for relative, expected in SKILL_BASE.items():
        if sha(SKILL / relative) != expected:
            raise RuntimeError("线上技能基线已变化：" + relative)
    os.umask(0o077)
    backup.mkdir(parents=True)
    shutil.copy2(env_path, backup / ".env")
    run("docker", "cp", api_name + ":/app/app.jar", str(backup / "app.jar"))
    for relative in SKILL_BASE:
        target = backup / "skill" / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(SKILL / relative, target)
    dockerfile = release / "Dockerfile.api"
    dockerfile.write_text(f"FROM eve-corp-manager-api:{BASE_TAG}\n"
                          f'LABEL org.opencontainers.image.revision="{manifest["gitRevision"]}"\n'
                          "COPY --chown=10001:0 app.jar /app/app.jar\n")
    run("docker", "build", "--pull=false", "--network=none", "--rm", "-f", str(dockerfile),
        "-t", "eve-corp-manager-api:" + TAG, str(release))
    # 本次前端无改动，沿用上一已发布镜像，保持统一 IMAGE_TAG 可重复部署。
    run("docker", "tag", "eve-corp-manager-web:" + BASE_TAG, "eve-corp-manager-web:" + TAG)
    receipt = {"tag": TAG, "gitRevision": manifest["gitRevision"], "files": manifest["files"],
               "previousTag": BASE_TAG, "backup": str(backup),
               "resources": {api_name: helper["resources"](api), web_name: helper["resources"](web)}}
    (backup / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    changed_skills = []
    try:
        run("docker", "cp", str(release / "app.jar"), api_name + ":/app/app.jar.next-g026")
        run("docker", "exec", api_name, "mv", "/app/app.jar.next-g026", "/app/app.jar")
        if run("docker", "exec", api_name, "sha256sum", "/app/app.jar").split()[0] != manifest["files"]["app.jar"]:
            raise RuntimeError("容器 JAR 校验失败")
        run("docker", "restart", api_name)
        helper["wait_healthy"](helper["base_url"](web))
        for relative in SKILL_BASE:
            target = SKILL / relative
            if sha(target) != SKILL_BASE[relative]:
                raise RuntimeError("技能在部署期间发生变化，停止覆盖：" + relative)
            pending = target.with_name(target.name + ".next-g026")
            shutil.copy2(release / "skill" / relative, pending)
            pending.chmod(target.stat().st_mode & 0o777)
            pending.replace(target)
            changed_skills.append(relative)
            if sha(target) != manifest["files"]["skill/" + relative]:
                raise RuntimeError("机器人技能校验失败")
        next_env = env_path.with_name(".env.next-g026")
        next_env.write_text(config.replace("IMAGE_TAG=" + BASE_TAG, "IMAGE_TAG=" + TAG))
        next_env.chmod(0o600)
        next_env.replace(env_path)
        for name, before in [(api_name, api), (web_name, web)]:
            if helper["resources"](inspect(name)) != helper["resources"](before):
                raise RuntimeError("容器资源配置发生变化")
        if run("docker", "exec", web_name, "sha256sum", helper["ROOT"] + "/index.html").split()[0] != OLD_INDEX_SHA:
            raise RuntimeError("前端产物发生变化")
    except Exception:
        run("docker", "stop", "-t", "15", api_name)
        run("docker", "cp", str(backup / "app.jar"), api_name + ":/app/app.jar")
        for relative in changed_skills:
            if sha(SKILL / relative) == manifest["files"]["skill/" + relative]:
                shutil.copy2(backup / "skill" / relative, SKILL / relative)
        shutil.copy2(backup / ".env", env_path)
        run("docker", "start", api_name)
        helper["wait_healthy"](helper["base_url"](web))
        raise
    receipt["stage"] = "healthy"
    (release / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print(json.dumps(receipt))


if __name__ == "__main__":
    main()
