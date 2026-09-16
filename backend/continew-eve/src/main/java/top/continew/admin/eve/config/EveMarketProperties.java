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
 * 吉他贸易中心公开市场数据的读取与缓存配置。
 *
 * <p>市场资料为全站共享的公开参考数据，不依赖军团或角色授权。</p>
 *
 * @author zhaoyuqing
 */
@Data
@Component
@ConfigurationProperties(prefix = "eve.market")
public class EveMarketProperties {

    /** 是否启用公开市场数据读取。 */
    private boolean enabled = true;
    /** CEVE Market 的国服公开数据根地址。 */
    private String baseUrl = "https://www.ceve-market.org";
    /** 上游连接与读取超时。 */
    private Duration timeout = Duration.ofSeconds(15);
    /** 列表报价的最短更新间隔，打开分页时最多一次批量查询二十个物品。 */
    private Duration quoteRefreshInterval = Duration.ofMinutes(15);
    /** 订单与日线详情的最短更新间隔。 */
    private Duration detailRefreshInterval = Duration.ofMinutes(30);
    /** 定时任务刷新近期浏览过物品的间隔。 */
    private Duration recentRefreshInterval = Duration.ofMinutes(30);
    /** 定时任务单轮最多刷新多少个近期浏览物品，保护公开上游。 */
    private int recentRefreshLimit = 40;
    /** 全量历史补温单轮最多处理的物品数，单项仅请求一次历史接口。 */
    private int historyWarmupLimit = 5;
    /** 全量报价轮转单轮最多处理的物品数，接口每次最多合并二十项。 */
    private int quoteWarmupLimit = 20;

    /** 校验缓存窗口与批量上限，避免错误配置放大第三方请求。 */
    @PostConstruct
    public void validate() {
        if (baseUrl == null || !baseUrl.startsWith("https://")) {
            throw new IllegalStateException("EVE 市场数据源必须使用 HTTPS 地址");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalStateException("EVE 市场请求超时必须大于零");
        }
        if (quoteRefreshInterval == null || quoteRefreshInterval.isNegative() || quoteRefreshInterval
            .isZero() || detailRefreshInterval == null || detailRefreshInterval.isNegative() || detailRefreshInterval
                .isZero() || recentRefreshInterval == null || recentRefreshInterval
                    .isNegative() || recentRefreshInterval.isZero()) {
            throw new IllegalStateException("EVE 市场缓存间隔必须大于零");
        }
        if (recentRefreshLimit < 1 || recentRefreshLimit > 100) {
            throw new IllegalStateException("EVE 市场近期刷新数量必须在 1 到 100 之间");
        }
        if (historyWarmupLimit < 1 || historyWarmupLimit > 100) {
            throw new IllegalStateException("EVE 市场历史补温数量必须在 1 到 100 之间");
        }
        if (quoteWarmupLimit < 1 || quoteWarmupLimit > 20) {
            throw new IllegalStateException("EVE 市场报价补温数量必须在 1 到 20 之间");
        }
    }
}
