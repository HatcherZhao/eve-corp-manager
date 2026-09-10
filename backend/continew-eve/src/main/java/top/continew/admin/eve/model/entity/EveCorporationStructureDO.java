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
 * 军团自有玩家建筑的当前完整快照。
 *
 * @author zhaoyuqing
 */
@Data
@TableName(value = "eve_corporation_structure", autoResultMap = true)
public class EveCorporationStructureDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 军团绑定记录 ID。 */
    private Long corporationRefId;
    /** 游戏建筑 ID。 */
    private Long structureId;
    /** 建筑类型 ID。 */
    private Integer typeId;
    /** 建筑类型中文名称快照。 */
    private String typeName;
    /** 游戏内建筑自定义名称。 */
    private String structureName;
    /** 所在星系 ID。 */
    private Long solarSystemId;
    /** 所在星系名称快照。 */
    private String solarSystemName;
    /** 国服建筑状态。 */
    private String state;
    /** 燃料耗尽时间；上游未返回时为 null。 */
    private LocalDateTime fuelExpiresAt;
    /** 建筑状态计时开始时间。 */
    private LocalDateTime stateTimerStartAt;
    /** 建筑状态计时结束时间。 */
    private LocalDateTime stateTimerEndAt;
    /** 拆锚时间。 */
    private LocalDateTime unanchorsAt;
    /** 建筑服务及状态快照。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<StructureService> services;
    /** 当前快照状态：ACTIVE 或 MISSING。 */
    private String status;
    /** 最近完整同步时间。 */
    private LocalDateTime lastSeenAt;
    /** 上游缓存过期时间。 */
    private LocalDateTime sourceExpiresAt;

    /** 建筑服务展示快照。 */
    public record StructureService(String name, String state) {
    }
}
