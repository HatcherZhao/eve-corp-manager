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

package top.continew.admin.eve.agent;

/**
 * 外部 Agent 应用身份及经过单团授权解析的查询范围。
 *
 * <p>仅通过密钥认证时军团范围为空，不能直接用于业务查询；选择有效授权军团后才填入范围。</p>
 *
 * @author zhaoyuqing
 */
public record EveAgentPrincipal(Long applicationId, Long tenantId, Long corporationRefId, Long corporationId,
                                String applicationName) {
}
