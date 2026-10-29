#!/usr/bin/env python3
"""更新两个既有 AstrBot 巡检任务的通知归属规则，仅修改任务说明。"""

import argparse
from copy import deepcopy
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
from urllib.request import Request, urlopen


BASE = "http://127.0.0.1:6185/api/cron/jobs"
POLICY_MARKER = "【2026-10-29 通知归属与统一解读规则】"
POLICY_END_MARKER = "【通知归属与统一解读规则结束】"
TOKEN = ""
STATE_ROOT = Path("/home/astrbot/data/workspaces")
STATE_NAMES = ("moon_patrol_state.json", "star_patrol_state.json")


def request(method, suffix="", payload=None):
    """调用 AstrBot 任务管理接口，不记录认证令牌或完整任务说明。"""
    body = json.dumps(payload, ensure_ascii=False).encode() if payload is not None else None
    req = Request(
        BASE + suffix,
        data=body,
        method=method,
        headers={"Authorization": "Bearer " + TOKEN, "Content-Type": "application/json"},
    )
    with urlopen(req, timeout=30) as response:
        result = json.load(response)
    if result.get("status") != "ok":
        raise RuntimeError("AstrBot 任务管理接口未返回成功")
    return result.get("data")


def payload_of(job):
    """兼容历史任务记录中的多层 JSON 编码。"""
    value = job.get("payload") or {}
    for _ in range(4):
        if not isinstance(value, str):
            break
        value = json.loads(value)
    if not isinstance(value, dict):
        raise RuntimeError("任务配置格式无效")
    return value


def note_of(job):
    """读取任务说明，优先使用任务载荷中的当前值。"""
    payload = payload_of(job)
    return str(payload.get("note") if payload.get("note") is not None else job.get("description") or "")


def notification_policy(corporation_name, include_moon_events):
    """生成保持既有封闭白名单并核实建筑归属的巡检规则。"""
    notification_types = (
        "StructureFuelAlert、StructureUnderAttack、MoonminingExtractionStarted、"
        "MoonminingAutomaticFracture、MoonminingLaserFired"
        if include_moon_events
        else "StructureFuelAlert、StructureUnderAttack"
    )
    return f"""{POLICY_MARKER}
通知检查适用于{corporation_name}任务的既有检查窗口、分页、延迟补查和持久去重规则，不改变燃料规则、发送目标或执行节奏。

自动推送的 notificationType 是封闭白名单，仅包括：{notification_types}。查询 notifications 时直接添加 notificationType 的 in 筛选，返回后再次核对白名单。Sov*、Entosis*（包括 SovStructureReinforced、EntosisCaptureStarted）及其他联盟主权、领土捕获事件不属于本任务，不发送，不改名为“受袭相关通知”，也不因本轮规则修正重新补发；mail 不能绕过白名单。

对白名单内准备报告的通知逐条调用 eve_read_document，优先使用 interpretation 的 title、summary、details、eventScope、eventScopeLabel、solarSystemName 和 structureTypeName；interpretationComplete 是 eve_read_document 的顶层字段。正文缺失或 interpretationComplete=false 时，只报告已有依据，不补写归属、建筑名或阶段。列表的 title、eventScope、eventScopeLabel 是服务端计算字段，不能放进 fields、filters、sort 或 groupBy。

白名单内的建筑事件只有在正文含 structureId 且与当前军团 structures 精确关联，或正文有明确所有者证据时，才能推送为本军团建筑告警。只含星系、型号或模糊名称的不推送；通知来自当前授权角色、同一星系存在本军团建筑或 eventScope=STRUCTURE 都不能代替资产归属证据。eventScope=ALLIANCE_SOVEREIGNTY 时直接按白名单外事件排除，不在例行巡检中输出摘要。

继续遵守原任务全部燃料与输出规则：以 structures 快照为主，燃料剩余小于等于 168 小时提醒，排除 typeId=47513“德拉库斯”铁壁的燃料提醒，不附加建议；没有符合条件的新事件或燃料提醒时静默。保留现有成功检查位置、通知/邮件去重标识和燃料提醒标识，不重置、不迁移、不手动执行任务。
{POLICY_END_MARKER}"""


def upsert_policy(note, policy):
    """幂等新增或替换规则段，并保留任务说明的其他内容。"""
    start = note.find(POLICY_MARKER)
    if start < 0:
        prefix = note.rstrip()
        return (prefix + "\n\n" if prefix else "") + policy.strip() + "\n"

    end = note.find(POLICY_END_MARKER, start)
    if end < 0:
        raise RuntimeError("既有通知规则缺少结束标记，停止更新以免覆盖其他任务说明")

    suffix = note[end + len(POLICY_END_MARKER):].lstrip("\r\n")
    prefix = note[:start].rstrip()
    updated = (prefix + "\n\n" if prefix else "") + policy.strip()
    if suffix:
        updated += "\n\n" + suffix
    return updated.rstrip() + "\n"


def config_snapshot(job):
    """提取必须保持不变的调度、会话、模型和发送配置。"""
    payload = deepcopy(payload_of(job))
    payload.pop("note", None)
    return {
        "job_id": job.get("job_id"),
        "name": job.get("name"),
        "enabled": job.get("enabled"),
        "cron_expression": job.get("cron_expression"),
        "timezone": job.get("timezone"),
        "payload_without_note": payload,
    }


def sanitized_job(job):
    """生成不含完整任务说明和凭据的备份摘要。"""
    note = note_of(job)
    snapshot = config_snapshot(job)
    payload_json = json.dumps(snapshot["payload_without_note"], ensure_ascii=False, sort_keys=True)
    return {
        "jobId": snapshot["job_id"],
        "name": snapshot["name"],
        "enabled": snapshot["enabled"],
        "cronExpression": snapshot["cron_expression"],
        "timezone": snapshot["timezone"],
        "payloadWithoutNoteSha256": hashlib.sha256(payload_json.encode()).hexdigest(),
        "noteLength": len(note),
        "noteSha256": hashlib.sha256(note.encode()).hexdigest(),
        "hasPolicyMarker": POLICY_MARKER in note,
    }


def patrol_state_hashes():
    """只读记录现有巡检状态文件哈希，不输出路径或文件内容。"""
    hashes = {}
    if not STATE_ROOT.exists():
        return hashes
    for state_name in STATE_NAMES:
        for path in STATE_ROOT.glob("*/" + state_name):
            if path.is_file():
                hashes[str(path)] = hashlib.sha256(path.read_bytes()).hexdigest()
    return hashes


def find_targets(jobs, original_job_id, star_job_name, original_corporation_name, star_corporation_name):
    """精确定位两个既有任务，禁止创建任务或容忍重名。"""
    originals = [job for job in jobs if job.get("job_id") == original_job_id]
    stars = [job for job in jobs if job.get("name") == star_job_name]
    if len(originals) != 1:
        raise RuntimeError("未唯一定位原巡检任务，停止更新")
    if len(stars) != 1:
        raise RuntimeError("未唯一定位指定名称的第二巡检任务，停止更新")
    if originals[0].get("job_id") == stars[0].get("job_id"):
        raise RuntimeError("两个目标任务指向同一记录，停止更新")
    return [
        (originals[0], original_corporation_name, True),
        (stars[0], star_corporation_name, False),
    ]


def prepare_updates(jobs, original_job_id, star_job_name, original_corporation_name, star_corporation_name):
    """准备两个目标任务的新旧说明及更新前配置快照。"""
    before = {}
    original_notes = {}
    expected_notes = {}
    targets = find_targets(
        jobs,
        original_job_id,
        star_job_name,
        original_corporation_name,
        star_corporation_name,
    )
    for job, corporation_name, include_moon_events in targets:
        job_id = job["job_id"]
        before[job_id] = config_snapshot(job)
        original_notes[job_id] = note_of(job)
        expected_note = upsert_policy(
            note_of(job),
            notification_policy(corporation_name, include_moon_events),
        )
        expected_notes[job_id] = expected_note
    return before, original_notes, expected_notes


def verify_job_collection(before_jobs, after_jobs, target_ids):
    """确认任务集合未增删，且两个目标之外的任务完全未变化。"""
    before_by_id = {job.get("job_id"): job for job in before_jobs}
    after_by_id = {job.get("job_id"): job for job in after_jobs}
    if set(before_by_id) != set(after_by_id):
        raise RuntimeError("更新前后任务 ID 集合发生变化")
    for job_id, before_job in before_by_id.items():
        if job_id not in target_ids and after_by_id[job_id] != before_job:
            raise RuntimeError(f"非目标任务发生变化：{job_id}")


def verify_after(jobs, before, expected_notes):
    """确认仅任务说明发生变化，关键配置和两个任务身份保持不变。"""
    by_id = {job.get("job_id"): job for job in jobs}
    for job_id, snapshot in before.items():
        if job_id not in by_id:
            raise RuntimeError(f"更新后任务不存在：{job_id}")
        job = by_id[job_id]
        if config_snapshot(job) != snapshot:
            raise RuntimeError(f"更新改变了任务关键配置：{job_id}")
        actual_note = note_of(job)
        if actual_note != expected_notes[job_id]:
            raise RuntimeError(f"任务说明回读不一致：{job_id}")
        if actual_note.count(POLICY_MARKER) != 1 or actual_note.count(POLICY_END_MARKER) != 1:
            raise RuntimeError(f"通知规则段未保持唯一：{job_id}")


def rollback_notes(attempted_ids, original_notes, before_jobs, before_configs, patcher, reader):
    """失败时仅恢复已尝试修改目标的 note，并回读确认恢复结果。"""
    rollback_errors = []
    rollback_ids = list(attempted_ids)
    try:
        current_by_id = {job.get("job_id"): job for job in reader()}
        rollback_ids = [
            job_id
            for job_id in attempted_ids
            if job_id not in current_by_id or note_of(current_by_id[job_id]) != original_notes[job_id]
        ]
    except Exception:
        # 无法判定服务端是否已落库时，保守恢复所有已尝试目标；仍然只提交 note。
        pass

    for job_id in reversed(rollback_ids):
        try:
            patcher(job_id, {"note": original_notes[job_id]})
        except Exception as exc:  # 回滚需继续尝试其余目标，最后统一报告。
            rollback_errors.append(f"{job_id}: {exc}")

    try:
        restored_jobs = reader()
        verify_job_collection(before_jobs, restored_jobs, set(before_configs))
        restored_by_id = {job.get("job_id"): job for job in restored_jobs}
        for job_id in attempted_ids:
            if job_id not in restored_by_id:
                raise RuntimeError(f"回滚后任务不存在：{job_id}")
            if config_snapshot(restored_by_id[job_id]) != before_configs[job_id]:
                raise RuntimeError(f"回滚后任务关键配置不一致：{job_id}")
            if note_of(restored_by_id[job_id]) != original_notes[job_id]:
                raise RuntimeError(f"回滚后任务说明不一致：{job_id}")
    except Exception as exc:
        rollback_errors.append(str(exc))

    if rollback_errors:
        raise RuntimeError("任务说明回滚未完全确认：" + "；".join(rollback_errors))


def update_with_rollback(
    jobs,
    patcher,
    reader,
    original_job_id,
    star_job_name,
    original_corporation_name,
    star_corporation_name,
):
    """依次更新两个说明；任一更新或校验失败时仅回滚已尝试目标的说明。"""
    before, original_notes, expected_notes = prepare_updates(
        jobs,
        original_job_id,
        star_job_name,
        original_corporation_name,
        star_corporation_name,
    )
    attempted_ids = []
    try:
        for job_id, expected_note in expected_notes.items():
            attempted_ids.append(job_id)
            patcher(job_id, {"note": expected_note})
        after_jobs = reader()
        verify_job_collection(jobs, after_jobs, set(before))
        verify_after(after_jobs, before, expected_notes)
        return after_jobs, before, expected_notes
    except Exception as update_error:
        try:
            rollback_notes(attempted_ids, original_notes, jobs, before, patcher, reader)
        except Exception as rollback_error:
            raise RuntimeError(f"更新失败：{update_error}；{rollback_error}") from update_error
        raise RuntimeError(f"更新失败，已确认恢复原任务说明：{update_error}") from update_error


def check():
    """使用虚构任务离线验证只改 note、任务匹配和规则幂等性。"""
    original_job_id = "original-job-test-id"
    star_job_name = "第二巡检任务（测试）"
    original_corporation_name = "甲军团（测试）"
    star_corporation_name = "乙军团（测试）"

    def fake_job(job_id, name, note, session):
        return {
            "job_id": job_id,
            "name": name,
            "enabled": True,
            "cron_expression": "0 12 * * *",
            "timezone": "Asia/Shanghai",
            "description": note,
            "payload": {
                "name": name,
                "enabled": True,
                "cron_expression": "0 12 * * *",
                "timezone": "Asia/Shanghai",
                "run_once": False,
                "session": session,
                "persona_id": "persona-test",
                "provider_id": "provider-test",
                "note": note,
            },
        }

    jobs = [
        fake_job(original_job_id, "第一巡检任务（测试）", "原有规则 A", "qq:GroupMessage:test-group"),
        fake_job("star-test-id", star_job_name, "第二任务规则 A\n\n保留结尾", "qq:GroupMessage:test-group"),
        fake_job("unrelated-test-id", "无关任务", "无关任务说明", "qq:GroupMessage:other-group"),
    ]
    patched_payloads = []

    def patcher(job_id, payload):
        if set(payload) != {"note"}:
            raise AssertionError("更新载荷包含 note 之外的字段")
        patched_payloads.append((job_id, deepcopy(payload)))
        job = next(item for item in jobs if item["job_id"] == job_id)
        job["payload"]["note"] = payload["note"]
        job["description"] = payload["note"]

    def reader():
        return deepcopy(jobs)

    original_jobs = deepcopy(jobs)
    original_configs = {job["job_id"]: config_snapshot(job) for job in jobs}
    update_args = (
        original_job_id,
        star_job_name,
        original_corporation_name,
        star_corporation_name,
    )
    after_jobs, before, expected_notes = update_with_rollback(
        deepcopy(jobs), patcher, reader, *update_args
    )
    verify_after(after_jobs, before, expected_notes)
    if len(patched_payloads) != 2:
        raise AssertionError("未同时更新两个既有任务")
    if original_configs != {job["job_id"]: config_snapshot(job) for job in jobs}:
        raise AssertionError("离线更新改变了必要配置字段")
    first_notes = {job["job_id"]: note_of(job) for job in jobs}
    after_second, before_second, expected_second = update_with_rollback(
        deepcopy(jobs), patcher, reader, *update_args
    )
    verify_after(after_second, before_second, expected_second)
    if first_notes != {job["job_id"]: note_of(job) for job in jobs}:
        raise AssertionError("规则替换不是幂等操作")
    if "保留结尾" not in note_of(jobs[1]):
        raise AssertionError("规则更新未保留原说明内容")
    replacement_sample = (
        "前置内容\n\n"
        + notification_policy("测试军团", False)
        + "\n\n后置内容\n"
    )
    replaced = upsert_policy(replacement_sample, notification_policy("更新军团", False))
    if "前置内容" not in replaced or "后置内容" not in replaced or "更新军团" not in replaced:
        raise AssertionError("规则替换未保留规则段前后的原说明")
    if replaced.count(POLICY_MARKER) != 1 or "测试军团" in replaced:
        raise AssertionError("规则替换未完整替换既有规则段")
    star_policy = notification_policy(star_corporation_name, False)
    original_policy = notification_policy(original_corporation_name, True)
    if "Sov*、Entosis*" not in star_policy or "不因本轮规则修正重新补发" not in star_policy:
        raise AssertionError("仅燃料与受袭任务未明确排除联盟主权事件或历史补发")
    if "MoonminingExtractionStarted" in star_policy:
        raise AssertionError("仅燃料与受袭任务白名单被错误扩展")
    if "MoonminingExtractionStarted" not in original_policy:
        raise AssertionError("原任务既有月矿事件白名单丢失")

    rollback_jobs = deepcopy(original_jobs)
    rollback_patches = []

    def failing_patcher(job_id, payload):
        if set(payload) != {"note"}:
            raise AssertionError("回滚测试出现 note 之外的更新字段")
        rollback_patches.append((job_id, deepcopy(payload)))
        job = next(item for item in rollback_jobs if item["job_id"] == job_id)
        job["payload"]["note"] = payload["note"]
        job["description"] = payload["note"]
        if len(rollback_patches) == 2:
            raise RuntimeError("模拟第二个任务更新失败")

    def rollback_reader():
        return deepcopy(rollback_jobs)

    try:
        update_with_rollback(
            deepcopy(original_jobs), failing_patcher, rollback_reader, *update_args
        )
        raise AssertionError("模拟更新失败未触发回滚")
    except RuntimeError as exc:
        if "已确认恢复原任务说明" not in str(exc):
            raise
    if rollback_jobs != original_jobs:
        raise AssertionError("失败回滚未完整恢复两个目标任务说明")

    pre_fail_jobs = deepcopy(original_jobs)
    pre_fail_patches = []

    def pre_fail_patcher(job_id, payload):
        if set(payload) != {"note"}:
            raise AssertionError("预写失败测试出现 note 之外的更新字段")
        pre_fail_patches.append((job_id, deepcopy(payload)))
        if len(pre_fail_patches) == 2:
            raise RuntimeError("模拟第二个任务写入前失败")
        job = next(item for item in pre_fail_jobs if item["job_id"] == job_id)
        job["payload"]["note"] = payload["note"]
        job["description"] = payload["note"]

    def pre_fail_reader():
        return deepcopy(pre_fail_jobs)

    try:
        update_with_rollback(
            deepcopy(original_jobs), pre_fail_patcher, pre_fail_reader, *update_args
        )
        raise AssertionError("模拟写入前失败未触发回滚")
    except RuntimeError as exc:
        if "已确认恢复原任务说明" not in str(exc):
            raise
    if pre_fail_jobs != original_jobs:
        raise AssertionError("写入前失败未恢复已实际修改的目标任务")
    if len(pre_fail_patches) != 3 or pre_fail_patches[-1][0] != original_job_id:
        raise AssertionError("回滚触达了未实际修改的目标任务")
    print(
        json.dumps(
            {
                "check": "ok",
                "matchedJobs": 2,
                "patchFields": ["note"],
                "idempotent": True,
                "rollbackVerified": True,
                "jobIdSetVerified": True,
                "otherJobsUnchangedVerified": True,
            },
            ensure_ascii=False,
        )
    )


def main():
    """更新线上任务说明，并以脱敏摘要备份和核验结果。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="仅运行离线检查，不连接 AstrBot")
    parser.add_argument("--original-job-id", help="包含月矿事件的既有巡检任务 ID")
    parser.add_argument("--star-job-name", help="仅燃料与受袭的既有巡检任务名称")
    parser.add_argument("--original-corporation-name", help="包含月矿事件任务的军团显示名称")
    parser.add_argument("--star-corporation-name", help="仅燃料与受袭任务的军团显示名称")
    args = parser.parse_args()
    if args.check:
        check()
        return
    required_values = {
        "--original-job-id": args.original_job_id,
        "--star-job-name": args.star_job_name,
        "--original-corporation-name": args.original_corporation_name,
        "--star-corporation-name": args.star_corporation_name,
    }
    missing = [option for option, value in required_values.items() if not value]
    if missing:
        parser.error("生产更新缺少必填参数：" + "、".join(missing))

    global TOKEN
    os.umask(0o077)
    session_path = Path("/home/astrbot/private/astrbot-login-session.json")
    session = json.loads(session_path.read_text())
    TOKEN = session["data"]["token"]
    jobs = request("GET")
    target_specs = find_targets(
        jobs,
        args.original_job_id,
        args.star_job_name,
        args.original_corporation_name,
        args.star_corporation_name,
    )
    targets = [job for job, _, _ in target_specs]

    backup_dir = Path("/home/astrbot/private/backups") / (
        datetime.now().strftime("%Y%m%d-%H%M%S") + "-notification-rule-update"
    )
    backup_dir.mkdir(parents=True, mode=0o700)
    (backup_dir / "target-jobs-full.json").write_text(
        json.dumps(targets, ensure_ascii=False, indent=2) + "\n"
    )
    before_summary = [sanitized_job(job) for job in targets]
    (backup_dir / "jobs-before-sanitized.json").write_text(
        json.dumps(before_summary, ensure_ascii=False, indent=2) + "\n"
    )
    state_hashes_before = patrol_state_hashes()

    def live_patcher(job_id, payload):
        request("PATCH", "/" + job_id, payload)

    def live_reader():
        return request("GET")

    after_jobs, before, expected_notes = update_with_rollback(
        jobs,
        live_patcher,
        live_reader,
        args.original_job_id,
        args.star_job_name,
        args.original_corporation_name,
        args.star_corporation_name,
    )
    state_hashes_after = patrol_state_hashes()
    after_by_id = {job.get("job_id"): job for job in after_jobs}
    after_summary = [sanitized_job(after_by_id[job["job_id"]]) for job in targets]
    (backup_dir / "jobs-after-sanitized.json").write_text(
        json.dumps(after_summary, ensure_ascii=False, indent=2) + "\n"
    )
    print(
        json.dumps(
            {
                "updatedJobs": [
                    {"jobId": job["job_id"], "name": job["name"], "changedFields": ["note"]}
                    for job in targets
                ],
                "backup": str(backup_dir),
                "sentMessages": False,
                "manualRuns": False,
                "dedupStateTouched": False,
                "dedupStateFilesObserved": len(set(state_hashes_before) | set(state_hashes_after)),
                "dedupStateObservedChanged": state_hashes_before != state_hashes_after,
            },
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
