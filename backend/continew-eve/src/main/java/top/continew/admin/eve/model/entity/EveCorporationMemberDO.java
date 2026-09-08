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
import top.continew.admin.eve.model.enums.EveCorporationMemberStatus;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * EVE 军团租户成员关系实体。
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_corporation_member")
public class EveCorporationMemberDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 军团绑定记录 ID。 */
    private Long corporationRefId;
    /** 角色绑定记录 ID。 */
    private Long characterRefId;
    /** 本站用户 ID。 */
    private Long userId;
    /** 成员关系状态。 */
    private EveCorporationMemberStatus status;
    /** 加入军团时间。 */
    private LocalDateTime joinedAt;
    /** 离开军团时间。 */
    private LocalDateTime leftAt;
    /** 最后成员身份验证时间。 */
    private LocalDateTime lastVerifiedAt;
}
