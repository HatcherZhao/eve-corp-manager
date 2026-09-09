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
 * 成员数据手动同步结果，追踪失败不影响基础名册发布。
 *
 * @author zhaoyuqing
 */
public record EveMemberSyncResp(int rosterCount, int trackingCount, boolean trackingSynchronized,
                                String trackingUnavailableReason, LocalDateTime synchronizedAt,
                                LocalDateTime rosterSourceExpiresAt, LocalDateTime trackingSourceExpiresAt) {
}
