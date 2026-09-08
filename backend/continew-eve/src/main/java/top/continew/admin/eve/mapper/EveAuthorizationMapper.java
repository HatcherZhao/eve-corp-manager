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
import org.apache.ibatis.annotations.Update;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.starter.data.mapper.BaseMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * EVE 国服授权 Mapper。
 *
 * @author zhaoyuqing
 */
@Mapper
public interface EveAuthorizationMapper extends BaseMapper<EveAuthorizationDO> {

    /** 按租户、用户和主键查询授权，禁止仅凭可猜测 ID 访问。 */
    @Results(id = "eveAuthorizationResultMap", value = {
        @Result(column = "scopes", property = "scopes", typeHandler = JacksonTypeHandler.class)})
    @Select("SELECT * FROM eve_authorization WHERE tenant_id = #{tenantId} AND user_id = #{userId} " + "AND id = #{authorizationId} AND deleted = 0 LIMIT 1")
    EveAuthorizationDO selectOwnedById(@Param("tenantId") Long tenantId,
                                       @Param("userId") Long userId,
                                       @Param("authorizationId") Long authorizationId);

    /** 按租户、用户和角色绑定查询授权候选，防止跨用户或跨租户组合。 */
    @ResultMap("eveAuthorizationResultMap")
    @Select("SELECT * FROM eve_authorization WHERE tenant_id = #{tenantId} AND user_id = #{userId} " + "AND character_ref_id = #{characterRefId} AND deleted = 0 ORDER BY id DESC")
    List<EveAuthorizationDO> selectByUserCharacter(@Param("tenantId") Long tenantId,
                                                   @Param("userId") Long userId,
                                                   @Param("characterRefId") Long characterRefId);

    /**
     * 查询同租户内军团归属一致的授权候选。
     *
     * <p>授权、角色绑定和军团记录必须在同一租户、服务器与角色归属链路中，避免跨授权拼接数据源。</p>
     */
    @ResultMap("eveAuthorizationResultMap")
    @Select("SELECT auth.* FROM eve_authorization auth " + "INNER JOIN eve_character character_binding ON character_binding.id = auth.character_ref_id " + "AND character_binding.tenant_id = auth.tenant_id " + "AND character_binding.user_id = auth.user_id " + "AND character_binding.server = auth.server " + "AND character_binding.status = 'ACTIVE' AND character_binding.deleted = 0 " + "INNER JOIN eve_corporation corporation ON corporation.tenant_id = character_binding.tenant_id " + "AND corporation.server = character_binding.server " + "AND corporation.corporation_id = character_binding.corporation_id " + "AND corporation.status = 'ACTIVE' AND corporation.deleted = 0 " + "INNER JOIN eve_corporation_member corporation_member " + "ON corporation_member.tenant_id = auth.tenant_id " + "AND corporation_member.corporation_ref_id = corporation.id " + "AND corporation_member.character_ref_id = auth.character_ref_id " + "AND corporation_member.user_id = auth.user_id " + "AND corporation_member.status = 'ACTIVE' AND corporation_member.deleted = 0 " + "WHERE auth.tenant_id = #{tenantId} AND auth.deleted = 0 " + "ORDER BY auth.id DESC")
    List<EveAuthorizationDO> selectTenantCandidates(@Param("tenantId") Long tenantId);

    /** 跨租户查询到达复核周期的有效授权，调用方必须逐条恢复租户上下文。 */
    @InterceptorIgnore(tenantLine = "true")
    @ResultMap("eveAuthorizationResultMap")
    @Select("SELECT * FROM eve_authorization WHERE status = 'ACTIVE' AND deleted = 0 " + "AND (last_verified_at IS NULL OR last_verified_at <= #{before}) " + "AND (next_review_at IS NULL OR next_review_at <= #{eligibleAt}) " + "ORDER BY next_review_at ASC, last_verified_at ASC, id ASC LIMIT #{limit}")
    List<EveAuthorizationDO> selectStaleActive(@Param("before") LocalDateTime before,
                                               @Param("eligibleAt") LocalDateTime eligibleAt,
                                               @Param("limit") int limit);

    /** 原子认领一条后台复核任务并设置有限退避，避免失败记录饿死后续授权。 */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE eve_authorization SET last_attempt_at = #{attemptedAt}, next_review_at = #{nextReviewAt} " + "WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND id = #{authorizationId} " + "AND status = 'ACTIVE' AND deleted = 0 " + "AND (next_review_at IS NULL OR next_review_at <= #{attemptedAt})")
    int claimReview(@Param("tenantId") Long tenantId,
                    @Param("userId") Long userId,
                    @Param("authorizationId") Long authorizationId,
                    @Param("attemptedAt") LocalDateTime attemptedAt,
                    @Param("nextReviewAt") LocalDateTime nextReviewAt);
}
