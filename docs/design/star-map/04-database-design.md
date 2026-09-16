# 星图与导航：数据库设计

## 数据归属

| 表 | 归属 | 作用 |
| --- | --- | --- |
| `eve_universe_system` | 全租户共享 | 官方星系坐标、安全等级、星门数量及同步元数据；名称与层级关联既有 `eve_static_location_reference` |
| `eve_universe_stargate` | 全租户共享 | 官方星门及其来源/目的星系关系，用于构建拓扑边 |
| `eve_universe_sync_state` | 全租户共享 | 可续跑的同步游标、覆盖率、最近成功和失败状态 |
| `eve_starmap_annotation` | 军团租户 | 人工运营标注 |
| `eve_starmap_route` | 军团租户 | 保存的军团路线元数据和当前验证结果 |
| `eve_starmap_route_point` | 军团租户 | 路线起点、经由点、终点及官方计算后的顺序节点 |

## 关键字段

### `eve_universe_system`

`system_id`（唯一）、`constellation_id`、`region_id`、`security_status`、`position_x`、`position_y`、`position_z`、`stargate_count`、`source_etag`、`source_expires_at`、`source_updated_at`、`synchronized_at`。三维坐标使用 `decimal(30,3)`，避免双精度转换导致长期渲染位置漂移；按 `region_id`、`constellation_id` 建索引。

### `eve_universe_stargate`

`stargate_id`（唯一）、`system_id`、`destination_stargate_id`、`destination_system_id`、`type_id`、`position_x/y/z`、`source_etag`、`source_expires_at`、`synchronized_at`。按 `system_id`、`destination_system_id` 建索引；服务层以较小星系 ID 归一化边，避免双向星门在图上出现两条重复连线。

### `eve_universe_sync_state`

`sync_key`（主键）、`status`、`cursor_value`、`expected_count`、`completed_count`、`last_success_at`、`last_failure_at`、`failure_code`、`next_retry_at`、`updated_at`。不把完整错误响应或访问令牌写入该表。

### `eve_starmap_annotation`

`id`、`tenant_id`、`solar_system_id`、`category`、`title`、`note`、`color_key`、`expires_at`、`archived`、`create_user`、`create_time`、`update_user`、`update_time`、`deleted`。唯一索引为 `(tenant_id, solar_system_id, title, deleted)`；按 `(tenant_id, archived, expires_at)` 索引，供地图快速读取有效标注。

### `eve_starmap_route` 与 `eve_starmap_route_point`

路线主表保存 `tenant_id`、`title`、`description`、`origin_system_id`、`destination_system_id`、`jump_count`、`validated_at`、`archived` 与审计字段。明细表保存 `route_id`、`point_order`、`solar_system_id`、`point_kind`（起点/经由/终点/计算节点）。所有读取先按路线主表 `tenant_id` 过滤，不能仅依赖明细 `route_id`。

## 来源映射与更新规则

| 来源系统/菜单 | 来源字段 | 目标表/字段 | 转换与同步规则 |
| --- | --- | --- | --- |
| 国服 `/universe/systems/{system_id}/` | `system_id`、`constellation_id`、`position`、`security_status`、`stargates` | `eve_universe_system` | 逐星系续跑，坐标拆为 X/Y/Z，星门列表进入待解析队列；`region_id` 由既有星座资料关联写入 |
| 国服 `/universe/stargates/{stargate_id}/` | `stargate_id`、`system_id`、`destination`、`position`、`type_id` | `eve_universe_stargate` | 以星门 ID 覆盖更新，目的星系用于生成图边 |
| `evedata.xlsx` 位置资料 | 星域、星座、星系中文名及层级 | `eve_static_location_reference`（既有） | 星图查询时按 ID 联结，中文名不重复维护 |
| 资产/建筑/月矿/成员追踪快照 | 已解析的星系 ID | 星图查询响应 | 运行时聚合，不复制入星图表，沿用原模块数据时间和权限 |
| 本站地图操作 | 标注、路线 | 三张 `eve_starmap_*` 租户表 | 每次写入记录创建/更新人 |
