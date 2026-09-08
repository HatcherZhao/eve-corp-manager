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
import top.continew.admin.eve.model.entity.EveAuthAuditDO;
import top.continew.starter.data.mapper.BaseMapper;

/**
 * EVE 授权审计 Mapper。
 *
 * @author zhaoyuqing
 */
@Mapper
public interface EveAuthAuditMapper extends BaseMapper<EveAuthAuditDO> {

    /**
     * 按精确请求 ID 查询军团认领前的无租户审计事件。
     *
     * <p>该查询绕过租户行拦截，但固定限制 {@code tenant_id IS NULL}，只能用于系统级安全审计。</p>
     *
     * @param requestId 请求追踪 ID
     * @return 无租户审计事件，不存在时返回 {@code null}
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM eve_auth_audit WHERE tenant_id IS NULL AND request_id = #{requestId} AND deleted = 0 LIMIT 1")
    EveAuthAuditDO selectPreTenantByRequestId(@Param("requestId") String requestId);
}
