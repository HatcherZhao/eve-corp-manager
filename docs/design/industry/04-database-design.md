# 工业制造与市场机会分析：数据库设计

## 1. 数据归属

| 表 | 归属 | 作用 |
| --- | --- | --- |
| `eve_industry_static_version` | 全局 | 国服/TQ 静态工业数据来源、版本、哈希和校验状态 |
| `eve_industry_blueprint` | 全局 | 蓝图与 activity 主数据 |
| `eve_industry_blueprint_material` | 全局 | activity 材料明细 |
| `eve_industry_blueprint_product` | 全局 | activity 产物明细 |
| `eve_industry_golden_case` | 全局 | 国服实际工业任务校验样例 |
| `eve_industry_config` | 军团租户 | 可保存的生产技能、市场费用、目标周期与默认参数 |
| `eve_industry_facility_profile` | 军团租户 | 实际/假设生产地点、设施、服务、Rig、税率和来源 |
| `eve_industry_blueprint_override` | 军团租户 | 某张蓝图的 ME/TE、BPO/BPC、runs 覆盖 |
| `eve_market_snapshot`（既有） | 全局/市场 | 复用现有吉他报价、订单 JSON、市场历史与同步状态作为动态事实来源 |
| `eve_industry_market_input_snapshot` | 军团租户/计算快照 | 固化某次计算实际使用的材料/产品价格、统计指标和来源时间 |
| `eve_industry_market_order_slice` | 军团租户/计算快照 | 固化逐档采购/变现实际使用的订单价位、数量和方向 |
| `eve_industry_system_snapshot` | 租户/计算快照 | 国服星系安等、SCI、设施参数和更新时间 |
| `eve_industry_opportunity_snapshot` | 军团租户 | 某次扫描的机会结果、状态、利润和风险 |
| `eve_industry_opportunity_material` | 军团租户 | 机会对应的物料需求与逐档采购明细；不展开自制供应链 |
| `eve_industry_plan` | 军团租户 | 固定计算快照的可执行生产计划 |
| `eve_industry_plan_step` | 军团租户 | 计划中的前置活动、材料采购和生产步骤 |

## 2. 关键字段

### 静态主数据

`eve_industry_static_version`：`id`、`source`、`server_version`、`build_number`、`downloaded_at`、`sha256`、`schema_version`、`validation_status`、`validated_at`。

`eve_industry_blueprint`：`id`、`static_version_id`、`blueprint_type_id`、`activity`、`product_type_id`、`base_time_seconds`、`probability`、`supported`、`validation_status`。材料/产物子表使用 `quantity`、`unit`、`is_optional`、`sequence_no`。

### 配置

`eve_industry_config`：`tenant_id`、`name`、`production_skill_json`、`market_fee_json`、`target_days`、`market_share_limit`、`freshness_policy_json`、`version`、`enabled`。

`eve_industry_facility_profile`：`tenant_id`、`name`、`reality_type`、`system_id`、`security_status`、`facility_type_id`、`supported_activities_json`、`online_services_json`、`rig_type_ids_json`、`facility_tax`、`parameter_source`、`confirmed_at`、`enabled`。

`eve_industry_blueprint_override`：`tenant_id`、`blueprint_type_id`、`blueprint_kind`、`material_efficiency`、`time_efficiency`、`remaining_runs`、`source`、`version`。

### 快照与结果

所有机会和计划必须保存 `config_version`、`static_version_id`、`market_snapshot_id`、`system_snapshot_id`、`calculated_at`、`status`、`risk_score`、`risk_tags_json`、`assumptions_json`。金额使用 `decimal(20,2)`，数量使用无符号整数，耗时使用秒级整数/decimal，避免浮点直接落库。

`eve_industry_opportunity_snapshot` 至少保存：产品/蓝图/配置键、推荐 runs/数量、材料成本、安装费、立即收入、挂单收入、利润、利润率、每小时/每日利润、预计售罄天数、资金占用、综合评分、评分版本和数据新鲜度。

## 3. 来源映射

| 来源系统/菜单 | 来源字段 | 目标表/字段 | 转换/同步规则 |
| --- | --- | --- | --- |
| 国服 `/status/` | `server_version` | `eve_industry_static_version.server_version` | 版本变化时标记相关静态数据待验证 |
| 国服 `/industry/systems/` | system、activity、cost_index | `eve_industry_system_snapshot` | 按 system + activity 保存带时间快照，缺失不写零 |
| 国服 `/universe/systems/{id}/` | security_status | `eve_industry_system_snapshot.security_status` | 与 SCI 快照关联，作为 Rig 安等修正输入 |
| 既有 `eve_market_snapshot` / 国服订单 | location、type、price、volume_remain、buy/sell | `eve_industry_market_input_snapshot`、`eve_industry_market_order_slice` | 只固化本次计算使用的吉他 4-4 订单档位，不重复建设市场同步主链路 |
| 既有 `eve_market_snapshot` / 国服历史 | volume、average、high、low、order_count | `eve_industry_market_input_snapshot` | 固化伏尔戈统计指标，明确星域口径 |
| 国服 `/markets/prices/` | adjusted_price | `eve_industry_market_input_snapshot.adjusted_price` | 用于 EIV，不用于玩家销售收入 |
| 国服蓝图接口 | type、ME、TE、runs、quantity | `eve_industry_blueprint_override` | 只作为实例覆盖；接口不提供配方 |
| 国服静态工业导入 | materials、products、time、probability | `eve_industry_blueprint*` | 必须带版本、哈希、来源与校验状态 |
| 本站设施配置 | 建筑、安等、服务、Rig、税率 | `eve_industry_facility_profile` | 人工配置可覆盖 ESI 事实，保存来源与确认时间 |

## 4. 索引、隔离与保留

租户表所有查询先按 `tenant_id` 过滤；静态表和公开市场快照不带租户。建议索引：机会 `(tenant_id, scan_id, status, ranking_score)`、材料 `(opportunity_id, type_id)`、计划 `(tenant_id, status, calculated_at)`、系统 `(system_id, activity, fetched_at)`、订单 `(region_id, location_id, type_id, is_buy_order, price)`。

机会快照和计划长期保留；临时扫描结果按保留策略清理，但被计划引用的快照不得删除。市场/SCI 原始快照按月归档或压缩；静态版本和 Golden Case 永久保留。
