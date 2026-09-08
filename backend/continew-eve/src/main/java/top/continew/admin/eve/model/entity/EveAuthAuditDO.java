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
import top.continew.admin.eve.model.enums.EveAuthAuditEventType;
import top.continew.admin.eve.model.enums.EveAuthAuditResult;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * EVE 国服授权审计实体，详情字段仅允许保存脱敏信息。
 *
 * <p>租户 ID 使用可空包装类型；军团认领前的失败事件不属于任何租户，只允许系统级审计按精确请求 ID 查询。</p>
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_auth_audit")
public class EveAuthAuditDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 授权记录 ID。 */
    private Long authorizationId;
    /** 角色绑定记录 ID。 */
    private Long characterRefId;
    /** 本站用户 ID。 */
    private Long userId;
    /** 授权事件类型。 */
    private EveAuthAuditEventType eventType;
    /** 事件结果。 */
    private EveAuthAuditResult result;
    /** 请求追踪 ID。 */
    private String requestId;
    /** 客户端 IP。 */
    private String clientIp;
    /** 客户端标识。 */
    private String userAgent;
    /** 脱敏后的审计详情。 */
    private String detail;
    /** 事件发生时间。 */
    private LocalDateTime occurredAt;
}
