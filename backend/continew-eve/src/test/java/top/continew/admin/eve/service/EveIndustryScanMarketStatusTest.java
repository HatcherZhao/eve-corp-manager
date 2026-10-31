/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package top.continew.admin.eve.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.client.CeveIndustryClient;
import top.continew.admin.eve.client.SerenityEsiClient;
import top.continew.admin.eve.config.EveMarketProperties;
import top.continew.admin.eve.mapper.EveIndustryBlueprintMapper;
import top.continew.admin.eve.mapper.EveIndustryBlueprintOverrideMapper;
import top.continew.admin.eve.mapper.EveIndustryConfigMapper;
import top.continew.admin.eve.mapper.EveIndustryFacilityProfileMapper;
import top.continew.admin.eve.mapper.EveIndustryGoldenCaseMapper;
import top.continew.admin.eve.mapper.EveIndustryMarketInputSnapshotMapper;
import top.continew.admin.eve.mapper.EveIndustryMarketOrderSliceMapper;
import top.continew.admin.eve.mapper.EveIndustryOpportunityMapper;
import top.continew.admin.eve.mapper.EveIndustryOpportunityMaterialMapper;
import top.continew.admin.eve.mapper.EveIndustryPlanMapper;
import top.continew.admin.eve.mapper.EveIndustryPlanStepMapper;
import top.continew.admin.eve.mapper.EveIndustryStaticVersionMapper;
import top.continew.admin.eve.mapper.EveIndustrySystemCostIndexMapper;
import top.continew.admin.eve.mapper.EveIndustrySystemSnapshotMapper;
import top.continew.admin.eve.mapper.EveMarketSnapshotMapper;
import top.continew.admin.eve.mapper.EveStaticTypeReferenceMapper;
import top.continew.admin.eve.model.EveIndustryScanReq;
import top.continew.admin.eve.model.EveMarketRefreshResult;
import top.continew.admin.eve.model.entity.EveIndustryBlueprintDO;
import top.continew.admin.eve.model.entity.EveIndustryConfigDO;
import top.continew.admin.eve.model.entity.EveIndustryFacilityProfileDO;
import top.continew.starter.core.exception.BusinessException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 工业扫描等待市场批次完成后才写入机会快照的回归测试。 */
class EveIndustryScanMarketStatusTest {

    private EveIndustryBlueprintMapper blueprintMapper;
    private EveIndustryConfigMapper configMapper;
    private EveIndustryFacilityProfileMapper facilityMapper;
    private EveIndustryOpportunityMapper opportunityMapper;
    private EveMarketService marketService;
    private TransactionTemplate transactionTemplate;
    private EveIndustryService service;

    @BeforeEach
    void setUp() {
        blueprintMapper = mock(EveIndustryBlueprintMapper.class);
        configMapper = mock(EveIndustryConfigMapper.class);
        facilityMapper = mock(EveIndustryFacilityProfileMapper.class);
        opportunityMapper = mock(EveIndustryOpportunityMapper.class);
        marketService = mock(EveMarketService.class);
        transactionTemplate = mock(TransactionTemplate.class);
        service = new EveIndustryService(mock(EveIndustryStaticVersionMapper.class), blueprintMapper, configMapper, facilityMapper, mock(EveIndustrySystemCostIndexMapper.class), mock(EveIndustryBlueprintOverrideMapper.class), mock(EveIndustryGoldenCaseMapper.class), opportunityMapper, mock(EveIndustryOpportunityMaterialMapper.class), mock(EveIndustryMarketInputSnapshotMapper.class), mock(EveIndustryMarketOrderSliceMapper.class), mock(EveIndustryPlanMapper.class), mock(EveIndustryPlanStepMapper.class), mock(EveIndustrySystemSnapshotMapper.class), mock(EveMarketSnapshotMapper.class), mock(EveStaticTypeReferenceMapper.class), marketService, mock(SerenityEsiClient.class), mock(CeveIndustryClient.class), new ObjectMapper(), transactionTemplate, new EveMarketProperties());
        UserContext context = new UserContext();
        context.setTenantId(10L);
        UserContextHolder.setContext(context, false);
        arrangeScanContext(10L);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clearContext();
    }

    @Test
    void shouldReturnPendingMarketProgressWithoutWritingOpportunity() {
        when(marketService.queueMarketTypes(any(), eq(true))).thenReturn(new EveMarketRefreshResult(List
            .of(100), List.of(), List.of(34)));

        var result = service.scanMarketStatus(request());

        assertThat(result.totalTypeCount()).isEqualTo(2);
        assertThat(result.readyTypeCount()).isEqualTo(1);
        assertThat(result.pendingTypeCount()).isEqualTo(1);
        assertThat(result.failedTypeCount()).isZero();
        assertThat(result.ready()).isFalse();
        verifyNoInteractions(opportunityMapper, transactionTemplate);
    }

    @Test
    void shouldReturnFailedMarketProgressWithoutWritingOpportunity() {
        when(marketService.queueMarketTypes(any(), eq(true))).thenReturn(new EveMarketRefreshResult(List
            .of(100), List.of(34), List.of()));

        var result = service.scanMarketStatus(request());

        assertThat(result.readyTypeCount()).isEqualTo(1);
        assertThat(result.pendingTypeCount()).isZero();
        assertThat(result.failedTypeCount()).isEqualTo(1);
        assertThat(result.ready()).isFalse();
        verifyNoInteractions(opportunityMapper, transactionTemplate);
    }

    @Test
    void shouldReturnReadyMarketProgressWithoutWritingOpportunity() {
        when(marketService.queueMarketTypes(any(), eq(true))).thenReturn(new EveMarketRefreshResult(List
            .of(34, 100), List.of(), List.of()));

        var result = service.scanMarketStatus(request());

        assertThat(result.readyTypeCount()).isEqualTo(2);
        assertThat(result.pendingTypeCount()).isZero();
        assertThat(result.failedTypeCount()).isZero();
        assertThat(result.ready()).isTrue();
        verifyNoInteractions(opportunityMapper, transactionTemplate);
    }

    @Test
    void shouldRejectConfigurationFromAnotherTenant() {
        arrangeScanContext(20L);

        assertThatThrownBy(() -> service.scanMarketStatus(request())).isInstanceOf(BusinessException.class)
            .hasMessageContaining("不属于当前租户");

        verifyNoInteractions(marketService, opportunityMapper, transactionTemplate);
    }

    @Test
    void shouldNotPersistOpportunityWhenMarketIsPending() {
        when(marketService.queueMarketTypes(any(), eq(true))).thenReturn(new EveMarketRefreshResult(List
            .of(100), List.of(), List.of(34)));

        assertThatThrownBy(() -> service.scan(request())).isInstanceOf(BusinessException.class)
            .hasMessageContaining("行情正在准备");

        verify(opportunityMapper, never()).insert(any(top.continew.admin.eve.model.entity.EveIndustryOpportunityDO.class));
        verify(transactionTemplate, never()).execute(any());
    }

    private void arrangeScanContext(Long tenantId) {
        EveIndustryConfigDO config = new EveIndustryConfigDO();
        config.setId(1L);
        config.setTenantId(tenantId);
        config.setEnabled(true);
        EveIndustryFacilityProfileDO facility = new EveIndustryFacilityProfileDO();
        facility.setId(2L);
        facility.setTenantId(10L);
        facility.setEnabled(true);
        EveIndustryBlueprintDO blueprint = new EveIndustryBlueprintDO();
        blueprint.setProductTypeId(100);
        blueprint.setMaterialsJson("[{\"typeId\":34,\"name\":\"三钛合金\",\"quantity\":10}]");
        when(configMapper.selectById(1L)).thenReturn(config);
        when(facilityMapper.selectById(2L)).thenReturn(facility);
        when(blueprintMapper.selectList(any())).thenReturn(List.of(blueprint));
    }

    private static EveIndustryScanReq request() {
        return new EveIndustryScanReq(1L, 2L, null, 100);
    }
}
