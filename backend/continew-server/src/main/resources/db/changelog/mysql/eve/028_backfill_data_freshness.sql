-- liquibase formatted sql

-- changeset eve-corp-manager:028-backfill-data-freshness
-- comment 将已有完整快照回填为统一数据新鲜度状态，避免升级后把历史数据误显示为无数据
INSERT INTO `eve_data_sync_status` (`id`, `tenant_id`, `corporation_ref_id`, `module`, `last_successful_at`,
                                    `source_expires_at`, `create_user`, `create_time`, `deleted`)
SELECT CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6)) * 1000000 AS UNSIGNED) + ROW_NUMBER() OVER () AS id,
       legacy.tenant_id,
       legacy.corporation_ref_id,
       legacy.module,
       legacy.last_successful_at,
       legacy.source_expires_at,
       1,
       CURRENT_TIMESTAMP,
       0
FROM (
         SELECT tenant_id, corporation_ref_id, 'ASSETS' AS module, MAX(last_seen_at) AS last_successful_at,
                MAX(source_expires_at) AS source_expires_at
         FROM eve_corporation_asset
         WHERE deleted = 0
         GROUP BY tenant_id, corporation_ref_id
         UNION ALL
         SELECT tenant_id, corporation_ref_id, 'STRUCTURES', MAX(last_seen_at), MAX(source_expires_at)
         FROM eve_corporation_structure
         WHERE deleted = 0
         GROUP BY tenant_id, corporation_ref_id
         UNION ALL
         SELECT tenant_id, corporation_ref_id, 'MOON_EXTRACTIONS', MAX(last_seen_at), MAX(source_expires_at)
         FROM eve_moon_extraction
         WHERE deleted = 0
         GROUP BY tenant_id, corporation_ref_id
         UNION ALL
         SELECT tenant_id, corporation_ref_id, 'MINING_LEDGER', MAX(synchronized_at), MAX(source_expires_at)
         FROM eve_mining_sync_run
         WHERE deleted = 0
         GROUP BY tenant_id, corporation_ref_id
         UNION ALL
         SELECT tenant_id, corporation_ref_id, 'MEMBER_ROSTER', MAX(finished_at), MAX(source_expires_at)
         FROM eve_member_sync_run
         WHERE deleted = 0 AND resource = 'ROSTER' AND status = 'SUCCEEDED'
         GROUP BY tenant_id, corporation_ref_id
         UNION ALL
         SELECT tenant_id, corporation_ref_id, 'MEMBER_TRACKING', MAX(finished_at), MAX(source_expires_at)
         FROM eve_member_sync_run
         WHERE deleted = 0 AND resource = 'TRACKING' AND status = 'SUCCEEDED'
         GROUP BY tenant_id, corporation_ref_id
     ) legacy
LEFT JOIN eve_data_sync_status existing
    ON existing.corporation_ref_id = legacy.corporation_ref_id
   AND existing.module = legacy.module
   AND existing.deleted = 0
WHERE existing.id IS NULL;
