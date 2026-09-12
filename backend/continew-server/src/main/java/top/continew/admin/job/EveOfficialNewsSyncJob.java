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
import top.continew.admin.eve.service.EveOfficialNewsService;

/**
 * 低频检查网易 EVE 国服官网公开资讯的定时入口。
 *
 * <p>具体锁、栏目节流和失败状态由服务层统一处理；定时频率不等于对官网的并发请求数。</p>
 *
 * @author zhaoyuqing
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EveOfficialNewsSyncJob {

    private final EveOfficialNewsService officialNewsService;

    /** 默认每十分钟检查一次，服务启动后延迟三十秒完成首轮公开资讯同步。 */
    @Scheduled(initialDelayString = "${eve.official-news.initial-delay:PT30S}", fixedDelayString = "${eve.official-news.scan-interval:PT10M}")
    public void synchronize() {
        try {
            officialNewsService.synchronize();
        } catch (RuntimeException e) {
            log.warn("EVE 国服官网资讯定时同步异常，errorType={}", e.getClass().getSimpleName());
        }
    }
}
