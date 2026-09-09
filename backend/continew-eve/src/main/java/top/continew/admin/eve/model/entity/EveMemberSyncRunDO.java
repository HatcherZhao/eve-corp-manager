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
 * EVE 成员资源同步批次审计实体，不保存上游原始报文或令牌。
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_member_sync_run")
public class EveMemberSyncRunDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 军团绑定记录 ID。 */
    private Long corporationRefId;
    /** 资源类型：ROSTER 或 TRACKING。 */
    private String resource;
    /** 结果状态：SUCCEEDED、FAILED 或 SKIPPED。 */
    private String status;
    /** 本次处理记录数。 */
    private Integer recordCount;
    /** 上游缓存过期时间。 */
    private LocalDateTime sourceExpiresAt;
    /** 可安全持久化的失败分类。 */
    private String failureCode;
    /** 开始时间。 */
    private LocalDateTime startedAt;
    /** 完成时间。 */
    private LocalDateTime finishedAt;
}
