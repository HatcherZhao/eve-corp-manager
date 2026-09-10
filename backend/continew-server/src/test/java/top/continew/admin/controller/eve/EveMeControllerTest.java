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

package top.continew.admin.controller.eve;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import top.continew.admin.eve.service.EveContextService;
import top.continew.admin.eve.service.EvePermissionRefreshService;
import top.continew.admin.system.service.EveSessionPermissionRefreshService;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

/**
 * EVE 工作台首屏上下文控制器测试。
 *
 * @author zhaoyuqing
 */
class EveMeControllerTest {

    /** 首屏应先自动复核过期游戏快照，再读取最新会话权限和能力矩阵。 */
    @Test
    void shouldRefreshExpiredGameSnapshotBeforeReturningWorkspaceContext() {
        EveContextService contextService = mock(EveContextService.class);
        EvePermissionRefreshService permissionRefreshService = mock(EvePermissionRefreshService.class);
        EveSessionPermissionRefreshService sessionPermissionRefreshService = mock(EveSessionPermissionRefreshService.class);
        EveMeController controller = new EveMeController(contextService, permissionRefreshService, sessionPermissionRefreshService);

        controller.getContext();

        InOrder inOrder = inOrder(permissionRefreshService, sessionPermissionRefreshService, contextService);
        inOrder.verify(permissionRefreshService).refreshIfSnapshotExpiredCurrent();
        inOrder.verify(sessionPermissionRefreshService).refreshCurrentEveUser();
        inOrder.verify(contextService).getCurrentContext();
    }
}
