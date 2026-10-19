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

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.common.base.model.entity.BaseDO;

import java.time.LocalDateTime;

/** 工业静态数据的可追溯版本。 */
@Data
@TableName("eve_industry_static_version")
@InterceptorIgnore(tenantLine = "true")
public class EveIndustryStaticVersionDO extends BaseDO {
    /** 数据来源。 */
    private String source;
    /** 国服服务器版本。 */
    private String serverVersion;
    /** 静态数据构建号。 */
    private Long buildNumber;
    /** 下载或导入时间。 */
    private LocalDateTime downloadedAt;
    /** 原文件 SHA-256。 */
    private String sha256;
    /** 导入结构版本。 */
    private String schemaVersion;
    /** 校验状态。 */
    private String validationStatus;
    /** 校验说明。 */
    private String validationNote;
}
