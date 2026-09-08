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

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import top.continew.admin.eve.config.SerenityProperties;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.model.EvePermissionRefreshResp;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.enums.EvePermissionRefreshStatus;
import top.continew.starter.extension.tenant.util.TenantUtils;

import java.util.List;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * 后台权限复核批处理测试。
 *
 * @author zhaoyuqing
 */
class EvePermissionReviewBatchServiceTest {

    /** 同一租户用户的每条过期授权都必须逐条复核，次要角色不得饿死。 */
    @Test
    void shouldReviewEveryStaleAuthorization() {
        EveAuthorizationMapper mapper = mock(EveAuthorizationMapper.class);
        EvePermissionRefreshService refreshService = mock(EvePermissionRefreshService.class);
        SerenityProperties properties = new SerenityProperties();
        properties.getSso().setEnabled(true);
        EveAuthorizationDO first = authorization(1L, 10L, 20L);
        EveAuthorizationDO second = authorization(2L, 10L, 20L);
        EveAuthorizationDO third = authorization(3L, 10L, 21L);
        when(mapper.selectStaleActive(any(), any(), anyInt())).thenReturn(List.of(first, second, third));
        when(mapper.claimReview(anyLong(), anyLong(), anyLong(), any(), any())).thenReturn(1);
        EvePermissionReviewBatchService service = new EvePermissionReviewBatchService(mapper, refreshService, properties);

        try (MockedStatic<TenantUtils> tenantUtils = mockStatic(TenantUtils.class)) {
            tenantUtils.when(() -> TenantUtils.execute(any(), any(Runnable.class))).thenAnswer(invocation -> {
                invocation.<Runnable>getArgument(1).run();
                return null;
            });
            assertThat(service.reviewBatch()).isEqualTo(3);
            verify(refreshService, times(1)).reviewAuthorization(first);
            verify(refreshService, times(1)).reviewAuthorization(second);
            verify(refreshService, times(1)).reviewAuthorization(third);
        }
    }

    /** 未知运行时异常必须终止批次，不能被后台任务长期吞掉。 */
    @Test
    void shouldPropagateUnexpectedReviewFailure() {
        EveAuthorizationMapper mapper = mock(EveAuthorizationMapper.class);
        EvePermissionRefreshService refreshService = mock(EvePermissionRefreshService.class);
        SerenityProperties properties = new SerenityProperties();
        properties.getSso().setEnabled(true);
        EveAuthorizationDO first = authorization(1L, 10L, 20L);
        EveAuthorizationDO second = authorization(2L, 10L, 21L);
        when(mapper.selectStaleActive(any(), any(), anyInt())).thenReturn(List.of(first, second));
        when(mapper.claimReview(anyLong(), anyLong(), anyLong(), any(), any())).thenReturn(1);
        doThrow(new IllegalStateException("upstream unavailable")).when(refreshService).reviewAuthorization(first);
        EvePermissionReviewBatchService service = new EvePermissionReviewBatchService(mapper, refreshService, properties);

        try (MockedStatic<TenantUtils> tenantUtils = mockStatic(TenantUtils.class)) {
            tenantUtils.when(() -> TenantUtils.execute(any(), any(Runnable.class))).thenAnswer(invocation -> {
                invocation.<Runnable>getArgument(1).run();
                return null;
            });

            org.assertj.core.api.Assertions.assertThatThrownBy(service::reviewBatch)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("upstream unavailable");
            verify(refreshService).reviewAuthorization(first);
            verify(refreshService, never()).reviewAuthorization(second);
        }
    }

    /** 失败任务进入有限退避后，下一批必须继续处理未被前一页覆盖的授权。 */
    @Test
    void shouldReviewLaterAuthorizationsAfterTemporaryFailures() {
        EveAuthorizationMapper mapper = mock(EveAuthorizationMapper.class);
        EvePermissionRefreshService refreshService = mock(EvePermissionRefreshService.class);
        SerenityProperties properties = new SerenityProperties();
        properties.getSso().setEnabled(true);
        properties.getPermissionRefresh().setBatchSize(2);
        EveAuthorizationDO firstFailed = authorization(1L, 10L, 20L);
        EveAuthorizationDO secondFailed = authorization(2L, 10L, 21L);
        EveAuthorizationDO later = authorization(3L, 10L, 22L);
        when(mapper.selectStaleActive(any(), any(), anyInt())).thenReturn(List.of(firstFailed, secondFailed), List
            .of(later));
        when(mapper.claimReview(anyLong(), anyLong(), anyLong(), any(), any())).thenReturn(1);
        EvePermissionRefreshResp unavailable = new EvePermissionRefreshResp(EvePermissionRefreshStatus.UPSTREAM_UNAVAILABLE, LocalDateTime
            .now(), null, null, false, null, null, List.of(), null, List.of(), List.of(), null, List.of());
        when(refreshService.reviewAuthorization(firstFailed)).thenReturn(unavailable);
        when(refreshService.reviewAuthorization(secondFailed)).thenReturn(unavailable);
        EvePermissionReviewBatchService service = new EvePermissionReviewBatchService(mapper, refreshService, properties);

        try (MockedStatic<TenantUtils> tenantUtils = mockStatic(TenantUtils.class)) {
            tenantUtils.when(() -> TenantUtils.execute(any(), any(Runnable.class))).thenAnswer(invocation -> {
                invocation.<Runnable>getArgument(1).run();
                return null;
            });

            assertThat(service.reviewBatch()).isEqualTo(2);
            assertThat(service.reviewBatch()).isEqualTo(1);
        }

        verify(mapper, times(2)).selectStaleActive(any(), any(), anyInt());
        verify(mapper, times(3)).claimReview(anyLong(), anyLong(), anyLong(), any(), any());
        verify(refreshService).reviewAuthorization(later);
    }

    /** 创建跨租户扫描返回的授权摘要。 */
    private static EveAuthorizationDO authorization(Long id, Long tenantId, Long userId) {
        EveAuthorizationDO authorization = new EveAuthorizationDO();
        authorization.setId(id);
        authorization.setTenantId(tenantId);
        authorization.setUserId(userId);
        return authorization;
    }
}
