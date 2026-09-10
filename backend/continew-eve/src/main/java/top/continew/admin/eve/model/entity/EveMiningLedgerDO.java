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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 国服观察者采矿账本的可追溯聚合明细。
 *
 * <p>同一自然键在后续同步中覆盖数量和名称快照，不会按同步次数累加。</p>
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_mining_ledger")
public class EveMiningLedgerDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 军团绑定记录 ID。 */
    private Long corporationRefId;
    /** 观察者实体 ID。 */
    private Long observerId;
    /** 观察者名称快照。 */
    private String observerName;
    /** 采矿角色游戏 ID。 */
    private Long characterId;
    /** 采矿角色名称快照。 */
    private String characterName;
    /** 国服记录的角色当时所属军团 ID。 */
    private Long recordedCorporationId;
    /** 矿物类型 ID。 */
    private Integer typeId;
    /** 矿物类型名称快照。 */
    private String typeName;
    /** 国服账本日期。 */
    private LocalDate recordedAt;
    /** 国服聚合数量。 */
    private Long quantity;
    /** 最近成功同步发现该条目的时间。 */
    private LocalDateTime lastSeenAt;
    /** 明细上游缓存过期时间。 */
    private LocalDateTime sourceExpiresAt;
}
