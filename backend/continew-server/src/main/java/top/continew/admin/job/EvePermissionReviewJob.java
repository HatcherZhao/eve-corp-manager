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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.continew.admin.eve.service.EvePermissionReviewBatchService;

/**
 * EVE 国服权限定时复核入口。
 *
 * <p>任务高频扫描即将到期的访问令牌并自动轮换；角色事实仍按缓存周期请求，避免无意义地高频访问国服。</p>
 *
 * @author zhaoyuqing
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "eve.serenity.sso", name = "enabled", havingValue = "true")
public class EvePermissionReviewJob {

    private final EvePermissionReviewBatchService reviewBatchService;

    /** 默认每分钟扫描一批授权；角色数据和令牌轮换窗口在服务层分别限流。 */
    @Scheduled(fixedDelayString = "${eve.serenity.permission-refresh.background-review-interval:PT1M}")
    public void review() {
        reviewBatchService.reviewBatch();
    }
}
