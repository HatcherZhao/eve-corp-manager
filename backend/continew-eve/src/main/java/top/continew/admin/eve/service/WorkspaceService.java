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
import org.springframework.stereotype.Service;
import top.continew.admin.eve.model.EveMeContextResp;
import top.continew.admin.eve.model.WorkspaceResp;

import java.util.List;

/** 根据当前用户能力策略生成工作台模块状态。 */
@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final EveContextService contextService;

    /** 返回当前用户真实可用的工作台能力。 */
    public WorkspaceResp getWorkspace() {
        EveMeContextResp context = contextService.getCurrentContext();
        List<WorkspaceResp.Capability> capabilities = context.capabilities()
            .stream()
            .map(item -> new WorkspaceResp.Capability(item.key(), item.title(), item.description(), item.status()
                .name()))
            .toList();
        return new WorkspaceResp("serenity", context.authorizationStatus().name(), capabilities);
    }
}
