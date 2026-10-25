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
 * 外部 Agent 授权管理和工具调用审计。
 *
 * <p>不保存查询参数、返回正文、邮件正文或凭据。</p>
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_agent_audit")
public class EveAgentAuditDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 外部应用 ID。 */
    private Long applicationId;
    /** 审计时的应用名称快照。 */
    private String applicationName;
    /** 军团绑定记录 ID。 */
    private Long corporationRefId;
    /** SITE_USER 或 AGENT。 */
    private String actorType;
    /** 站内操作者 ID；Agent 调用时为空。 */
    private Long actorUserId;
    /** 工具名或授权管理事件名。 */
    private String toolName;
    /** 是否成功。 */
    private Boolean success;
    /** 调用耗时毫秒数。 */
    private Long durationMs;
    /** 返回记录数量，不包含返回内容。 */
    private Integer resultCount;
    /** 事件发生时间，按 UTC 保存。 */
    private LocalDateTime occurredAt;
}
