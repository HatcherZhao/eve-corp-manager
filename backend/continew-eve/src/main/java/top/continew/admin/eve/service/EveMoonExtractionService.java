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
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.client.SerenityEsiClient;
import top.continew.admin.eve.client.SerenityEsiClientException;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCorporationMapper;
import top.continew.admin.eve.mapper.EveCorporationStructureMapper;
import top.continew.admin.eve.mapper.EveMoonExtractionMapper;
import top.continew.admin.eve.mapper.EveMoonStructureNoteMapper;
import top.continew.admin.eve.model.EveMeContextResp;
import top.continew.admin.eve.model.EveMoonExtractionResp;
import top.continew.admin.eve.model.EveMoonExtractionSyncResp;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO;
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.admin.eve.model.entity.EveCorporationStructureDO;
import top.continew.admin.eve.model.entity.EveMoonExtractionDO;
import top.continew.admin.eve.model.entity.EveMoonStructureNoteDO;
import top.continew.admin.eve.model.entity.EveStaticLocationReferenceDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.model.serenity.SerenityCorporationMoonExtractionResponse;
import top.continew.admin.eve.model.serenity.SerenityEsiPagedResponse;
import top.continew.admin.eve.model.serenity.SerenityUniverseMoonResponse;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 管理国服月矿提取时间线的查询、同步与军团备注。
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveMoonExtractionService {

    private static final String EXTRACTION_SCOPE = "esi-industry.read_corporation_mining.v1";
    private static final int MAX_UPSTREAM_PAGES = 1000;
    private final EveContextService contextService;
    private final EveCorporationMapper corporationMapper;
    private final EveCorporationStructureMapper structureMapper;
    private final EveMoonExtractionMapper extractionMapper;
    private final EveMoonStructureNoteMapper structureNoteMapper;
    private final EveAuthorizationMapper authorizationMapper;
    private final top.continew.admin.eve.mapper.EveCharacterRoleSnapshotMapper roleSnapshotMapper;
    private final EveAuthorizationLifecycleService authorizationLifecycleService;
    private final EvePermissionRefreshService permissionRefreshService;
    private final EveStaticReferenceService staticReferenceService;
    private final SerenityEsiClient esiClient;
    private final RedissonClient redissonClient;
    private final EveDataFreshnessService dataFreshnessService;

    /** 分页查询当前军团仍有效的月矿时间线。 */
    public PageResp<EveMoonExtractionResp> page(int page, int size, String keyword, String timeline) {
        EveCorporationDO corporation = requireCurrentCorporation(UserContextHolder.getContext().getTenantId());
        LambdaQueryWrapper<EveMoonExtractionDO> query = new LambdaQueryWrapper<EveMoonExtractionDO>()
            .eq(EveMoonExtractionDO::getCorporationRefId, corporation.getId())
            .eq(EveMoonExtractionDO::getStatus, "ACTIVE")
            .eq(EveMoonExtractionDO::getDeleted, 0L)
            .orderByAsc(EveMoonExtractionDO::getChunkArrivalAt);
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            query.and(item -> item.like(EveMoonExtractionDO::getStructureName, value)
                .or()
                .like(EveMoonExtractionDO::getMoonName, value)
                .or()
                .like(EveMoonExtractionDO::getSolarSystemName, value)
                .or()
                .like(EveMoonExtractionDO::getStructureId, value)
                .or()
                .like(EveMoonExtractionDO::getMoonId, value));
        }
        if ("UPCOMING".equals(timeline)) {
            query.ge(EveMoonExtractionDO::getChunkArrivalAt, LocalDateTime.now(ZoneOffset.UTC));
        } else if ("ARRIVED".equals(timeline)) {
            query.le(EveMoonExtractionDO::getChunkArrivalAt, LocalDateTime.now(ZoneOffset.UTC));
        }
        Page<EveMoonExtractionDO> result = extractionMapper.selectPage(new Page<>(page, size), query);
        Map<Long, String> structureNotes = loadStructureNotes(corporation.getId(), result.getRecords());
        return new PageResp<>(result.getRecords()
            .stream()
            .map(item -> toResponse(item, structureNotes.get(item.getStructureId())))
            .toList(), result.getTotal());
    }

    /** 同步当前军团的完整月矿提取时间线。 */
    @Transactional(rollbackFor = Exception.class)
    public EveMoonExtractionSyncResp syncCurrentTenant() {
        Long tenantId = UserContextHolder.getContext().getTenantId();
        EveCorporationDO corporation = requireCurrentCorporation(tenantId);
        RLock lock = redissonClient.getLock("eve:serenity:moon-extraction-sync:" + tenantId + ":" + corporation
            .getId());
        boolean locked = false;
        boolean unlockDeferred = false;
        try {
            locked = lock.tryLock(1, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("月矿情报正在同步，请稍后重试");
            }
            unlockDeferred = EveAuthorizationLifecycleService.deferUnlockUntilTransactionCompletion(lock);
            EveMoonExtractionSyncResp response = synchronize(tenantId, corporation);
            dataFreshnessService.recordSuccess(tenantId, corporation
                .getId(), EveDataFreshnessService.MOON_EXTRACTIONS, response.synchronizedAt(), response
                    .sourceExpiresAt());
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("月矿情报正在同步，请稍后重试");
        } catch (RuntimeException e) {
            if (locked) {
                dataFreshnessService.recordFailure(tenantId, corporation
                    .getId(), EveDataFreshnessService.MOON_EXTRACTIONS, e);
            }
            throw e;
        } finally {
            if (locked && !unlockDeferred && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 由后台调度器同步指定军团的月矿提取时间线。 */
    @Transactional(rollbackFor = Exception.class)
    public EveMoonExtractionSyncResp syncForCorporation(Long tenantId, Long corporationRefId) {
        EveCorporationDO corporation = requireSyncCorporation(tenantId, corporationRefId);
        RLock lock = redissonClient.getLock("eve:serenity:moon-extraction-sync:" + tenantId + ":" + corporationRefId);
        boolean locked = false;
        boolean unlockDeferred = false;
        try {
            locked = lock.tryLock(1, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("月矿情报正在同步，请稍后重试");
            }
            unlockDeferred = EveAuthorizationLifecycleService.deferUnlockUntilTransactionCompletion(lock);
            EveMoonExtractionSyncResp response = synchronize(tenantId, corporation);
            dataFreshnessService
                .recordSuccess(tenantId, corporationRefId, EveDataFreshnessService.MOON_EXTRACTIONS, response
                    .synchronizedAt(), response.sourceExpiresAt());
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("月矿情报正在同步，请稍后重试");
        } catch (RuntimeException e) {
            if (locked) {
                dataFreshnessService
                    .recordFailure(tenantId, corporationRefId, EveDataFreshnessService.MOON_EXTRACTIONS, e);
            }
            throw e;
        } finally {
            if (locked && !unlockDeferred && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 保存月矿堡长期备注，并让同一座堡后续同步到的新一轮月矿继续展示该备注。 */
    @Transactional(rollbackFor = Exception.class)
    public EveMoonExtractionResp saveNote(Long extractionId, String note) {
        EveMoonExtractionDO extraction = requireActiveExtraction(extractionId);
        String normalizedNote = blankToNull(note);
        Long operatorId = UserContextHolder.getContext().getId();
        EveMoonStructureNoteDO structureNote = structureNoteMapper
            .selectOne(new LambdaQueryWrapper<EveMoonStructureNoteDO>()
                .eq(EveMoonStructureNoteDO::getTenantId, extraction.getTenantId())
                .eq(EveMoonStructureNoteDO::getCorporationRefId, extraction.getCorporationRefId())
                .eq(EveMoonStructureNoteDO::getStructureId, extraction.getStructureId())
                .eq(EveMoonStructureNoteDO::getDeleted, 0L));
        if (structureNote == null) {
            structureNote = new EveMoonStructureNoteDO();
            structureNote.setTenantId(extraction.getTenantId());
            structureNote.setCorporationRefId(extraction.getCorporationRefId());
            structureNote.setStructureId(extraction.getStructureId());
            structureNote.setNote(normalizedNote);
            structureNote.setCreateUser(operatorId);
            structureNote.setDeleted(0L);
            structureNoteMapper.insert(structureNote);
        } else {
            structureNote.setNote(normalizedNote);
            structureNote.setUpdateUser(operatorId);
            structureNoteMapper.updateById(structureNote);
        }
        return toResponse(extraction, normalizedNote);
    }

    /** 读取全部页面后才发布成功的国服快照。 */
    private EveMoonExtractionSyncResp synchronize(Long tenantId, EveCorporationDO corporation) {
        EveAuthorizationDO source = selectSource(tenantId);
        if (source == null) {
            throw new BusinessException("缺少可读取月矿情报的有效空间站管理员授权，请重新授权并刷新权限");
        }
        String accessToken = authorizationLifecycleService.ensureAccessToken(source);
        SerenityEsiPagedResponse<SerenityCorporationMoonExtractionResponse> firstPage = esiClient
            .getCorporationMoonExtractionsWithMetadata(corporation.getCorporationId(), 1, accessToken);
        if (firstPage.pageCount() > MAX_UPSTREAM_PAGES) {
            throw new BusinessException("国服月矿分页数量异常，已拒绝发布本次同步结果");
        }
        List<SerenityCorporationMoonExtractionResponse> sources = new ArrayList<>(firstPage.body());
        for (int page = 2; page <= firstPage.pageCount(); page++) {
            sources.addAll(esiClient.getCorporationMoonExtractionsWithMetadata(corporation
                .getCorporationId(), page, accessToken).body());
        }
        List<SerenityCorporationMoonExtractionResponse> valid = sources.stream()
            .filter(item -> item != null && item.structureId() != null && item.structureId() > 0 && item
                .moonId() != null && item.moonId() > 0 && item.extractionStartAt() != null && item
                    .chunkArrivalAt() != null && item.naturalDecayAt() != null)
            .collect(Collectors.collectingAndThen(Collectors.toMap(EveMoonExtractionService::sourceKey, item -> item, (
                                                                                                                       left,
                                                                                                                       right) -> right), values -> new ArrayList<>(values
                                                                                                                           .values())));
        LocalDateTime observedAt = LocalDateTime.now(ZoneOffset.UTC);
        publish(tenantId, corporation, valid, observedAt, firstPage.expiresAt());
        return new EveMoonExtractionSyncResp(valid.size(), firstPage.pageCount(), observedAt, firstPage.expiresAt());
    }

    /** 原子替换国服月矿时间线。 */
    private void publish(Long tenantId,
                         EveCorporationDO corporation,
                         List<SerenityCorporationMoonExtractionResponse> sources,
                         LocalDateTime observedAt,
                         LocalDateTime sourceExpiresAt) {
        Map<String, EveMoonExtractionDO> existing = extractionMapper
            .selectList(new LambdaQueryWrapper<EveMoonExtractionDO>()
                .eq(EveMoonExtractionDO::getCorporationRefId, corporation.getId())
                .eq(EveMoonExtractionDO::getDeleted, 0L))
            .stream()
            .collect(Collectors.toMap(EveMoonExtractionService::storedKey, item -> item, (left, right) -> left));
        Map<Long, EveCorporationStructureDO> structures = structureMapper
            .selectList(new LambdaQueryWrapper<EveCorporationStructureDO>()
                .eq(EveCorporationStructureDO::getCorporationRefId, corporation.getId())
                .eq(EveCorporationStructureDO::getStatus, "ACTIVE")
                .eq(EveCorporationStructureDO::getDeleted, 0L))
            .stream()
            .collect(Collectors.toMap(EveCorporationStructureDO::getStructureId, item -> item, (left, right) -> left));
        Map<Long, SerenityUniverseMoonResponse> moons = resolveMoons(sources);
        Set<String> seen = new HashSet<>();
        for (SerenityCorporationMoonExtractionResponse source : sources) {
            String key = sourceKey(source);
            seen.add(key);
            EveMoonExtractionDO target = existing.remove(key);
            if (target == null) {
                target = new EveMoonExtractionDO();
                target.setTenantId(tenantId);
                target.setCorporationRefId(corporation.getId());
                target.setStructureId(source.structureId());
                target.setExtractionStartAt(source.extractionStartAt());
                target.setCreateUser(1L);
                target.setDeleted(0L);
            }
            EveCorporationStructureDO structure = structures.get(source.structureId());
            SerenityUniverseMoonResponse moon = moons.get(source.moonId());
            Long solarSystemId = moon == null
                ? structure == null ? null : structure.getSolarSystemId()
                : moon.solarSystemId();
            EveStaticLocationReferenceDO system = staticReferenceService.findLocation("SOLAR_SYSTEM", solarSystemId);
            target.setStructureName(structure == null ? null : structure.getStructureName());
            target.setStructureTypeName(structure == null ? null : structure.getTypeName());
            target.setMoonId(source.moonId());
            target.setMoonName(moon == null ? null : moon.name());
            target.setSolarSystemId(solarSystemId);
            target.setSolarSystemName(system == null
                ? structure == null ? null : structure.getSolarSystemName()
                : system.getReferenceName());
            target.setChunkArrivalAt(source.chunkArrivalAt());
            target.setNaturalDecayAt(source.naturalDecayAt());
            target.setStatus("ACTIVE");
            target.setLastSeenAt(observedAt);
            target.setSourceExpiresAt(sourceExpiresAt);
            if (target.getId() == null) {
                extractionMapper.insert(target);
            } else {
                extractionMapper.updateById(target);
            }
        }
        existing.values().forEach(item -> {
            item.setStatus("MISSING");
            extractionMapper.updateById(item);
        });
    }

    /** 公开月球名称失败不影响已验证的军团时间线发布。 */
    private Map<Long, SerenityUniverseMoonResponse> resolveMoons(Collection<SerenityCorporationMoonExtractionResponse> sources) {
        Map<Long, SerenityUniverseMoonResponse> result = new HashMap<>();
        sources.stream().map(SerenityCorporationMoonExtractionResponse::moonId).distinct().forEach(moonId -> {
            try {
                result.put(moonId, esiClient.getUniverseMoon(moonId));
            } catch (SerenityEsiClientException ignored) {
                // 月球公开名称是展示增强，国服响应中的时间线仍应安全发布。
            }
        });
        return result;
    }

    /** 选择拥有月矿 Scope 和空间站管理员资格的国服数据源。 */
    private EveAuthorizationDO selectSource(Long tenantId) {
        List<EveAuthorizationDO> candidates = authorizationMapper.selectTenantCandidates(tenantId)
            .stream()
            .filter(item -> EveAuthorizationStatus.ACTIVE.equals(item.getStatus()))
            .filter(item -> item.getScopes() != null && item.getScopes().contains(EXTRACTION_SCOPE))
            .toList();
        for (EveAuthorizationDO candidate : candidates) {
            if (hasExtractionRole(tenantId, candidate)) {
                return candidate;
            }
            permissionRefreshService.reviewAuthorization(candidate);
            if (hasExtractionRole(tenantId, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** CEO 或空间站管理员角色可作为月矿数据源，严格不以本站角色替代游戏权限。 */
    private boolean hasExtractionRole(Long tenantId, EveAuthorizationDO authorization) {
        EveCharacterRoleSnapshotDO snapshot = roleSnapshotMapper.selectLatest(tenantId, authorization
            .getCharacterRefId());
        return snapshot != null && Objects.equals(tenantId, snapshot.getTenantId()) && Objects.equals(authorization
            .getCharacterRefId(), snapshot.getCharacterRefId()) && snapshot.getSourceExpiresAt() != null && snapshot
                .getSourceExpiresAt()
                .isAfter(LocalDateTime.now(ZoneOffset.UTC)) && (Boolean.TRUE.equals(snapshot.getIsCeo()) || snapshot
                    .getRoles() != null && snapshot.getRoles().contains("Station_Manager"));
    }

    /** 由当前会话定位军团绑定，客户端不得提交可猜测的军团标识。 */
    private EveCorporationDO requireCurrentCorporation(Long tenantId) {
        EveMeContextResp context = contextService.getCurrentContext();
        if (context.corporation() == null) {
            throw new BusinessException("当前账号尚未加入已认领军团");
        }
        EveCorporationDO corporation = corporationMapper.selectByTenantAndCorporationId(tenantId, context.corporation()
            .corporationId());
        if (corporation == null || !Objects.equals(tenantId, corporation.getTenantId())) {
            throw new BusinessException("当前军团租户数据不可用");
        }
        return corporation;
    }

    /** 验证持久化任务中的军团目标仍属于有效租户。 */
    private EveCorporationDO requireSyncCorporation(Long tenantId, Long corporationRefId) {
        EveCorporationDO corporation = corporationMapper.selectActiveByTenantAndRefId(tenantId, corporationRefId);
        if (corporation == null) {
            throw new BusinessException("自动同步目标军团已不可用");
        }
        return corporation;
    }

    /** 确保当前会话只能维护本军团仍有效的月矿记录。 */
    private EveMoonExtractionDO requireActiveExtraction(Long extractionId) {
        EveMoonExtractionDO extraction = extractionMapper.selectById(extractionId);
        Long tenantId = UserContextHolder.getContext().getTenantId();
        EveCorporationDO corporation = requireCurrentCorporation(tenantId);
        if (extraction == null || !Objects.equals(tenantId, extraction.getTenantId()) || !Objects.equals(corporation
            .getId(), extraction.getCorporationRefId()) || !"ACTIVE".equals(extraction.getStatus()) || !Objects
                .equals(extraction.getDeleted(), 0L)) {
            throw new BusinessException("月矿时间线不存在或已失效");
        }
        return extraction;
    }

    /** 批量读取当前分页涉及的月矿堡备注，避免每条月矿记录额外查询一次。 */
    private Map<Long, String> loadStructureNotes(Long corporationRefId, List<EveMoonExtractionDO> extractions) {
        Set<Long> structureIds = extractions.stream()
            .map(EveMoonExtractionDO::getStructureId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (structureIds.isEmpty()) {
            return Map.of();
        }
        Long tenantId = UserContextHolder.getContext().getTenantId();
        return structureNoteMapper.selectList(new LambdaQueryWrapper<EveMoonStructureNoteDO>()
            .eq(EveMoonStructureNoteDO::getTenantId, tenantId)
            .eq(EveMoonStructureNoteDO::getCorporationRefId, corporationRefId)
            .in(EveMoonStructureNoteDO::getStructureId, structureIds)
            .eq(EveMoonStructureNoteDO::getDeleted, 0L))
            .stream()
            .collect(Collectors.toMap(EveMoonStructureNoteDO::getStructureId, EveMoonStructureNoteDO::getNote, (left,
                                                                                                                right) -> left));
    }

    /** 转换单条国服月矿快照，并携带同一月矿堡长期备注。 */
    private static EveMoonExtractionResp toResponse(EveMoonExtractionDO extraction, String structureNote) {
        return new EveMoonExtractionResp(extraction.getId(), extraction.getStructureId(), extraction
            .getStructureName(), extraction.getStructureTypeName(), extraction.getMoonId(), extraction
                .getMoonName(), extraction.getSolarSystemId(), extraction.getSolarSystemName(), extraction
                    .getExtractionStartAt(), extraction.getChunkArrivalAt(), extraction.getNaturalDecayAt(), extraction
                        .getStatus(), extraction.getLastSeenAt(), extraction.getSourceExpiresAt(), extraction
                            .getNote(), structureNote);
    }

    /** 月矿时间线的上游唯一键。 */
    private static String sourceKey(SerenityCorporationMoonExtractionResponse source) {
        return source.structureId() + ":" + source.extractionStartAt();
    }

    /** 本地快照对应的唯一键。 */
    private static String storedKey(EveMoonExtractionDO source) {
        return source.getStructureId() + ":" + source.getExtractionStartAt();
    }

    /** 将空白备注统一持久化为空值。 */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
