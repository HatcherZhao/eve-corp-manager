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
import top.continew.admin.eve.model.enums.EveCharacterStatus;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * EVE 游戏角色与本站用户的绑定实体。
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_character")
public class EveCharacterDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 本站用户 ID。 */
    private Long userId;
    /** 服务器代码。 */
    private String server;
    /** EVE 角色 ID。 */
    private Long characterId;
    /** EVE 军团 ID。 */
    private Long corporationId;
    /** 用于识别角色转手的所有者校验摘要。 */
    private String ownerHash;
    /** 游戏角色名称。 */
    private String name;
    /** 角色绑定状态。 */
    private EveCharacterStatus status;
    /** 是否为本站用户的主角色。 */
    private Boolean isPrimary;
    /** 加入当前军团的时间。 */
    private LocalDateTime joinedAt;
    /** 最后身份验证时间。 */
    private LocalDateTime lastVerifiedAt;
}
