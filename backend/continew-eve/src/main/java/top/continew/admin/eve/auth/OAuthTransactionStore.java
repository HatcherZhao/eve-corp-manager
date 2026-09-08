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

package top.continew.admin.eve.auth;

import java.time.Duration;
import java.util.Optional;

/**
 * OAuth 短期事务存储。
 *
 * @author zhaoyuqing
 */
public interface OAuthTransactionStore {

    /**
     * 保存事务，state 只用于生成不可逆 Redis key。
     *
     * @param state       随机 state
     * @param transaction 事务内容
     * @param ttl         有效期
     */
    void save(String state, OAuthTransaction transaction, Duration ttl);

    /**
     * 原子消费事务，确保同一 state 只能使用一次。
     *
     * @param state 回调 state
     * @return 被消费的事务
     */
    Optional<OAuthTransaction> consume(String state);
}
