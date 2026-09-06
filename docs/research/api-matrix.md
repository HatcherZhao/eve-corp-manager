# 国服接口权限矩阵

从 2026-09-06 取得的国服 Swagger 自动提取。路径前缀为 /latest；正式开发按每条描述选择固定 /vN。所有条目是“国服契约已确认、真实账号未验证”。

x-required-roles 为空不能解释为无权限要求；例如成员角色列表的要求写在 description 中。缓存时长是契约值，实际响应头优先。POST 的资产名称/位置查询是批量读取，不是修改游戏数据。

| 方法与路径 | Scope | EVE 角色/条件 | 分页 | 缓存秒 |
|---|---|---|---|---|
| `GET /characters/{character_id}/mining/` | esi-industry.read_character_mining.v1 | 扩展字段未声明，需读描述 | page | 600 |
| `GET /characters/{character_id}/roles/` | esi-characters.read_corporation_roles.v1 | 扩展字段未声明，需读描述 | 无分页参数 | 3600 |
| `GET /corporation/{corporation_id}/mining/extractions/` | esi-industry.read_corporation_mining.v1 | Station_Manager | page | 1800 |
| `GET /corporation/{corporation_id}/mining/observers/` | esi-industry.read_corporation_mining.v1 | Accountant | page | 3600 |
| `GET /corporation/{corporation_id}/mining/observers/{observer_id}/` | esi-industry.read_corporation_mining.v1 | Accountant | page | 3600 |
| `GET /corporations/{corporation_id}/` | 公开/未声明 | 公开 | 无分页参数 | 3600 |
| `GET /corporations/{corporation_id}/alliancehistory/` | 公开/未声明 | 扩展字段未声明，需读描述 | 无分页参数 | 3600 |
| `GET /corporations/{corporation_id}/assets/` | esi-assets.read_corporation_assets.v1 | Director | page | 3600 |
| `POST /corporations/{corporation_id}/assets/locations/` | esi-assets.read_corporation_assets.v1 | Director | 无分页参数 | 未声明 |
| `POST /corporations/{corporation_id}/assets/names/` | esi-assets.read_corporation_assets.v1 | Director | 无分页参数 | 未声明 |
| `GET /corporations/{corporation_id}/blueprints/` | esi-corporations.read_blueprints.v1 | Director | page | 3600 |
| `GET /corporations/{corporation_id}/bookmarks/` | esi-bookmarks.read_corporation_bookmarks.v1 | 扩展字段未声明，需读描述 | page | 3600 |
| `GET /corporations/{corporation_id}/bookmarks/folders/` | esi-bookmarks.read_corporation_bookmarks.v1 | 扩展字段未声明，需读描述 | page | 3600 |
| `GET /corporations/{corporation_id}/contacts/` | esi-corporations.read_contacts.v1 | 扩展字段未声明，需读描述 | page | 300 |
| `GET /corporations/{corporation_id}/contacts/labels/` | esi-corporations.read_contacts.v1 | 扩展字段未声明，需读描述 | 无分页参数 | 300 |
| `GET /corporations/{corporation_id}/containers/logs/` | esi-corporations.read_container_logs.v1 | Director | page | 600 |
| `GET /corporations/{corporation_id}/contracts/` | esi-contracts.read_corporation_contracts.v1 | 扩展字段未声明，需读描述 | page | 300 |
| `GET /corporations/{corporation_id}/contracts/{contract_id}/bids/` | esi-contracts.read_corporation_contracts.v1 | 扩展字段未声明，需读描述 | page | 3600 |
| `GET /corporations/{corporation_id}/contracts/{contract_id}/items/` | esi-contracts.read_corporation_contracts.v1 | 扩展字段未声明，需读描述 | 无分页参数 | 3600 |
| `GET /corporations/{corporation_id}/customs_offices/` | esi-planets.read_customs_offices.v1 | Director | page | 3600 |
| `GET /corporations/{corporation_id}/divisions/` | esi-corporations.read_divisions.v1 | Director | 无分页参数 | 3600 |
| `GET /corporations/{corporation_id}/facilities/` | esi-corporations.read_facilities.v1 | Factory_Manager | 无分页参数 | 3600 |
| `GET /corporations/{corporation_id}/fw/stats/` | esi-corporations.read_fw_stats.v1 | 扩展字段未声明，需读描述 | 无分页参数 | 未声明 |
| `GET /corporations/{corporation_id}/icons/` | 公开/未声明 | 扩展字段未声明，需读描述 | 无分页参数 | 3600 |
| `GET /corporations/{corporation_id}/industry/jobs/` | esi-industry.read_corporation_jobs.v1 | Factory_Manager | page | 300 |
| `GET /corporations/{corporation_id}/killmails/recent/` | esi-killmails.read_corporation_killmails.v1 | Director | page | 300 |
| `GET /corporations/{corporation_id}/medals/` | esi-corporations.read_medals.v1 | 扩展字段未声明，需读描述 | page | 3600 |
| `GET /corporations/{corporation_id}/medals/issued/` | esi-corporations.read_medals.v1 | Director | page | 3600 |
| `GET /corporations/{corporation_id}/members/` | esi-corporations.read_corporation_membership.v1 | 必须是该军团成员 | 无分页参数 | 3600 |
| `GET /corporations/{corporation_id}/members/limit/` | esi-corporations.track_members.v1 | Director | 无分页参数 | 3600 |
| `GET /corporations/{corporation_id}/members/titles/` | esi-corporations.read_titles.v1 | Director | 无分页参数 | 3600 |
| `GET /corporations/{corporation_id}/membertracking/` | esi-corporations.track_members.v1 | Director | 无分页参数 | 3600 |
| `GET /corporations/{corporation_id}/orders/` | esi-markets.read_corporation_orders.v1 | Accountant, Trader | page | 1200 |
| `GET /corporations/{corporation_id}/orders/history/` | esi-markets.read_corporation_orders.v1 | Accountant, Trader | page | 3600 |
| `GET /corporations/{corporation_id}/roles/` | esi-corporations.read_corporation_membership.v1 | Personnel Manager 或任一可授予角色（见描述） | 无分页参数 | 3600 |
| `GET /corporations/{corporation_id}/roles/history/` | esi-corporations.read_corporation_membership.v1 | Director | page | 3600 |
| `GET /corporations/{corporation_id}/shareholders/` | esi-wallet.read_corporation_wallets.v1 | Director | page | 3600 |
| `GET /corporations/{corporation_id}/standings/` | esi-corporations.read_standings.v1 | 扩展字段未声明，需读描述 | page | 3600 |
| `GET /corporations/{corporation_id}/starbases/` | esi-corporations.read_starbases.v1 | Director | page | 3600 |
| `GET /corporations/{corporation_id}/starbases/{starbase_id}/` | esi-corporations.read_starbases.v1 | Director | 无分页参数 | 3600 |
| `GET /corporations/{corporation_id}/structures/` | esi-corporations.read_structures.v1 | Station_Manager | page | 3600 |
| `GET /corporations/{corporation_id}/titles/` | esi-corporations.read_titles.v1 | Director | 无分页参数 | 3600 |
| `GET /corporations/{corporation_id}/wallets/` | esi-wallet.read_corporation_wallets.v1 | Accountant, Junior_Accountant | 无分页参数 | 300 |
| `GET /corporations/{corporation_id}/wallets/{division}/journal/` | esi-wallet.read_corporation_wallets.v1 | Accountant, Junior_Accountant | page | 3600 |
| `GET /corporations/{corporation_id}/wallets/{division}/transactions/` | esi-wallet.read_corporation_wallets.v1 | Accountant, Junior_Accountant | from_id | 3600 |
| `GET /universe/moons/{moon_id}/` | 公开/未声明 | 扩展字段未声明，需读描述 | 无分页参数 | 未声明 |
| `GET /universe/structures/` | 公开/未声明 | 扩展字段未声明，需读描述 | 无分页参数 | 3600 |
| `GET /universe/structures/{structure_id}/` | esi-universe.read_structures.v1 | 扩展字段未声明，需读描述 | 无分页参数 | 3600 |
| `GET /universe/systems/` | 公开/未声明 | 扩展字段未声明，需读描述 | 无分页参数 | 未声明 |
| `GET /universe/systems/{system_id}/` | 公开/未声明 | 扩展字段未声明，需读描述 | 无分页参数 | 未声明 |
| `GET /universe/types/` | 公开/未声明 | 扩展字段未声明，需读描述 | page | 未声明 |
| `GET /universe/types/{type_id}/` | 公开/未声明 | 扩展字段未声明，需读描述 | 无分页参数 | 未声明 |

## 核心响应字段

- `GET /characters/{character_id}/mining/`：date、quantity、solar_system_id、type_id
- `GET /corporation/{corporation_id}/mining/extractions/`：chunk_arrival_time、extraction_start_time、moon_id、natural_decay_time、structure_id
- `GET /corporation/{corporation_id}/mining/observers/`：last_updated、observer_id、observer_type
- `GET /corporation/{corporation_id}/mining/observers/{observer_id}/`：character_id、last_updated、quantity、recorded_corporation_id、type_id
- `GET /corporations/{corporation_id}/assets/`：is_blueprint_copy、is_singleton、item_id、location_flag、location_id、location_type、quantity、type_id
- `POST /corporations/{corporation_id}/assets/locations/`：item_id、position
- `POST /corporations/{corporation_id}/assets/names/`：item_id、name
- `GET /corporations/{corporation_id}/membertracking/`：base_id、character_id、location_id、logoff_date、logon_date、ship_type_id、start_date
- `GET /corporations/{corporation_id}/structures/`：corporation_id、fuel_expires、name、next_reinforce_apply、next_reinforce_hour、profile_id、reinforce_hour、services、state、state_timer_end、state_timer_start、structure_id、system_id、type_id、unanchors_at
- `GET /universe/structures/`：见快照 schema（标量/引用）
- `GET /universe/structures/{structure_id}/`：name、owner_id、position、solar_system_id、type_id

完整定义、required/可空字段、批次上限、枚举和错误返回见 [国服快照](../../references/serenity-swagger-2026-09-06.json)，机器可读索引见 [endpoint-index.json](../../references/endpoint-index.json)。
