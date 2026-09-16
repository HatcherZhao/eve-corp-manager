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

package top.continew.admin.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.continew.admin.eve.service.EveMarketService;

/**
 * 低频保温近期使用的吉他市场缓存，不做全量物品轮询。
 *
 * @author zhaoyuqing
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EveMarketSyncJob {

    private final EveMarketService marketService;

    /** 默认每三十分钟刷新近期访问过且详情已到期的物品。 */
    @Scheduled(initialDelayString = "${eve.market.initial-delay:PT90S}", fixedDelayString = "${eve.market.recent-refresh-interval:PT30M}")
    public void synchronizeRecentSnapshots() {
        try {
            marketService.refreshRecentSnapshots();
        } catch (RuntimeException e) {
            log.warn("吉他市场近期缓存刷新异常，errorType={}", e.getClass().getSimpleName());
        }
    }

    /** 自动补齐全部可交易物品的历史日线，不以用户是否浏览作为同步条件。 */
    @Scheduled(initialDelayString = "${eve.market.history-warmup-initial-delay:PT15S}", fixedDelayString = "${eve.market.history-warmup-interval:PT20S}")
    public void warmPriceHistories() {
        try {
            marketService.warmPriceHistories();
        } catch (RuntimeException e) {
            log.warn("吉他市场价格历史补温异常，errorType={}", e.getClass().getSimpleName());
        }
    }

    /** 自动轮转刷新整个可交易物品目录的吉他报价。 */
    @Scheduled(initialDelayString = "${eve.market.quote-warmup-initial-delay:PT10S}", fixedDelayString = "${eve.market.quote-warmup-interval:PT15S}")
    public void warmMarketQuotes() {
        try {
            marketService.warmMarketQuotes();
        } catch (RuntimeException e) {
            log.warn("吉他市场全量报价补温异常，errorType={}", e.getClass().getSimpleName());
        }
    }
}
