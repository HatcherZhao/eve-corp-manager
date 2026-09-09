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
 * 军团完整游戏成员名册实体，与本站账号绑定关系完全独立。
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_corporation_roster_member")
public class EveCorporationRosterMemberDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 军团绑定记录 ID。 */
    private Long corporationRefId;
    /** 游戏角色 ID。 */
    private Long characterId;
    /** 最近解析到的角色名称。 */
    private String characterName;
    /** 军团内部成员分组。 */
    private String organizationGroup;
    /** 军团内部成员备注。 */
    private String memberNote;
    /** 名册状态：ACTIVE 或 LEFT。 */
    private String status;
    /** 上游追踪数据提供的入团时间。 */
    private LocalDateTime joinedAt;
    /** 本站确认离团时间。 */
    private LocalDateTime leftAt;
    /** 最近一次完整名册发现时间。 */
    private LocalDateTime lastSeenAt;
}
