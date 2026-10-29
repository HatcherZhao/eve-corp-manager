# 军团通知范围

本规则用于军团建筑巡检和自动推送。查询中的 `corporationId` 是授权数据选择器；`notifications` 和 `mail` 来自角色邮箱，其中可能包含联盟主权事件及其他军团信息。可读取不等于可作为目标军团的建筑事件推送。

## 先限定事件类型

以当前定时任务说明明确列出的事件为封闭白名单；“优先识别”不得解释为可以扩展到其他事件。现有两类任务口径为：

| 巡检任务口径 | notificationType 白名单 |
| --- | --- |
| 仅燃料与受袭的巡检任务 | StructureFuelAlert、StructureUnderAttack |
| 另含月矿事件的巡检任务 | StructureFuelAlert、StructureUnderAttack、MoonminingExtractionStarted、MoonminingAutomaticFracture、MoonminingLaserFired |

具体任务采用哪一组，以该任务现有 `note` 已明确的白名单为准；本规则只收紧解释和归属校验，不能把一种任务口径扩展成另一种。

查询 `notifications` 时就添加 `notificationType` 的 `in` 筛选，继续保留原时间窗口、分页与军团参数；返回后仍逐条核对白名单。示例仅展示类型筛选片段：

```json
{"field":"notificationType","operator":"in","value":["StructureFuelAlert","StructureUnderAttack"]}
```

`Sov*`、`Entosis*`（包括 `SovStructureReinforced`、`EntosisCaptureStarted`）及联盟主权、领土捕获事件均不属于这两个军团建筑巡检任务。即使角色收到、同星系存在本军团建筑，或历史记录曾推送过，也不发送，不改名为“受袭相关通知”，也不因本轮解读规则修正重新补发。未知类型不猜测成受袭。邮件不能用来绕过事件范围。

用户主动查询联盟主权通知时，可以读取并解释该事件，但必须单独标注为联盟级主权事件，不得改变上述自动推送白名单。通知列表的 `title`、`eventScope`、`eventScopeLabel` 是服务端计算字段，只读取，不放进 `fields`、`filters`、`sort` 或 `groupBy`。读取通知正文时优先使用 `eve_read_document` 返回的 `interpretation`；`interpretationComplete` 是正文读取结果的顶层字段，为 `false` 时只按有限正文说明。`EntosisCaptureStarted` 表述为“主权中心开始遭受侵噬链接”，`SovStructureReinforced` 表述为“主权中心进入增强模式”。不得虚构联盟名称、所有者或军团归属。

## 再核实资产归属

1. 完整分页读取本次目标军团的 `structures`，建立 structureId 到建筑名、型号、星系、源更新时间的映射。ID 始终按字符串处理。
2. 白名单内的通知先读取完整相关正文，检查 `hasMore`、`contentAvailable`；解析原文实际提供的字段。原文可能写 structureID，标准列表字段为 structureId，不能把 structureTypeID（型号）、solarSystemID（星系）当作建筑实例 ID。存在统一解读时优先使用 `interpretation` 辅助理解事件，但资产归属仍须由实际 ID 或所有者证据确认。
3. 本军团资产需有可追溯证据：建筑实例 ID 与目标军团 structures 匹配；或通知明确提供事件涉及建筑的所有者军团且等于目标军团；或可靠历史归属记录能证明事件发生时属于该军团。所有者字段必须来自实际数据并核对语义，攻击者、收件人、联盟 ID 和查询参数均不能代替所有者。
4. 有明确其他所有者或归属冲突时不发送，记录待核实。只含星系、型号、模糊名称的通知不满足归属证据。不得靠名称相似、同星系、角色本身属于军团来补全归属。
5. 被毁或转移后的建筑可能已不在当前清单。缺席不直接证明他团所有；有可靠事件时点证据可按原事件范围处理，无证据则保留待核实，不能猜测，也不能发送模糊受袭告警。
6. mail 仅补充任务允许且已核实属于本军团的事件。联盟广播、联盟收件范围本身不证明本军团建筑遇袭；即使直接投递本军团，也需核对具体事件和资产，不能转发一般联盟战报。

## 输出与状态

- 本军团 structures 快照的燃料检查仍按原阈值执行；原 typeId=47513 的燃料排除规则、月矿事件含义、北京时间和去重策略不变。
- 不符合类型或归属条件的记录不推群，不作为检查异常刷屏。需要留痕时写入独立的 ignored_events／pending_ownership，带军团、来源 ID、类型与原因；不得记为发送成功。未确认归属的候选保留原 ID，后续用已缓存数据补证；不要因扫描游标推进而视为已经处理完毕。
- 既往确实发送过的去重记录保留，避免重新补发；旧任务笔记里“曾作为相关通知推送”只是历史事实，不是未来推送授权。
- 仅完成全部必要查询和分页后推进检查位置；查询失败不能伪装成无事件。发送成功后才登记已发送。
- 没有符合条件的新事件或燃料提醒时静默。用户主动问为什么排除某条时，可以解释来源不足；不要在例行巡检里追加被排除事件的摘要。
