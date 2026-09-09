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

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * 军团成员名册项；追踪字段在无权限时严格返回空。
 *
 * @author zhaoyuqing
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EveMemberResp(Long id, Long characterId, String characterName, String organizationGroup,
                            String memberNote, String status, LocalDateTime joinedAt, LocalDateTime leftAt,
                            LocalDateTime lastSeenAt, EveMemberTrackingResp tracking) {
}
