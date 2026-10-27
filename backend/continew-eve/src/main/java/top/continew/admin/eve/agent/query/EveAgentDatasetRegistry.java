/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package top.continew.admin.eve.agent.query;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部 Agent 可查询数据集注册表，只允许预先声明的表、字段和军团隔离条件进入 SQL。
 *
 * @author zhaoyuqing
 */
final class EveAgentDatasetRegistry {

    private static final Map<String, Dataset> DATASETS = buildDatasets();

    private EveAgentDatasetRegistry() {
    }

    /**
     * 返回全部数据集定义。
     *
     * @return 不可变数据集映射
     */
    static Map<String, Dataset> all() {
        return DATASETS;
    }

    /**
     * 按键读取数据集，不接受任意表名。
     *
     * @param key 数据集键
     * @return 数据集定义
     */
    static Dataset require(String key) {
        Dataset dataset = DATASETS.get(key);
        if (dataset == null) {
            throw new IllegalArgumentException("不支持的数据集: " + key);
        }
        return dataset;
    }

    /** 注册业务数据白名单，身份条件不接受客户端覆盖。 */
    private static Map<String, Dataset> buildDatasets() {
        Map<String, Dataset> datasets = new LinkedHashMap<>();
        add(datasets, corporation());
        add(datasets, dataset("characters", "军团角色绑定摘要", Scope.COMMUNICATION, "eve_character c", "c.tenant_id=:tenantId AND c.corporation_id=:corporationId AND c.deleted=0", "name ASC", f("id", "c.id", Type.ID, true), f("userId", "c.user_id", Type.ID, true), f("characterId", "c.character_id", Type.ID, true), f("name", "c.name", Type.STRING, true), f("server", "c.server", Type.STRING, true), f("status", "c.status", Type.STRING, true), f("isPrimary", "c.is_primary", Type.BOOLEAN, true), fb("joinedAt", "c.joined_at", Type.TIME, true), fb("lastVerifiedAt", "c.last_verified_at", Type.TIME, true)));
        add(datasets, dataset("authorization_summary", "角色授权安全摘要", Scope.COMMUNICATION, "eve_authorization a JOIN eve_character c ON c.id=a.character_ref_id AND c.tenant_id=a.tenant_id AND c.deleted=0", "a.tenant_id=:tenantId AND c.corporation_id=:corporationId AND a.deleted=0", "expiresAt DESC", f("id", "a.id", Type.ID, true), f("characterRefId", "a.character_ref_id", Type.ID, true), f("characterId", "c.character_id", Type.ID, true), f("characterName", "c.name", Type.STRING, true), f("scopes", "a.scopes", Type.JSON, false), f("expiresAt", "a.expires_at", Type.TIME, true), f("refreshExpiresAt", "a.refresh_expires_at", Type.TIME, true), f("status", "a.status", Type.STRING, true), f("revokedAt", "a.revoked_at", Type.TIME, true), f("failureCount", "a.failure_count", Type.NUMBER, true), f("failureCode", "a.failure_code", Type.STRING, true), f("lastVerificationStatus", "a.last_verification_status", Type.STRING, true), fb("lastVerifiedAt", "a.last_verified_at", Type.TIME, true)));
        add(datasets, dataset("character_role_snapshots", "角色军团权限快照", Scope.COMMUNICATION, "eve_character_role_snapshot s JOIN eve_character c ON c.id=s.character_ref_id AND c.tenant_id=s.tenant_id AND c.deleted=0", "s.tenant_id=:tenantId AND c.corporation_id=:corporationId AND s.deleted=0", "capturedAt DESC", f("id", "s.id", Type.ID, true), f("characterRefId", "s.character_ref_id", Type.ID, true), f("characterId", "s.character_id", Type.ID, true), f("characterName", "c.name", Type.STRING, true), f("isCeo", "s.is_ceo", Type.BOOLEAN, true), f("roles", "s.roles", Type.JSON, false), f("rolesAtHq", "s.roles_at_hq", Type.JSON, false), f("rolesAtBase", "s.roles_at_base", Type.JSON, false), f("rolesAtOther", "s.roles_at_other", Type.JSON, false), fb("capturedAt", "s.captured_at", Type.TIME, true), f("sourceExpiresAt", "s.source_expires_at", Type.TIME, true)));
        add(datasets, dataset("members", "军团成员与追踪", Scope.CORPORATION, "eve_corporation_roster_member r LEFT JOIN eve_corporation_member_tracking t ON t.roster_member_id=r.id AND t.tenant_id=r.tenant_id AND t.deleted=0", "r.tenant_id=:tenantId AND r.corporation_ref_id=:corporationRefId AND r.deleted=0", "lastSeenAt DESC", f("id", "r.id", Type.ID, true), f("characterId", "r.character_id", Type.ID, true), f("characterName", "r.character_name", Type.STRING, true), f("organizationGroup", "r.organization_group", Type.STRING, true), f("memberNote", "r.member_note", Type.STRING, true), f("status", "r.status", Type.STRING, true), f("joinedAt", "r.joined_at", Type.TIME, true), f("leftAt", "r.left_at", Type.TIME, true), f("lastSeenAt", "r.last_seen_at", Type.TIME, true), f("baseName", "t.base_name", Type.STRING, true), f("locationName", "t.location_name", Type.STRING, true), f("shipTypeName", "t.ship_type_name", Type.STRING, true), f("lastLogonAt", "t.last_logon_at", Type.TIME, true), f("lastLogoffAt", "t.last_logoff_at", Type.TIME, true)));
        add(datasets, dataset("member_operations", "成员操作审计", Scope.TENANT, "eve_member_operation_audit a", "a.tenant_id=:tenantId AND a.deleted=0", "occurredAt DESC", f("id", "a.id", Type.ID, true), f("rosterMemberId", "a.roster_member_id", Type.ID, true), f("eventType", "a.event_type", Type.STRING, true), f("summary", "a.summary", Type.STRING, true), f("actorUsername", "a.actor_username", Type.STRING, true), f("occurredAt", "a.occurred_at", Type.TIME, true)));
        add(datasets, dataset("assets", "军团资产", Scope.CORPORATION, "eve_corporation_asset a", "a.tenant_id=:tenantId AND a.corporation_ref_id=:corporationRefId AND a.deleted=0", "lastSeenAt DESC", f("id", "a.id", Type.ID, true), f("itemId", "a.item_id", Type.ID, true), f("typeId", "a.type_id", Type.ID, true), f("typeName", "a.type_name", Type.STRING, true), f("itemName", "a.item_name", Type.STRING, true), f("locationId", "a.location_id", Type.ID, true), f("locationName", "a.location_name", Type.STRING, true), f("locationType", "a.location_type", Type.STRING, true), f("locationFlag", "a.location_flag", Type.STRING, true), f("quantity", "a.quantity", Type.NUMBER, true), f("singleton", "a.singleton", Type.BOOLEAN, true), f("blueprintCopy", "a.blueprint_copy", Type.BOOLEAN, true), f("status", "a.status", Type.STRING, true), f("lastSeenAt", "a.last_seen_at", Type.TIME, true), f("sourceExpiresAt", "a.source_expires_at", Type.TIME, true)));
        add(datasets, dataset("structures", "军团建筑", Scope.CORPORATION, "eve_corporation_structure s", "s.tenant_id=:tenantId AND s.corporation_ref_id=:corporationRefId AND s.deleted=0", "lastSeenAt DESC", f("id", "s.id", Type.ID, true), f("structureId", "s.structure_id", Type.ID, true), f("structureName", "s.structure_name", Type.STRING, true), f("typeId", "s.type_id", Type.ID, true), f("typeName", "s.type_name", Type.STRING, true), f("solarSystemId", "s.solar_system_id", Type.ID, true), f("solarSystemName", "s.solar_system_name", Type.STRING, true), f("state", "s.state", Type.STRING, true), f("fuelExpiresAt", "s.fuel_expires_at", Type.TIME, true), f("services", "s.services", Type.JSON, false), f("status", "s.status", Type.STRING, true), f("lastSeenAt", "s.last_seen_at", Type.TIME, true), f("sourceExpiresAt", "s.source_expires_at", Type.TIME, true)));
        add(datasets, moonExtractions());
        add(datasets, dataset("moon_operations", "月矿排产记录", Scope.TENANT, "eve_moon_operation o JOIN eve_moon_extraction e ON e.id=o.extraction_ref_id AND e.tenant_id=o.tenant_id AND e.deleted=0", "o.tenant_id=:tenantId AND e.corporation_ref_id=:corporationRefId AND o.deleted=0", "plannedAt DESC", f("id", "o.id", Type.ID, true), f("extractionRefId", "o.extraction_ref_id", Type.ID, true), f("structureId", "e.structure_id", Type.ID, true), f("structureName", "e.structure_name", Type.STRING, true), f("responsibleCharacterId", "o.responsible_character_id", Type.ID, true), f("responsibleCharacterName", "o.responsible_character_name", Type.STRING, true), f("plannedAt", "o.planned_at", Type.TIME, true), f("status", "o.status", Type.STRING, true), f("note", "o.note", Type.STRING, true)));
        add(datasets, dataset("moon_operation_tasks", "月矿协同任务", Scope.TENANT, "eve_moon_operation_task t JOIN eve_moon_operation o ON o.id=t.operation_ref_id AND o.tenant_id=t.tenant_id AND o.deleted=0 JOIN eve_moon_extraction e ON e.id=o.extraction_ref_id AND e.tenant_id=o.tenant_id AND e.deleted=0", "t.tenant_id=:tenantId AND e.corporation_ref_id=:corporationRefId AND t.deleted=0", "sort ASC", f("id", "t.id", Type.ID, true), f("operationRefId", "t.operation_ref_id", Type.ID, true), f("title", "t.title", Type.STRING, true), f("assignedCharacterId", "t.assigned_character_id", Type.ID, true), f("assignedCharacterName", "t.assigned_character_name", Type.STRING, true), f("scheduledAt", "t.scheduled_at", Type.TIME, true), f("status", "t.status", Type.STRING, true), f("sort", "t.sort", Type.NUMBER, true), f("note", "t.note", Type.STRING, true)));
        add(datasets, dataset("moon_ore_profiles", "月矿堡矿种比例", Scope.CORPORATION, "eve_moon_ore_profile p", "p.tenant_id=:tenantId AND p.corporation_ref_id=:corporationRefId AND p.deleted=0", "lastCacheCheckedAt DESC", f("id", "p.id", Type.ID, true), f("structureId", "p.structure_id", Type.ID, true), f("moonId", "p.moon_id", Type.ID, true), f("status", "p.status", Type.STRING, true), f("ores", "p.ores_json", Type.JSON, false), f("oreNames", "p.ore_names", Type.STRING, true), f("sourceSentAt", "p.source_sent_at", Type.TIME, true), f("lastCacheCheckedAt", "p.last_cache_checked_at", Type.TIME, true)));
        add(datasets, dataset("moon_structure_notes", "月矿堡备注", Scope.CORPORATION, "eve_moon_structure_note n", "n.tenant_id=:tenantId AND n.corporation_ref_id=:corporationRefId AND n.deleted=0", "updateTime DESC", f("id", "n.id", Type.ID, true), f("structureId", "n.structure_id", Type.ID, true), f("note", "n.note", Type.STRING, true), fb("updateTime", "n.update_time", Type.TIME, true)));
        add(datasets, dataset("mining_observers", "采矿观测点", Scope.CORPORATION, "eve_mining_observer o", "o.tenant_id=:tenantId AND o.corporation_ref_id=:corporationRefId AND o.deleted=0", "lastSeenAt DESC", f("id", "o.id", Type.ID, true), f("observerId", "o.observer_id", Type.ID, true), f("observerType", "o.observer_type", Type.STRING, true), f("observerName", "o.observer_name", Type.STRING, true), f("sourceLastUpdated", "o.source_last_updated", Type.DATE, true), f("status", "o.status", Type.STRING, true), f("lastSeenAt", "o.last_seen_at", Type.TIME, true)));
        add(datasets, dataset("mining_ledger", "采矿明细", Scope.CORPORATION, "eve_mining_ledger l", "l.tenant_id=:tenantId AND l.corporation_ref_id=:corporationRefId AND l.deleted=0", "recordedAt DESC", f("id", "l.id", Type.ID, true), f("observerId", "l.observer_id", Type.ID, true), f("observerName", "l.observer_name", Type.STRING, true), f("characterId", "l.character_id", Type.ID, true), f("characterName", "l.character_name", Type.STRING, true), f("typeId", "l.type_id", Type.ID, true), f("typeName", "l.type_name", Type.STRING, true), f("recordedAt", "l.recorded_at", Type.DATE, true), f("quantity", "l.quantity", Type.NUMBER, true), f("lastSeenAt", "l.last_seen_at", Type.TIME, true)));
        add(datasets, dataset("mining_compression_mapping", "月矿压缩估值映射", Scope.GLOBAL, "eve_mining_compression_mapping m", "1=1", "rawTypeId ASC", f("rawTypeId", "m.raw_type_id", Type.ID, true), f("compressedTypeId", "m.compressed_type_id", Type.ID, true), f("compressionRatio", "m.compression_ratio", Type.NUMBER, true), fb("updateTime", "m.update_time", Type.TIME, true)));
        add(datasets, market());
        addIndustry(datasets);
        addStaticAndStarmap(datasets);
        add(datasets, communication("notifications", "游戏通知", "eve_game_notification n", "n.notification_id", "notificationId", List
            .of(f("id", "n.id", Type.ID, true), f("notificationId", "n.notification_id", Type.ID, true), f("characterId", "n.character_id", Type.ID, true), f("isRead", "n.is_read", Type.BOOLEAN, true), f("senderId", "n.sender_id", Type.ID, true), f("senderType", "n.sender_type", Type.STRING, true), f("notificationType", "n.notification_type", Type.STRING, true), f("category", "n.category", Type.STRING, true), f("contentPreview", "LEFT(n.content, 500)", Type.STRING, true), f("contentTruncated", "CHAR_LENGTH(n.content)>500", Type.BOOLEAN, true), f("sentAt", "n.sent_at", Type.TIME, true), f("lastSeenAt", "n.last_seen_at", Type.TIME, true))));
        add(datasets, communication("mail", "游戏邮件", "eve_game_mail m", "m.mail_id", "mailId", List
            .of(f("id", "m.id", Type.ID, true), f("mailId", "m.mail_id", Type.ID, true), f("characterId", "m.character_id", Type.ID, true), f("fromId", "m.from_id", Type.ID, true), f("subject", "m.subject", Type.STRING, true), f("sentAt", "m.sent_at", Type.TIME, true), f("isRead", "m.is_read", Type.BOOLEAN, true), f("labels", "m.labels", Type.JSON, false), f("recipients", "m.recipients", Type.JSON, false), f("bodyPreview", "LEFT(m.body, 500)", Type.STRING, true), f("bodyTruncated", "CHAR_LENGTH(m.body)>500", Type.BOOLEAN, true), f("bodySynchronizedAt", "m.body_synchronized_at", Type.TIME, true), f("lastSeenAt", "m.last_seen_at", Type.TIME, true))));
        add(datasets, news());
        add(datasets, dataset("data_freshness", "数据新鲜度", Scope.CORPORATION, "eve_data_sync_status s", "s.tenant_id=:tenantId AND s.corporation_ref_id=:corporationRefId AND s.deleted=0", "module ASC", f("id", "s.id", Type.ID, true), f("module", "s.module", Type.STRING, true), f("lastSuccessfulAt", "s.last_successful_at", Type.TIME, true), f("sourceExpiresAt", "s.source_expires_at", Type.TIME, true), f("lastFailureAt", "s.last_failure_at", Type.TIME, true), f("failureCode", "s.failure_code", Type.STRING, true)));
        add(datasets, dataset("permission_summary", "本站业务权限安全摘要", Scope.TENANT, "sys_role r JOIN eve_effective_role_menu rm ON rm.role_id=r.id AND rm.tenant_id=r.tenant_id JOIN sys_menu m ON m.id=rm.menu_id AND m.deleted=0", "r.tenant_id=:tenantId AND r.deleted=0 AND m.permission IS NOT NULL", "roleCode ASC", f("roleId", "r.id", Type.ID, true), f("menuId", "m.id", Type.ID, true), f("roleName", "r.name", Type.STRING, true), f("roleCode", "r.code", Type.STRING, true), f("dataScope", "r.data_scope", Type.NUMBER, true), f("menuTitle", "m.title", Type.STRING, true), f("permission", "m.permission", Type.STRING, true), f("status", "m.status", Type.NUMBER, true)));
        add(datasets, dataset("agent_audit", "外部 Agent 调用审计", Scope.CORPORATION, "eve_agent_audit a", "a.tenant_id=:tenantId AND a.corporation_ref_id=:corporationRefId AND a.deleted=0", "occurredAt DESC", f("id", "a.id", Type.ID, true), f("applicationId", "a.application_id", Type.ID, true), f("applicationName", "a.application_name", Type.STRING, true), f("actorType", "a.actor_type", Type.STRING, true), f("toolName", "a.tool_name", Type.STRING, true), f("success", "a.success", Type.BOOLEAN, true), f("durationMs", "a.duration_ms", Type.NUMBER, true), f("resultCount", "a.result_count", Type.NUMBER, true), f("occurredAt", "a.occurred_at", Type.TIME, true)));
        return Map.copyOf(datasets);
    }

    /** 军团基础资料按绑定主键和租户同时隔离。 */
    private static Dataset corporation() {
        return dataset("corporation", "军团基本资料", Scope.CORPORATION, "eve_corporation c", "c.id=:corporationRefId AND c.tenant_id=:tenantId AND c.corporation_id=:corporationId AND c.deleted=0", "name ASC", f("id", "c.id", Type.ID, true), f("corporationId", "c.corporation_id", Type.ID, true), f("server", "c.server", Type.STRING, true), f("name", "c.name", Type.STRING, true), f("ticker", "c.ticker", Type.STRING, true), f("ceoCharacterId", "c.ceo_character_id", Type.ID, true), f("allianceId", "c.alliance_id", Type.ID, true), f("memberCount", "c.member_count", Type.NUMBER, true), f("taxRate", "c.tax_rate", Type.DECIMAL, true), f("status", "c.status", Type.STRING, true), fb("lastSyncedAt", "c.last_synced_at", Type.TIME, true));
    }

    /** 月矿排期只读取已有快照，不触发矿种初始化。 */
    private static Dataset moonExtractions() {
        return dataset("moon_extractions", "月矿排期", Scope.CORPORATION, "eve_moon_extraction e", "e.tenant_id=:tenantId AND e.corporation_ref_id=:corporationRefId AND e.deleted=0", "chunkArrivalAt DESC", f("id", "e.id", Type.ID, true), f("structureId", "e.structure_id", Type.ID, true), f("structureName", "e.structure_name", Type.STRING, true), f("structureTypeName", "e.structure_type_name", Type.STRING, true), f("moonId", "e.moon_id", Type.ID, true), f("moonName", "e.moon_name", Type.STRING, true), f("solarSystemId", "e.solar_system_id", Type.ID, true), f("solarSystemName", "e.solar_system_name", Type.STRING, true), f("extractionStartAt", "e.extraction_start_at", Type.TIME, true), f("chunkArrivalAt", "e.chunk_arrival_at", Type.TIME, true), f("naturalDecayAt", "e.natural_decay_at", Type.TIME, true), f("status", "e.status", Type.STRING, true), f("lastSeenAt", "e.last_seen_at", Type.TIME, true));
    }

    /** 发布公共行情快照，不公开原始上游异常。 */
    private static Dataset market() {
        return dataset("market", "吉他市场行情与历史", Scope.GLOBAL, "eve_market_snapshot m LEFT JOIN eve_static_type_reference t ON t.type_id=m.type_id", "1=1", "sourceUpdatedAt DESC", f("typeId", "m.type_id", Type.ID, true), f("typeName", "t.type_name", Type.STRING, true), f("highestBuyPrice", "m.highest_buy_price", Type.DECIMAL, true), f("lowestSellPrice", "m.lowest_sell_price", Type.DECIMAL, true), f("buyVolume", "m.buy_volume", Type.NUMBER, true), f("sellVolume", "m.sell_volume", Type.NUMBER, true), fb("sourceUpdatedAt", "m.source_updated_at", Type.TIME, true), fb("quoteSynchronizedAt", "m.quote_synchronized_at", Type.TIME, true), fb("detailSynchronizedAt", "m.detail_synchronized_at", Type.TIME, true), f("history", "m.history_json", Type.JSON, false));
    }

    /** 注册工业参考资料和租户独有的配置、测算及计划。 */
    private static void addIndustry(Map<String, Dataset> datasets) {
        add(datasets, dataset("industry_blueprints", "蓝图材料配方", Scope.GLOBAL, "eve_industry_blueprint b", "b.deleted=0", "blueprintName ASC", f("id", "b.id", Type.ID, true), f("blueprintTypeId", "b.blueprint_type_id", Type.ID, true), f("blueprintName", "b.blueprint_name", Type.STRING, true), f("activity", "b.activity", Type.STRING, true), f("productTypeId", "b.product_type_id", Type.ID, true), f("productName", "b.product_name", Type.STRING, true), f("productQuantity", "b.product_quantity", Type.DECIMAL, true), f("baseTimeSeconds", "b.base_time_seconds", Type.DECIMAL, true), f("probability", "b.probability", Type.DECIMAL, true), f("materials", "b.materials_json", Type.JSON, false), f("products", "b.products_json", Type.JSON, false), f("source", "b.source", Type.STRING, true), f("supported", "b.supported", Type.BOOLEAN, true)));
        add(datasets, dataset("industry_configs", "工业生产配置", Scope.TENANT, "eve_industry_config c", "c.tenant_id=:tenantId AND c.deleted=0", "updateTime DESC", f("id", "c.id", Type.ID, true), f("name", "c.name", Type.STRING, true), f("productionSkills", "c.production_skill_json", Type.JSON, false), f("marketFees", "c.market_fee_json", Type.JSON, false), f("targetDays", "c.target_days", Type.DECIMAL, true), f("marketShareLimit", "c.market_share_limit", Type.DECIMAL, true), f("version", "c.version", Type.NUMBER, true), f("enabled", "c.enabled", Type.BOOLEAN, true), fb("updateTime", "c.update_time", Type.TIME, true)));
        add(datasets, dataset("industry_facilities", "工业设施配置", Scope.TENANT, "eve_industry_facility_profile f", "f.tenant_id=:tenantId AND f.deleted=0", "updateTime DESC", f("id", "f.id", Type.ID, true), f("name", "f.name", Type.STRING, true), f("realityType", "f.reality_type", Type.STRING, true), f("systemId", "f.system_id", Type.ID, true), f("systemName", "f.system_name", Type.STRING, true), f("facilityName", "f.facility_name", Type.STRING, true), f("facilityTax", "f.facility_tax", Type.DECIMAL, true), f("versionedSurcharge", "f.versioned_surcharge", Type.DECIMAL, true), f("materialMultiplier", "f.material_multiplier", Type.DECIMAL, true), f("timeMultiplier", "f.time_multiplier", Type.DECIMAL, true), f("costMultiplier", "f.cost_multiplier", Type.DECIMAL, true), f("enabled", "f.enabled", Type.BOOLEAN, true), fb("updateTime", "f.update_time", Type.TIME, true)));
        add(datasets, dataset("industry_blueprint_overrides", "蓝图材料效率与时间效率", Scope.TENANT, "eve_industry_blueprint_override o", "o.tenant_id=:tenantId AND o.deleted=0", "updateTime DESC", f("id", "o.id", Type.ID, true), f("blueprintTypeId", "o.blueprint_type_id", Type.ID, true), f("blueprintKind", "o.blueprint_kind", Type.STRING, true), f("materialEfficiency", "o.material_efficiency", Type.NUMBER, true), f("timeEfficiency", "o.time_efficiency", Type.NUMBER, true), f("remainingRuns", "o.remaining_runs", Type.NUMBER, true), f("source", "o.source", Type.STRING, true), f("version", "o.version", Type.NUMBER, true), fb("updateTime", "o.update_time", Type.TIME, true)));
        add(datasets, dataset("industry_opportunities", "工业生产机会", Scope.TENANT, "eve_industry_opportunity_snapshot o", "o.tenant_id=:tenantId AND o.deleted=0", "calculatedAt DESC", f("id", "o.id", Type.ID, true), f("scanId", "o.scan_id", Type.STRING, true), f("productTypeId", "o.product_type_id", Type.ID, true), f("productName", "o.product_name", Type.STRING, true), f("status", "o.status", Type.STRING, true), f("recommendedRuns", "o.recommended_runs", Type.NUMBER, true), f("recommendedQuantity", "o.recommended_quantity", Type.NUMBER, true), f("materialCost", "o.material_cost", Type.DECIMAL, true), f("immediateProfit", "o.immediate_profit", Type.DECIMAL, true), f("sellOrderProfit", "o.sell_order_profit", Type.DECIMAL, true), f("margin", "o.margin", Type.DECIMAL, true), f("dailyProfit", "o.daily_profit", Type.DECIMAL, true), f("liquidityScore", "o.liquidity_score", Type.DECIMAL, true), f("riskScore", "o.risk_score", Type.DECIMAL, true), fb("calculatedAt", "o.calculated_at", Type.TIME, true)));
        add(datasets, dataset("industry_opportunity_materials", "机会材料明细", Scope.TENANT, "eve_industry_opportunity_material m", "m.tenant_id=:tenantId AND m.deleted=0", "id ASC", f("id", "m.id", Type.ID, true), f("opportunityId", "m.opportunity_id", Type.ID, true), f("materialTypeId", "m.material_type_id", Type.ID, true), f("materialName", "m.material_name", Type.STRING, true), f("requiredQuantity", "m.required_quantity", Type.NUMBER, true), f("purchaseCost", "m.purchase_cost", Type.DECIMAL, true), f("averagePrice", "m.average_price", Type.DECIMAL, true)));
        add(datasets, dataset("industry_plans", "工业生产计划", Scope.TENANT, "eve_industry_plan p", "p.tenant_id=:tenantId AND p.deleted=0", "updateTime DESC", f("id", "p.id", Type.ID, true), f("name", "p.name", Type.STRING, true), f("status", "p.status", Type.STRING, true), f("activity", "p.activity", Type.STRING, true), f("productTypeId", "p.product_type_id", Type.ID, true), f("productName", "p.product_name", Type.STRING, true), f("runs", "p.runs", Type.NUMBER, true), f("targetQuantity", "p.target_quantity", Type.NUMBER, true), f("totalMaterialCost", "p.total_material_cost", Type.DECIMAL, true), f("totalInstallCost", "p.total_install_cost", Type.DECIMAL, true), f("expectedRevenue", "p.expected_revenue", Type.DECIMAL, true), f("expectedProfit", "p.expected_profit", Type.DECIMAL, true), f("expectedTimeSeconds", "p.expected_time_seconds", Type.DECIMAL, true), fb("confirmedAt", "p.confirmed_at", Type.TIME, true), fb("updateTime", "p.update_time", Type.TIME, true)));
        add(datasets, dataset("industry_plan_steps", "工业计划步骤与快照", Scope.TENANT, "eve_industry_plan_step s", "s.tenant_id=:tenantId AND s.deleted=0", "sequenceNo ASC", f("id", "s.id", Type.ID, true), f("planId", "s.plan_id", Type.ID, true), f("stepKey", "s.step_key", Type.STRING, true), f("stepType", "s.step_type", Type.STRING, true), f("sequenceNo", "s.sequence_no", Type.NUMBER, true), f("status", "s.status", Type.STRING, true), f("title", "s.title", Type.STRING, true), f("typeId", "s.type_id", Type.ID, true), f("quantity", "s.quantity", Type.NUMBER, true), f("unitCost", "s.unit_cost", Type.DECIMAL, true), f("totalCost", "s.total_cost", Type.DECIMAL, true), f("durationSeconds", "s.duration_seconds", Type.DECIMAL, true), f("payload", "s.payload_json", Type.JSON, false), f("startedAt", "s.started_at", Type.TIME, true), f("completedAt", "s.completed_at", Type.TIME, true)));
        add(datasets, dataset("industry_market_snapshots", "工业市场输入快照", Scope.TENANT, "eve_industry_market_input_snapshot s", "s.tenant_id=:tenantId AND s.deleted=0", "capturedAt DESC", f("id", "s.id", Type.ID, true), f("snapshotKey", "s.snapshot_key", Type.STRING, true), f("marketRegionId", "s.market_region_id", Type.ID, true), f("marketStationId", "s.market_station_id", Type.ID, true), fb("capturedAt", "s.captured_at", Type.TIME, true), fb("sourceUpdatedAt", "s.source_updated_at", Type.TIME, true), f("source", "s.source", Type.STRING, true), f("quote", "s.quote_json", Type.JSON, false), f("freshnessStatus", "s.freshness_status", Type.STRING, true), f("ruleVersion", "s.rule_version", Type.STRING, true)));
        add(datasets, dataset("industry_market_order_slices", "工业市场订单档位", Scope.TENANT, "eve_industry_market_order_slice s", "s.tenant_id=:tenantId AND s.deleted=0", "price ASC", f("id", "s.id", Type.ID, true), f("snapshotId", "s.snapshot_id", Type.ID, true), f("typeId", "s.type_id", Type.ID, true), f("side", "s.side", Type.STRING, true), f("price", "s.price", Type.DECIMAL, true), f("quantity", "s.quantity", Type.NUMBER, true), f("cumulativeQuantity", "s.cumulative_quantity", Type.NUMBER, true), f("sourceOrderId", "s.source_order_id", Type.ID, true), f("sourceUpdatedAt", "s.source_updated_at", Type.TIME, true)));
        add(datasets, dataset("industry_system_snapshots", "工业星系与设施事实快照", Scope.TENANT, "eve_industry_system_snapshot s", "s.tenant_id=:tenantId AND s.deleted=0", "capturedAt DESC", f("id", "s.id", Type.ID, true), f("snapshotKey", "s.snapshot_key", Type.STRING, true), f("systemId", "s.system_id", Type.ID, true), f("systemName", "s.system_name", Type.STRING, true), f("securityStatus", "s.security_status", Type.DECIMAL, true), f("manufacturingSci", "s.manufacturing_sci", Type.DECIMAL, true), f("reactionSci", "s.reaction_sci", Type.DECIMAL, true), f("facility", "s.facility_json", Type.JSON, false), f("source", "s.source", Type.STRING, true), fb("capturedAt", "s.captured_at", Type.TIME, true), f("expiresAt", "s.expires_at", Type.TIME, true)));
        add(datasets, dataset("industry_system_cost_index", "工业星系成本指数", Scope.GLOBAL, "eve_industry_system_cost_index s", "s.deleted=0", "fetchedAt DESC", f("id", "s.id", Type.ID, true), f("systemId", "s.system_id", Type.ID, true), f("activity", "s.activity", Type.STRING, true), f("costIndex", "s.cost_index", Type.DECIMAL, true), f("source", "s.source", Type.STRING, true), fb("fetchedAt", "s.fetched_at", Type.TIME, true), f("expiresAt", "s.expires_at", Type.TIME, true)));
    }

    /** 注册公共拓扑参考资料与当前军团的私有星图运营资料。 */
    private static void addStaticAndStarmap(Map<String, Dataset> datasets) {
        add(datasets, dataset("static_types", "物品静态资料", Scope.GLOBAL, "eve_static_type_reference t", "1=1", "name ASC", f("typeId", "t.type_id", Type.ID, true), f("name", "t.type_name", Type.STRING, true), fng("description", "t.type_description", Type.STRING, false), f("category1", "t.market_category_l1", Type.STRING, true), f("category2", "t.market_category_l2", Type.STRING, true), f("category3", "t.market_category_l3", Type.STRING, true), fb("sourceUpdatedAt", "t.source_updated_at", Type.TIME, true)));
        add(datasets, dataset("static_locations", "地点静态资料", Scope.GLOBAL, "eve_static_location_reference l", "1=1", "name ASC", f("referenceType", "l.reference_type", Type.STRING, true), f("referenceId", "l.reference_id", Type.ID, true), f("name", "l.reference_name", Type.STRING, true), f("solarSystemId", "l.solar_system_id", Type.ID, true), f("constellationId", "l.constellation_id", Type.ID, true), f("regionId", "l.region_id", Type.ID, true), f("securityStatus", "l.security_status", Type.DECIMAL, true), fb("sourceUpdatedAt", "l.source_updated_at", Type.TIME, true)));
        add(datasets, dataset("universe_systems", "国服星系拓扑", Scope.GLOBAL, "eve_universe_system s", "1=1", "systemId ASC", f("systemId", "s.system_id", Type.ID, true), f("constellationId", "s.constellation_id", Type.ID, true), f("regionId", "s.region_id", Type.ID, true), f("securityStatus", "s.security_status", Type.DECIMAL, true), f("positionX", "s.position_x", Type.DECIMAL, true), f("positionY", "s.position_y", Type.DECIMAL, true), f("positionZ", "s.position_z", Type.DECIMAL, true), f("stargateCount", "s.stargate_count", Type.NUMBER, true), f("sourceExpiresAt", "s.source_expires_at", Type.TIME, true), f("synchronizedAt", "s.synchronized_at", Type.TIME, true)));
        add(datasets, dataset("universe_stargates", "国服星门拓扑", Scope.GLOBAL, "eve_universe_stargate g", "1=1", "stargateId ASC", f("stargateId", "g.stargate_id", Type.ID, true), f("systemId", "g.system_id", Type.ID, true), f("destinationStargateId", "g.destination_stargate_id", Type.ID, true), f("destinationSystemId", "g.destination_system_id", Type.ID, true), f("typeId", "g.type_id", Type.ID, true), f("positionX", "g.position_x", Type.DECIMAL, true), f("positionY", "g.position_y", Type.DECIMAL, true), f("positionZ", "g.position_z", Type.DECIMAL, true), f("sourceExpiresAt", "g.source_expires_at", Type.TIME, true), f("synchronizedAt", "g.synchronized_at", Type.TIME, true)));
        add(datasets, dataset("starmap_annotations", "星图运营标注", Scope.TENANT, "eve_starmap_annotation a", "a.tenant_id=:tenantId AND a.deleted=0", "updateTime DESC", f("id", "a.id", Type.ID, true), f("solarSystemId", "a.solar_system_id", Type.ID, true), f("category", "a.category", Type.STRING, true), f("title", "a.title", Type.STRING, true), f("note", "a.note", Type.STRING, true), f("colorKey", "a.color_key", Type.STRING, true), fb("expiresAt", "a.expires_at", Type.TIME, true), f("archived", "a.archived", Type.BOOLEAN, true), fb("updateTime", "a.update_time", Type.TIME, true)));
        add(datasets, dataset("starmap_routes", "军团航线", Scope.TENANT, "eve_starmap_route r", "r.tenant_id=:tenantId AND r.deleted=0", "validatedAt DESC", f("id", "r.id", Type.ID, true), f("title", "r.title", Type.STRING, true), f("description", "r.description", Type.STRING, true), f("originSystemId", "r.origin_system_id", Type.ID, true), f("destinationSystemId", "r.destination_system_id", Type.ID, true), f("jumpCount", "r.jump_count", Type.NUMBER, true), f("validatedAt", "r.validated_at", Type.TIME, true), f("archived", "r.archived", Type.BOOLEAN, true)));
        add(datasets, dataset("starmap_route_points", "军团航线节点", Scope.TENANT, "eve_starmap_route_point p", "p.tenant_id=:tenantId AND p.deleted=0", "pointOrder ASC", f("id", "p.id", Type.ID, true), f("routeId", "p.route_id", Type.ID, true), f("pointOrder", "p.point_order", Type.NUMBER, true), f("solarSystemId", "p.solar_system_id", Type.ID, true), f("pointKind", "p.point_kind", Type.STRING, true)));
    }

    /** 邮件和通知必须同时匹配当前军团有效角色、授权及精确 Scope。 */
    private static Dataset communication(String key,
                                         String title,
                                         String table,
                                         String idExpression,
                                         String idField,
                                         List<Field> fields) {
        String alias = key.equals("mail") ? "m" : "n";
        String requiredScope = key.equals("mail") ? "esi-mail.read_mail.v1" : "esi-characters.read_notifications.v1";
        String from = table + " JOIN eve_character c ON c.id=" + alias + ".character_ref_id AND c.tenant_id=" + alias + ".tenant_id AND c.deleted=0 AND c.status='ACTIVE' JOIN eve_authorization a ON a.character_ref_id=" + alias + ".character_ref_id AND a.tenant_id=" + alias + ".tenant_id AND a.deleted=0 AND a.status='ACTIVE' AND JSON_CONTAINS(a.scopes, JSON_QUOTE('" + requiredScope + "'))";
        String where = alias + ".tenant_id=:tenantId AND c.corporation_id=:corporationId AND " + alias + ".deleted=0";
        return new Dataset(key, title, Scope.COMMUNICATION, from, where, "sentAt DESC", toMap(fields), idExpression, idField);
    }

    /** 只读已缓存官方资讯，不执行网页抓取。 */
    private static Dataset news() {
        return new Dataset("news", "官方资讯", Scope.GLOBAL, "eve_official_news n", "n.deleted=0", "publishedAt DESC", toMap(List
            .of(f("id", "n.id", Type.ID, true), f("sourceCode", "n.source_code", Type.STRING, true), f("category", "n.source_category", Type.STRING, true), f("title", "n.title", Type.STRING, true), f("summary", "n.summary", Type.STRING, true), f("originalUrl", "n.original_url", Type.STRING, true), f("contentPreview", "LEFT(n.content_text, 500)", Type.STRING, true), f("contentTruncated", "CHAR_LENGTH(n.content_text)>500", Type.BOOLEAN, true), f("coverUrl", "n.cover_url", Type.STRING, true), fb("publishedAt", "n.published_at", Type.TIME, true))), "n.id", "id");
    }

    /** 构造不提供专用正文读取能力的普通数据集。 */
    private static Dataset dataset(String key,
                                   String title,
                                   Scope scope,
                                   String from,
                                   String where,
                                   String defaultSort,
                                   Field... fields) {
        return new Dataset(key, title, scope, from, where, defaultSort, toMap(List.of(fields)), null, null);
    }

    /** 固化字段白名单，避免运行时被调用方修改。 */
    private static Map<String, Field> toMap(List<Field> fields) {
        Map<String, Field> result = new LinkedHashMap<>();
        fields.forEach(field -> result.put(field.name(), field));
        return Map.copyOf(result);
    }

    /** 普通字段默认按 UTC 解释时间，JSON 不参与分组。 */
    private static Field f(String name, String expression, Type type, boolean aggregatable) {
        return new Field(name, expression, type, type == Type.STRING, true, aggregatable, type != Type.JSON, TimeBasis.UTC);
    }

    /** 标记已有业务字段以北京时间保存。 */
    private static Field fb(String name, String expression, Type type, boolean aggregatable) {
        return new Field(name, expression, type, type == Type.STRING, true, aggregatable, type != Type.JSON, TimeBasis.BEIJING);
    }

    /** 大文本字段禁止分组；聚合能力由调用点明确指定。 */
    private static Field fng(String name, String expression, Type type, boolean aggregatable) {
        return new Field(name, expression, type, type == Type.STRING, true, aggregatable, false, TimeBasis.UTC);
    }

    /** 按公开数据集键加入注册表。 */
    private static void add(Map<String, Dataset> datasets, Dataset dataset) {
        datasets.put(dataset.key(), dataset);
    }

    /** 数据来源的授权隔离范围。 */
    enum Scope {
        GLOBAL, TENANT, CORPORATION, COMMUNICATION
    }

    /** 用于筛选校验与无损序列化的业务字段类型。 */
    enum Type {
        STRING, ID, NUMBER, DECIMAL, BOOLEAN, DATE, TIME, JSON
    }

    /** 无时区数据库时间的解释基准。 */
    enum TimeBasis {
        UTC, BEIJING
    }

    /** 公开字段、可信 SQL 表达式及允许的查询能力。 */
    record Field(String name, String expression, Type type, boolean searchable, boolean sortable, boolean aggregatable,
                 boolean groupable, TimeBasis timeBasis) {
    }

    /** 数据集的可信来源、强制身份条件及可见字段定义。 */
    record Dataset(String key, String title, Scope scope, String from, String where, String defaultSort,
                   Map<String, Field> fields, String documentIdExpression, String documentIdField) {
    }
}
