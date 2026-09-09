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
 * 军团资产的当前完整快照条目。
 *
 * <p>每个游戏物品 ID 在同一军团仅保留一条当前记录；同步成功后未再次出现的条目会标记为
 * {@code MISSING}，从而保留最近一次快照的审计价值而不污染资产列表。</p>
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_corporation_asset")
public class EveCorporationAssetDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 军团绑定记录 ID。 */
    private Long corporationRefId;
    /** 游戏物品 ID。 */
    private Long itemId;
    /** 物品类型 ID。 */
    private Integer typeId;
    /** 最近解析到的物品类型名称。 */
    private String typeName;
    /** 游戏内为可命名物品设置的自定义名称。 */
    private String itemName;
    /** 是否为军团自有的玩家建筑。 */
    private Boolean corporationStructure;
    /** 上游位置 ID。 */
    private Long locationId;
    /** 最近解析到的位置名称。 */
    private String locationName;
    /** 顶层空间站或建筑所属的星系 ID。 */
    private Long solarSystemId;
    /** 顶层空间站或建筑所属的星系名称。 */
    private String solarSystemName;
    /** 上游位置类别。 */
    private String locationType;
    /** 上游仓位标记。 */
    private String locationFlag;
    /** 数量。 */
    private Integer quantity;
    /** 是否独立物品。 */
    private Boolean singleton;
    /** 是否蓝图拷贝。 */
    private Boolean blueprintCopy;
    /** 当前资产状态：ACTIVE 或 MISSING。 */
    private String status;
    /** 最近一次完整快照中观察到的时间。 */
    private LocalDateTime lastSeenAt;
    /** 上游资产列表缓存过期时间。 */
    private LocalDateTime sourceExpiresAt;
}
