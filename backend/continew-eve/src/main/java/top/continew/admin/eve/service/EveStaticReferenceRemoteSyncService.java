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

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import top.continew.admin.eve.config.EveStaticReferenceRemoteSyncProperties;
import top.continew.admin.eve.model.EveStaticReferenceImportResp;
import top.continew.starter.core.exception.BusinessException;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * 下载可信公开 evedata.xlsx 并复用原子导入流程更新静态资料。
 *
 * <p>下载文件仅在导入期间落入系统临时目录；内容、大小和工作簿校验全部通过后，
 * {@link EveStaticReferenceService} 才会替换数据库快照。</p>
 *
 * @author zhaoyuqing
 */
@Service
public class EveStaticReferenceRemoteSyncService {

    private final EveStaticReferenceRemoteSyncProperties properties;
    private final EveStaticReferenceService staticReferenceService;
    private final RestClient restClient;

    /** 创建公开资料同步服务。 */
    public EveStaticReferenceRemoteSyncService(EveStaticReferenceRemoteSyncProperties properties,
                                               EveStaticReferenceService staticReferenceService,
                                               @Qualifier("eveStaticReferenceRemoteSyncRestClient") RestClient restClient) {
        this.properties = properties;
        this.staticReferenceService = staticReferenceService;
        this.restClient = restClient;
    }

    /** 下载公开资料并在内容发生变化时原子导入。 */
    public EveStaticReferenceImportResp synchronize() {
        URI sourceUri = requireHttpsSource();
        ResponseEntity<byte[]> response = download(sourceUri);
        byte[] content = response.getBody();
        validateContent(response, content);
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile("eve-static-reference-remote-", ".xlsx");
            Files.write(temporaryFile, content);
            if (response.getHeaders().getLastModified() > 0) {
                Files.setLastModifiedTime(temporaryFile, FileTime.fromMillis(response.getHeaders().getLastModified()));
            }
            return staticReferenceService.importWorkbook(temporaryFile, sourceFileName(sourceUri), false);
        } catch (IOException e) {
            throw new BusinessException("无法暂存 EVE 公开静态资料文件");
        } finally {
            deleteTemporaryFile(temporaryFile);
        }
    }

    /** 仅允许 HTTPS 公开源，避免运维配置意外降级为不可信明文下载。 */
    private URI requireHttpsSource() {
        try {
            URI sourceUri = URI.create(properties.getSourceUrl());
            if (!"https".equalsIgnoreCase(sourceUri.getScheme()) || sourceUri.getHost() == null) {
                throw new IllegalArgumentException("公开资料地址必须使用 HTTPS");
            }
            return sourceUri;
        } catch (IllegalArgumentException e) {
            throw new BusinessException("EVE 公开静态资料地址配置无效");
        }
    }

    /** 下载文件正文；HTTP 状态异常由 RestClient 转换为安全的本站异常。 */
    private ResponseEntity<byte[]> download(URI sourceUri) {
        try {
            return restClient.get().uri(sourceUri).retrieve().toEntity(byte[].class);
        } catch (RestClientException e) {
            throw new BusinessException("下载 EVE 公开静态资料失败");
        }
    }

    /** 校验下载体大小，工作簿结构和必填列由后续导入服务完整校验。 */
    private void validateContent(ResponseEntity<byte[]> response, byte[] content) {
        long maxDownloadBytes = properties.getMaxDownloadBytes();
        if (maxDownloadBytes <= 0 || content == null || content.length == 0 || content.length > maxDownloadBytes || response
            .getHeaders()
            .getContentLength() > maxDownloadBytes) {
            throw new BusinessException("EVE 公开静态资料文件无效或超过大小限制");
        }
    }

    /** 从公开地址提取安全的显示文件名，不接受响应头提供的任意文件名。 */
    private static String sourceFileName(URI sourceUri) {
        String path = sourceUri.getPath();
        int separator = path == null ? -1 : path.lastIndexOf('/');
        String fileName = separator < 0 ? path : path.substring(separator + 1);
        return fileName != null && fileName.toLowerCase().endsWith(".xlsx") ? fileName : "evedata.xlsx";
    }

    /** 删除单次下载使用的临时文件，失败时由操作系统回收。 */
    private static void deleteTemporaryFile(Path temporaryFile) {
        if (temporaryFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException ignored) {
            // 临时文件不包含授权或用户数据，遗留文件由系统最终回收。
        }
    }
}
