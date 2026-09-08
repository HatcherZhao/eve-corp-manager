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

package top.continew.admin.eve.model.serenity;

import java.time.LocalDateTime;

/**
 * ESI 响应体及上游缓存元数据。
 *
 * @param body      响应体
 * @param expiresAt 上游 Expires 时间，未提供时为空
 * @param etag      上游 ETag，未提供时为空
 * @param <T>       响应体类型
 * @author zhaoyuqing
 */
public record SerenityEsiResponse<T>(T body, LocalDateTime expiresAt, String etag) {
}
