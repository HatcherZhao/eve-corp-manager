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
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import top.continew.admin.eve.model.entity.EveCharacterRoleSnapshotDO;
import top.continew.starter.data.mapper.BaseMapper;

import java.util.List;

/**
 * EVE 角色权限快照 Mapper。
 *
 * @author zhaoyuqing
 */
@Mapper
public interface EveCharacterRoleSnapshotMapper extends BaseMapper<EveCharacterRoleSnapshotDO> {

    /** 查询同租户指定角色绑定的最新游戏角色快照。 */
    @Results(id = "eveCharacterRoleSnapshotResultMap", value = {
        @Result(column = "roles", property = "roles", typeHandler = JacksonTypeHandler.class),
        @Result(column = "roles_at_hq", property = "rolesAtHq", typeHandler = JacksonTypeHandler.class),
        @Result(column = "roles_at_base", property = "rolesAtBase", typeHandler = JacksonTypeHandler.class),
        @Result(column = "roles_at_other", property = "rolesAtOther", typeHandler = JacksonTypeHandler.class)})
    @Select("SELECT * FROM eve_character_role_snapshot WHERE tenant_id = #{tenantId} " + "AND character_ref_id = #{characterRefId} AND deleted = 0 ORDER BY captured_at DESC, id DESC LIMIT 1")
    EveCharacterRoleSnapshotDO selectLatest(@Param("tenantId") Long tenantId,
                                            @Param("characterRefId") Long characterRefId);

    /** 批量查询同租户多个角色绑定的最新游戏角色快照。 */
    @ResultMap("eveCharacterRoleSnapshotResultMap")
    @Select({"<script>", "SELECT ranked.* FROM (",
        "SELECT snapshot.*, ROW_NUMBER() OVER (PARTITION BY snapshot.character_ref_id ",
        "ORDER BY snapshot.captured_at DESC, snapshot.id DESC) AS rn ",
        "FROM eve_character_role_snapshot snapshot WHERE snapshot.tenant_id = #{tenantId} ",
        "AND snapshot.deleted = 0 AND snapshot.character_ref_id IN ",
        "<foreach collection='characterRefIds' item='characterRefId' open='(' separator=',' close=')'>",
        "#{characterRefId}", "</foreach>", ") ranked WHERE ranked.rn = 1", "</script>"})
    List<EveCharacterRoleSnapshotDO> selectLatestByCharacterRefs(@Param("tenantId") Long tenantId,
                                                                 @Param("characterRefIds") List<Long> characterRefIds);
}
