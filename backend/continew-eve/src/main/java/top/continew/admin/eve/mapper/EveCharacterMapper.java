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

package top.continew.admin.eve.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import top.continew.admin.eve.model.entity.EveCharacterDO;
import top.continew.starter.data.mapper.BaseMapper;

import java.util.List;

/**
 * EVE 角色绑定 Mapper。
 *
 * @author zhaoyuqing
 */
@Mapper
public interface EveCharacterMapper extends BaseMapper<EveCharacterDO> {

    /** 注册前按国服服务器和角色 ID 查询全局唯一绑定。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM eve_character WHERE server = #{server} AND character_id = #{characterId} AND deleted = 0 LIMIT 1")
    EveCharacterDO selectByExternalId(@Param("server") String server, @Param("characterId") Long characterId);

    /** 按租户和本站用户查询全部游戏角色，用于识别已失活绑定。 */
    @Select("SELECT * FROM eve_character WHERE tenant_id = #{tenantId} AND user_id = #{userId} " + "AND deleted = 0 ORDER BY is_primary DESC, id ASC")
    List<EveCharacterDO> selectByUser(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    /** 按租户和本站用户查询有效游戏角色，租户条件不可省略。 */
    @Select("SELECT * FROM eve_character WHERE tenant_id = #{tenantId} AND user_id = #{userId} " + "AND status = 'ACTIVE' AND deleted = 0 ORDER BY is_primary DESC, id ASC")
    List<EveCharacterDO> selectActiveByUser(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    /** 按租户、本站用户和角色绑定主键读取自动任务目标。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM eve_character WHERE tenant_id = #{tenantId} AND user_id = #{userId} " + "AND id = #{characterRefId} AND status = 'ACTIVE' AND deleted = 0 LIMIT 1")
    EveCharacterDO selectActiveByTenantUserAndRefId(@Param("tenantId") Long tenantId,
                                                    @Param("userId") Long userId,
                                                    @Param("characterRefId") Long characterRefId);

    /** 按租户和角色绑定主键读取有效邮箱同步目标及其本站用户归属。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM eve_character WHERE tenant_id = #{tenantId} AND id = #{characterRefId} " + "AND status = 'ACTIVE' AND deleted = 0 LIMIT 1")
    EveCharacterDO selectActiveByTenantAndRefId(@Param("tenantId") Long tenantId,
                                                @Param("characterRefId") Long characterRefId);
}
