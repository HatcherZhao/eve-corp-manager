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
import top.continew.admin.eve.model.entity.EveCorporationStructureDO;
import top.continew.starter.data.mapper.BaseMapper;

/**
 * 军团建筑当前快照 Mapper。
 *
 * @author zhaoyuqing
 */
@Mapper
public interface EveCorporationStructureMapper extends BaseMapper<EveCorporationStructureDO> {

    /** 按当前租户、军团和建筑 ID 查询快照，禁止只使用可猜测的外部 ID。 */
    @Select("SELECT * FROM eve_corporation_structure WHERE tenant_id = #{tenantId} " + "AND corporation_ref_id = #{corporationRefId} AND structure_id = #{structureId} AND deleted = 0 LIMIT 1")
    EveCorporationStructureDO selectByStructureId(@Param("tenantId") Long tenantId,
                                                  @Param("corporationRefId") Long corporationRefId,
                                                  @Param("structureId") Long structureId);
}
