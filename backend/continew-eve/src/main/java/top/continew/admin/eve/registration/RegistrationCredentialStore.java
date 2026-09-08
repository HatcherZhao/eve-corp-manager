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

package top.continew.admin.eve.registration;

import java.time.Duration;
import java.util.Optional;

/**
 * 一次性本站注册凭证存储。
 *
 * @author zhaoyuqing
 */
public interface RegistrationCredentialStore {

    /** 签发短期注册凭证。 */
    String issue(String browserBindingDigest, VerifiedRegistrationIdentity identity, Duration ttl);

    /** 校验并读取短期注册凭证及可恢复激活结果，但不提前消费。 */
    Optional<RegistrationCredentialSnapshot> find(String credential, String browserBindingDigest);

    /** 在数据库提交后记录稳定激活结果，供会话交付失败时重试。 */
    boolean markActivated(String credential, String browserBindingDigest, RegistrationActivation activation);

    /** 原子消费短期注册凭证。 */
    Optional<RegistrationCredentialSnapshot> consume(String credential, String browserBindingDigest);
}
