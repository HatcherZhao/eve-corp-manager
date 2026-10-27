#!/usr/bin/env python3
"""复用本人现有会话，只读核验已发布通知标题、详情和分类，不输出令牌或业务正文。"""

import json
from pathlib import Path
import runpy
import subprocess
import sys
from urllib.parse import urlencode
from urllib.request import Request, urlopen


def main():
    """使用现有会话校验组织通知，报告类型覆盖和断言结果。"""
    user_id, tenant_id = sys.argv[1:3]
    helper = Path(sys.argv[3]) if len(sys.argv) > 3 else Path(__file__).resolve().parents[1] / "g021/02-live-permission-smoke.py"
    module = runpy.run_path(str(helper))
    api = json.loads(subprocess.check_output(["docker", "inspect", "eve-corp-manager-api"]))[0]
    env = dict(item.split("=", 1) for item in api["Config"]["Env"] if "=" in item)
    token = module["find_token"](module["RedisReader"](env), user_id)
    web = json.loads(subprocess.check_output(["docker", "inspect", "eve-corp-manager-web"]))[0]
    binding = web["HostConfig"]["PortBindings"]["80/tcp"][0]
    host = binding["HostIp"]
    if host in {"", "0.0.0.0", "::"}:
        host = "127.0.0.1"
    base = f"http://{host}:{binding['HostPort']}/api/eve/notifications?"
    headers = {"Authorization": "Bearer " + token, "X-Tenant-Id": tenant_id}

    def read(**query):
        """以只读分页查询检查缓存，不触发上游同步。"""
        with urlopen(Request(base + urlencode({"page": 1, "size": 100, **query}), headers=headers), timeout=45) as response:
            data = json.load(response)
        if data.get("success") is not True:
            raise RuntimeError("通知分页接口未成功")
        return data["data"]

    checks = {
        "unknown notification type (6040)": ("STRUCTURE_ASSET_SAFETY", "岩浆气不足一天", "不足一天"),
        "unknown notification type (6041)": ("STRUCTURE_ASSET_SAFETY", "岩浆气耗尽，生产停止", "生产已停止"),
        "StructureFuelAlert": ("STRUCTURE_ASSET_SAFETY", "建筑燃料不足一天", "不足以支撑一天"),
        "SovStructureReinforced": ("WAR_SOVEREIGNTY", "主权建筑进入增强模式", "增强模式"),
        "EntosisCaptureStarted": ("WAR_SOVEREIGNTY", "主权建筑正被占领", "侵噬链接"),
        "CorpNewCEOMsg": ("CORPORATION_MEMBER", "军团 CEO 更换", "CEO"),
        "CorpFriendlyFireEnableTimerStarted": ("CORPORATION_MEMBER", "军团友军误伤将变为合法行为", "将变为合法"),
        "CharAppAcceptMsg": ("CORPORATION_MEMBER", "成员加入军团", "加入军团"),
        "CorpAppAcceptMsg": ("CORPORATION_MEMBER", "收到军团邀请", "收到加入军团的邀请"),
    }
    covered = []
    for notification_type, (category, title, summary) in checks.items():
        data = read(category=category, keyword=notification_type)
        rows = [row for row in data.get("list", []) if row.get("type", "").strip() == notification_type]
        if not rows:
            raise RuntimeError("缺少预期历史通知类型：" + notification_type)
        for row in rows:
            if row.get("category") != category or row.get("title") != title or summary not in row.get("summary", ""):
                raise RuntimeError("通知标题、分类或摘要不符合校准结果：" + notification_type)
        if notification_type == "StructureFuelAlert":
            detail = {item["label"]: item["value"] for item in rows[0].get("details", [])}
            if "剩余燃料" not in detail or "燃料需求" in detail:
                raise RuntimeError("燃料数量详情未修正")
        if notification_type == "SovStructureReinforced":
            detail = {item["label"]: item["value"] for item in rows[0].get("details", [])}
            if detail.get("指挥节点解除隐形时间（北京时间）") != "2026年10月7日 18:10":
                raise RuntimeError("主权节点时间与游戏校准样本不一致")
        covered.append({"type": notification_type, "rows": len(rows), "passed": True})
    # 分类修正后同一类型不能继续留在其他分页结果里。
    if read(category="OTHER", keyword="unknown notification type (6040)").get("total", 0) != 0:
        raise RuntimeError("历史岩浆气通知仍错误归入其他")
    print(json.dumps({"stage": "passed", "checks": covered, "historicalCategoryFilter": True}, ensure_ascii=False))


if __name__ == "__main__":
    main()
