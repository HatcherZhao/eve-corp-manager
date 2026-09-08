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
import lombok.ToString;
import top.continew.admin.common.base.model.entity.TenantBaseDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.model.enums.EveAuthorizationVerificationStatus;
import top.continew.starter.encrypt.field.annotation.FieldEncrypt;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.List;

/**
 * EVE 国服 OAuth 授权实体。
 *
 * <p>访问令牌与刷新令牌由字段加密组件透明加解密，并明确排除在日志字符串之外。</p>
 *
 * @author zhaoyuqing
 */
@Data
@TableName(value = "eve_authorization", autoResultMap = true)
public class EveAuthorizationDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色绑定记录 ID。 */
    private Long characterRefId;
    /** 本站用户 ID。 */
    private Long userId;
    /** 服务器代码。 */
    private String server;
    /** 已授权 OAuth Scope 集合。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> scopes;
    /** 加密保存的访问令牌。 */
    @FieldEncrypt
    @ToString.Exclude
    private String accessToken;
    /** 加密保存的刷新令牌。 */
    @FieldEncrypt
    @ToString.Exclude
    private String refreshToken;
    /** 令牌类型。 */
    private String tokenType;
    /** 访问令牌过期时间。 */
    private LocalDateTime expiresAt;
    /** 刷新令牌过期时间。 */
    private LocalDateTime refreshExpiresAt;
    /** 授权生命周期状态。 */
    private EveAuthorizationStatus status;
    /** 授权撤销时间。 */
    private LocalDateTime revokedAt;
    /** 连续失败次数。 */
    private Integer failureCount;
    /** 脱敏后的最后失败代码，不保存上游原始响应。 */
    private String failureCode;
    /** 最近一次授权验证状态。 */
    private EveAuthorizationVerificationStatus lastVerificationStatus;
    /** 最后验证时间。 */
    private LocalDateTime lastVerifiedAt;
    /** 最近一次后台复核尝试时间。 */
    private LocalDateTime lastAttemptAt;
    /** 下一次允许后台复核的时间。 */
    private LocalDateTime nextReviewAt;
}
