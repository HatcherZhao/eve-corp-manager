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

package top.continew.admin.eve.security;

/**
 * 国服 OAuth 回调校验异常，消息中不得包含原始 URL 或敏感参数。
 *
 * @author zhaoyuqing
 */
public class SerenityCallbackException extends RuntimeException {

    /**
     * 创建安全的回调异常。
     *
     * @param message 不含敏感值的固定错误说明
     */
    public SerenityCallbackException(String message) {
        super(message);
    }
}
