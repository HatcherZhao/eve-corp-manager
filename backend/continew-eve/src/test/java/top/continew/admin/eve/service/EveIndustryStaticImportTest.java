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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import top.continew.admin.eve.client.SerenityEsiClient;
import top.continew.admin.eve.mapper.EveIndustryBlueprintMapper;
import top.continew.admin.eve.mapper.EveIndustryBlueprintOverrideMapper;
import top.continew.admin.eve.mapper.EveIndustryConfigMapper;
import top.continew.admin.eve.mapper.EveIndustryFacilityProfileMapper;
import top.continew.admin.eve.mapper.EveIndustryGoldenCaseMapper;
import top.continew.admin.eve.mapper.EveIndustryOpportunityMapper;
import top.continew.admin.eve.mapper.EveIndustryOpportunityMaterialMapper;
import top.continew.admin.eve.mapper.EveIndustryStaticVersionMapper;
import top.continew.admin.eve.mapper.EveIndustrySystemCostIndexMapper;
import top.continew.admin.eve.mapper.EveMarketSnapshotMapper;
import top.continew.admin.eve.mapper.EveStaticTypeReferenceMapper;
import top.continew.admin.eve.model.EveIndustryStaticImportResp;
import top.continew.admin.eve.model.entity.EveIndustryBlueprintDO;
import top.continew.admin.eve.model.entity.EveIndustryStaticVersionDO;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 规范化工业静态数据导入的全量校验与安全状态测试。 */
@ExtendWith(MockitoExtension.class)
class EveIndustryStaticImportTest {

    @Mock
    private EveIndustryStaticVersionMapper staticVersionMapper;
    @Mock
    private EveIndustryBlueprintMapper blueprintMapper;
    @Mock
    private EveIndustryConfigMapper configMapper;
    @Mock
    private EveIndustryFacilityProfileMapper facilityMapper;
    @Mock
    private EveIndustrySystemCostIndexMapper systemCostIndexMapper;
    @Mock
    private EveIndustryBlueprintOverrideMapper overrideMapper;
    @Mock
    private EveIndustryGoldenCaseMapper goldenCaseMapper;
    @Mock
    private EveIndustryOpportunityMapper opportunityMapper;
    @Mock
    private EveIndustryOpportunityMaterialMapper opportunityMaterialMapper;
    @Mock
    private EveMarketSnapshotMapper marketSnapshotMapper;
    @Mock
    private EveStaticTypeReferenceMapper typeReferenceMapper;
    @Mock
    private EveMarketService marketService;
    @Mock
    private SerenityEsiClient esiClient;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks
    private EveIndustryService service;

    /** 缺少配方时给出玩家可直接定位的补齐入口。 */
    @Test
    void shouldExplainMissingBlueprintDataInChinese() {
        when(blueprintMapper.selectCount(any())).thenReturn(0L);

        assertThat(service.dataStatus().message()).contains("数据与校验", "同步公开蓝图").doesNotContain("可执行", "SCI");
    }

    /** 配方可用不代表所有设施与行情已就绪，提示仍需选择生产条件。 */
    @Test
    void shouldDescribeAvailableBlueprintsWithoutPromisingProductionReadiness() {
        when(blueprintMapper.selectCount(any())).thenReturn(2L, 1L);

        assertThat(service.dataStatus().message()).contains("生产配置", "设施", "分析利润");
    }

    @Test
    void shouldImportValidatedJsonAsPendingBlueprint() {
        doAnswer(invocation -> {
            EveIndustryStaticVersionDO version = invocation.getArgument(0);
            version.setId(100L);
            return 1;
        }).when(staticVersionMapper).insert(any(EveIndustryStaticVersionDO.class));
        String json = """
            [{
              "blueprintTypeId": 1001,
              "blueprintName": "测试蓝图",
              "activity": "manufacturing",
              "productTypeId": 2001,
              "productName": "测试产物",
              "productQuantity": 1,
              "baseTimeSeconds": 60,
              "materials": [{"typeId": 34, "name": "三钛合金", "quantity": 10}]
            }]
            """;

        EveIndustryStaticImportResp result = service.importStaticData("industry.json", json
            .getBytes(StandardCharsets.UTF_8), "SERENITY_EXPORT", "3535651", null, "eve-industry-normalized-v1");

        assertThat(result.imported()).isTrue();
        assertThat(result.activityCount()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();
        verify(blueprintMapper).insert(org.mockito.ArgumentMatchers.<EveIndustryBlueprintDO>argThat(item -> !item
            .getSupported() && "PENDING".equals(item.getValidationStatus()) && "MANUFACTURING".equals(item
                .getActivity())));
    }

    @Test
    void shouldRejectWholeFileWhenAnyActivityIsInvalid() {
        String jsonl = """
            {"blueprintTypeId":1001,"activity":"MANUFACTURING","productTypeId":2001,"productQuantity":1,"baseTimeSeconds":60,"materials":[]}
            {not-json}
            """;

        EveIndustryStaticImportResp result = service.importStaticData("industry.jsonl", jsonl
            .getBytes(StandardCharsets.UTF_8), "SERENITY_EXPORT", "3535651", null, "eve-industry-normalized-v1");

        assertThat(result.imported()).isFalse();
        assertThat(result.errors()).extracting("code").contains("MATERIALS", "JSONL_PARSE");
        verify(staticVersionMapper, never()).insert(any(EveIndustryStaticVersionDO.class));
        verify(blueprintMapper, never()).insert(any(EveIndustryBlueprintDO.class));
    }
}
