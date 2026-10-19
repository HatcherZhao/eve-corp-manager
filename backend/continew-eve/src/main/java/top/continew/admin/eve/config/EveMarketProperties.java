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
 * 吉他贸易中心市场数据的同步与缓存配置。
 *
 * <p>市场资料为全站共享的公开参考数据，只由后台通过国服官方 ESI 同步，
 * 页面与手动操作不会直接向上游发起请求。</p>
 *
 * @author zhaoyuqing
 */
@Data
@Component
@ConfigurationProperties(prefix = "eve.market")
public class EveMarketProperties {

    /** 是否启用公开市场数据读取。 */
    private boolean enabled = true;
    /** The Forge 区域 ID，吉他所在地的官方市场订单目录。 */
    private long regionId = 10000002L;
    /** 吉他星系 ID；订单同步只保留该星系订单，保持既有市场口径。 */
    private long solarSystemId = 30000142L;
    /** 可选的固定空间站 ID；为空时包含吉他星系全部公开订单。 */
    private Long stationId;
    /** 完整订单簿同步间隔；同步过程中全部请求经全局 ESI 节流器串行执行。 */
    private Duration orderSyncInterval = Duration.ofMinutes(30);
    /** 报价在页面上的最大新鲜度，超过后仅显示本地快照的过期状态。 */
    private Duration quoteRefreshInterval = Duration.ofMinutes(45);
    /** 订单簿详情在页面上的最大新鲜度，超过后不触发按需抓取。 */
    private Duration detailRefreshInterval = Duration.ofMinutes(45);
    /** 区域历史补温间隔；每轮只请求极少数物品，避免向官方 ESI 形成高频扫描。 */
    private Duration historyWarmupInterval = Duration.ofMinutes(5);
    /** 区域历史补温单轮最多处理的物品数。 */
    private int historyWarmupLimit = 1;

    /** 校验缓存窗口与批量上限，避免错误配置放大第三方请求。 */
    @PostConstruct
    public void validate() {
        if (regionId <= 0 || solarSystemId <= 0 || (stationId != null && stationId <= 0)) {
            throw new IllegalStateException("EVE 市场区域、星系或空间站配置无效");
        }
        if (orderSyncInterval == null || orderSyncInterval.isNegative() || orderSyncInterval.isZero()
            || quoteRefreshInterval == null || quoteRefreshInterval.isNegative() || quoteRefreshInterval.isZero()
            || detailRefreshInterval == null || detailRefreshInterval.isNegative() || detailRefreshInterval.isZero()
            || historyWarmupInterval == null || historyWarmupInterval.isNegative() || historyWarmupInterval.isZero()) {
            throw new IllegalStateException("EVE 市场缓存间隔必须大于零");
        }
        if (historyWarmupLimit < 1 || historyWarmupLimit > 10) {
            throw new IllegalStateException("EVE 市场历史补温数量必须在 1 到 10 之间");
        }
    }
}
