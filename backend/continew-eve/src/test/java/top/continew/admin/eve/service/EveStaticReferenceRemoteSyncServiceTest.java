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

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import top.continew.admin.eve.config.EveStaticReferenceRemoteSyncProperties;
import top.continew.admin.eve.model.EveStaticReferenceImportResp;
import top.continew.starter.core.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * EVE 公开静态资料下载服务测试。
 *
 * @author zhaoyuqing
 */
class EveStaticReferenceRemoteSyncServiceTest {

    /** 下载成功后应把临时文件交给既有原子导入流程，且固定使用公开地址中的文件名。 */
    @Test
    void shouldDownloadWorkbookAndDelegateToAtomicImporter() throws Exception {
        byte[] workbook = "valid-xlsx-content".getBytes(StandardCharsets.UTF_8);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        EveStaticReferenceService importer = mock(EveStaticReferenceService.class);
        EveStaticReferenceImportResp expected = new EveStaticReferenceImportResp("evedata.xlsx", null, 12, 8, true);
        doAnswer(invocation -> {
            Path temporaryFile = invocation.getArgument(0);
            assertThat(Files.readAllBytes(temporaryFile)).isEqualTo(workbook);
            return expected;
        }).when(importer).importWorkbook(any(Path.class), eq("evedata.xlsx"), eq(false));
        server.expect(requestTo("https://example.test/dumps/evedata.xlsx"))
            .andExpect(method(GET))
            .andRespond(withSuccess(workbook, MediaType
                .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));

        EveStaticReferenceRemoteSyncService service = new EveStaticReferenceRemoteSyncService(properties(), importer, client);

        assertThat(service.synchronize()).isSameAs(expected);
        verify(importer).importWorkbook(any(Path.class), eq("evedata.xlsx"), eq(false));
        server.verify();
    }

    /** 超过配置上限的响应不得触发数据库导入。 */
    @Test
    void shouldRejectOversizedDownloadBeforeImport() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        EveStaticReferenceService importer = mock(EveStaticReferenceService.class);
        EveStaticReferenceRemoteSyncProperties properties = properties();
        properties.setMaxDownloadBytes(3);
        server.expect(requestTo("https://example.test/dumps/evedata.xlsx"))
            .andExpect(method(GET))
            .andRespond(withSuccess("1234", MediaType.APPLICATION_OCTET_STREAM));

        EveStaticReferenceRemoteSyncService service = new EveStaticReferenceRemoteSyncService(properties, importer, client);

        assertThatThrownBy(service::synchronize).isInstanceOf(BusinessException.class)
            .hasMessage("EVE 公开静态资料文件无效或超过大小限制");
        verifyNoInteractions(importer);
        server.verify();
    }

    /** 创建测试用固定公开源配置。 */
    private static EveStaticReferenceRemoteSyncProperties properties() {
        EveStaticReferenceRemoteSyncProperties properties = new EveStaticReferenceRemoteSyncProperties();
        properties.setSourceUrl("https://example.test/dumps/evedata.xlsx");
        return properties;
    }
}
