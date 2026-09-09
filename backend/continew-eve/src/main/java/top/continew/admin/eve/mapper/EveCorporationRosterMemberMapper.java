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
import org.apache.ibatis.annotations.Update;
import top.continew.admin.eve.model.entity.EveCorporationRosterMemberDO;
import top.continew.starter.data.mapper.BaseMapper;

import java.util.List;

/**
 * EVE 军团完整游戏成员名册 Mapper。
 *
 * @author zhaoyuqing
 */
@Mapper
public interface EveCorporationRosterMemberMapper extends BaseMapper<EveCorporationRosterMemberDO> {

    /** 按当前租户、军团和游戏角色查询名册成员。 */
    @Select("SELECT * FROM eve_corporation_roster_member WHERE tenant_id = #{tenantId} " + "AND corporation_ref_id = #{corporationRefId} AND character_id = #{characterId} AND deleted = 0 LIMIT 1")
    EveCorporationRosterMemberDO selectByCharacterId(@Param("tenantId") Long tenantId,
                                                     @Param("corporationRefId") Long corporationRefId,
                                                     @Param("characterId") Long characterId);

    /** 查询当前军团仍在团的完整名册，用于成功批次的离团差异计算。 */
    @Select("SELECT * FROM eve_corporation_roster_member WHERE tenant_id = #{tenantId} " + "AND corporation_ref_id = #{corporationRefId} AND status = 'ACTIVE' AND deleted = 0")
    List<EveCorporationRosterMemberDO> selectActiveByCorporation(@Param("tenantId") Long tenantId,
                                                                 @Param("corporationRefId") Long corporationRefId);

    /**
     * 更新成员内部组织信息，显式写入空值以支持清空分组或备注。
     *
     * @return 实际更新行数
     */
    @Update("UPDATE eve_corporation_roster_member SET organization_group = #{organizationGroup}, member_note = " + "#{memberNote}, update_user = #{updateUser}, update_time = NOW() WHERE id = #{id} AND tenant_id = " + "#{tenantId} AND deleted = 0")
    int updateOrganization(@Param("tenantId") Long tenantId,
                           @Param("id") Long id,
                           @Param("organizationGroup") String organizationGroup,
                           @Param("memberNote") String memberNote,
                           @Param("updateUser") Long updateUser);
}
