/*
 * Copyright 2026 EVE Corp Manager contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package top.continew.admin.eve.service;

import org.springframework.stereotype.Service;
import top.continew.admin.eve.model.WorkspaceResp;

import java.util.List;

/** ESI integration is deliberately disabled until the SSO validation milestone. */
@Service
public class WorkspaceService {
    public WorkspaceResp getWorkspace() {
        return new WorkspaceResp("serenity", "not_implemented", List.of(
            capability("corporation", "军团信息", "军团资料与联盟归属"),
            capability("assets", "军团资产", "资产、机库分区与容器层级"),
            capability("structures", "建筑设施", "建筑状态、服务与燃料到期"),
            capability("extractions", "月矿计划", "矿块拉取、到达与自然碎裂时间"),
            capability("mining", "采矿账本", "观察者记录的矿种、数量与角色"),
            capability("members", "军团人员", "成员名单与有权查看的追踪记录")
        ));
    }

    private WorkspaceResp.Capability capability(String key, String title, String description) {
        return new WorkspaceResp.Capability(key, title, description, "not_implemented");
    }
}
