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
 * 军团成员运营追踪快照，仅在通过本站追踪权限后读取和返回。
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_corporation_member_tracking")
public class EveCorporationMemberTrackingDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 名册成员 ID。 */
    private Long rosterMemberId;
    /** 上游基地 ID。 */
    private Long baseId;
    /** 基地名称解析快照。 */
    private String baseName;
    /** 上游位置 ID。 */
    private Long locationId;
    /** 位置名称解析快照。 */
    private String locationName;
    /** 上游舰船类型 ID。 */
    private Long shipTypeId;
    /** 舰船类型名称解析快照。 */
    private String shipTypeName;
    /** 上游最近登录记录时间，不代表实时在线。 */
    private LocalDateTime lastLogonAt;
    /** 上游最近登出记录时间，不代表实时在线。 */
    private LocalDateTime lastLogoffAt;
    /** 上游数据观测时间。 */
    private LocalDateTime sourceObservedAt;
    /** 上游缓存过期时间。 */
    private LocalDateTime sourceExpiresAt;
}
