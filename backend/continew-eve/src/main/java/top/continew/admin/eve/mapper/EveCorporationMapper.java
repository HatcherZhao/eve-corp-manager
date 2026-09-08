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
import top.continew.admin.eve.model.entity.EveCorporationDO;
import top.continew.starter.data.mapper.BaseMapper;

/**
 * EVE 军团租户绑定 Mapper。
 *
 * @author zhaoyuqing
 */
@Mapper
public interface EveCorporationMapper extends BaseMapper<EveCorporationDO> {

    /** 认领前按国服服务器和军团 ID 查询唯一绑定。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM eve_corporation WHERE server = #{server} AND corporation_id = #{corporationId} AND deleted = 0 LIMIT 1")
    EveCorporationDO selectByExternalId(@Param("server") String server, @Param("corporationId") Long corporationId);

    /** 按当前租户和游戏军团 ID 查询绑定。 */
    @Select("SELECT * FROM eve_corporation WHERE tenant_id = #{tenantId} AND corporation_id = #{corporationId} " + "AND deleted = 0 LIMIT 1")
    EveCorporationDO selectByTenantAndCorporationId(@Param("tenantId") Long tenantId,
                                                    @Param("corporationId") Long corporationId);
}
