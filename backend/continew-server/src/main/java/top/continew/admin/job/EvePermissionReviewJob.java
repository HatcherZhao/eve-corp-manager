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
 * EVE 国服权限定时复核入口，实际频率仍受一小时角色缓存周期约束。
 *
 * @author zhaoyuqing
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "eve.serenity.sso", name = "enabled", havingValue = "true")
public class EvePermissionReviewJob {

    private final EvePermissionReviewBatchService reviewBatchService;

    /** 每十五分钟扫描一批到期授权，避免集中高频请求国服。 */
    @Scheduled(cron = "0 */15 * * * ?")
    public void review() {
        reviewBatchService.reviewBatch();
    }
}
