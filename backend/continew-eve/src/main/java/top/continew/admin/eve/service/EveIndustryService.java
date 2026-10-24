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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.mapper.EveIndustryBlueprintMapper;
import top.continew.admin.eve.mapper.EveIndustryBlueprintOverrideMapper;
import top.continew.admin.eve.mapper.EveIndustryConfigMapper;
import top.continew.admin.eve.mapper.EveIndustryFacilityProfileMapper;
import top.continew.admin.eve.mapper.EveIndustryGoldenCaseMapper;
import top.continew.admin.eve.mapper.EveIndustryOpportunityMapper;
import top.continew.admin.eve.mapper.EveIndustryOpportunityMaterialMapper;
import top.continew.admin.eve.mapper.EveIndustryMarketInputSnapshotMapper;
import top.continew.admin.eve.mapper.EveIndustryMarketOrderSliceMapper;
import top.continew.admin.eve.mapper.EveIndustryPlanMapper;
import top.continew.admin.eve.mapper.EveIndustryPlanStepMapper;
import top.continew.admin.eve.mapper.EveIndustrySystemSnapshotMapper;
import top.continew.admin.eve.mapper.EveIndustryStaticVersionMapper;
import top.continew.admin.eve.mapper.EveIndustrySystemCostIndexMapper;
import top.continew.admin.eve.mapper.EveMarketSnapshotMapper;
import top.continew.admin.eve.mapper.EveStaticTypeReferenceMapper;
import top.continew.admin.eve.client.SerenityEsiClient;
import top.continew.admin.eve.client.CeveIndustryClient;
import top.continew.admin.eve.model.EveIndustryBlueprintOverrideReq;
import top.continew.admin.eve.model.EveIndustryBlueprintBatchReq;
import top.continew.admin.eve.model.EveIndustryBlueprintReq;
import top.continew.admin.eve.model.EveIndustryCalculationMaterialResp;
import top.continew.admin.eve.model.EveIndustryCalculationReq;
import top.continew.admin.eve.model.EveIndustryCalculationResp;
import top.continew.admin.eve.model.EveIndustryConfigReq;
import top.continew.admin.eve.model.EveIndustryDataStatusResp;
import top.continew.admin.eve.model.EveIndustryFacilityReq;
import top.continew.admin.eve.model.EveIndustryGoldenCaseReq;
import top.continew.admin.eve.model.EveIndustryGoldenCaseResp;
import top.continew.admin.eve.model.EveIndustryGoldenMaterial;
import top.continew.admin.eve.model.EveIndustryMaterial;
import top.continew.admin.eve.model.EveIndustryOpportunityResp;
import top.continew.admin.eve.model.EveIndustryScanReq;
import top.continew.admin.eve.model.EveIndustryPlanCreateReq;
import top.continew.admin.eve.model.EveIndustryPlanResp;
import top.continew.admin.eve.model.EveIndustryPlanStatusReq;
import top.continew.admin.eve.model.EveIndustryStaticActivityInput;
import top.continew.admin.eve.model.EveIndustryStaticImportError;
import top.continew.admin.eve.model.EveIndustryStaticImportResp;
import top.continew.admin.eve.model.EveMarketHistoryResp;
import top.continew.admin.eve.model.EveMarketOrderResp;
import top.continew.admin.eve.model.entity.EveIndustryBlueprintDO;
import top.continew.admin.eve.model.entity.EveIndustryBlueprintOverrideDO;
import top.continew.admin.eve.model.entity.EveIndustryConfigDO;
import top.continew.admin.eve.model.entity.EveIndustryFacilityProfileDO;
import top.continew.admin.eve.model.entity.EveIndustryGoldenCaseDO;
import top.continew.admin.eve.model.entity.EveIndustryOpportunityDO;
import top.continew.admin.eve.model.entity.EveIndustryOpportunityMaterialDO;
import top.continew.admin.eve.model.entity.EveIndustryPlanDO;
import top.continew.admin.eve.model.entity.EveIndustryPlanStepDO;
import top.continew.admin.eve.model.entity.EveIndustryMarketInputSnapshotDO;
import top.continew.admin.eve.model.entity.EveIndustryMarketOrderSliceDO;
import top.continew.admin.eve.model.entity.EveIndustrySystemSnapshotDO;
import top.continew.admin.eve.model.entity.EveIndustryStaticVersionDO;
import top.continew.admin.eve.model.entity.EveIndustrySystemCostIndexDO;
import top.continew.admin.eve.model.entity.EveMarketSnapshotDO;
import top.continew.admin.eve.model.entity.EveStaticTypeReferenceDO;
import top.continew.admin.eve.model.serenity.SerenityIndustrySystemResponse;
import top.continew.admin.eve.model.serenity.SerenityMarketPriceResponse;
import top.continew.admin.eve.model.serenity.SerenityEsiResponse;
import top.continew.starter.core.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 提供工业配置、静态蓝图和吉他市场利润机会计算。 */
@Service
@RequiredArgsConstructor
public class EveIndustryService {

    private static final BigDecimal DEFAULT_SKILL_MULTIPLIER = BigDecimal.ONE;
    private static final BigDecimal DEFAULT_FEE = BigDecimal.ZERO;
    private static final int DEFAULT_LINES = 1;
    private static final int MAX_STATIC_IMPORT_ACTIVITIES = 100_000;
    private static final String INDUSTRY_RULE_VERSION = "SERENITY-CEVE-1";
    private static final Set<String> STATIC_SOURCES = Set
        .of("SERENITY_EXPORT", "TRANQUILITY_SDE", "CEVE_INDUSTRY_PUBLIC");
    private static final Set<String> EXECUTABLE_SOURCES = Set.of("SERENITY_EXPORT", "CEVE_INDUSTRY_PUBLIC");
    private static final Set<String> FORMAL_ACTIVITIES = Set.of("MANUFACTURING", "REACTION");

    private final EveIndustryStaticVersionMapper staticVersionMapper;
    private final EveIndustryBlueprintMapper blueprintMapper;
    private final EveIndustryConfigMapper configMapper;
    private final EveIndustryFacilityProfileMapper facilityMapper;
    private final EveIndustrySystemCostIndexMapper systemCostIndexMapper;
    private final EveIndustryBlueprintOverrideMapper overrideMapper;
    private final EveIndustryGoldenCaseMapper goldenCaseMapper;
    private final EveIndustryOpportunityMapper opportunityMapper;
    private final EveIndustryOpportunityMaterialMapper opportunityMaterialMapper;
    private final EveIndustryMarketInputSnapshotMapper marketInputSnapshotMapper;
    private final EveIndustryMarketOrderSliceMapper marketOrderSliceMapper;
    private final EveIndustryPlanMapper planMapper;
    private final EveIndustryPlanStepMapper planStepMapper;
    private final EveIndustrySystemSnapshotMapper systemSnapshotMapper;
    private final EveMarketSnapshotMapper marketSnapshotMapper;
    private final EveStaticTypeReferenceMapper typeReferenceMapper;
    private final EveMarketService marketService;
    private final SerenityEsiClient esiClient;
    private final CeveIndustryClient ceveIndustryClient;
    private final ObjectMapper objectMapper;

    /** 查询当前租户已启用的生产配置。 */
    public List<EveIndustryConfigDO> configs() {
        return configMapper.selectList(new LambdaQueryWrapper<EveIndustryConfigDO>()
            .eq(EveIndustryConfigDO::getTenantId, currentTenantId())
            .eq(EveIndustryConfigDO::getEnabled, true)
            .orderByAsc(EveIndustryConfigDO::getName));
    }

    /** 保存或更新生产配置。 */
    @Transactional(rollbackFor = Exception.class)
    public EveIndustryConfigDO saveConfig(Long id, EveIndustryConfigReq req) {
        EveIndustryConfigDO entity = id == null
            ? new EveIndustryConfigDO()
            : configMapper.selectOne(new LambdaQueryWrapper<EveIndustryConfigDO>().eq(EveIndustryConfigDO::getId, id)
                .eq(EveIndustryConfigDO::getTenantId, currentTenantId()));
        if (entity == null) {
            throw new BusinessException("工业配置不存在");
        }
        entity.setName(req.name());
        entity.setProductionSkillJson(defaultJson(req.productionSkillJson(), "{}"));
        entity.setMarketFeeJson(defaultJson(req.marketFeeJson(), "{}"));
        entity.setTargetDays(req.targetDays());
        entity.setMarketShareLimit(req.marketShareLimit());
        entity.setFreshnessPolicyJson(defaultJson(req.freshnessPolicyJson(), "{}"));
        entity.setVersion(entity.getVersion() == null ? 1 : entity.getVersion() + (id == null ? 0 : 1));
        entity.setEnabled(true);
        entity.setTenantId(currentTenantId());
        if (id == null) {
            configMapper.insert(entity);
        } else {
            configMapper.updateById(entity);
        }
        return entity;
    }

    /** 查询当前租户已启用的生产设施。 */
    public List<EveIndustryFacilityProfileDO> facilities() {
        return facilityMapper.selectList(new LambdaQueryWrapper<EveIndustryFacilityProfileDO>()
            .eq(EveIndustryFacilityProfileDO::getTenantId, currentTenantId())
            .eq(EveIndustryFacilityProfileDO::getEnabled, true)
            .orderByAsc(EveIndustryFacilityProfileDO::getName));
    }

    /** 保存或更新生产设施配置。 */
    @Transactional(rollbackFor = Exception.class)
    public EveIndustryFacilityProfileDO saveFacility(Long id, EveIndustryFacilityReq req) {
        EveIndustryFacilityProfileDO entity = id == null
            ? new EveIndustryFacilityProfileDO()
            : facilityMapper.selectOne(new LambdaQueryWrapper<EveIndustryFacilityProfileDO>()
                .eq(EveIndustryFacilityProfileDO::getId, id)
                .eq(EveIndustryFacilityProfileDO::getTenantId, currentTenantId()));
        if (entity == null) {
            throw new BusinessException("工业设施配置不存在");
        }
        entity.setName(req.name());
        entity.setRealityType(req.realityType());
        entity.setSystemId(req.systemId());
        entity.setSystemName(req.systemName());
        entity.setSecurityStatus(req.securityStatus());
        entity.setFacilityTypeId(req.facilityTypeId());
        entity.setFacilityName(req.facilityName());
        entity.setSupportedActivitiesJson(defaultJson(req.supportedActivitiesJson(), "[\"MANUFACTURING\"]"));
        entity.setOnlineServicesJson(defaultJson(req.onlineServicesJson(), "[]"));
        entity.setRigTypeIdsJson(defaultJson(req.rigTypeIdsJson(), "[]"));
        entity.setFacilityTax(req.facilityTax());
        entity.setVersionedSurcharge(req.versionedSurcharge());
        entity.setMaterialMultiplier(req.materialMultiplier());
        entity.setTimeMultiplier(req.timeMultiplier());
        entity.setCostMultiplier(req.costMultiplier());
        entity.setManufacturingSci(req.manufacturingSci());
        entity.setReactionSci(req.reactionSci());
        entity.setSource(req.source() == null || req.source().isBlank() ? "MANUAL" : req.source());
        entity.setConfirmedAt(LocalDateTime.now());
        entity.setEnabled(true);
        entity.setTenantId(currentTenantId());
        if (id == null) {
            facilityMapper.insert(entity);
        } else {
            facilityMapper.updateById(entity);
        }
        return entity;
    }

    /** 从国服 ESI 更新一个已保存设施所在星系的制造和反应 SCI。 */
    @Transactional(rollbackFor = Exception.class)
    public EveIndustryFacilityProfileDO synchronizeFacilitySci(Long id) {
        EveIndustryFacilityProfileDO facility = facilityMapper
            .selectOne(new LambdaQueryWrapper<EveIndustryFacilityProfileDO>()
                .eq(EveIndustryFacilityProfileDO::getId, id)
                .eq(EveIndustryFacilityProfileDO::getTenantId, currentTenantId()));
        if (facility == null) {
            throw new BusinessException("工业设施配置不存在");
        }
        SerenityEsiResponse<List<SerenityIndustrySystemResponse>> response = esiClient.getIndustrySystemsWithMetadata();
        SerenityIndustrySystemResponse system = response.body()
            .stream()
            .filter(item -> facility.getSystemId().equals(item.solarSystemId()))
            .findFirst()
            .orElseThrow(() -> new BusinessException("国服未返回该星系的工业成本指数"));
        Map<String, BigDecimal> indices = new HashMap<>();
        if (system.costIndices() != null) {
            system.costIndices().forEach(item -> {
                indices.put(item.activity(), item.costIndex());
                upsertSystemCostIndex(system.solarSystemId(), item.activity(), item.costIndex(), response);
            });
        }
        facility.setManufacturingSci(indices.get("manufacturing"));
        facility.setReactionSci(indices.get("reaction"));
        facility.setSource("ESI");
        facility.setConfirmedAt(LocalDateTime.now());
        facilityMapper.updateById(facility);
        return facility;
    }

    /** 将国服 ESI 返回的 SCI 与缓存元数据保存为可追溯的最新快照。 */
    private void upsertSystemCostIndex(Long systemId,
                                       String activity,
                                       BigDecimal costIndex,
                                       SerenityEsiResponse<?> response) {
        EveIndustrySystemCostIndexDO entity = systemCostIndexMapper
            .selectOne(new LambdaQueryWrapper<EveIndustrySystemCostIndexDO>()
                .eq(EveIndustrySystemCostIndexDO::getSystemId, systemId)
                .eq(EveIndustrySystemCostIndexDO::getActivity, activity));
        boolean create = entity == null;
        if (create) {
            entity = new EveIndustrySystemCostIndexDO();
            entity.setSystemId(systemId);
            entity.setActivity(activity);
        }
        entity.setCostIndex(costIndex);
        entity.setSource("SERENITY_ESI");
        entity.setEtag(response.etag());
        entity.setFetchedAt(LocalDateTime.now());
        entity.setExpiresAt(response.expiresAt());
        if (create) {
            systemCostIndexMapper.insert(entity);
        } else {
            systemCostIndexMapper.updateById(entity);
        }
    }

    /** 查询当前租户的蓝图研究等级覆盖。 */
    public List<EveIndustryBlueprintOverrideDO> blueprintOverrides() {
        return overrideMapper.selectList(new LambdaQueryWrapper<EveIndustryBlueprintOverrideDO>()
            .eq(EveIndustryBlueprintOverrideDO::getTenantId, currentTenantId())
            .orderByAsc(EveIndustryBlueprintOverrideDO::getBlueprintTypeId));
    }

    /** 保存蓝图研究等级覆盖。 */
    @Transactional(rollbackFor = Exception.class)
    public EveIndustryBlueprintOverrideDO saveBlueprintOverride(Long id, EveIndustryBlueprintOverrideReq req) {
        if ("BPC".equals(req.blueprintKind()) && (req.remainingRuns() == null || req.remainingRuns() < 1)) {
            throw new BusinessException("蓝图拷贝的剩余生产次数必须大于 0");
        }
        EveIndustryBlueprintOverrideDO entity = id == null
            ? new EveIndustryBlueprintOverrideDO()
            : overrideMapper.selectOne(new LambdaQueryWrapper<EveIndustryBlueprintOverrideDO>()
                .eq(EveIndustryBlueprintOverrideDO::getId, id)
                .eq(EveIndustryBlueprintOverrideDO::getTenantId, currentTenantId()));
        if (entity == null) {
            throw new BusinessException("蓝图配置不存在");
        }
        entity.setBlueprintTypeId(req.blueprintTypeId());
        entity.setBlueprintKind(req.blueprintKind());
        entity.setMaterialEfficiency(Math.max(0, req.materialEfficiency()));
        entity.setTimeEfficiency(Math.max(0, req.timeEfficiency()));
        entity.setRemainingRuns(req.remainingRuns());
        entity.setSource(req.source() == null || req.source().isBlank() ? "MANUAL" : req.source());
        entity.setVersion(entity.getVersion() == null ? 1 : entity.getVersion() + (id == null ? 0 : 1));
        entity.setTenantId(currentTenantId());
        if (id == null) {
            overrideMapper.insert(entity);
        } else {
            overrideMapper.updateById(entity);
        }
        return entity;
    }

    /** 创建静态数据版本，供后续国服导入器绑定蓝图。 */
    @Transactional(rollbackFor = Exception.class)
    public EveIndustryStaticVersionDO createStaticVersion(String source,
                                                          String serverVersion,
                                                          Long buildNumber,
                                                          String sha256,
                                                          String schemaVersion) {
        EveIndustryStaticVersionDO version = new EveIndustryStaticVersionDO();
        version.setSource(source);
        version.setServerVersion(serverVersion);
        version.setBuildNumber(buildNumber);
        version.setDownloadedAt(LocalDateTime.now());
        version.setSha256(sha256);
        version.setSchemaVersion(schemaVersion);
        version.setValidationStatus("PENDING");
        staticVersionMapper.insert(version);
        return version;
    }

    /** 导入一项带来源的蓝图活动规则；不允许无版本的孤儿配方。 */
    @Transactional(rollbackFor = Exception.class)
    public EveIndustryBlueprintDO importBlueprint(EveIndustryBlueprintReq req) {
        EveIndustryStaticVersionDO version = staticVersionMapper.selectById(req.staticVersionId());
        if (version == null) {
            throw new BusinessException("静态数据版本不存在");
        }
        EveIndustryBlueprintDO entity = new EveIndustryBlueprintDO();
        entity.setStaticVersionId(req.staticVersionId());
        entity.setBlueprintTypeId(req.blueprintTypeId());
        entity.setBlueprintName(req.blueprintName());
        entity.setActivity(req.activity());
        entity.setProductTypeId(req.productTypeId());
        entity.setProductName(req.productName());
        entity.setProductQuantity(req.productQuantity());
        entity.setBaseTimeSeconds(req.baseTimeSeconds());
        entity.setProbability(req.probability());
        entity.setMaterialsJson(req.materialsJson());
        entity.setProductsJson(req.productsJson());
        entity.setSource(req.source());
        entity.setValidationStatus(req.validationStatus());
        entity.setSupported(req.supported());
        blueprintMapper.insert(entity);
        return entity;
    }

    /** 以有限批次导入规范化蓝图，避免单次请求无限占用内存和事务。 */
    @Transactional(rollbackFor = Exception.class)
    public int importBlueprints(EveIndustryBlueprintBatchReq req) {
        for (EveIndustryBlueprintReq activity : req.activities()) {
            importBlueprint(activity);
        }
        return req.activities().size();
    }

    /** 查询最近导入的静态工业数据版本。 */
    public List<EveIndustryStaticVersionDO> staticVersions() {
        return staticVersionMapper.selectList(new LambdaQueryWrapper<EveIndustryStaticVersionDO>()
            .orderByDesc(EveIndustryStaticVersionDO::getDownloadedAt)
            .last("LIMIT 100"));
    }

    /** 查询指定版本或全部版本的蓝图活动，供数据校验页面选择。 */
    public List<EveIndustryBlueprintDO> blueprints(Long staticVersionId) {
        LambdaQueryWrapper<EveIndustryBlueprintDO> query = new LambdaQueryWrapper<EveIndustryBlueprintDO>()
            .orderByAsc(EveIndustryBlueprintDO::getProductName)
            .last("LIMIT 2000");
        if (staticVersionId != null) {
            query.eq(EveIndustryBlueprintDO::getStaticVersionId, staticVersionId);
        }
        return blueprintMapper.selectList(query);
    }

    /**
     * 导入规范化 JSON 或 JSONL 工业静态数据。
     *
     * <p>整份文件先解析并校验，通过后才创建版本和蓝图记录。手工上传数据默认保留待验证状态；
     * 公开工业工具同步则走独立的已验证来源流程。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public EveIndustryStaticImportResp importStaticData(String sourceFileName,
                                                        byte[] content,
                                                        String source,
                                                        String serverVersion,
                                                        Long buildNumber,
                                                        String schemaVersion) {
        if (content == null || content.length == 0) {
            throw new BusinessException("工业静态数据文件不能为空");
        }
        String normalizedSource = source == null ? "" : source.trim().toUpperCase();
        if (!STATIC_SOURCES.contains(normalizedSource)) {
            throw new BusinessException("工业静态数据来源只允许 SERENITY_EXPORT、TRANQUILITY_SDE 或 CEVE_INDUSTRY_PUBLIC");
        }
        if ("SERENITY_EXPORT".equals(normalizedSource) && (serverVersion == null || serverVersion.isBlank())) {
            throw new BusinessException("导入国服数据时必须填写国服游戏版本");
        }
        String sha256 = sha256(content);
        EveIndustryStaticVersionDO duplicate = staticVersionMapper
            .selectOne(new LambdaQueryWrapper<EveIndustryStaticVersionDO>()
                .eq(EveIndustryStaticVersionDO::getSource, normalizedSource)
                .eq(EveIndustryStaticVersionDO::getSha256, sha256)
                .last("LIMIT 1"));
        if (duplicate != null) {
            long count = blueprintMapper.selectCount(new LambdaQueryWrapper<EveIndustryBlueprintDO>()
                .eq(EveIndustryBlueprintDO::getStaticVersionId, duplicate.getId()));
            return new EveIndustryStaticImportResp(duplicate.getId(), sourceFileName, sha256, (int)count, false, List
                .of());
        }
        ParsedStaticActivities parsed = parseStaticActivities(sourceFileName, content);
        List<EveIndustryStaticImportError> errors = new ArrayList<>(parsed.errors());
        errors.addAll(validateStaticActivities(parsed.activities()));
        if (!errors.isEmpty()) {
            return new EveIndustryStaticImportResp(null, sourceFileName, sha256, parsed.activities()
                .size(), false, errors);
        }
        EveIndustryStaticVersionDO version = createStaticVersion(normalizedSource, serverVersion, buildNumber, sha256, schemaVersion == null || schemaVersion
            .isBlank() ? "eve-industry-normalized-v1" : schemaVersion);
        for (IndexedActivity indexed : parsed.activities()) {
            EveIndustryStaticActivityInput activity = indexed.activity();
            EveIndustryBlueprintDO entity = new EveIndustryBlueprintDO();
            entity.setStaticVersionId(version.getId());
            entity.setBlueprintTypeId(activity.blueprintTypeId());
            entity.setBlueprintName(resolveTypeName(activity.blueprintTypeId(), activity.blueprintName()));
            entity.setActivity(activity.activity().trim().toUpperCase());
            entity.setProductTypeId(activity.productTypeId());
            entity.setProductName(resolveTypeName(activity.productTypeId(), activity.productName()));
            entity.setProductQuantity(activity.productQuantity());
            entity.setBaseTimeSeconds(activity.baseTimeSeconds());
            entity.setProbability(activity.probability());
            entity.setMaterialsJson(writeJson(activity.materials()));
            entity.setProductsJson(writeJson(activity.products() == null ? List.of() : activity.products()));
            entity.setSource(normalizedSource);
            entity.setValidationStatus("PENDING");
            entity.setSupported(false);
            blueprintMapper.insert(entity);
        }
        return new EveIndustryStaticImportResp(version.getId(), sourceFileName, sha256, parsed.activities()
            .size(), true, List.of());
    }

    /** 同步 ceve-industry.cn 的公开蓝图，并按网站规则直接启用制造和反应活动。 */
    @Transactional(rollbackFor = Exception.class)
    public EveIndustryStaticImportResp synchronizeCevePublicBlueprints() {
        JsonNode rawBlueprints = ceveIndustryClient.getAllBlueprints();
        String sha256 = sha256(rawBlueprints.toString().getBytes(StandardCharsets.UTF_8));
        EveIndustryStaticVersionDO duplicate = staticVersionMapper
            .selectOne(new LambdaQueryWrapper<EveIndustryStaticVersionDO>()
                .eq(EveIndustryStaticVersionDO::getSource, "CEVE_INDUSTRY_PUBLIC")
                .eq(EveIndustryStaticVersionDO::getSha256, sha256)
                .last("LIMIT 1"));
        if (duplicate != null) {
            long count = blueprintMapper.selectCount(new LambdaQueryWrapper<EveIndustryBlueprintDO>()
                .eq(EveIndustryBlueprintDO::getStaticVersionId, duplicate.getId()));
            return new EveIndustryStaticImportResp(duplicate
                .getId(), "ceve-allBluePrints.json", sha256, (int)count, false, List.of());
        }
        List<EveIndustryStaticActivityInput> activities = parseCeveBlueprints(rawBlueprints);
        List<IndexedActivity> indexed = new ArrayList<>();
        for (int index = 0; index < activities.size(); index++) {
            indexed.add(new IndexedActivity(index + 1, activities.get(index)));
        }
        List<EveIndustryStaticImportError> errors = validateStaticActivities(indexed);
        if (!errors.isEmpty()) {
            return new EveIndustryStaticImportResp(null, "ceve-allBluePrints.json", sha256, activities
                .size(), false, errors);
        }
        EveIndustryStaticVersionDO version = createStaticVersion("CEVE_INDUSTRY_PUBLIC", "ceve-industry.cn", null, sha256, "ceve-industry-public-v1");
        version.setValidationStatus("VALIDATED");
        version.setValidationNote("按 ceve-industry.cn 公开蓝图与公式同步");
        staticVersionMapper.updateById(version);
        for (EveIndustryStaticActivityInput activity : activities) {
            EveIndustryBlueprintDO entity = new EveIndustryBlueprintDO();
            entity.setStaticVersionId(version.getId());
            entity.setBlueprintTypeId(activity.blueprintTypeId());
            entity.setBlueprintName(activity.blueprintName());
            entity.setActivity(activity.activity());
            entity.setProductTypeId(activity.productTypeId());
            entity.setProductName(activity.productName());
            entity.setProductQuantity(activity.productQuantity());
            entity.setBaseTimeSeconds(activity.baseTimeSeconds());
            entity.setProbability(activity.probability());
            entity.setMaterialsJson(writeJson(activity.materials()));
            entity.setProductsJson(writeJson(activity.products()));
            entity.setSource("CEVE_INDUSTRY_PUBLIC");
            entity.setValidationStatus("VALIDATED");
            entity.setSupported(FORMAL_ACTIVITIES.contains(activity.activity()));
            blueprintMapper.insert(entity);
        }
        return new EveIndustryStaticImportResp(version.getId(), "ceve-allBluePrints.json", sha256, activities
            .size(), true, List.of());
    }

    /** 查询当前租户的 Golden Case 与最近校验结果。 */
    public List<EveIndustryGoldenCaseResp> goldenCases() {
        List<EveIndustryGoldenCaseDO> cases = goldenCaseMapper
            .selectList(new LambdaQueryWrapper<EveIndustryGoldenCaseDO>()
                .eq(EveIndustryGoldenCaseDO::getTenantId, currentTenantId())
                .orderByDesc(EveIndustryGoldenCaseDO::getValidatedAt)
                .orderByDesc(EveIndustryGoldenCaseDO::getId));
        return cases.stream().map(this::toGoldenCaseResp).toList();
    }

    /** 保存国服游戏内实际任务样例并立即运行公式对账。 */
    @Transactional(rollbackFor = Exception.class)
    public EveIndustryGoldenCaseResp createGoldenCase(EveIndustryGoldenCaseReq req) {
        EveIndustryBlueprintDO blueprint = blueprintMapper.selectById(req.blueprintId());
        EveIndustryConfigDO config = configMapper.selectById(req.configId());
        EveIndustryFacilityProfileDO facility = facilityMapper.selectById(req.facilityId());
        if (blueprint == null || config == null || facility == null) {
            throw new BusinessException("游戏内对照记录关联的蓝图、生产配置或设施不存在");
        }
        if (!currentTenantId().equals(config.getTenantId()) || !currentTenantId().equals(facility.getTenantId())) {
            throw new BusinessException("游戏内对照记录关联的生产配置或设施不属于当前军团");
        }
        BigDecimal sci = "REACTION".equals(blueprint.getActivity())
            ? facility.getReactionSci()
            : facility.getManufacturingSci();
        if (sci == null) {
            throw new BusinessException("核对游戏内数据前，请先同步或填写对应生产类型的星系成本指数");
        }
        Map<Integer, BigDecimal> adjustedPrices = req.adjustedPrices() == null
            ? currentAdjustedPrices()
            : new HashMap<>(req.adjustedPrices());
        List<EveIndustryMaterial> materials = readMaterials(blueprint.getMaterialsJson());
        List<Integer> missingPrices = materials.stream()
            .map(EveIndustryMaterial::typeId)
            .filter(typeId -> adjustedPrices.get(typeId) == null)
            .toList();
        if (!missingPrices.isEmpty()) {
            throw new BusinessException("缺少计算安装费所需的官方参考价，材料编号：" + missingPrices);
        }
        BigDecimal skillTimeMultiplier = jsonDecimal(config
            .getProductionSkillJson(), "timeMultiplier", DEFAULT_SKILL_MULTIPLIER);
        List<EveIndustryGoldenMaterial> calculatedMaterials = materials.stream()
            .map(item -> new EveIndustryGoldenMaterial(item.typeId(), item.name(), EveIndustryCalculator
                .materialQuantity(item.quantity(), req.runs(), blueprint.getProductQuantity(), req
                    .materialEfficiency(), facility.getMaterialMultiplier())))
            .toList();
        BigDecimal calculatedTime = EveIndustryCalculator.activitySeconds(blueprint.getBaseTimeSeconds(), req
            .runs(), req.timeEfficiency(), skillTimeMultiplier, facility.getTimeMultiplier(), blueprint.getActivity());
        BigDecimal eiv = materials.stream()
            .map(item -> item.quantity()
                .multiply(BigDecimal.valueOf(req.runs()))
                .multiply(adjustedPrices.get(item.typeId())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal calculatedInstall = EveIndustryCalculator.installationCost(eiv, sci, facility
            .getCostMultiplier(), facility.getFacilityTax(), facility.getVersionedSurcharge());
        long materialTolerance = req.materialTolerance() == null ? 0L : req.materialTolerance();
        BigDecimal timeTolerance = req.timeToleranceSeconds() == null ? BigDecimal.ONE : req.timeToleranceSeconds();
        BigDecimal installTolerance = req.installCostTolerance() == null ? BigDecimal.ONE : req.installCostTolerance();
        List<String> details = compareGoldenCase(req.expectedMaterials(), calculatedMaterials, materialTolerance, req
            .expectedTimeSeconds(), calculatedTime, timeTolerance, req
                .expectedInstallCost(), calculatedInstall, installTolerance);
        EveIndustryGoldenCaseDO entity = new EveIndustryGoldenCaseDO();
        entity.setTenantId(currentTenantId());
        entity.setName(req.name());
        entity.setBlueprintId(blueprint.getId());
        entity.setStaticVersionId(blueprint.getStaticVersionId());
        entity.setConfigId(config.getId());
        entity.setFacilityId(facility.getId());
        entity.setRuns(req.runs());
        entity.setMaterialEfficiency(req.materialEfficiency());
        entity.setTimeEfficiency(req.timeEfficiency());
        entity.setInputSnapshotJson(writeJson(goldenInputSnapshot(config, facility, sci, adjustedPrices)));
        entity.setExpectedMaterialsJson(writeJson(req.expectedMaterials()));
        entity.setExpectedTimeSeconds(req.expectedTimeSeconds());
        entity.setExpectedInstallCost(req.expectedInstallCost());
        entity.setCalculatedMaterialsJson(writeJson(calculatedMaterials));
        entity.setCalculatedTimeSeconds(calculatedTime);
        entity.setCalculatedInstallCost(calculatedInstall);
        entity.setMaterialTolerance(materialTolerance);
        entity.setTimeToleranceSeconds(timeTolerance);
        entity.setInstallCostTolerance(installTolerance);
        entity.setValidationStatus(details.isEmpty() ? "PASSED" : "FAILED");
        entity.setValidationDetailJson(writeJson(details));
        entity.setValidatedAt(LocalDateTime.now());
        goldenCaseMapper.insert(entity);
        if (details.isEmpty()) {
            markBlueprintValidated(blueprint);
        }
        return toGoldenCaseResp(entity);
    }

    /** 返回某个机会计算快照采用的材料数量和逐档采购成本。 */
    public List<EveIndustryOpportunityMaterialDO> opportunityMaterials(Long opportunityId) {
        return opportunityMaterialMapper.selectList(new LambdaQueryWrapper<EveIndustryOpportunityMaterialDO>()
            .eq(EveIndustryOpportunityMaterialDO::getOpportunityId, opportunityId)
            .eq(EveIndustryOpportunityMaterialDO::getTenantId, currentTenantId())
            .orderByDesc(EveIndustryOpportunityMaterialDO::getPurchaseCost));
    }

    /** 按网站规则对单张蓝图进行即时试算，供蓝图计算页和生产计划复用。 */
    public EveIndustryCalculationResp calculate(EveIndustryCalculationReq req) {
        EveIndustryBlueprintDO blueprint = blueprintMapper.selectById(req.blueprintId());
        EveIndustryConfigDO config = configMapper.selectById(req.configId());
        EveIndustryFacilityProfileDO facility = facilityMapper.selectById(req.facilityId());
        if (blueprint == null || config == null || facility == null) {
            throw new BusinessException("蓝图、生产配置或设施不存在");
        }
        if (!currentTenantId().equals(config.getTenantId()) || !currentTenantId().equals(facility.getTenantId())) {
            throw new BusinessException("生产配置或设施不属于当前租户");
        }
        List<EveIndustryMaterial> materials = readMaterials(blueprint.getMaterialsJson());
        Set<Integer> marketIds = new HashSet<>();
        marketIds.add(blueprint.getProductTypeId());
        materials.forEach(item -> marketIds.add(item.typeId()));
        marketService.prepareIndustrySnapshots(marketIds, 120);
        Map<Integer, BigDecimal> adjustedPrices = currentAdjustedPrices();
        BigDecimal skillTimeMultiplier = jsonDecimal(config
            .getProductionSkillJson(), "timeMultiplier", DEFAULT_SKILL_MULTIPLIER);
        BigDecimal seconds = EveIndustryCalculator.activitySeconds(blueprint.getBaseTimeSeconds(), req.runs(), req
            .timeEfficiency(), skillTimeMultiplier, facility.getTimeMultiplier(), blueprint.getActivity());
        List<EveIndustryCalculationMaterialResp> rows = new ArrayList<>();
        BigDecimal materialCost = BigDecimal.ZERO;
        BigDecimal eiv = BigDecimal.ZERO;
        List<String> warnings = new ArrayList<>();
        for (EveIndustryMaterial material : materials) {
            long quantity = EveIndustryCalculator.materialQuantity(material.quantity(), req.runs(), blueprint
                .getProductQuantity(), req.materialEfficiency(), facility.getMaterialMultiplier());
            EveMarketSnapshotDO snapshot = marketSnapshotMapper.selectById(material.typeId());
            EveIndustryCalculator.OrderFill fill = EveIndustryCalculator.fill(readOrders(snapshot, false).stream()
                .map(item -> new EveIndustryCalculator.OrderLevel(item.price(), item.volume() == null
                    ? 0
                    : item.volume()))
                .toList(), quantity);
            boolean complete = fill.filledQuantity() >= quantity;
            if (!complete) {
                warnings.add("市场上可购买的材料数量不足：" + material.name());
            }
            materialCost = materialCost.add(fill.total());
            BigDecimal adjustedPrice = adjustedPrices.get(material.typeId());
            if (adjustedPrice == null) {
                warnings.add("缺少计算安装费所需的官方参考价，材料编号：" + material.typeId());
            } else {
                eiv = eiv.add(material.quantity().multiply(BigDecimal.valueOf(req.runs())).multiply(adjustedPrice));
            }
            rows.add(new EveIndustryCalculationMaterialResp(material.typeId(), material.name(), quantity, fill
                .total(), fill.averagePrice(), complete));
        }
        BigDecimal sci = "REACTION".equals(blueprint.getActivity())
            ? facility.getReactionSci()
            : facility.getManufacturingSci();
        if (sci == null) {
            warnings.add("未填写生产星系的成本指数，安装费估算可能偏低");
            sci = BigDecimal.ZERO;
        }
        BigDecimal install = EveIndustryCalculator.installationCost(eiv, sci, facility.getCostMultiplier(), facility
            .getFacilityTax(), facility.getVersionedSurcharge());
        EveMarketSnapshotDO productMarket = marketSnapshotMapper.selectById(blueprint.getProductTypeId());
        long productQuantity = blueprint.getProductQuantity().multiply(BigDecimal.valueOf(req.runs())).longValue();
        EveIndustryCalculator.OrderFill immediate = EveIndustryCalculator.fill(readOrders(productMarket, true).stream()
            .map(item -> new EveIndustryCalculator.OrderLevel(item.price(), item.volume() == null ? 0 : item.volume()))
            .toList(), productQuantity);
        EveIndustryCalculator.OrderFill sellOrder = EveIndustryCalculator.fill(readOrders(productMarket, false).stream()
            .map(item -> new EveIndustryCalculator.OrderLevel(item.price(), item.volume() == null ? 0 : item.volume()))
            .toList(), productQuantity);
        BigDecimal salesTax = jsonDecimal(config.getMarketFeeJson(), "salesTax", DEFAULT_FEE);
        BigDecimal brokerFee = jsonDecimal(config.getMarketFeeJson(), "brokerFee", DEFAULT_FEE);
        BigDecimal immediateRevenue = immediate.total().multiply(BigDecimal.ONE.subtract(salesTax));
        BigDecimal sellRevenue = sellOrder.total().multiply(BigDecimal.ONE.subtract(salesTax).subtract(brokerFee));
        if (immediate.filledQuantity() < productQuantity || sellOrder.filledQuantity() < productQuantity) {
            warnings.add("产品的市场挂单数量不足，收入与利润估算可能不完整");
        }
        BigDecimal totalCost = materialCost.add(install);
        return new EveIndustryCalculationResp(blueprint.getId(), blueprint.getBlueprintName(), blueprint
            .getProductTypeId(), blueprint.getProductName(), blueprint.getActivity(), req.runs(), blueprint
                .getProductQuantity(), seconds, rows, materialCost, install, totalCost, immediateRevenue, sellRevenue, immediateRevenue
                    .subtract(totalCost), sellRevenue.subtract(totalCost), warnings.stream().distinct().toList());
    }

    /** 查询最新一轮扫描结果。 */
    public List<EveIndustryOpportunityResp> opportunities(int limit) {
        EveIndustryOpportunityDO latest = opportunityMapper.selectOne(new LambdaQueryWrapper<EveIndustryOpportunityDO>()
            .eq(EveIndustryOpportunityDO::getTenantId, currentTenantId())
            .orderByDesc(EveIndustryOpportunityDO::getCalculatedAt)
            .last("LIMIT 1"));
        if (latest == null) {
            return List.of();
        }
        List<EveIndustryOpportunityDO> rows = opportunityMapper
            .selectList(new LambdaQueryWrapper<EveIndustryOpportunityDO>()
                .eq(EveIndustryOpportunityDO::getTenantId, currentTenantId())
                .eq(EveIndustryOpportunityDO::getScanId, latest.getScanId())
                .orderByDesc(EveIndustryOpportunityDO::getDailyProfit)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 200)));
        return toResponses(rows);
    }

    /** 查询当前租户的生产计划，按最近更新时间倒序。 */
    public List<EveIndustryPlanDO> plans() {
        return planMapper.selectList(new LambdaQueryWrapper<EveIndustryPlanDO>()
            .eq(EveIndustryPlanDO::getTenantId, currentTenantId())
            .ne(EveIndustryPlanDO::getStatus, "ARCHIVED")
            .orderByDesc(EveIndustryPlanDO::getUpdateTime));
    }

    /** 查询生产计划详情及其步骤。 */
    public EveIndustryPlanResp plan(Long id) {
        EveIndustryPlanDO plan = planMapper.selectOne(new LambdaQueryWrapper<EveIndustryPlanDO>()
            .eq(EveIndustryPlanDO::getId, id)
            .eq(EveIndustryPlanDO::getTenantId, currentTenantId()));
        if (plan == null) {
            throw new BusinessException("生产计划不存在");
        }
        List<EveIndustryPlanStepDO> steps = planStepMapper.selectList(new LambdaQueryWrapper<EveIndustryPlanStepDO>()
            .eq(EveIndustryPlanStepDO::getPlanId, id)
            .eq(EveIndustryPlanStepDO::getTenantId, currentTenantId())
            .orderByAsc(EveIndustryPlanStepDO::getSequenceNo));
        return new EveIndustryPlanResp(plan, steps);
    }

    /** 从一条机会快照创建草稿计划，并复制材料采购步骤，避免后续行情刷新改变计划。 */
    @Transactional(rollbackFor = Exception.class)
    public EveIndustryPlanResp createPlan(EveIndustryPlanCreateReq req) {
        EveIndustryOpportunityDO opportunity = opportunityMapper
            .selectOne(new LambdaQueryWrapper<EveIndustryOpportunityDO>().eq(EveIndustryOpportunityDO::getId, req
                .opportunityId()).eq(EveIndustryOpportunityDO::getTenantId, currentTenantId()));
        if (opportunity == null) {
            throw new BusinessException("工业机会不存在或不属于当前租户");
        }
        if (!"EXECUTABLE".equals(opportunity.getStatus())) {
            throw new BusinessException("这条结果暂不能加入计划，请先补齐蓝图、行情或设施数据");
        }
        EveIndustryBlueprintDO blueprint = blueprintMapper.selectById(opportunity.getBlueprintId());
        EveIndustryConfigDO config = configMapper.selectById(opportunity.getConfigId());
        EveIndustryFacilityProfileDO facility = facilityMapper.selectById(opportunity.getFacilityId());
        if (blueprint == null) {
            throw new BusinessException("机会关联蓝图不存在");
        }
        if (config == null || facility == null || !currentTenantId().equals(config.getTenantId()) || !currentTenantId()
            .equals(facility.getTenantId())) {
            throw new BusinessException("机会关联的配置或设施不存在或不属于当前租户");
        }
        int runs = req.runs() == null || req.runs() < 1 ? opportunity.getRecommendedRuns() : req.runs();
        if (runs < 1) {
            throw new BusinessException("计划生产次数必须大于 0");
        }
        if (!Integer.valueOf(runs).equals(opportunity.getRecommendedRuns())) {
            throw new BusinessException("计划生产次数必须与分析结果一致；需要调整时，请重新分析后创建计划");
        }
        EveIndustryPlanDO plan = new EveIndustryPlanDO();
        plan.setTenantId(currentTenantId());
        plan.setName(req.name());
        plan.setSourceOpportunityId(opportunity.getId());
        plan.setStatus("DRAFT");
        plan.setActivity(blueprint.getActivity());
        plan.setProductTypeId(opportunity.getProductTypeId());
        plan.setProductName(opportunity.getProductName());
        plan.setRuns(runs);
        plan.setTargetQuantity(blueprint.getProductQuantity().multiply(BigDecimal.valueOf(runs)).longValue());
        plan.setConfigId(opportunity.getConfigId());
        plan.setFacilityId(opportunity.getFacilityId());
        plan.setBlueprintId(opportunity.getBlueprintId());
        plan.setStaticVersionId(blueprint.getStaticVersionId());
        plan.setConfigVersion(config.getVersion());
        EveIndustryBlueprintOverrideDO blueprintOverride = overrideMapper
            .selectOne(new LambdaQueryWrapper<EveIndustryBlueprintOverrideDO>()
                .eq(EveIndustryBlueprintOverrideDO::getBlueprintTypeId, blueprint.getBlueprintTypeId())
                .eq(EveIndustryBlueprintOverrideDO::getTenantId, currentTenantId())
                .last("LIMIT 1"));
        plan.setBlueprintOverrideVersion(blueprintOverride == null ? null : blueprintOverride.getVersion());
        plan.setMarketSnapshotId(opportunity.getMarketSnapshotId());
        plan.setSystemSnapshotId(opportunity.getSystemSnapshotId());
        plan.setRuleVersion(opportunity.getRuleVersion());
        plan.setTotalMaterialCost(opportunity.getMaterialCost());
        plan.setTotalInstallCost(opportunity.getInstallCost());
        plan.setExpectedRevenue(opportunity.getSellOrderRevenue());
        plan.setExpectedProfit(opportunity.getSellOrderProfit());
        BigDecimal skillTimeMultiplier = jsonDecimal(config
            .getProductionSkillJson(), "timeMultiplier", DEFAULT_SKILL_MULTIPLIER);
        BigDecimal facilityTimeMultiplier = facility.getTimeMultiplier() == null
            ? BigDecimal.ONE
            : facility.getTimeMultiplier();
        plan.setExpectedTimeSeconds(EveIndustryCalculator.activitySeconds(blueprint
            .getBaseTimeSeconds(), runs, blueprintOverride == null
                ? 0
                : blueprintOverride.getTimeEfficiency(), skillTimeMultiplier, facilityTimeMultiplier, blueprint
                    .getActivity()));
        plan.setRiskTagsJson(opportunity.getRiskTagsJson());
        plan.setAssumptionsJson(opportunity.getAssumptionsJson());
        Map<String, Object> inputSnapshot = new LinkedHashMap<>();
        inputSnapshot.put("opportunityId", opportunity.getId());
        inputSnapshot.put("blueprintId", blueprint.getId());
        inputSnapshot.put("staticVersionId", blueprint.getStaticVersionId());
        inputSnapshot.put("configId", config.getId());
        inputSnapshot.put("configVersion", config.getVersion());
        inputSnapshot.put("blueprintOverrideVersion", plan.getBlueprintOverrideVersion());
        inputSnapshot.put("facilityId", facility.getId());
        inputSnapshot.put("facilitySource", facility.getSource());
        inputSnapshot.put("systemId", facility.getSystemId());
        inputSnapshot.put("market", "JITA_4_4");
        inputSnapshot.put("inventoryMode", "MARKET_ONLY");
        inputSnapshot.put("assumptions", opportunity.getAssumptionsJson());
        inputSnapshot.put("sourceOpportunityInputSnapshot", opportunity.getInputSnapshotJson());
        inputSnapshot.put("marketSnapshotId", opportunity.getMarketSnapshotId());
        inputSnapshot.put("systemSnapshotId", opportunity.getSystemSnapshotId());
        inputSnapshot.put("ruleVersion", opportunity.getRuleVersion());
        plan.setInputSnapshotJson(writeJson(inputSnapshot));
        planMapper.insert(plan);
        List<EveIndustryOpportunityMaterialDO> materials = opportunityMaterialMapper
            .selectList(new LambdaQueryWrapper<EveIndustryOpportunityMaterialDO>()
                .eq(EveIndustryOpportunityMaterialDO::getOpportunityId, opportunity.getId())
                .eq(EveIndustryOpportunityMaterialDO::getTenantId, currentTenantId())
                .orderByDesc(EveIndustryOpportunityMaterialDO::getPurchaseCost));
        int sequence = 1;
        for (EveIndustryOpportunityMaterialDO material : materials) {
            EveIndustryPlanStepDO step = new EveIndustryPlanStepDO();
            step.setTenantId(currentTenantId());
            step.setPlanId(plan.getId());
            step.setStepKey("PURCHASE-" + material.getMaterialTypeId());
            step.setStepType("PURCHASE");
            step.setSequenceNo(sequence++);
            step.setDependsOnJson("[]");
            step.setStatus("PENDING");
            step.setTitle("采购 " + (material.getMaterialName() == null
                ? material.getMaterialTypeId()
                : material.getMaterialName()));
            step.setTypeId(material.getMaterialTypeId());
            step.setQuantity(material.getRequiredQuantity());
            step.setUnitCost(material.getAveragePrice());
            step.setTotalCost(material.getPurchaseCost());
            step.setPayloadJson(writeJson(Map.of("sourceOpportunityId", opportunity
                .getId(), "inventoryMode", "MARKET_ONLY")));
            planStepMapper.insert(step);
        }
        EveIndustryPlanStepDO production = new EveIndustryPlanStepDO();
        production.setTenantId(currentTenantId());
        production.setPlanId(plan.getId());
        production.setStepKey("PRODUCTION-1");
        production.setStepType("PRODUCTION");
        production.setSequenceNo(sequence);
        production.setDependsOnJson(writeJson(materials.stream()
            .map(item -> "PURCHASE-" + item.getMaterialTypeId())
            .toList()));
        production.setStatus("PENDING");
        production.setTitle("生产 " + opportunity.getProductName());
        production.setTypeId(opportunity.getProductTypeId());
        production.setQuantity(plan.getTargetQuantity());
        production.setTotalCost(opportunity.getInstallCost());
        production.setDurationSeconds(plan.getExpectedTimeSeconds());
        production.setPayloadJson(writeJson(Map.of("blueprintId", opportunity.getBlueprintId(), "runs", runs)));
        planStepMapper.insert(production);
        EveIndustryPlanStepDO sales = new EveIndustryPlanStepDO();
        sales.setTenantId(currentTenantId());
        sales.setPlanId(plan.getId());
        sales.setStepKey("SALES-1");
        sales.setStepType("SALES");
        sales.setSequenceNo(sequence + 1);
        sales.setDependsOnJson("[\"PRODUCTION-1\"]");
        sales.setStatus("PENDING");
        sales.setTitle("销售 " + opportunity.getProductName());
        sales.setTypeId(opportunity.getProductTypeId());
        sales.setQuantity(plan.getTargetQuantity());
        sales.setTotalCost(BigDecimal.ZERO);
        sales.setPayloadJson(writeJson(Map.of("expectedRevenue", plan.getExpectedRevenue(), "market", "JITA_4_4")));
        planStepMapper.insert(sales);
        return plan(plan.getId());
    }

    /** 更新生产计划状态，状态变化保留在主表上供后续执行模块消费。 */
    @Transactional(rollbackFor = Exception.class)
    public EveIndustryPlanDO updatePlanStatus(Long id, EveIndustryPlanStatusReq req) {
        Set<String> statuses = Set
            .of("DRAFT", "CONFIRMED", "PURCHASING", "PRODUCING", "DELIVERABLE", "DELIVERED", "CANCELLED", "ARCHIVED");
        if (!statuses.contains(req.status())) {
            throw new BusinessException("不支持的生产计划状态");
        }
        EveIndustryPlanDO plan = planMapper.selectOne(new LambdaQueryWrapper<EveIndustryPlanDO>()
            .eq(EveIndustryPlanDO::getId, id)
            .eq(EveIndustryPlanDO::getTenantId, currentTenantId()));
        if (plan == null) {
            throw new BusinessException("生产计划不存在");
        }
        plan.setStatus(req.status());
        if ("CONFIRMED".equals(req.status())) {
            plan.setConfirmedAt(LocalDateTime.now());
        }
        if ("ARCHIVED".equals(req.status())) {
            plan.setArchivedAt(LocalDateTime.now());
        }
        planMapper.updateById(plan);
        return plan;
    }

    /** 复制一份计划草稿，继续引用原计划的机会快照。 */
    @Transactional(rollbackFor = Exception.class)
    public EveIndustryPlanResp copyPlan(Long id) {
        EveIndustryPlanDO source = planMapper.selectOne(new LambdaQueryWrapper<EveIndustryPlanDO>()
            .eq(EveIndustryPlanDO::getId, id)
            .eq(EveIndustryPlanDO::getTenantId, currentTenantId()));
        if (source == null || source.getSourceOpportunityId() == null) {
            throw new BusinessException("生产计划不存在或缺少来源机会");
        }
        return createPlan(new EveIndustryPlanCreateReq(source.getName() + " 副本", source.getSourceOpportunityId(), source
            .getRuns()));
    }

    /** 基于创建时固化的来源机会更新草稿金额，不读取最新行情覆盖已确认计划。 */
    @Transactional(rollbackFor = Exception.class)
    public EveIndustryPlanResp recalculatePlan(Long id) {
        EveIndustryPlanDO plan = planMapper.selectOne(new LambdaQueryWrapper<EveIndustryPlanDO>()
            .eq(EveIndustryPlanDO::getId, id)
            .eq(EveIndustryPlanDO::getTenantId, currentTenantId()));
        if (plan == null) {
            throw new BusinessException("生产计划不存在");
        }
        if (!"DRAFT".equals(plan.getStatus())) {
            throw new BusinessException("仅草稿计划允许重新计算");
        }
        EveIndustryOpportunityDO opportunity = opportunityMapper
            .selectOne(new LambdaQueryWrapper<EveIndustryOpportunityDO>().eq(EveIndustryOpportunityDO::getId, plan
                .getSourceOpportunityId()).eq(EveIndustryOpportunityDO::getTenantId, currentTenantId()));
        if (opportunity == null) {
            throw new BusinessException("计划来源机会不存在");
        }
        plan.setTotalMaterialCost(opportunity.getMaterialCost());
        plan.setTotalInstallCost(opportunity.getInstallCost());
        plan.setExpectedRevenue(opportunity.getSellOrderRevenue());
        plan.setExpectedProfit(opportunity.getSellOrderProfit());
        plan.setRiskTagsJson(opportunity.getRiskTagsJson());
        plan.setAssumptionsJson(opportunity.getAssumptionsJson());
        planMapper.updateById(plan);
        return plan(plan.getId());
    }

    /** 按一套生产配置和设施扫描已导入的蓝图活动。 */
    @Transactional(rollbackFor = Exception.class)
    public List<EveIndustryOpportunityResp> scan(EveIndustryScanReq req) {
        EveIndustryConfigDO config = configMapper.selectById(req.configId());
        EveIndustryFacilityProfileDO facility = facilityMapper.selectById(req.facilityId());
        if (config == null || facility == null) {
            throw new BusinessException("生产配置或设施不存在");
        }
        if (!currentTenantId().equals(config.getTenantId()) || !currentTenantId().equals(facility.getTenantId())) {
            throw new BusinessException("生产配置或设施不属于当前租户");
        }
        if (!Boolean.TRUE.equals(config.getEnabled()) || !Boolean.TRUE.equals(facility.getEnabled())) {
            throw new BusinessException("生产配置或设施未启用");
        }
        String scanId = UUID.randomUUID().toString();
        LambdaQueryWrapper<EveIndustryBlueprintDO> wrapper = new LambdaQueryWrapper<EveIndustryBlueprintDO>()
            .eq(EveIndustryBlueprintDO::getSupported, true)
            .eq(EveIndustryBlueprintDO::getValidationStatus, "VALIDATED")
            .orderByDesc(EveIndustryBlueprintDO::getProductTypeId);
        if (req.activity() != null && !req.activity().isBlank()) {
            wrapper.eq(EveIndustryBlueprintDO::getActivity, req.activity());
        }
        List<EveIndustryBlueprintDO> blueprints = blueprintMapper.selectList(wrapper);
        int limit = req.limit() == null ? 100 : Math.min(Math.max(req.limit(), 1), 200);
        if (blueprints.size() > limit) {
            blueprints = blueprints.subList(0, limit);
        }
        Set<Integer> marketTypeIds = new HashSet<>();
        for (EveIndustryBlueprintDO blueprint : blueprints) {
            marketTypeIds.add(blueprint.getProductTypeId());
            readMaterials(blueprint.getMaterialsJson()).forEach(item -> marketTypeIds.add(item.typeId()));
        }
        marketService.prepareIndustrySnapshots(marketTypeIds, 120);
        Map<Integer, BigDecimal> adjustedPrices = new HashMap<>();
        try {
            for (SerenityMarketPriceResponse item : esiClient.getMarketPricesWithMetadata().body()) {
                if (item.adjustedPrice() != null) {
                    adjustedPrices.put(item.typeId(), item.adjustedPrice());
                }
            }
        } catch (RuntimeException ignored) {
            // 缺少国服 adjusted price 时机会会降级为可试算，不能改用国际服价格。
        }
        List<EveIndustryOpportunityDO> results = new ArrayList<>();
        for (EveIndustryBlueprintDO blueprint : blueprints) {
            if (results.size() >= limit) {
                break;
            }
            EveIndustryOpportunityDO result = calculate(scanId, blueprint, config, facility, adjustedPrices);
            if (result != null) {
                opportunityMapper.insert(result);
                persistMaterials(result, blueprint, config, facility);
                results.add(result);
            }
        }
        results.sort(Comparator.comparing(EveIndustryOpportunityDO::getDailyProfit).reversed());
        return toResponses(results);
    }

    /** 返回当前工业数据覆盖状态。 */
    public EveIndustryDataStatusResp dataStatus() {
        long all = blueprintMapper.selectCount(new LambdaQueryWrapper<>());
        long validated = blueprintMapper.selectCount(new LambdaQueryWrapper<EveIndustryBlueprintDO>()
            .eq(EveIndustryBlueprintDO::getSupported, true)
            .eq(EveIndustryBlueprintDO::getValidationStatus, "VALIDATED"));
        EveIndustryStaticVersionDO latest = staticVersionMapper
            .selectOne(new LambdaQueryWrapper<EveIndustryStaticVersionDO>()
                .orderByDesc(EveIndustryStaticVersionDO::getDownloadedAt)
                .last("LIMIT 1"));
        long market = marketSnapshotMapper.selectCount(null);
        String message = validated == 0 ? "还没有可用的蓝图配方，请先在“数据与校验”同步公开蓝图或导入国服配方" : "已有可用蓝图配方，可选择生产配置和设施分析利润";
        return new EveIndustryDataStatusResp(all, validated, latest == null
            ? null
            : latest.getDownloadedAt(), market, message);
    }

    private EveIndustryOpportunityDO calculate(String scanId,
                                               EveIndustryBlueprintDO blueprint,
                                               EveIndustryConfigDO config,
                                               EveIndustryFacilityProfileDO facility,
                                               Map<Integer, BigDecimal> adjustedPrices) {
        Set<String> supportedActivities = readStringSet(facility.getSupportedActivitiesJson());
        if (!supportedActivities.isEmpty() && !supportedActivities.contains(blueprint.getActivity())) {
            return null;
        }
        List<EveIndustryMaterial> materials = readMaterials(blueprint.getMaterialsJson());
        if (materials.isEmpty()) {
            return null;
        }
        EveIndustryBlueprintOverrideDO override = overrideMapper
            .selectOne(new LambdaQueryWrapper<EveIndustryBlueprintOverrideDO>()
                .eq(EveIndustryBlueprintOverrideDO::getBlueprintTypeId, blueprint.getBlueprintTypeId())
                .eq(EveIndustryBlueprintOverrideDO::getTenantId, currentTenantId())
                .last("LIMIT 1"));
        int me = override == null ? 0 : Math.max(0, override.getMaterialEfficiency());
        int te = override == null ? 0 : Math.max(0, override.getTimeEfficiency());
        int lines = jsonInt(config.getProductionSkillJson(), "productionLines", DEFAULT_LINES);
        BigDecimal skillTimeMultiplier = jsonDecimal(config
            .getProductionSkillJson(), "timeMultiplier", DEFAULT_SKILL_MULTIPLIER);
        BigDecimal salesTax = jsonDecimal(config.getMarketFeeJson(), "salesTax", DEFAULT_FEE);
        BigDecimal brokerFee = jsonDecimal(config.getMarketFeeJson(), "brokerFee", DEFAULT_FEE);
        long orderDepth = marketDepth(blueprint.getProductTypeId(), true);
        long averageDailyVolume = averageDailyVolume(blueprint.getProductTypeId());
        long demand = EveIndustryCalculator.safeDemand(averageDailyVolume, config.getTargetDays(), config
            .getMarketShareLimit(), orderDepth);
        BigDecimal secondsPerRun = EveIndustryCalculator.activitySeconds(blueprint
            .getBaseTimeSeconds(), 1, te, skillTimeMultiplier, facility.getTimeMultiplier(), blueprint.getActivity());
        long capacity = secondsPerRun.signum() <= 0
            ? demand
            : BigDecimal.valueOf(86400)
                .multiply(config.getTargetDays())
                .divide(secondsPerRun, 0, RoundingMode.FLOOR)
                .longValue() * lines;
        long targetQuantity = Math.min(demand, capacity * blueprint.getProductQuantity().longValue());
        // 没有可消化需求时不再强制生成一流程，保留为 TRIAL 并记录需求不足风险。
        int runs = demand <= 0
            ? 0
            : (int)Math.max(1, (long)Math.ceil(targetQuantity / blueprint.getProductQuantity().doubleValue()));
        if (override != null && "BPC".equals(override.getBlueprintKind()) && override.getRemainingRuns() != null) {
            runs = Math.min(runs, override.getRemainingRuns());
        }
        targetQuantity = Math.max(0, blueprint.getProductQuantity().multiply(BigDecimal.valueOf(runs)).longValue());
        BigDecimal materialCost = BigDecimal.ZERO;
        boolean marketComplete = true;
        for (EveIndustryMaterial material : materials) {
            long required = EveIndustryCalculator.materialQuantity(material.quantity(), runs, blueprint
                .getProductQuantity(), me, facility.getMaterialMultiplier());
            EveMarketSnapshotDO market = marketSnapshotMapper.selectById(material.typeId());
            List<EveMarketOrderResp> sellOrders = readOrders(market, false);
            EveIndustryCalculator.OrderFill fill = EveIndustryCalculator.fill(sellOrders.stream()
                .map(item -> new EveIndustryCalculator.OrderLevel(item.price(), item.volume() == null
                    ? 0
                    : item.volume()))
                .toList(), required);
            materialCost = materialCost.add(fill.total());
            marketComplete &= fill.filledQuantity() >= required;
        }
        EveMarketSnapshotDO productMarket = marketSnapshotMapper.selectById(blueprint.getProductTypeId());
        EveIndustryCalculator.OrderFill immediate = EveIndustryCalculator.fill(readOrders(productMarket, true).stream()
            .map(item -> new EveIndustryCalculator.OrderLevel(item.price(), item.volume() == null ? 0 : item.volume()))
            .toList(), targetQuantity);
        EveIndustryCalculator.OrderFill sellOrder = EveIndustryCalculator.fill(readOrders(productMarket, false).stream()
            .map(item -> new EveIndustryCalculator.OrderLevel(item.price(), item.volume() == null ? 0 : item.volume()))
            .toList(), targetQuantity);
        boolean adjustedPriceComplete = materials.stream().allMatch(item -> adjustedPrices.containsKey(item.typeId()));
        BigDecimal eiv = BigDecimal.ZERO;
        for (EveIndustryMaterial material : materials) {
            BigDecimal price = adjustedPrices.get(material.typeId());
            if (price != null) {
                eiv = eiv.add(material.quantity().multiply(BigDecimal.valueOf(runs)).multiply(price));
            }
        }
        BigDecimal sci = "REACTION".equals(blueprint.getActivity())
            ? facility.getReactionSci()
            : facility.getManufacturingSci();
        boolean sciComplete = sci != null;
        if (sci == null) {
            sci = BigDecimal.ZERO;
        }
        BigDecimal install = EveIndustryCalculator.installationCost(eiv, sci, facility.getCostMultiplier(), facility
            .getFacilityTax(), facility.getVersionedSurcharge());
        BigDecimal immediateRevenue = immediate.total().multiply(BigDecimal.ONE.subtract(salesTax));
        BigDecimal sellRevenue = sellOrder.total().multiply(BigDecimal.ONE.subtract(salesTax).subtract(brokerFee));
        BigDecimal immediateProfit = immediateRevenue.subtract(materialCost).subtract(install);
        BigDecimal sellProfit = sellRevenue.subtract(materialCost).subtract(install);
        BigDecimal hours = secondsPerRun.multiply(BigDecimal.valueOf(runs))
            .divide(BigDecimal.valueOf(3600), 8, RoundingMode.HALF_UP);
        BigDecimal hourly = hours.signum() <= 0 ? BigDecimal.ZERO : sellProfit.divide(hours, 2, RoundingMode.HALF_UP);
        List<String> tags = new ArrayList<>();
        if (demand <= 0) {
            tags.add("DEMAND_UNAVAILABLE");
        }
        if (!marketComplete || immediate.filledQuantity() < targetQuantity) {
            tags.add("ORDER_DEPTH_INSUFFICIENT");
        }
        if (averageDailyVolume <= 0) {
            tags.add("NO_HISTORY_VOLUME");
        }
        if (!adjustedPriceComplete) {
            tags.add("ADJUSTED_PRICE_MISSING");
        }
        if (!sciComplete) {
            tags.add("SCI_MISSING");
        }
        if ("TRANQUILITY_SDE".equals(blueprint.getSource())) {
            tags.add("TQ_FALLBACK_NOT_SERENITY_VERIFIED");
        }
        String status;
        if (!FORMAL_ACTIVITIES.contains(blueprint.getActivity())) {
            status = "UNAVAILABLE";
            tags.add("ACTIVITY_NOT_IN_EXECUTABLE_SCOPE");
        } else {
            status = blueprint.getSupported() && EXECUTABLE_SOURCES.contains(blueprint.getSource()) && "VALIDATED"
                .equals(blueprint
                    .getValidationStatus()) && demand > 0 && marketComplete && adjustedPriceComplete && sciComplete && immediate
                        .filledQuantity() >= targetQuantity ? "EXECUTABLE" : "TRIAL";
        }
        EveIndustryOpportunityDO result = new EveIndustryOpportunityDO();
        EveIndustryMarketInputSnapshotDO inputSnapshot = new EveIndustryMarketInputSnapshotDO();
        inputSnapshot.setTenantId(currentTenantId());
        inputSnapshot.setSnapshotKey(scanId + "-" + blueprint.getId());
        inputSnapshot.setMarketRegionId(10000002L);
        inputSnapshot.setMarketStationId(60003760L);
        inputSnapshot.setCapturedAt(LocalDateTime.now());
        inputSnapshot.setSource("JITA_4_4");
        inputSnapshot.setFreshnessStatus(marketComplete ? "FRESH" : "STALE");
        inputSnapshot.setRuleVersion(INDUSTRY_RULE_VERSION);
        inputSnapshot.setQuoteJson(writeJson(Map.of("productTypeId", blueprint
            .getProductTypeId(), "targetQuantity", targetQuantity, "materialCost", materialCost, "immediateRevenue", immediateRevenue, "sellOrderRevenue", sellRevenue)));
        marketInputSnapshotMapper.insert(inputSnapshot);
        EveIndustrySystemSnapshotDO systemSnapshot = new EveIndustrySystemSnapshotDO();
        systemSnapshot.setTenantId(currentTenantId());
        systemSnapshot.setSnapshotKey(scanId + "-" + blueprint.getId());
        systemSnapshot.setSystemId(facility.getSystemId());
        systemSnapshot.setSystemName(facility.getSystemName());
        systemSnapshot.setSecurityStatus(facility.getSecurityStatus());
        systemSnapshot.setManufacturingSci(facility.getManufacturingSci());
        systemSnapshot.setReactionSci(facility.getReactionSci());
        Map<String, Object> facilitySnapshot = new LinkedHashMap<>();
        facilitySnapshot.put("facilityId", facility.getId());
        facilitySnapshot.put("facilityName", facility.getFacilityName());
        facilitySnapshot.put("materialMultiplier", facility.getMaterialMultiplier());
        facilitySnapshot.put("timeMultiplier", facility.getTimeMultiplier());
        facilitySnapshot.put("costMultiplier", facility.getCostMultiplier());
        facilitySnapshot.put("facilityTax", facility.getFacilityTax());
        facilitySnapshot.put("versionedSurcharge", facility.getVersionedSurcharge());
        systemSnapshot.setFacilityJson(writeJson(facilitySnapshot));
        systemSnapshot.setSource(facility.getSource() == null ? "MANUAL" : facility.getSource());
        systemSnapshot.setCapturedAt(LocalDateTime.now());
        systemSnapshotMapper.insert(systemSnapshot);
        for (EveIndustryMaterial material : materials) {
            long required = EveIndustryCalculator.materialQuantity(material.quantity(), runs, blueprint
                .getProductQuantity(), me, facility.getMaterialMultiplier());
            persistOrderSlices(inputSnapshot.getId(), material.typeId(), "SELL", readOrders(marketSnapshotMapper
                .selectById(material.typeId()), false), required);
        }
        persistOrderSlices(inputSnapshot.getId(), blueprint
            .getProductTypeId(), "BUY", readOrders(productMarket, true), targetQuantity);
        persistOrderSlices(inputSnapshot.getId(), blueprint
            .getProductTypeId(), "SELL", readOrders(productMarket, false), targetQuantity);
        result.setTenantId(currentTenantId());
        result.setScanId(scanId);
        result.setBlueprintId(blueprint.getId());
        result.setStaticVersionId(blueprint.getStaticVersionId());
        result.setProductTypeId(blueprint.getProductTypeId());
        result.setProductName(blueprint.getProductName());
        result.setConfigId(config.getId());
        result.setFacilityId(facility.getId());
        result.setConfigVersion(config.getVersion());
        result.setBlueprintOverrideVersion(override == null ? null : override.getVersion());
        result.setMarketSnapshotId(inputSnapshot.getId());
        result.setSystemSnapshotId(systemSnapshot.getId());
        result.setRuleVersion(INDUSTRY_RULE_VERSION);
        result.setStatus(status);
        result.setRecommendedRuns(runs);
        result.setRecommendedQuantity(targetQuantity);
        result.setMaterialCost(materialCost);
        result.setInstallCost(install);
        result.setImmediateRevenue(immediateRevenue);
        result.setSellOrderRevenue(sellRevenue);
        result.setImmediateProfit(immediateProfit);
        result.setSellOrderProfit(sellProfit);
        result.setMargin(materialCost.add(install).signum() == 0
            ? BigDecimal.ZERO
            : sellProfit.divide(materialCost.add(install), 6, RoundingMode.HALF_UP));
        result.setHourlyProfit(hourly);
        result.setDailyProfit(hourly.multiply(BigDecimal.valueOf(lines * 24)));
        result.setLiquidityScore(BigDecimal.valueOf(Math.min(100, averageDailyVolume <= 0
            ? 0
            : (double)demand / Math.max(1, averageDailyVolume) * 100)).setScale(4, RoundingMode.HALF_UP));
        result.setRiskScore(BigDecimal.valueOf(Math.min(100, tags.size() * 20L)).setScale(4, RoundingMode.HALF_UP));
        result.setRiskTagsJson(writeJson(tags));
        result.setAssumptionsJson(writeJson(Map
            .of("market", "JITA_4_4", "transportCost", 0, "eiv", "SERENITY_ADJUSTED_PRICE")));
        Map<String, Object> calculationInput = new LinkedHashMap<>();
        calculationInput.put("configVersion", config.getVersion());
        calculationInput.put("facilityId", facility.getId());
        calculationInput.put("staticVersionId", blueprint.getStaticVersionId());
        calculationInput.put("marketSnapshotId", inputSnapshot.getId());
        calculationInput.put("systemSnapshotId", systemSnapshot.getId());
        calculationInput.put("ruleVersion", INDUSTRY_RULE_VERSION);
        result.setInputSnapshotJson(writeJson(calculationInput));
        result.setCalculatedAt(LocalDateTime.now());
        return result;
    }

    /** 保存机会实际采用的材料数量与逐档采购结果，供详情复盘。 */
    private void persistMaterials(EveIndustryOpportunityDO opportunity,
                                  EveIndustryBlueprintDO blueprint,
                                  EveIndustryConfigDO config,
                                  EveIndustryFacilityProfileDO facility) {
        EveIndustryBlueprintOverrideDO override = overrideMapper
            .selectOne(new LambdaQueryWrapper<EveIndustryBlueprintOverrideDO>()
                .eq(EveIndustryBlueprintOverrideDO::getBlueprintTypeId, blueprint.getBlueprintTypeId())
                .eq(EveIndustryBlueprintOverrideDO::getTenantId, currentTenantId())
                .last("LIMIT 1"));
        int me = override == null ? 0 : Math.max(0, override.getMaterialEfficiency());
        for (EveIndustryMaterial material : readMaterials(blueprint.getMaterialsJson())) {
            long required = EveIndustryCalculator.materialQuantity(material.quantity(), opportunity
                .getRecommendedRuns(), blueprint.getProductQuantity(), me, facility.getMaterialMultiplier());
            EveIndustryCalculator.OrderFill fill = EveIndustryCalculator.fill(readOrders(marketSnapshotMapper
                .selectById(material.typeId()), false).stream()
                .map(item -> new EveIndustryCalculator.OrderLevel(item.price(), item.volume() == null
                    ? 0
                    : item.volume()))
                .toList(), required);
            EveIndustryOpportunityMaterialDO row = new EveIndustryOpportunityMaterialDO();
            row.setTenantId(currentTenantId());
            row.setOpportunityId(opportunity.getId());
            row.setMaterialTypeId(material.typeId());
            row.setMaterialName(material.name());
            row.setRequiredQuantity(required);
            row.setPurchaseCost(fill.total());
            row.setAveragePrice(fill.averagePrice());
            opportunityMaterialMapper.insert(row);
        }
    }

    private List<EveIndustryOpportunityResp> toResponses(List<EveIndustryOpportunityDO> rows) {
        Map<Long, EveIndustryBlueprintDO> blueprints = new HashMap<>();
        Map<Long, EveIndustryFacilityProfileDO> facilities = new HashMap<>();
        Set<Integer> productIds = new HashSet<>();
        rows.forEach(item -> productIds.add(item.getProductTypeId()));
        for (EveIndustryOpportunityDO item : rows) {
            EveIndustryBlueprintDO blueprint = blueprints.computeIfAbsent(item
                .getBlueprintId(), blueprintMapper::selectById);
            facilities.computeIfAbsent(item.getFacilityId(), facilityMapper::selectById);
        }
        return rows.stream().map(item -> {
            EveIndustryBlueprintDO blueprint = blueprints.get(item.getBlueprintId());
            EveIndustryFacilityProfileDO facility = facilities.get(item.getFacilityId());
            return new EveIndustryOpportunityResp(item.getId(), item.getProductTypeId(), item
                .getProductName(), blueprint == null ? null : blueprint.getActivity(), blueprint == null
                    ? null
                    : blueprint.getBlueprintName(), facility == null
                        ? null
                        : facility.getFacilityName(), facility == null ? null : facility.getSystemName(), item
                            .getStatus(), item.getRecommendedRuns(), item.getRecommendedQuantity(), item
                                .getMaterialCost(), item.getInstallCost(), item.getImmediateRevenue(), item
                                    .getSellOrderRevenue(), item.getImmediateProfit(), item.getSellOrderProfit(), item
                                        .getMargin(), item.getHourlyProfit(), item.getDailyProfit(), item
                                            .getLiquidityScore(), item.getRiskScore(), readStringList(item
                                                .getRiskTagsJson()), item.getCalculatedAt());
        }).toList();
    }

    /** 解析 JSON 数组、包含 activities 的 JSON 对象，或逐行 JSONL。 */
    private ParsedStaticActivities parseStaticActivities(String sourceFileName, byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8).strip();
        List<IndexedActivity> activities = new ArrayList<>();
        List<EveIndustryStaticImportError> errors = new ArrayList<>();
        boolean jsonl = sourceFileName != null && sourceFileName.toLowerCase().endsWith(".jsonl");
        if (!jsonl) {
            try {
                JsonNode root = objectMapper.readTree(text);
                JsonNode activityNodes = root.isArray() ? root : root.path("activities");
                if (!activityNodes.isArray()) {
                    errors.add(new EveIndustryStaticImportError(1, "ROOT_FORMAT", "JSON 根节点必须是活动数组，或包含 activities 数组"));
                } else {
                    int index = 0;
                    for (JsonNode node : activityNodes) {
                        index++;
                        try {
                            activities.add(new IndexedActivity(index, objectMapper
                                .treeToValue(node, EveIndustryStaticActivityInput.class)));
                        } catch (Exception e) {
                            errors.add(new EveIndustryStaticImportError(index, "JSON_MAPPING", compactMessage(e)));
                        }
                    }
                }
                return new ParsedStaticActivities(activities, errors);
            } catch (Exception e) {
                errors.add(new EveIndustryStaticImportError(1, "JSON_PARSE", compactMessage(e)));
                return new ParsedStaticActivities(activities, errors);
            }
        }
        String[] lines = text.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isEmpty()) {
                continue;
            }
            try {
                activities.add(new IndexedActivity(index + 1, objectMapper
                    .readValue(line, EveIndustryStaticActivityInput.class)));
            } catch (Exception e) {
                errors.add(new EveIndustryStaticImportError(index + 1, "JSONL_PARSE", compactMessage(e)));
            }
        }
        return new ParsedStaticActivities(activities, errors);
    }

    /** 将网站蓝图对象转换为项目统一的制造/反应活动模型。 */
    private List<EveIndustryStaticActivityInput> parseCeveBlueprints(JsonNode root) {
        List<EveIndustryStaticActivityInput> result = new ArrayList<>();
        for (JsonNode blueprint : root) {
            JsonNode manufacturing = blueprint.path("activities").path("manufacturing");
            JsonNode materials = manufacturing.path("materials");
            JsonNode products = manufacturing.path("products");
            if (!manufacturing.isObject() || !materials.isArray() || !products.isArray() || products.isEmpty()) {
                continue;
            }
            JsonNode product = products.get(0);
            String activity = "REACTION".equalsIgnoreCase(blueprint.path("blueprintType").asText())
                ? "REACTION"
                : "MANUFACTURING";
            List<EveIndustryMaterial> materialList = new ArrayList<>();
            for (JsonNode item : materials) {
                int typeId = item.path("typeId").asInt(0);
                if (typeId > 0) {
                    materialList.add(new EveIndustryMaterial(typeId, String.valueOf(typeId), item.path("quantity")
                        .decimalValue()));
                }
            }
            List<EveIndustryMaterial> productList = new ArrayList<>();
            int productTypeId = product.path("typeId").asInt(0);
            if (productTypeId > 0) {
                productList.add(new EveIndustryMaterial(productTypeId, blueprint.path("typeIdSde")
                    .path("name")
                    .asText(String.valueOf(productTypeId)), product.path("quantity").decimalValue()));
            }
            int blueprintTypeId = blueprint.path("blueprintTypeId").asInt(0);
            if (blueprintTypeId <= 0 || productList.isEmpty() || materialList.isEmpty()) {
                continue;
            }
            result.add(new EveIndustryStaticActivityInput(blueprintTypeId, blueprint.path("typeIdSde")
                .path("name")
                .asText(String.valueOf(blueprintTypeId)), activity, productTypeId, productList.get(0)
                    .name(), productList.get(0).quantity(), manufacturing.path("time").decimalValue(), product
                        .path("probability")
                        .decimalValue(), materialList, productList));
        }
        return result;
    }

    /** 校验规范化蓝图活动的字段范围、唯一性和材料内容。 */
    private List<EveIndustryStaticImportError> validateStaticActivities(List<IndexedActivity> activities) {
        List<EveIndustryStaticImportError> errors = new ArrayList<>();
        if (activities.isEmpty()) {
            errors.add(new EveIndustryStaticImportError(null, "EMPTY", "文件中没有可导入的蓝图活动"));
            return errors;
        }
        if (activities.size() > MAX_STATIC_IMPORT_ACTIVITIES) {
            errors
                .add(new EveIndustryStaticImportError(null, "TOO_MANY_ACTIVITIES", "单次最多导入 " + MAX_STATIC_IMPORT_ACTIVITIES + " 项蓝图活动"));
            return errors;
        }
        Set<String> keys = new HashSet<>();
        for (IndexedActivity indexed : activities) {
            EveIndustryStaticActivityInput item = indexed.activity();
            int line = indexed.line();
            if (item == null) {
                errors.add(new EveIndustryStaticImportError(line, "EMPTY_ACTIVITY", "蓝图活动不能为空"));
                continue;
            }
            if (item.blueprintTypeId() == null || item.blueprintTypeId() <= 0) {
                errors.add(new EveIndustryStaticImportError(line, "BLUEPRINT_TYPE_ID", "blueprintTypeId 必须为正整数"));
            }
            if (item.activity() == null || item.activity().isBlank()) {
                errors.add(new EveIndustryStaticImportError(line, "ACTIVITY", "activity 不能为空"));
            }
            if (item.productTypeId() == null || item.productTypeId() <= 0) {
                errors.add(new EveIndustryStaticImportError(line, "PRODUCT_TYPE_ID", "productTypeId 必须为正整数"));
            }
            if (item.productQuantity() == null || item.productQuantity().signum() <= 0) {
                errors.add(new EveIndustryStaticImportError(line, "PRODUCT_QUANTITY", "productQuantity 必须大于 0"));
            }
            if (item.baseTimeSeconds() == null || item.baseTimeSeconds().signum() < 0) {
                errors.add(new EveIndustryStaticImportError(line, "BASE_TIME", "baseTimeSeconds 不能小于 0"));
            }
            if (item.probability() != null && (item.probability().signum() < 0 || item.probability()
                .compareTo(BigDecimal.ONE) > 0)) {
                errors.add(new EveIndustryStaticImportError(line, "PROBABILITY", "probability 必须位于 0 到 1"));
            }
            if (item.materials() == null || item.materials().isEmpty()) {
                errors.add(new EveIndustryStaticImportError(line, "MATERIALS", "materials 不能为空"));
            } else {
                Set<Integer> materialIds = new HashSet<>();
                for (EveIndustryMaterial material : item.materials()) {
                    if (material == null || material.typeId() == null || material.typeId() <= 0 || material
                        .quantity() == null || material.quantity().signum() <= 0) {
                        errors
                            .add(new EveIndustryStaticImportError(line, "MATERIAL", "每项材料都必须包含正整数 typeId 和大于 0 的 quantity"));
                        continue;
                    }
                    if (!materialIds.add(material.typeId())) {
                        errors
                            .add(new EveIndustryStaticImportError(line, "DUPLICATE_MATERIAL", "同一活动包含重复材料 typeId=" + material
                                .typeId()));
                    }
                }
            }
            if (item.blueprintTypeId() != null && item.activity() != null && !keys.add(item
                .blueprintTypeId() + ":" + item.activity().trim().toUpperCase())) {
                errors.add(new EveIndustryStaticImportError(line, "DUPLICATE_ACTIVITY", "文件中存在重复的蓝图活动"));
            }
        }
        return errors.stream().limit(200).toList();
    }

    /** 对比材料、工时和安装费，返回超过容差的差异说明。 */
    private List<String> compareGoldenCase(List<EveIndustryGoldenMaterial> expected,
                                           List<EveIndustryGoldenMaterial> calculated,
                                           long materialTolerance,
                                           BigDecimal expectedTime,
                                           BigDecimal calculatedTime,
                                           BigDecimal timeTolerance,
                                           BigDecimal expectedInstall,
                                           BigDecimal calculatedInstall,
                                           BigDecimal installTolerance) {
        Map<Integer, Long> expectedMap = new HashMap<>();
        expected.forEach(item -> expectedMap.put(item.typeId(), item.quantity()));
        Map<Integer, Long> calculatedMap = new HashMap<>();
        calculated.forEach(item -> calculatedMap.put(item.typeId(), item.quantity()));
        Set<Integer> typeIds = new HashSet<>(expectedMap.keySet());
        typeIds.addAll(calculatedMap.keySet());
        List<String> details = new ArrayList<>();
        for (Integer typeId : typeIds) {
            long expectedQuantity = expectedMap.getOrDefault(typeId, 0L);
            long calculatedQuantity = calculatedMap.getOrDefault(typeId, 0L);
            if (Math.abs(expectedQuantity - calculatedQuantity) > materialTolerance) {
                details.add("材料 " + typeId + "：实际 " + expectedQuantity + "，计算 " + calculatedQuantity);
            }
        }
        if (expectedTime.subtract(calculatedTime).abs().compareTo(timeTolerance) > 0) {
            details.add("任务时间：实际 " + expectedTime + " 秒，计算 " + calculatedTime + " 秒");
        }
        if (expectedInstall.subtract(calculatedInstall).abs().compareTo(installTolerance) > 0) {
            details.add("安装费：实际 " + expectedInstall + "，计算 " + calculatedInstall);
        }
        return details;
    }

    /** Golden Case 通过后启用对应蓝图；版本仅在全部蓝图都已验证时启用。 */
    private void markBlueprintValidated(EveIndustryBlueprintDO blueprint) {
        blueprint.setValidationStatus("VALIDATED");
        // 国际服候选配方即使通过样例计算，也不能冒充国服正式静态事实。
        blueprint.setSupported(EXECUTABLE_SOURCES.contains(blueprint.getSource()));
        blueprintMapper.updateById(blueprint);
        long total = blueprintMapper.selectCount(new LambdaQueryWrapper<EveIndustryBlueprintDO>()
            .eq(EveIndustryBlueprintDO::getStaticVersionId, blueprint.getStaticVersionId()));
        long validated = blueprintMapper.selectCount(new LambdaQueryWrapper<EveIndustryBlueprintDO>()
            .eq(EveIndustryBlueprintDO::getStaticVersionId, blueprint.getStaticVersionId())
            .eq(EveIndustryBlueprintDO::getValidationStatus, "VALIDATED")
            .eq(EveIndustryBlueprintDO::getSupported, true));
        EveIndustryStaticVersionDO version = staticVersionMapper.selectById(blueprint.getStaticVersionId());
        if (version != null) {
            boolean complete = total > 0 && total == validated;
            version.setValidationStatus(complete ? "VALIDATED" : "PENDING");
            version.setValidationNote("Golden Case 已验证 " + validated + "/" + total + " 项蓝图活动");
            staticVersionMapper.updateById(version);
        }
    }

    /** 固化 Golden Case 的公式输入，后续配置变化不会改变历史证据。 */
    private Map<String, Object> goldenInputSnapshot(EveIndustryConfigDO config,
                                                    EveIndustryFacilityProfileDO facility,
                                                    BigDecimal sci,
                                                    Map<Integer, BigDecimal> adjustedPrices) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("configId", config.getId());
        snapshot.put("configVersion", config.getVersion());
        snapshot.put("productionSkill", readJsonMap(config.getProductionSkillJson()));
        snapshot.put("facilityId", facility.getId());
        snapshot.put("systemId", facility.getSystemId());
        snapshot.put("materialMultiplier", facility.getMaterialMultiplier());
        snapshot.put("timeMultiplier", facility.getTimeMultiplier());
        snapshot.put("costMultiplier", facility.getCostMultiplier());
        snapshot.put("facilityTax", facility.getFacilityTax());
        snapshot.put("versionedSurcharge", facility.getVersionedSurcharge());
        snapshot.put("sci", sci);
        snapshot.put("adjustedPrices", adjustedPrices);
        snapshot.put("formulaVersion", "eve-industry-v1");
        return snapshot;
    }

    /** 拉取国服 adjusted price，并只保留非空值。 */
    private Map<Integer, BigDecimal> currentAdjustedPrices() {
        Map<Integer, BigDecimal> prices = new HashMap<>();
        for (SerenityMarketPriceResponse item : esiClient.getMarketPricesWithMetadata().body()) {
            if (item.adjustedPrice() != null) {
                prices.put(item.typeId(), item.adjustedPrice());
            }
        }
        return prices;
    }

    private EveIndustryGoldenCaseResp toGoldenCaseResp(EveIndustryGoldenCaseDO entity) {
        EveIndustryBlueprintDO blueprint = blueprintMapper.selectById(entity.getBlueprintId());
        return new EveIndustryGoldenCaseResp(entity.getId(), entity.getName(), entity
            .getBlueprintId(), blueprint == null ? null : blueprint.getBlueprintName(), entity.getRuns(), entity
                .getMaterialEfficiency(), entity.getTimeEfficiency(), readGoldenMaterials(entity
                    .getExpectedMaterialsJson()), readGoldenMaterials(entity.getCalculatedMaterialsJson()), entity
                        .getExpectedTimeSeconds(), entity.getCalculatedTimeSeconds(), entity
                            .getExpectedInstallCost(), entity.getCalculatedInstallCost(), entity
                                .getValidationStatus(), readStringList(entity.getValidationDetailJson()), entity
                                    .getValidatedAt());
    }

    private List<EveIndustryGoldenMaterial> readGoldenMaterials(String json) {
        try {
            return objectMapper.readValue(defaultJson(json, "[]"), new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> readJsonMap(String json) {
        try {
            return objectMapper.readValue(defaultJson(json, "{}"), new TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String resolveTypeName(Integer typeId, String suppliedName) {
        if (suppliedName != null && !suppliedName.isBlank()) {
            return suppliedName.trim();
        }
        EveStaticTypeReferenceDO reference = typeReferenceMapper
            .selectOne(new LambdaQueryWrapper<EveStaticTypeReferenceDO>()
                .eq(EveStaticTypeReferenceDO::getTypeId, typeId)
                .last("LIMIT 1"));
        return reference == null || reference.getTypeName() == null ? String.valueOf(typeId) : reference.getTypeName();
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算工业静态数据 SHA-256", e);
        }
    }

    private static String compactMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    private long marketDepth(Integer typeId, boolean buy) {
        EveMarketSnapshotDO snapshot = marketSnapshotMapper.selectById(typeId);
        return readOrders(snapshot, buy).stream().mapToLong(item -> item.volume() == null ? 0 : item.volume()).sum();
    }

    private long averageDailyVolume(Integer typeId) {
        EveMarketSnapshotDO snapshot = marketSnapshotMapper.selectById(typeId);
        List<EveMarketHistoryResp> history = readHistory(snapshot);
        return history.stream()
            .skip(Math.max(0, history.size() - 30L))
            .map(EveMarketHistoryResp::volume)
            .filter(item -> item != null)
            .mapToLong(Long::longValue)
            .sum() / Math.max(1, Math.min(30, history.size()));
    }

    private List<EveMarketOrderResp> readOrders(EveMarketSnapshotDO snapshot, boolean buy) {
        if (snapshot == null) {
            return List.of();
        }
        try {
            String json = buy ? snapshot.getBuyOrdersJson() : snapshot.getSellOrdersJson();
            if (json == null) {
                return List.of();
            }
            List<EveMarketOrderResp> orders = objectMapper.readValue(json, new TypeReference<>() {
            });
            Comparator<EveMarketOrderResp> comparator = Comparator.comparing(EveMarketOrderResp::price, Comparator
                .nullsLast(Comparator.naturalOrder()));
            return orders.stream().sorted(buy ? comparator.reversed() : comparator).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 将本次计算实际可见的订单档位固化，供机会和计划复盘。 */
    private void persistOrderSlices(Long snapshotId,
                                    Integer typeId,
                                    String side,
                                    List<EveMarketOrderResp> orders,
                                    long requestedQuantity) {
        long cumulative = 0;
        for (EveMarketOrderResp order : orders) {
            long available = order.volume() == null ? 0 : Math.max(0, order.volume());
            if (available == 0) {
                continue;
            }
            long quantity = requestedQuantity <= 0 ? available : Math.min(available, requestedQuantity - cumulative);
            if (quantity <= 0) {
                break;
            }
            cumulative += quantity;
            EveIndustryMarketOrderSliceDO slice = new EveIndustryMarketOrderSliceDO();
            slice.setTenantId(currentTenantId());
            slice.setSnapshotId(snapshotId);
            slice.setTypeId(typeId);
            slice.setSide(side);
            slice.setPrice(order.price());
            slice.setQuantity(quantity);
            slice.setCumulativeQuantity(cumulative);
            slice.setSourceOrderId(null);
            slice.setSourceUpdatedAt(order.reportedAt());
            marketOrderSliceMapper.insert(slice);
            if (requestedQuantity > 0 && cumulative >= requestedQuantity) {
                break;
            }
        }
    }

    private List<EveMarketHistoryResp> readHistory(EveMarketSnapshotDO snapshot) {
        if (snapshot == null || snapshot.getHistoryJson() == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(snapshot.getHistoryJson(), new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<EveIndustryMaterial> readMaterials(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private Set<String> readStringSet(String json) {
        try {
            return new HashSet<>(objectMapper.readValue(defaultJson(json, "[]"), new TypeReference<List<String>>() {
            }));
        } catch (Exception e) {
            return Set.of();
        }
    }

    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(defaultJson(json, "[]"), new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private int jsonInt(String json, String field, int fallback) {
        try {
            return objectMapper.readTree(defaultJson(json, "{}")).path(field).asInt(fallback);
        } catch (Exception e) {
            return fallback;
        }
    }

    private BigDecimal jsonDecimal(String json, String field, BigDecimal fallback) {
        try {
            String value = objectMapper.readTree(defaultJson(json, "{}")).path(field).asText(null);
            return value == null ? fallback : new BigDecimal(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static String defaultJson(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Long currentTenantId() {
        return UserContextHolder.getContext().getTenantId();
    }

    private record IndexedActivity(int line, EveIndustryStaticActivityInput activity) {
    }

    private record ParsedStaticActivities(List<IndexedActivity> activities, List<EveIndustryStaticImportError> errors) {
    }
}
