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

package top.continew.admin.controller.eve;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import top.continew.admin.eve.model.EveCorporationAssetResp;
import top.continew.admin.eve.service.EveCorporationAssetService;
import top.continew.admin.eve.service.EveManualSyncRequestService;
import top.continew.admin.eve.service.EveStaticReferenceService;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 军团资产导出接口测试。
 *
 * @author zhaoyuqing
 */
class EveCorporationAssetControllerTest {

    /** 导出必须保留筛选条件、使用 UTF-8 BOM，并对表格公式文本进行安全转义。 */
    @Test
    void shouldExportFilteredAssetsAsSafeCsv() throws Exception {
        EveCorporationAssetService assetService = mock(EveCorporationAssetService.class);
        EveCorporationAssetController controller = new EveCorporationAssetController(assetService, mock(EveManualSyncRequestService.class), mock(EveStaticReferenceService.class));
        when(assetService.listForExport("火箭", "station")).thenReturn(List
            .of(new EveCorporationAssetResp(1001L, 34, "三钛合金", "=危险名称", 60003760L, "吉他 IV - 月面 4 - 加达里海军物流支援站", "station", "CorpSAG1", 12, true, false, LocalDateTime
                .of(2026, 9, 10, 12, 0), LocalDateTime.of(2026, 9, 10, 13, 0))));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export("火箭", "station", response);

        assertThat(response.getContentType()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=corp-assets.csv");
        assertThat(response.getContentAsString()).startsWith("\uFEFF物品ID").contains("\"'=危险名称\"", "\"是\"", "\"否\"");
        verify(assetService).listForExport("火箭", "station");
    }
}
