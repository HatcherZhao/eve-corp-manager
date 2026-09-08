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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.continew.admin.system.model.entity.UserRoleDO;
import top.continew.admin.system.model.resp.role.RoleUserResp;
import top.continew.starter.data.mapper.BaseMapper;

import java.util.List;

/**
 * 用户和角色 Mapper
 *
 * @author Charles7c
 * @since 2023/2/13 23:13
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRoleDO> {

    /**
     * 分页查询列表
     *
     * @param page         分页条件
     * @param queryWrapper 查询条件
     * @return 分页列表信息
     */
    IPage<RoleUserResp> selectUserPage(@Param("page") IPage<UserRoleDO> page,
                                       @Param(Constants.WRAPPER) QueryWrapper<UserRoleDO> queryWrapper);

    /** 删除用户指定角色集合，用于系统维护派生角色。 */
    @Delete({"<script>", "DELETE FROM sys_user_role WHERE user_id = #{userId} AND role_id IN",
        "<foreach collection='roleIds' item='roleId' open='(' separator=',' close=')'>#{roleId}</foreach>",
        "</script>"})
    int deleteByUserAndRoleIds(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

    /** 查询用户当前全部角色 ID。 */
    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUser(@Param("userId") Long userId);

    /** 删除用户全部角色关联。 */
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    /** 删除用户指定业务角色集合，不触碰游戏派生角色。 */
    @Delete({"<script>", "DELETE FROM sys_user_role WHERE user_id = #{userId} AND role_id IN",
        "<foreach collection='roleIds' item='roleId' open='(' separator=',' close=')'>#{roleId}</foreach>",
        "</script>"})
    int deleteBusinessRoles(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

}
