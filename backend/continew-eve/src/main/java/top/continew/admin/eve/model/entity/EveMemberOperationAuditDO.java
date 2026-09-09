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

package top.continew.admin.eve.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.common.base.model.entity.TenantBaseDO;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 军团成员组织和权限变更的脱敏审计记录。
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_member_operation_audit")
public class EveMemberOperationAuditDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 游戏名册成员 ID；本站权限事件可为空。 */
    private Long rosterMemberId;
    /** 本站目标用户 ID；组织信息事件可为空。 */
    private Long targetUserId;
    /** 事件类型。 */
    private String eventType;
    /** 不含敏感详情的操作摘要。 */
    private String summary;
    /** 操作者本站用户 ID。 */
    private Long actorUserId;
    /** 操作者用户名快照。 */
    private String actorUsername;
    /** 发生时间。 */
    private LocalDateTime occurredAt;
}
