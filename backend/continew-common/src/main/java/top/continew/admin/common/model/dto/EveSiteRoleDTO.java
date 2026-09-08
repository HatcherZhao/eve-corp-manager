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

package top.continew.admin.common.model.dto;

import top.continew.admin.common.enums.DataScopeEnum;

import java.util.List;

/**
 * EVE 权限页使用的本站角色安全摘要。
 *
 * @param id          角色 ID
 * @param code        角色编码
 * @param name        角色名称
 * @param description 角色说明
 * @param dataScope   数据权限范围
 * @param system      是否为系统内置角色
 * @param permissions 该角色授予的有效权限码
 * @author zhaoyuqing
 */
public record EveSiteRoleDTO(Long id, String code, String name, String description, DataScopeEnum dataScope,
                             boolean system, List<String> permissions) {
}
