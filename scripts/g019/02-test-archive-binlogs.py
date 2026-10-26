#!/usr/bin/env python3
"""仅在临时目录中验证 binlog 归档及复制保护，不连接数据库。"""

import gzip
import hashlib
import importlib.util
import io
import os
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("archive_binlogs", Path(__file__).with_name("01-archive-binlogs.py"))
RECOVERY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RECOVERY)


class ArchiveTests(unittest.TestCase):
    """验证归档可恢复、禁止覆盖和复制依赖的停止条件。"""

    def test_archive_preserves_source_and_restores_exact_bytes(self):
        """压缩后逐字节可恢复，且原始日志不会被辅助函数删除。"""
        with tempfile.TemporaryDirectory() as folder:
            source = Path(folder) / "mysql-bin.000001"
            destination = source.with_suffix(".gz")
            payload = bytes(range(256)) * 8192
            source.write_bytes(payload)
            record = RECOVERY.archive(source, destination)
            self.assertEqual(source.read_bytes(), payload)
            self.assertEqual(gzip.decompress(destination.read_bytes()), payload)
            self.assertEqual(record["originalSha256"], hashlib.sha256(payload).hexdigest())
            self.assertEqual(record["archiveSha256"], hashlib.sha256(destination.read_bytes()).hexdigest())
            self.assertEqual(destination.stat().st_mode & 0o777, 0o600)

    def test_existing_archive_is_not_overwritten(self):
        """已有归档是停止条件，不能覆盖上次恢复凭证。"""
        with tempfile.TemporaryDirectory() as folder:
            source = Path(folder) / "mysql-bin.000001"
            destination = source.with_suffix(".gz")
            source.write_bytes(b"source")
            destination.write_bytes(b"previous archive")
            with self.assertRaisesRegex(RuntimeError, "已存在"):
                RECOVERY.archive(source, destination)
            self.assertEqual(destination.read_bytes(), b"previous archive")

    def test_invalid_archive_readback_blocks_completion(self):
        """读回内容错误时只留下待检查的临时归档，原日志仍在。"""
        with tempfile.TemporaryDirectory() as folder:
            source = Path(folder) / "mysql-bin.000001"
            destination = source.with_suffix(".gz")
            source.write_bytes(b"original bytes")
            with patch.object(RECOVERY.gzip, "open", return_value=io.BytesIO(b"invalid bytes")):
                with self.assertRaisesRegex(RuntimeError, "读回校验失败"):
                    RECOVERY.archive(source, destination)
            self.assertTrue(source.exists())
            self.assertFalse(destination.exists())

    def test_archive_failure_never_executes_purge(self):
        """完整流程中的归档失败不能进入 PURGE 语句。"""
        with tempfile.TemporaryDirectory() as folder:
            data = Path(folder) / "data"
            data.mkdir()
            source = data / "mysql-bin.000001"
            source.write_bytes(b"original bytes")
            old = time.time() - 4 * 86400
            os.utime(source, (old, old))
            arguments = ["archive", "--data-dir", str(data), "--archive-dir", str(Path(folder) / "archive"),
                         "--execute"]

            def sql_result(container, sql):
                """仅模拟日志目录，其他前置查询表示无复制。"""
                if sql == "SHOW BINARY LOGS":
                    return [["mysql-bin.000001", "14"], ["mysql-bin.000002", "14"]]
                if sql == "SHOW MASTER STATUS":
                    return [["mysql-bin.000002"]]
                return []

            with patch.object(RECOVERY, "mysql", side_effect=sql_result) as mysql, \
                    patch.object(RECOVERY.shutil, "disk_usage", return_value=type("Usage", (), {"free": 0})()), \
                    patch.object(RECOVERY, "archive", side_effect=RuntimeError("校验失败")), \
                    patch("sys.argv", arguments):
                with self.assertRaisesRegex(RuntimeError, "校验失败"):
                    RECOVERY.main()
                self.assertFalse(any(call.args[1].startswith("PURGE") for call in mysql.call_args_list))
            self.assertEqual(source.read_bytes(), b"original bytes")

    def test_registered_replica_blocks_processing(self):
        """发现注册副本时，在任何归档清理动作前停止。"""
        with patch.object(RECOVERY, "mysql", return_value=[["replica"]]):
            with self.assertRaisesRegex(RuntimeError, "复制依赖"):
                RECOVERY.check_replication("test")

    def test_binlog_consumer_blocks_processing(self):
        """没有副本登记但仍有日志消费者时也必须停止。"""
        with patch.object(RECOVERY, "mysql", side_effect=[[], [], [], [["1"]]]):
            with self.assertRaisesRegex(RuntimeError, "复制依赖"):
                RECOVERY.check_replication("test")

    def test_standalone_server_passes_read_only_checks(self):
        """无复制依赖时允许进入后续显式执行流程。"""
        with patch.object(RECOVERY, "mysql", return_value=[]) as mysql:
            RECOVERY.check_replication("test")
            self.assertEqual(mysql.call_count, 4)


if __name__ == "__main__":
    unittest.main()
