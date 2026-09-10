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

package top.continew.admin.eve.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.time.Duration;

/**
 * 国服公开图片的本地持久化缓存配置。
 *
 * @author zhaoyuqing
 */
@Data
@Component
@ConfigurationProperties(prefix = "eve.image-cache")
public class EveImageCacheProperties {

    /** 网易国服公开图片服务基址。 */
    private String baseUrl = "https://image.evepc.163.com";

    /** 缓存文件根目录；生产部署应挂载为可持久化数据卷。 */
    private String cacheDirectory = "./data/eve-images";

    /** 类型图标刷新周期；舰船、建筑与普通物品均使用类型图标。 */
    private Duration typeTtl = Duration.ofDays(365);

    /** 军团徽标刷新周期。 */
    private Duration corporationTtl = Duration.ofDays(180);

    /** 角色肖像刷新周期。 */
    private Duration characterTtl = Duration.ofDays(90);

    /** 下发给浏览器的本站缓存时间。 */
    private Duration browserMaxAge = Duration.ofDays(30);

    /** 上游故障时允许浏览器继续使用旧响应的最长时间。 */
    private Duration browserStaleIfError = Duration.ofDays(365);

    /** 启动时校验缓存配置，防止运行时写入工作目录外的意外位置。 */
    @PostConstruct
    public void validate() {
        if (baseUrl == null || !baseUrl.matches("https://[^\\s/]+(?:/.*)?")) {
            throw new IllegalStateException("EVE 图片服务必须配置为 HTTPS 地址");
        }
        if (cacheDirectory == null || cacheDirectory.isBlank()) {
            throw new IllegalStateException("EVE 图片缓存目录不能为空");
        }
        validateTtl(typeTtl, "type-ttl");
        validateTtl(corporationTtl, "corporation-ttl");
        validateTtl(characterTtl, "character-ttl");
        validateTtl(browserMaxAge, "browser-max-age");
        validateTtl(browserStaleIfError, "browser-stale-if-error");
    }

    /** 校验各类缓存周期必须为正数。 */
    private static void validateTtl(Duration value, String property) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException("EVE 图片缓存周期必须大于零：" + property);
        }
    }
}
