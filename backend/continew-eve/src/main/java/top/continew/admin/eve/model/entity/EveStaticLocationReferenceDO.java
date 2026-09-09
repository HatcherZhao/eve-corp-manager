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

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * evedata.xlsx 中的星域、星座、星系和位置静态资料。
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_static_location_reference")
public class EveStaticLocationReferenceDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键。 */
    @TableId
    private Long id;
    /** 资料类型。 */
    private String referenceType;
    /** 游戏位置 ID。 */
    private Long referenceId;
    /** 中文位置名称。 */
    private String referenceName;
    /** 所属星系 ID。 */
    private Long solarSystemId;
    /** 所属星座 ID。 */
    private Long constellationId;
    /** 所属星域 ID。 */
    private Long regionId;
    /** 安全等级。 */
    private BigDecimal securityStatus;
    /** 来源资料更新时间。 */
    private LocalDateTime sourceUpdatedAt;
}
