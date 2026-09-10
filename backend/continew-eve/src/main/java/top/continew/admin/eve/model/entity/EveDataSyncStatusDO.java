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
 * 军团数据模块最近同步结果的统一摘要。
 *
 * <p>只保存安全的失败分类，绝不保存 OAuth 令牌、上游报文或异常详情。</p>
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_data_sync_status")
public class EveDataSyncStatusDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 军团绑定记录 ID。 */
    private Long corporationRefId;
    /** 数据模块代码。 */
    private String module;
    /** 最近一次成功发布完整快照的时间。 */
    private LocalDateTime lastSuccessfulAt;
    /** 最近成功响应声明的上游缓存过期时间。 */
    private LocalDateTime sourceExpiresAt;
    /** 最近一次失败的时间。 */
    private LocalDateTime lastFailureAt;
    /** 可安全展示的失败分类。 */
    private String failureCode;
}
