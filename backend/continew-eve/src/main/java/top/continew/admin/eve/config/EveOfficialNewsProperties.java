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

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 网易 EVE 国服官网资讯同步配置。
 *
 * <p>栏目地址在代码中固定为官方 HTTPS 地址，配置仅控制节流和故障后的重试窗口。</p>
 *
 * @author zhaoyuqing
 */
@Data
@Component
@ConfigurationProperties(prefix = "eve.official-news")
public class EveOfficialNewsProperties {

    /** 是否启用官网公开资讯同步。 */
    private boolean enabled = true;
    /** 列表与正文请求的连接、读取超时。 */
    private Duration timeout = Duration.ofSeconds(20);
    /** 单个 HTML 文档允许的最大体积。 */
    private long maxDocumentBytes = 2 * 1024 * 1024;
    /** 已存在文章的正文复核间隔，官网改稿后会更新本地快照。 */
    private Duration contentRecheckInterval = Duration.ofHours(6);

    /** 启动时校验外部输入边界，避免错误配置放大抓取流量。 */
    @PostConstruct
    public void validate() {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalStateException("官网资讯抓取超时必须大于零");
        }
        if (maxDocumentBytes <= 0) {
            throw new IllegalStateException("官网资讯 HTML 最大体积必须大于零");
        }
        if (contentRecheckInterval == null || contentRecheckInterval.isZero() || contentRecheckInterval.isNegative()) {
            throw new IllegalStateException("官网资讯正文复核间隔必须大于零");
        }
    }
}
