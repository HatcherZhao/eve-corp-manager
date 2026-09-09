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
import java.util.List;

/**
 * 带上游分页信息的 ESI 响应。
 *
 * @param body      当前页的数据
 * @param expiresAt 上游缓存过期时间
 * @param etag      上游实体标签
 * @param pageCount 上游声明的总页数
 * @author zhaoyuqing
 */
public record SerenityEsiPagedResponse<T>(List<T> body, LocalDateTime expiresAt, String etag, int pageCount) {
}
