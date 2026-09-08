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

package top.continew.admin.eve.recovery;

import java.time.Duration;
import java.util.Optional;

/**
 * EVE 身份验证后的短期一次性本站密码重置凭证存储。
 *
 * @author zhaoyuqing
 */
public interface PasswordRecoveryCredentialStore {

    /** 签发绑定浏览器的短期密码重置凭证。 */
    String issue(String browserBindingDigest, Long tenantId, Long userId, Duration ttl);

    /** 原子消费短期密码重置凭证。 */
    Optional<PasswordRecoveryIdentity> consume(String credential, String browserBindingDigest);
}
