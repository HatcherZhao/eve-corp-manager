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

/**
 * 月矿堡的军团内部备注，独立于每轮国服提取快照以便长期保留。
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_moon_structure_note")
public class EveMoonStructureNoteDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 军团绑定记录 ID。 */
    private Long corporationRefId;
    /** 游戏月矿堡（精炼厂）建筑 ID。 */
    private Long structureId;
    /** 军团维护的月矿堡备注。 */
    private String note;
}
