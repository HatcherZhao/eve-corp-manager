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
 * 当前用户授权角色的游戏内邮件本地缓存。
 *
 * <p>邮件按本站用户和角色绑定严格隔离；正文按需缓存，绝不被其他用户或军团管理员读取。</p>
 *
 * @author zhaoyuqing
 */
@Data
@TableName(value = "eve_game_mail", autoResultMap = true)
public class EveGameMailDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 本站用户 ID。 */
    private Long userId;
    /** 授权角色绑定记录 ID。 */
    private Long characterRefId;
    /** 授权角色游戏 ID。 */
    private Long characterId;
    /** 国服邮件 ID。 */
    private Long mailId;
    /** 发件人游戏 ID。 */
    private Long fromId;
    /** 邮件主题。 */
    private String subject;
    /** 国服邮件发送时间。 */
    private LocalDateTime sentAt;
    /** 是否已读。 */
    private Boolean isRead;
    /** 国服标签 ID 列表。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Integer> labels;
    /** 国服收件人快照。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<SerenityGameMailRecipient> recipients;
    /** 按需读取后缓存的正文。 */
    private String body;
    /** 正文最近从国服同步的时间。 */
    private LocalDateTime bodySynchronizedAt;
    /** 正文响应的上游缓存过期时间。 */
    private LocalDateTime bodySourceExpiresAt;
    /** 最近一次邮件头同步时间。 */
    private LocalDateTime lastSeenAt;
    /** 邮件头响应的上游缓存过期时间。 */
    private LocalDateTime sourceExpiresAt;
}
