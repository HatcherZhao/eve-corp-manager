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
import top.continew.admin.eve.model.entity.EveAgentApplicationDO;
import top.continew.starter.data.mapper.BaseMapper;

import java.time.LocalDateTime;

/**
 * 外部 Agent 应用凭据 Mapper。
 *
 * @author zhaoyuqing
 */
@Mapper
public interface EveAgentApplicationMapper extends BaseMapper<EveAgentApplicationDO> {

    /** 按凭据摘要跨租户定位候选应用，军团和有效期由服务再次校验。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM eve_agent_application WHERE token_hash = #{tokenHash} AND deleted = 0 LIMIT 1")
    EveAgentApplicationDO selectByTokenHash(@Param("tokenHash") String tokenHash);

    /** 按当前军团读取待管理应用，避免使用外部传入的军团标识。 */
    @Select("SELECT * FROM eve_agent_application WHERE tenant_id = #{tenantId} " + "AND corporation_ref_id = #{corporationRefId} AND id = #{id} AND deleted = 0 LIMIT 1")
    EveAgentApplicationDO selectManaged(@Param("tenantId") Long tenantId,
                                        @Param("corporationRefId") Long corporationRefId,
                                        @Param("id") Long id);

    /** 原子轮换仍有效应用的凭据，已撤销应用不得恢复。 */
    @Update("UPDATE eve_agent_application SET token_hash = #{tokenHash}, token_prefix = #{tokenPrefix}, " + "last_rotated_at = #{rotatedAt}, update_user = #{updateUser}, update_time = NOW() " + "WHERE tenant_id = #{tenantId} AND corporation_ref_id = #{corporationRefId} AND id = #{id} " + "AND status = 'ACTIVE' AND deleted = 0")
    int rotateCredential(@Param("tenantId") Long tenantId,
                         @Param("corporationRefId") Long corporationRefId,
                         @Param("id") Long id,
                         @Param("tokenHash") String tokenHash,
                         @Param("tokenPrefix") String tokenPrefix,
                         @Param("rotatedAt") LocalDateTime rotatedAt,
                         @Param("updateUser") Long updateUser);

    /** 原子撤销仍有效应用，重复撤销不改变历史状态。 */
    @Update("UPDATE eve_agent_application SET status = 'REVOKED', revoked_at = #{revokedAt}, " + "update_user = #{updateUser}, update_time = NOW() WHERE tenant_id = #{tenantId} " + "AND corporation_ref_id = #{corporationRefId} AND id = #{id} AND status = 'ACTIVE' AND deleted = 0")
    int revoke(@Param("tenantId") Long tenantId,
               @Param("corporationRefId") Long corporationRefId,
               @Param("id") Long id,
               @Param("revokedAt") LocalDateTime revokedAt,
               @Param("updateUser") Long updateUser);

    /** 成功完成一次调用后更新最近使用时间，不依赖浏览器租户上下文。 */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE eve_agent_application SET last_used_at = #{usedAt}, update_time = NOW() " + "WHERE tenant_id = #{tenantId} AND corporation_ref_id = #{corporationRefId} " + "AND id = #{id} AND status = 'ACTIVE' AND deleted = 0")
    int touchLastUsed(@Param("tenantId") Long tenantId,
                      @Param("corporationRefId") Long corporationRefId,
                      @Param("id") Long id,
                      @Param("usedAt") LocalDateTime usedAt);
}
