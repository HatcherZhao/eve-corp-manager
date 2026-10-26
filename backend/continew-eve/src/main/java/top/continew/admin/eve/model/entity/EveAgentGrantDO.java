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
 * 军团授予外部 Agent 应用的独立只读授权。
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_agent_grant")
public class EveAgentGrantDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 外部应用 ID。 */
    private Long applicationId;
    /** 获授权军团绑定记录 ID。 */
    private Long corporationRefId;
    /** 获授权游戏军团 ID。 */
    private Long corporationId;
    /** ACTIVE 或 REVOKED。 */
    private String status;
    /** 授权时间，按 UTC 保存。 */
    private LocalDateTime grantedAt;
    /** 授权操作者。 */
    private Long grantedBy;
    /** 撤销时间，按 UTC 保存。 */
    private LocalDateTime revokedAt;
    /** 撤销操作者。 */
    private Long revokedBy;
}
