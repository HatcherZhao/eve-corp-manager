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
 * EVE 静态资料的最近导入版本。
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_static_reference_import")
public class EveStaticReferenceImportDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 资料集名称。 */
    @TableId
    private String referenceName;
    /** 来源文件名。 */
    private String sourceFileName;
    /** 来源文件 SHA-256。 */
    private String sourceSha256;
    /** 来源文件更新时间。 */
    private LocalDateTime sourceUpdatedAt;
    /** 导入完成时间。 */
    private LocalDateTime importedAt;
    /** 本次写入记录数。 */
    private Integer recordCount;
}
