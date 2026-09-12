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

package top.continew.admin.eve.model;

import java.time.LocalDateTime;

/**
 * 面向站内阅读页的官网资讯安全响应，不暴露数据库或抓取内部标识。
 *
 * @author zhaoyuqing
 */
public record EveOfficialNewsResp(String category, String categoryLabel, String title, String summary,
                                  String originalUrl, String contentHtml, String contentText, String coverUrl,
                                  LocalDateTime publishedAt, LocalDateTime lastSyncedAt) {
}
