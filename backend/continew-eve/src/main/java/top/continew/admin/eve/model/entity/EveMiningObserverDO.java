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
 * 军团采矿账本观察者的当前完整快照。
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_mining_observer")
public class EveMiningObserverDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 军团绑定记录 ID。 */
    private Long corporationRefId;
    /** 国服观察者实体 ID。 */
    private Long observerId;
    /** 国服观察者类别。 */
    private String observerType;
    /** 可解析时保存的观察者名称快照。 */
    private String observerName;
    /** 国服为该观察者返回的最后账本日期。 */
    private LocalDate sourceLastUpdated;
    /** 当前快照状态：ACTIVE 或 MISSING。 */
    private String status;
    /** 最近完成观察者清单同步时间。 */
    private LocalDateTime lastSeenAt;
    /** 观察者清单上游缓存过期时间。 */
    private LocalDateTime sourceExpiresAt;
}
