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

package top.continew.admin.system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.continew.admin.system.model.entity.RoleDO;
import top.continew.starter.data.mapper.BaseMapper;

import java.util.Collection;
import java.util.List;

/**
 * 角色 Mapper
 *
 * @author Charles7c
 * @since 2023/2/8 23:17
 */
@Mapper
public interface RoleMapper extends BaseMapper<RoleDO> {

    /** 按角色编码查询租户内有效角色。 */
    @Select("SELECT * FROM sys_role WHERE code = #{code} AND deleted = 0 LIMIT 1")
    RoleDO selectByCode(@Param("code") String code);

    /** 按角色编码集合查询租户内有效角色。 */
    @Select({"<script>", "SELECT * FROM sys_role WHERE deleted = 0 AND code IN",
        "<foreach collection='codes' item='code' open='(' separator=',' close=')'>#{code}</foreach>", "</script>"})
    List<RoleDO> selectByCodes(@Param("codes") Collection<String> codes);
}
