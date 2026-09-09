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
import java.time.LocalDateTime;

/**
 * evedata.xlsx 中的 EVE 类型静态资料。
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_static_type_reference")
public class EveStaticTypeReferenceDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键。 */
    @TableId
    private Long id;
    /** 游戏类型 ID。 */
    private Integer typeId;
    /** 中文类型名称。 */
    private String typeName;
    /** 类型说明。 */
    private String typeDescription;
    /** 第一市场分类。 */
    private String marketCategoryL1;
    /** 第二市场分类。 */
    private String marketCategoryL2;
    /** 第三市场分类。 */
    private String marketCategoryL3;
    /** 第四市场分类。 */
    private String marketCategoryL4;
    /** 第五市场分类。 */
    private String marketCategoryL5;
    /** 第六市场分类。 */
    private String marketCategoryL6;
    /** 来源资料更新时间。 */
    private LocalDateTime sourceUpdatedAt;
}
