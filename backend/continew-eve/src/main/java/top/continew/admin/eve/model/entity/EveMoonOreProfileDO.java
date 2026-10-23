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
 * 按军团月矿堡长期保存的矿种档案。
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_moon_ore_profile")
public class EveMoonOreProfileDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 军团绑定记录 ID。 */
    private Long corporationRefId;
    /** 游戏月矿堡建筑 ID。 */
    private Long structureId;
    /** 最近匹配的月球 ID。 */
    private Long moonId;
    /** 档案状态：READY 或 PENDING。 */
    private String status;
    /** 矿种及归一化产出比例 JSON。 */
    private String oresJson;
    /** 供矿种名称搜索的冗余文本。 */
    private String oreNames;
    /** 来源通知 ID，仅用于内部幂等与追踪。 */
    private Long sourceNotificationId;
    /** 来源通知发送时间。 */
    private LocalDateTime sourceSentAt;
    /** PENDING 档案最近一次检查本地通知缓存的时间。 */
    private LocalDateTime lastCacheCheckedAt;
}
