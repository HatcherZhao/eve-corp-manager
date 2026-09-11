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

package top.continew.admin.eve.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.eve.client.SerenityEsiClientException;
import top.continew.admin.eve.client.SerenityTokenClientException;
import top.continew.admin.eve.mapper.EveDataSyncStatusMapper;
import top.continew.admin.eve.model.EveDataFreshnessResp;
import top.continew.admin.eve.model.entity.EveDataSyncStatusDO;
import top.continew.admin.eve.model.enums.EveDataFreshnessStatus;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 汇总并持久化军团数据模块的新鲜度与同步失败状态。
 *
 * <p>失败记录使用独立事务写入，确保业务同步回滚后仍能向用户说明旧快照为何没有更新。</p>
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveDataFreshnessService {

    public static final String ASSETS = "ASSETS";
    public static final String STRUCTURES = "STRUCTURES";
    public static final String MOON_EXTRACTIONS = "MOON_EXTRACTIONS";
    public static final String MINING_LEDGER = "MINING_LEDGER";
    public static final String MEMBER_ROSTER = "MEMBER_ROSTER";
    public static final String MEMBER_TRACKING = "MEMBER_TRACKING";

    private static final List<ModuleDefinition> MODULES = List
        .of(new ModuleDefinition(ASSETS, "军团资产", "/eve/assets"), new ModuleDefinition(STRUCTURES, "军团建筑", "/eve/structures"), new ModuleDefinition(MOON_EXTRACTIONS, "月矿情报", "/eve/extractions"), new ModuleDefinition(MINING_LEDGER, "月矿开采统计", "/eve/mining"), new ModuleDefinition(MEMBER_ROSTER, "成员名册", "/eve/members"), new ModuleDefinition(MEMBER_TRACKING, "成员追踪", "/eve/members"));

    private final EveDataSyncStatusMapper statusMapper;

    /** 返回一个军团全部已上线数据模块的统一同步健康状态。 */
    public List<EveDataFreshnessResp> list(Long tenantId, Long corporationRefId) {
        if (tenantId == null || corporationRefId == null) {
            return MODULES.stream().map(definition -> empty(definition)).toList();
        }
        Map<String, EveDataSyncStatusDO> records = new LinkedHashMap<>();
        statusMapper.selectList(new LambdaQueryWrapper<EveDataSyncStatusDO>()
            .eq(EveDataSyncStatusDO::getCorporationRefId, corporationRefId)
            .eq(EveDataSyncStatusDO::getDeleted, 0L)).forEach(item -> records.put(item.getModule(), item));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return MODULES.stream().map(definition -> toResponse(definition, records.get(definition.code()), now)).toList();
    }

    /** 成功发布完整快照后更新成功时间，并清除已被本次成功覆盖的失败提示。 */
    public void recordSuccess(Long tenantId,
                              Long corporationRefId,
                              String module,
                              LocalDateTime synchronizedAt,
                              LocalDateTime sourceExpiresAt) {
        EveDataSyncStatusDO record = findOrCreate(tenantId, corporationRefId, module);
        record.setLastSuccessfulAt(synchronizedAt);
        record.setSourceExpiresAt(sourceExpiresAt);
        record.setLastFailureAt(null);
        record.setFailureCode(null);
        save(record);
    }

    /** 即使同步事务回滚，也独立持久化脱敏后的失败分类。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordFailure(Long tenantId, Long corporationRefId, String module, Throwable exception) {
        EveDataSyncStatusDO record = findOrCreate(tenantId, corporationRefId, module);
        record.setLastFailureAt(LocalDateTime.now(ZoneOffset.UTC));
        record.setFailureCode(failureCode(exception));
        save(record);
    }

    /** 记录业务已明确分类的同步不可用状态，不暴露原始异常信息。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordFailure(Long tenantId, Long corporationRefId, String module, String failureCode) {
        EveDataSyncStatusDO record = findOrCreate(tenantId, corporationRefId, module);
        record.setLastFailureAt(LocalDateTime.now(ZoneOffset.UTC));
        record.setFailureCode(failureCode);
        save(record);
    }

    /** 统一将上游与本站异常转换为可展示且不含敏感信息的分类。 */
    private static String failureCode(Throwable exception) {
        if (exception instanceof SerenityEsiClientException esiException) {
            return esiException.getFailureCode().name();
        }
        if (exception instanceof SerenityTokenClientException tokenException) {
            return tokenException.getFailureCode().name();
        }
        return "SYNC_REJECTED";
    }

    private EveDataSyncStatusDO findOrCreate(Long tenantId, Long corporationRefId, String module) {
        EveDataSyncStatusDO record = statusMapper.selectOne(new LambdaQueryWrapper<EveDataSyncStatusDO>()
            .eq(EveDataSyncStatusDO::getCorporationRefId, corporationRefId)
            .eq(EveDataSyncStatusDO::getModule, module)
            .eq(EveDataSyncStatusDO::getDeleted, 0L));
        if (record != null) {
            return record;
        }
        EveDataSyncStatusDO created = new EveDataSyncStatusDO();
        created.setTenantId(tenantId);
        created.setCorporationRefId(corporationRefId);
        created.setModule(module);
        created.setCreateUser(1L);
        created.setDeleted(0L);
        return created;
    }

    private void save(EveDataSyncStatusDO record) {
        if (record.getId() == null) {
            statusMapper.insert(record);
        } else {
            statusMapper.updateById(record);
        }
    }

    private static EveDataFreshnessResp toResponse(ModuleDefinition definition,
                                                   EveDataSyncStatusDO record,
                                                   LocalDateTime now) {
        if (record == null || record.getLastSuccessfulAt() == null) {
            return empty(definition, record);
        }
        boolean failedAfterSuccess = record.getLastFailureAt() != null && record.getLastFailureAt()
            .isAfter(record.getLastSuccessfulAt());
        EveDataFreshnessStatus status = failedAfterSuccess
            ? EveDataFreshnessStatus.SYNC_FAILED
            : record.getSourceExpiresAt() != null && !record.getSourceExpiresAt().isAfter(now)
                ? EveDataFreshnessStatus.STALE
                : EveDataFreshnessStatus.FRESH;
        return new EveDataFreshnessResp(definition.code(), definition.title(), definition.route(), status, record
            .getLastSuccessfulAt(), record.getSourceExpiresAt(), record.getLastFailureAt(), record
                .getFailureCode(), isRetryable(record.getFailureCode()));
    }

    private static EveDataFreshnessResp empty(ModuleDefinition definition) {
        return empty(definition, null);
    }

    private static EveDataFreshnessResp empty(ModuleDefinition definition, EveDataSyncStatusDO record) {
        return new EveDataFreshnessResp(definition.code(), definition.title(), definition
            .route(), EveDataFreshnessStatus.NO_DATA, null, null, record == null
                ? null
                : record.getLastFailureAt(), record == null ? null : record.getFailureCode(), isRetryable(record == null
                    ? null
                    : record.getFailureCode()));
    }

    private static boolean isRetryable(String failureCode) {
        return "TRANSIENT".equals(failureCode) || "SYNC_REJECTED".equals(failureCode);
    }

    private record ModuleDefinition(String code, String title, String route) {
    }
}
