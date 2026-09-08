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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 游戏军团角色目录查询入口。
 *
 * @author zhaoyuqing
 */
public final class EveCorporationRoleCatalog {

    private static final Map<String, EveCorporationRole> ROLE_BY_CODE = Arrays.stream(EveCorporationRole.values())
        .collect(Collectors.toUnmodifiableMap(EveCorporationRole::getCode, Function.identity()));

    private EveCorporationRoleCatalog() {
    }

    /**
     * 解析单个国服角色代码。
     *
     * @param rawCode 国服接口原始代码
     * @return 已知角色元数据，或保留原始代码的未知角色定义
     */
    public static CorporationRoleDefinition resolve(String rawCode) {
        EveCorporationRole role = ROLE_BY_CODE.get(rawCode);
        return role == null ? CorporationRoleDefinition.unknown(rawCode) : CorporationRoleDefinition.known(role);
    }

    /**
     * 解析一组国服角色代码。
     *
     * @param rawCodes 国服接口原始代码
     * @return 顺序与接口响应一致的角色定义
     */
    public static List<CorporationRoleDefinition> resolveAll(List<String> rawCodes) {
        if (rawCodes == null || rawCodes.isEmpty()) {
            return List.of();
        }
        return rawCodes.stream().map(EveCorporationRoleCatalog::resolve).toList();
    }

    /**
     * 返回当前契约声明的全部角色。
     *
     * @return 不可变角色目录
     */
    public static List<CorporationRoleDefinition> all() {
        return Arrays.stream(EveCorporationRole.values()).map(CorporationRoleDefinition::known).toList();
    }
}
