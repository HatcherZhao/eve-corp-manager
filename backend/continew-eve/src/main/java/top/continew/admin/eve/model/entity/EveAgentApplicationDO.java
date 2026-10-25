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
 * 军团授予外部 AI Agent 的只读应用凭据。
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_agent_application")
public class EveAgentApplicationDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 军团绑定记录 ID。 */
    private Long corporationRefId;
    /** 游戏军团 ID，便于鉴权时冻结军团边界。 */
    private Long corporationId;
    /** 应用名称。 */
    private String name;
    /** 凭据 SHA-256 摘要，数据库不保存原始凭据。 */
    private String tokenHash;
    /** 仅供管理员识别凭据的非敏感前缀。 */
    private String tokenPrefix;
    /** ACTIVE 或 REVOKED。 */
    private String status;
    /** 凭据过期时间，按 UTC 保存。 */
    private LocalDateTime expiresAt;
    /** 最近成功完成调用的时间，按 UTC 保存。 */
    private LocalDateTime lastUsedAt;
    /** 最近轮换时间，按 UTC 保存。 */
    private LocalDateTime lastRotatedAt;
    /** 撤销时间，按 UTC 保存。 */
    private LocalDateTime revokedAt;
}
