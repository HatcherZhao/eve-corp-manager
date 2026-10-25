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

package top.continew.admin.eve.agent.model;

import top.continew.admin.eve.model.EveDateTime;

import java.time.LocalDateTime;

/**
 * 外部 Agent 应用安全展示模型，不包含凭据摘要。
 *
 * @author zhaoyuqing
 */
public record EveAgentApplicationResp(Long id, String name, String status,
                                      @EveDateTime(zone = "Asia/Shanghai") LocalDateTime createdAt,
                                      @EveDateTime LocalDateTime expiresAt, @EveDateTime LocalDateTime lastUsedAt,
                                      String tokenPrefix) {
}
