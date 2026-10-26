#!/usr/bin/env python3
"""将已关闭的旧 binlog 校验归档后，通过 MySQL 正规清理，保留业务数据。"""

import argparse
import datetime
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time


def mysql(container, sql):
    """在现有容器内使用既有凭据，不向终端输出密码。"""
    result = subprocess.run(
        [
            "docker", "exec", container, "sh", "-c",
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot '
            '--batch --raw --skip-column-names -e "$1"',
            "sh", sql,
        ],
        capture_output=True, text=True, timeout=30, check=True,
    )
    return [line.split("\t") for line in result.stdout.splitlines() if line]


def check_replication(container):
    """有复制通道、注册副本或正在消费日志的连接时停止自动处理。"""
    checks = [
        "SHOW REPLICAS",
        "SHOW REPLICA STATUS",
        "SELECT 1 FROM performance_schema.replication_connection_configuration LIMIT 1",
        "SELECT 1 FROM information_schema.processlist WHERE command LIKE 'Binlog%' LIMIT 1",
    ]
    if any(mysql(container, sql) for sql in checks):
        raise RuntimeError("发现复制依赖，停止自动归档清理，需人工核对消费位置")


def digest(stream):
    """计算完整数据流摘要和长度，用于验证归档可还原。"""
    value = hashlib.sha256()
    length = 0
    while chunk := stream.read(1024 * 1024):
        value.update(chunk)
        length += len(chunk)
    return value.hexdigest(), length


def archive(source, destination):
    """压缩并完整读回校验；校验前不触碰 MySQL 日志清单。"""
    before = source.stat()
    temporary = destination.with_suffix(destination.suffix + ".partial")
    if destination.exists() or temporary.exists():
        raise RuntimeError(f"归档文件已存在，停止以免覆盖：{destination.name}")
    original_hash = hashlib.sha256()
    with source.open("rb") as reader, temporary.open("xb") as output:
        os.fchmod(output.fileno(), 0o600)
        with gzip.GzipFile(filename="", mode="wb", fileobj=output, compresslevel=1, mtime=0) as writer:
            while chunk := reader.read(1024 * 1024):
                original_hash.update(chunk)
                writer.write(chunk)
        output.flush()
        os.fsync(output.fileno())
    after = source.stat()
    if (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
        raise RuntimeError("源日志在归档期间发生变化，禁止清理")
    with gzip.open(temporary, "rb") as reader:
        restored_hash, restored_size = digest(reader)
    if restored_hash != original_hash.hexdigest() or restored_size != before.st_size:
        raise RuntimeError("归档读回校验失败，禁止清理")
    temporary.rename(destination)
    with destination.open("rb") as reader:
        compressed_hash, compressed_size = digest(reader)
    return {
        "file": source.name,
        "originalBytes": before.st_size,
        "originalSha256": restored_hash,
        "archiveBytes": compressed_size,
        "archiveSha256": compressed_hash,
    }


def main():
    """以单文件为边界，归档、验证、持久化清单后再安全清理。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--container", default="mysql8_common")
    parser.add_argument("--data-dir", type=Path, default=Path("/home/mysql8/mysql/data"))
    parser.add_argument("--archive-dir", type=Path, required=True)
    parser.add_argument("--free-target-gib", type=float, default=40)
    parser.add_argument("--minimum-online-hours", type=float, default=72)
    parser.add_argument("--execute", action="store_true", help="默认只检查，显式指定才归档清理")
    args = parser.parse_args()
    if args.minimum_online_hours < 72 or args.free_target_gib <= 0:
        parser.error("至少保留最近 72 小时在线日志，并设置正数空间目标")
    check_replication(args.container)
    logs = mysql(args.container, "SHOW BINARY LOGS")
    active = mysql(args.container, "SHOW MASTER STATUS")[0][0]
    cutoff = time.time() - args.minimum_online_hours * 3600
    data_dir = args.data_dir.resolve(strict=True)
    candidates = []
    for row in logs:
        name = row[0]
        if not re.fullmatch(r"mysql-bin\.[0-9]+", name):
            raise RuntimeError("日志文件名不符合预期，停止处理")
        source = data_dir / name
        if source.is_symlink():
            raise RuntimeError("不允许处理符号链接")
        if name == active or source.stat().st_mtime >= cutoff:
            break
        candidates.append(name)
    print(json.dumps({"candidates": len(candidates), "active": active, "execute": args.execute}), flush=True)
    if not args.execute:
        return
    args.archive_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(args.archive_dir, 0o700)
    manifest = args.archive_dir / "manifest.jsonl"
    with manifest.open("a", encoding="utf-8") as journal:
        os.fchmod(journal.fileno(), 0o600)
        for name in candidates:
            if shutil.disk_usage(data_dir).free >= args.free_target_gib * 1024**3:
                break
            current = mysql(args.container, "SHOW BINARY LOGS")
            names = [row[0] for row in current]
            if name not in names:
                continue
            if names[0] != name or len(names) < 2:
                raise RuntimeError("日志前缀边界变化，禁止扩大清理范围")
            destination = args.archive_dir / (name + ".gz")
            record = archive(data_dir / name, destination)
            record["verifiedAt"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
            journal.write(json.dumps({"state": "verified", **record}) + "\n")
            journal.flush()
            os.fsync(journal.fileno())
            directory_fd = os.open(args.archive_dir, os.O_RDONLY | os.O_DIRECTORY)
            try:
                os.fsync(directory_fd)
            finally:
                os.close(directory_fd)
            check_replication(args.container)
            # MySQL 自行维护索引，不删除原始日志文件、不重置 GTID 或业务表。
            mysql(args.container, f"PURGE BINARY LOGS TO '{names[1]}'")
            if name in [row[0] for row in mysql(args.container, "SHOW BINARY LOGS")]:
                raise RuntimeError("MySQL 未清理目标文件，停止并检查告警")
            free = shutil.disk_usage(data_dir).free
            journal.write(json.dumps({"state": "purged", "file": name, "freeBytes": free}) + "\n")
            journal.flush()
            os.fsync(journal.fileno())
            print(json.dumps({"file": name, "originalMiB": round(record["originalBytes"] / 1024**2, 1),
                              "archiveMiB": round(record["archiveBytes"] / 1024**2, 1),
                              "freeGiB": round(free / 1024**3, 2)}), flush=True)
    print(json.dumps({"finished": True, "freeGiB": round(shutil.disk_usage(data_dir).free / 1024**3, 2)}), flush=True)


if __name__ == "__main__":
    main()
