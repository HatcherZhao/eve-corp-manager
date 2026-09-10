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

import cn.dev33.satoken.annotation.SaIgnore;
import com.feiniaojin.gracefulresponse.api.ExcludeFromGracefulResponse;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.eve.config.EveImageCacheProperties;
import top.continew.admin.eve.service.EveImageCacheService;

import java.io.IOException;

/**
 * EVE 国服公开图片的本站缓存读取接口；图片为公开资料，不要求本站登录态。
 *
 * @author zhaoyuqing
 */
@Hidden
@SaIgnore
@RestController
@RequiredArgsConstructor
@RequestMapping("/eve/images")
public class EveImageController {

    private final EveImageCacheService imageCacheService;
    private final EveImageCacheProperties imageCacheProperties;

    /** 返回服务器缓存的 EVE 类型图标。 */
    @ExcludeFromGracefulResponse
    @GetMapping("/types/{typeId}.png")
    public ResponseEntity<FileSystemResource> typeIcon(@PathVariable long typeId) throws IOException {
        return response(imageCacheService.typeIcon(typeId));
    }

    /** 返回服务器缓存的 EVE 角色肖像。 */
    @ExcludeFromGracefulResponse
    @GetMapping("/characters/{characterId}.jpg")
    public ResponseEntity<FileSystemResource> characterPortrait(@PathVariable long characterId) throws IOException {
        return response(imageCacheService.characterPortrait(characterId));
    }

    /** 返回服务器缓存的 EVE 军团徽标。 */
    @ExcludeFromGracefulResponse
    @GetMapping("/corporations/{corporationId}.png")
    public ResponseEntity<FileSystemResource> corporationLogo(@PathVariable long corporationId) throws IOException {
        return response(imageCacheService.corporationLogo(corporationId));
    }

    /** 将已落盘图片以长浏览器缓存响应返回，不将二进制包装为统一 JSON 响应。 */
    private ResponseEntity<FileSystemResource> response(EveImageCacheService.CachedImage image) throws IOException {
        FileSystemResource resource = new FileSystemResource(image.path());
        long lastModified = resource.lastModified();
        long contentLength = resource.contentLength();
        return ResponseEntity.ok()
            .contentType(image.mediaType())
            .contentLength(contentLength)
            .lastModified(lastModified)
            .eTag("\"" + lastModified + "-" + contentLength + "\"")
            .cacheControl(CacheControl.maxAge(imageCacheProperties.getBrowserMaxAge())
                .cachePublic()
                .staleIfError(imageCacheProperties.getBrowserStaleIfError()))
            .body(resource);
    }
}
