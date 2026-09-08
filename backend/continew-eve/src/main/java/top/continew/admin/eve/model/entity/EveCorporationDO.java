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
import top.continew.admin.eve.model.enums.EveCorporationStatus;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * EVE 军团与本站租户的一对一绑定实体。
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_corporation")
public class EveCorporationDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 服务器代码。 */
    private String server;
    /** EVE 军团 ID。 */
    private Long corporationId;
    /** 军团名称。 */
    private String name;
    /** 军团简称。 */
    private String ticker;
    /** CEO 角色 ID。 */
    private Long ceoCharacterId;
    /** 联盟 ID。 */
    private Long allianceId;
    /** 成员数量。 */
    private Integer memberCount;
    /** 军团税率。 */
    private BigDecimal taxRate;
    /** 军团租户绑定状态。 */
    private EveCorporationStatus status;
    /** 最后同步时间。 */
    private LocalDateTime lastSyncedAt;
}
