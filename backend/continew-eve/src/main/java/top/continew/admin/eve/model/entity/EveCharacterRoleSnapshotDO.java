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

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import top.continew.admin.common.base.model.entity.TenantBaseDO;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.List;

/**
 * EVE 角色军团权限快照实体。
 *
 * @author zhaoyuqing
 */
@Data
@TableName(value = "eve_character_role_snapshot", autoResultMap = true)
public class EveCharacterRoleSnapshotDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色绑定记录 ID。 */
    private Long characterRefId;
    /** 服务器代码。 */
    private String server;
    /** EVE 角色 ID。 */
    private Long characterId;
    /** 采集时是否为军团 CEO。 */
    private Boolean isCeo;
    /** 全局军团角色。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> roles;
    /** 总部军团角色。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> rolesAtHq;
    /** 基地军团角色。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> rolesAtBase;
    /** 其他地点军团角色。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> rolesAtOther;
    /** 快照采集时间。 */
    private LocalDateTime capturedAt;
    /** ESI 缓存过期时间。 */
    private LocalDateTime sourceExpiresAt;
    /** 是否因上游未提供 Expires 而使用本地 TTL 估算。 */
    private Boolean sourceExpiryEstimated;
}
