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
 * 当前用户授权角色的游戏通知本地缓存。
 *
 * <p>通知同邮件一样属于角色个人资源，缓存键必须隔离至本站用户及角色绑定。</p>
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_game_notification")
public class EveGameNotificationDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 本站用户 ID。 */
    private Long userId;
    /** 授权角色绑定记录 ID。 */
    private Long characterRefId;
    /** 授权角色游戏 ID。 */
    private Long characterId;
    /** 国服通知 ID。 */
    private Long notificationId;
    /** 是否已读，仅展示国服返回事实。 */
    private Boolean isRead;
    /** 通知发送方游戏 ID。 */
    private Long senderId;
    /** 通知发送方实体类型。 */
    private String senderType;
    /** 国服原始通知类型。 */
    private String notificationType;
    /** 平台稳定分类。 */
    private String category;
    /** 国服通知正文。 */
    private String content;
    /** 通知产生时间。 */
    private LocalDateTime sentAt;
    /** 最近一次同步时间。 */
    private LocalDateTime lastSeenAt;
    /** 国服列表响应有效期。 */
    private LocalDateTime sourceExpiresAt;
}
