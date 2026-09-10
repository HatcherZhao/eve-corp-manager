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
 * 军团月矿情报的国服当前完整快照。
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_moon_extraction")
public class EveMoonExtractionDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 军团绑定记录 ID。 */
    private Long corporationRefId;
    /** 游戏炼化厂建筑 ID。 */
    private Long structureId;
    /** 炼化厂名称快照。 */
    private String structureName;
    /** 炼化厂类型名称快照。 */
    private String structureTypeName;
    /** 游戏月球 ID。 */
    private Long moonId;
    /** 月球名称快照。 */
    private String moonName;
    /** 所在星系 ID。 */
    private Long solarSystemId;
    /** 所在星系名称快照。 */
    private String solarSystemName;
    /** 国服提取开始时间。 */
    private LocalDateTime extractionStartAt;
    /** 国服矿块到达时间。 */
    private LocalDateTime chunkArrivalAt;
    /** 国服矿块自然碎裂时间。 */
    private LocalDateTime naturalDecayAt;
    /** 当前快照状态：ACTIVE 或 MISSING。 */
    private String status;
    /** 最近完整同步时间。 */
    private LocalDateTime lastSeenAt;
    /** 上游缓存过期时间。 */
    private LocalDateTime sourceExpiresAt;
    /** 军团内部维护的月矿简短备注。 */
    private String note;
}
