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
import top.continew.admin.eve.model.entity.EveCorporationMemberTrackingDO;
import top.continew.starter.data.mapper.BaseMapper;

import java.util.List;

/**
 * EVE 军团成员追踪快照 Mapper。
 *
 * @author zhaoyuqing
 */
@Mapper
public interface EveCorporationMemberTrackingMapper extends BaseMapper<EveCorporationMemberTrackingDO> {

    /** 按当前租户和名册成员查询追踪快照。 */
    @Select("SELECT * FROM eve_corporation_member_tracking WHERE tenant_id = #{tenantId} " + "AND roster_member_id = #{rosterMemberId} AND deleted = 0 LIMIT 1")
    EveCorporationMemberTrackingDO selectByRosterMemberId(@Param("tenantId") Long tenantId,
                                                          @Param("rosterMemberId") Long rosterMemberId);

    /** 批量读取追踪快照，仅供已经完成追踪权限校验的服务调用。 */
    @Select({"<script>", "SELECT * FROM eve_corporation_member_tracking WHERE tenant_id = #{tenantId} AND deleted = 0 ",
        "AND roster_member_id IN",
        "<foreach collection='rosterMemberIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
        "</script>"})
    List<EveCorporationMemberTrackingDO> selectByRosterMemberIds(@Param("tenantId") Long tenantId,
                                                                 @Param("rosterMemberIds") List<Long> rosterMemberIds);
}
