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

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import top.continew.admin.common.base.model.entity.TenantBaseDO;
import top.continew.admin.eve.model.serenity.SerenityGameMailRecipient;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 游戏内邮件发送审计记录。
 *
 * <p>只保存发送元数据，不保存正文；无法确定上游是否已接收时保留 UNKNOWN 以避免自动重发。</p>
 *
 * @author zhaoyuqing
 */
@Data
@TableName(value = "eve_game_mail_send_audit", autoResultMap = true)
public class EveGameMailSendAuditDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 本站用户 ID。 */
    private Long userId;
    /** 授权角色绑定记录 ID。 */
    private Long characterRefId;
    /** 发件角色游戏 ID。 */
    private Long characterId;
    /** 国服成功返回的邮件 ID。 */
    private Long mailId;
    /** 收件人快照。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<SerenityGameMailRecipient> recipients;
    /** 邮件主题。 */
    private String subject;
    /** PENDING、SENT、FAILED 或 UNKNOWN。 */
    private String status;
    /** 脱敏后的失败分类。 */
    private String failureCode;
    /** 请求发起时间。 */
    private LocalDateTime requestedAt;
    /** 结果写入时间。 */
    private LocalDateTime completedAt;
}
