# 工业制造与市场机会分析：接口与数据调研

> 调研日期：2026-09-27。国服接口以网易 Serenity 为准。

## 数据源结论

国服 ESI 适合实时和实例数据，但不能构建完整蓝图配方库；当前 `docs/evedata.xlsx` 只包含物品/地理/市场分类等展示资料，也不含工业活动配方。完整计算需要单独导入带 provenance 的国服静态工业数据。

调研时国服 `/status/` 返回 `server_version=3535651`，同期国际服官方 SDE build 为 `3542233`；同一 Jita 星系的制造 SCI 实测也不同，因此不能用国际服数据静默替换国服事实。

## 国服 ESI 接口

| 接口 | 关键字段 | 用途 | 新鲜度/授权 |
| --- | --- | --- | --- |
| `GET https://ali-esi.evepc.163.com/latest/status/` | `server_version` | 绑定国服版本，静态数据版本变更检测 | 公开 |
| `/industry/systems/` | `solar_system_id`、`manufacturing`、`copying`、`invention`、`reaction`、研究 SCI | 星系成本指数 | 公开，最多缓存约 3600 秒 |
| `/industry/facilities/` | facility、type、owner、system、region | NPC 行业地点候选 | 公开；不保证活动能力和税率 |
| `/universe/systems/{system_id}/` | `security_status`、名称、星门 | 生产地点安等与名称 | 公开 |
| `/universe/types/{type_id}/`、Dogma | hull、服务模块、Rig 属性 | 建筑/服务/Rig 加成与国服类型校验 | 公开 |
| `/markets/{region_id}/orders/` | price、volume_remain、location_id、type_id、is_buy_order | 吉他逐档采购/销售订单 | 公开；按订单缓存 |
| `/markets/{region_id}/history/` | average、highest、lowest、order_count、volume | 伏尔戈成交量、趋势和波动 | 公开；星域级，不是吉他空间站级 |
| `/markets/prices/` | adjusted_price | EIV/安装费基准 | 公开 |
| `/characters/{id}/blueprints/`、`/corporations/{id}/blueprints/` | type、ME、TE、runs、BPO/BPC | 可选的真实蓝图实例核验 | 需授权；不含配方 |
| `/corporations/{id}/structures/` | structure、type、system、services、state | 本军团建筑实例事实 | 需 `Station_Manager` 等授权；不返回完整 Rig/税率 |

## 国服静态工业数据导入

正式主数据至少需要：蓝图类型、activity、materials、products、base time、发明概率、BPO/BPC 关系、技能/设施要求及规则版本。每次导入保存：`source`、`server_version/build`、`downloaded_at`、`sha256`、`schema_version`、`validation_status`。

首选与国服客户端/服务器版本对应的静态导出；截至调研日未核实到网易公开完整 Serenity SDE/JSONL 入口。CCP TQ SDE 可作候选回退，但必须标记 `source=tranquility_sde`，不能进入正式可执行榜单，除非通过国服 Golden Case 与版本校验。

## 计算事实与风险

- 材料按整份 job 计算并向上取整。社区成熟实现通常采用 `max(runs, ceil(round(base × runs × (1-ME/100) × facility × rig, 2)))`；最终小数精度必须用国服 Golden Case 固化，不能把该表达式直接当成网易官方契约。
- 活动时间按 `baseTime × runs × blueprintTE × skillMultipliers × facility × rig` 连乘；最终秒级取整同样需要国服 Golden Case。
- EIV 使用 ME0、无设施材料减免的基础材料数量 × 国服 adjusted price；不能用实际折后采购量计算 EIV。
- CCP Viridian 给出的 TQ 基线是 `TIF = EIV × ((SCI × bonuses) + FacilityTax + SCC + AlphaClone)`；本项目将 `versionedSurcharge` 作为可配置的国服版本化附加费，不把 TQ 的 SCC=4% 直接硬编码到国服。设施税和玩家建筑服务需人工配置或授权数据补充。
- Raitaru/Azbel/Sotiyo 等 hull 基础加成可由国服 Dogma 读取；Rig 加成需要结合实际 Rig 类型和系统安等修正，不能写死一个通用百分比。
- T2 发明按前置活动展示；若未来启用成本，需要显式建模失败概率、数据核心、解密器和发明结果 ME/TE。

## 数据同步策略

市场订单短 TTL 刷新；SCI 和国服状态按接口缓存刷新；静态配方按版本导入和差异校验。国服 `server_version` 变化时，相关静态规则标记为待验证。所有计算结果记录市场、SCI、静态配方和配置快照。

## 证据链接

- [网易国服 ESI Swagger](https://ali-esi.evepc.163.com/latest/swagger.json)
- [网易国服状态](https://ali-esi.evepc.163.com/latest/status/)
- [国服系统行业成本指数](https://ali-esi.evepc.163.com/latest/industry/systems/?datasource=serenity)
- [国服行业设施](https://ali-esi.evepc.163.com/latest/industry/facilities/?datasource=serenity)
- [CCP 官方 TQ SDE 文档](https://developers.eveonline.com/static-data/)
- [CCP 官方安装费说明（Viridian）](https://www.eveonline.com/news/view/viridian-expansion-notes)
