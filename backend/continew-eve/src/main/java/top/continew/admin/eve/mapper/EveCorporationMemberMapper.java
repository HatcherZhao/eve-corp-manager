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
import top.continew.admin.eve.model.entity.EveCorporationMemberDO;
import top.continew.starter.data.mapper.BaseMapper;

/**
 * EVE 军团成员关系 Mapper。
 *
 * @author zhaoyuqing
 */
@Mapper
public interface EveCorporationMemberMapper extends BaseMapper<EveCorporationMemberDO> {

    /** 按租户、用户和角色绑定查询当前成员关系。 */
    @Select("SELECT * FROM eve_corporation_member WHERE tenant_id = #{tenantId} AND user_id = #{userId} " + "AND character_ref_id = #{characterRefId} AND deleted = 0 ORDER BY id DESC LIMIT 1")
    EveCorporationMemberDO selectCurrent(@Param("tenantId") Long tenantId,
                                         @Param("userId") Long userId,
                                         @Param("characterRefId") Long characterRefId);
}
