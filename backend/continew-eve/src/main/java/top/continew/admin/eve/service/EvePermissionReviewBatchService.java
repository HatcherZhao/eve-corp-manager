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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.starter.extension.tenant.util.TenantUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 按国服角色缓存周期分批复核有效授权。
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvePermissionReviewBatchService {

    private final EveAuthorizationMapper authorizationMapper;
    private final EvePermissionRefreshService permissionRefreshService;
    private final SerenityProperties properties;

    /**
     * 复核一批已超过缓存周期的授权；上游失败由刷新服务归一，未知异常使批次失败并触发告警。
     *
     * @return 本批成功发起复核的数量
     */
    public int reviewBatch() {
        if (!properties.getSso().isEnabled()) {
            return 0;
        }
        LocalDateTime selectedAt = LocalDateTime.now();
        LocalDateTime roleRefreshBefore = selectedAt.minus(properties.getPermissionRefresh().getRoleCacheTtl());
        LocalDateTime tokenRefreshBefore = LocalDateTime.now(ZoneOffset.UTC)
            .plus(properties.getPermissionRefresh().getTokenRefreshSkew())
            .plus(properties.getPermissionRefresh().getBackgroundReviewInterval());
        List<EveAuthorizationDO> authorizations = authorizationMapper
            .selectStaleActive(roleRefreshBefore, tokenRefreshBefore, selectedAt, properties.getPermissionRefresh()
                .getBatchSize());
        int reviewed = 0;
        for (EveAuthorizationDO authorization : authorizations) {
            LocalDateTime attemptedAt = LocalDateTime.now();
            LocalDateTime nextReviewAt = attemptedAt.plus(properties.getPermissionRefresh().getCooldown());
            if (authorizationMapper.claimReview(authorization.getTenantId(), authorization.getUserId(), authorization
                .getId(), attemptedAt, nextReviewAt) != 1) {
                continue;
            }
            try {
                TenantUtils.execute(authorization.getTenantId(), () -> permissionRefreshService
                    .reviewAuthorization(authorization));
                reviewed++;
            } catch (RuntimeException e) {
                log.error("EVE 权限后台复核出现未归类异常，tenantId={}，userId={}，errorType={}", authorization
                    .getTenantId(), authorization.getUserId(), e.getClass().getSimpleName(), e);
                throw e;
            }
        }
        return reviewed;
    }
}
