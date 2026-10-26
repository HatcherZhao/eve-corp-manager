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

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import top.continew.admin.eve.model.entity.EveAgentGrantDO;
import top.continew.starter.data.mapper.BaseMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 外部 Agent 多军团授权 Mapper。
 *
 * @author zhaoyuqing
 */
@Mapper
public interface EveAgentGrantMapper extends BaseMapper<EveAgentGrantDO> {

    /** 授权写入必须保留目标军团租户，不能依赖当前线程的租户插件。 */
    @Override
    @InterceptorIgnore(tenantLine = "true")
    int insert(EveAgentGrantDO entity);

    /** 查询应用在指定军团上的授权，包括已撤销状态以禁止静默恢复。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM eve_agent_grant WHERE application_id = #{applicationId} " + "AND tenant_id = #{tenantId} AND corporation_ref_id = #{corporationRefId} AND deleted = 0 LIMIT 1")
    EveAgentGrantDO selectByApplicationAndCorporation(@Param("applicationId") Long applicationId,
                                                      @Param("tenantId") Long tenantId,
                                                      @Param("corporationRefId") Long corporationRefId);

    /** 当前军团按授权主键读取待撤销记录，跨军团统一按不存在处理。 */
    @Select("SELECT * FROM eve_agent_grant WHERE id = #{id} AND tenant_id = #{tenantId} " + "AND corporation_ref_id = #{corporationRefId} AND deleted = 0 LIMIT 1")
    EveAgentGrantDO selectByApplicationAndCorporationById(@Param("id") Long id,
                                                          @Param("tenantId") Long tenantId,
                                                          @Param("corporationRefId") Long corporationRefId);

    /** 查询应用当前全部有效军团授权。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM eve_agent_grant WHERE application_id = #{applicationId} " + "AND status = 'ACTIVE' AND deleted = 0 ORDER BY corporation_id ASC")
    List<EveAgentGrantDO> selectActiveByApplication(@Param("applicationId") Long applicationId);

    /** 应用所有者查看该应用的全部军团授权。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM eve_agent_grant WHERE application_id = #{applicationId} " + "AND deleted = 0 ORDER BY granted_at DESC, id DESC")
    List<EveAgentGrantDO> selectByApplication(@Param("applicationId") Long applicationId);

    /** 当前军团查看授予全部应用的授权。 */
    @Select("SELECT * FROM eve_agent_grant WHERE tenant_id = #{tenantId} " + "AND corporation_ref_id = #{corporationRefId} AND deleted = 0 ORDER BY granted_at DESC, id DESC")
    List<EveAgentGrantDO> selectByCorporation(@Param("tenantId") Long tenantId,
                                              @Param("corporationRefId") Long corporationRefId);

    /** 当前军团原子撤销一项有效授权，不能影响同应用的其他军团。 */
    @Update("UPDATE eve_agent_grant SET status = 'REVOKED', revoked_at = #{revokedAt}, revoked_by = #{revokedBy}, " + "update_user = #{revokedBy}, update_time = NOW() WHERE id = #{id} AND tenant_id = #{tenantId} " + "AND corporation_ref_id = #{corporationRefId} AND status = 'ACTIVE' AND deleted = 0")
    int revoke(@Param("id") Long id,
               @Param("tenantId") Long tenantId,
               @Param("corporationRefId") Long corporationRefId,
               @Param("revokedAt") LocalDateTime revokedAt,
               @Param("revokedBy") Long revokedBy);

    /** 仅目标军团管理者重新确认时恢复已撤销授权，并清除旧撤销信息。 */
    @Update("UPDATE eve_agent_grant SET status = 'ACTIVE', granted_at = #{grantedAt}, granted_by = #{grantedBy}, " + "revoked_at = NULL, revoked_by = NULL, update_user = #{grantedBy}, update_time = NOW() " + "WHERE id = #{id} AND tenant_id = #{tenantId} AND corporation_ref_id = #{corporationRefId} " + "AND status = 'REVOKED' AND deleted = 0")
    int reactivate(@Param("id") Long id,
                   @Param("tenantId") Long tenantId,
                   @Param("corporationRefId") Long corporationRefId,
                   @Param("grantedAt") LocalDateTime grantedAt,
                   @Param("grantedBy") Long grantedBy);
}
