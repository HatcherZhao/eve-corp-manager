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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import top.continew.admin.eve.config.EveImageCacheProperties;
import top.continew.starter.core.exception.BusinessException;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 将网易公开的 EVE 图片保存为服务器文件缓存，并在上游临时故障时保留旧图片可用。
 *
 * @author zhaoyuqing
 */
@Service
public class EveImageCacheService {

    /** 单张国服图片的可接受最大体积，避免异常响应耗尽磁盘。 */
    private static final int MAX_IMAGE_SIZE = 4 * 1024 * 1024;

    private final RestClient restClient;
    private final EveImageCacheProperties properties;
    private final ConcurrentHashMap<String, Object> refreshLocks = new ConcurrentHashMap<>();

    /** 创建使用国服 HTTP 超时设置的图片缓存服务。 */
    public EveImageCacheService(@Qualifier("serenityRestClient") RestClient restClient,
                                EveImageCacheProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /** 获取指定类型的图标缓存，适用于舰船、建筑与普通物品。 */
    public CachedImage typeIcon(long typeId) {
        return resolve(ImageKind.TYPE, typeId);
    }

    /** 获取指定角色的游戏肖像缓存。 */
    public CachedImage characterPortrait(long characterId) {
        return resolve(ImageKind.CHARACTER, characterId);
    }

    /** 获取指定军团的游戏徽标缓存。 */
    public CachedImage corporationLogo(long corporationId) {
        return resolve(ImageKind.CORPORATION, corporationId);
    }

    /** 命中有效文件时直接返回；缓存过期后仅由一个请求刷新，失败则返回旧文件。 */
    private CachedImage resolve(ImageKind kind, long id) {
        if (id <= 0) {
            throw new BusinessException("EVE 图片 ID 无效");
        }
        Path target = cachePath(kind, id);
        if (isFresh(target, kind.ttl(properties))) {
            return cached(target, kind);
        }
        Object lock = refreshLocks.computeIfAbsent(kind.name() + ':' + id, ignored -> new Object());
        synchronized (lock) {
            try {
                if (!isFresh(target, kind.ttl(properties))) {
                    refresh(kind, id, target);
                }
                return cached(target, kind);
            } catch (BusinessException e) {
                if (Files.isRegularFile(target)) {
                    return cached(target, kind);
                }
                throw e;
            } finally {
                refreshLocks.remove(kind.name() + ':' + id, lock);
            }
        }
    }

    /** 生成受控缓存路径，ID 与扩展名均不接受客户端输入。 */
    private Path cachePath(ImageKind kind, long id) {
        return Path.of(properties.getCacheDirectory())
            .toAbsolutePath()
            .normalize()
            .resolve(kind.directory)
            .resolve(id + kind.extension);
    }

    /** 判断缓存文件是否仍在配置的刷新周期内。 */
    private static boolean isFresh(Path path, Duration ttl) {
        try {
            return Files.isRegularFile(path) && Files.getLastModifiedTime(path)
                .toInstant()
                .plus(ttl)
                .isAfter(Instant.now());
        } catch (IOException e) {
            return false;
        }
    }

    /** 从国服下载图片并通过原子替换发布，避免并发读取到半写入文件。 */
    private void refresh(ImageKind kind, long id, Path target) {
        byte[] body;
        try {
            body = restClient.get().uri(upstreamUrl(kind, id)).retrieve().body(byte[].class);
        } catch (RestClientException e) {
            throw new BusinessException("国服图片服务暂不可用");
        }
        if (body == null || body.length == 0 || body.length > MAX_IMAGE_SIZE) {
            throw new BusinessException("国服图片响应无效");
        }
        try {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
            try {
                Files.write(temporary, body, StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new BusinessException("无法写入 EVE 图片缓存");
        }
    }

    /** 生成固定国服图片地址，避免将任意远程地址带入服务端请求。 */
    private String upstreamUrl(ImageKind kind, long id) {
        return properties.getBaseUrl().replaceAll("/+$", "") + kind.upstreamPath(id);
    }

    /** 封装已落盘图片的路径与固定媒体类型。 */
    private static CachedImage cached(Path path, ImageKind kind) {
        return new CachedImage(path, kind.mediaType);
    }

    /** 已缓存图片的安全响应信息。 */
    public record CachedImage(Path path, MediaType mediaType) {
    }

    /** 图片类别、固定扩展与官方路径的受控映射。 */
    private enum ImageKind {
        TYPE("types", ".png", MediaType.IMAGE_PNG, "/Type/%d_64.png") {
            @Override
            Duration ttl(EveImageCacheProperties properties) {
                return properties.getTypeTtl();
            }
        },
        CHARACTER("characters", ".jpg", MediaType.IMAGE_JPEG, "/Character/%d_128.jpg") {
            @Override
            Duration ttl(EveImageCacheProperties properties) {
                return properties.getCharacterTtl();
            }
        },
        CORPORATION("corporations", ".png", MediaType.IMAGE_PNG, "/Corporation/%d_128.png") {
            @Override
            Duration ttl(EveImageCacheProperties properties) {
                return properties.getCorporationTtl();
            }
        };

        private final String directory;
        private final String extension;
        private final MediaType mediaType;
        private final String upstreamPattern;

        ImageKind(String directory, String extension, MediaType mediaType, String upstreamPattern) {
            this.directory = directory;
            this.extension = extension;
            this.mediaType = mediaType;
            this.upstreamPattern = upstreamPattern;
        }

        /** 返回当前图片类别的刷新周期。 */
        abstract Duration ttl(EveImageCacheProperties properties);

        /** 返回对应 ID 的国服公开图片相对路径。 */
        String upstreamPath(long id) {
            return upstreamPattern.formatted(id);
        }
    }
}
