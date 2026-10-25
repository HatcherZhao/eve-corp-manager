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
import top.continew.admin.eve.model.entity.EveAgentAuditDO;
import top.continew.starter.data.mapper.BaseMapper;

/**
 * 外部 Agent 审计 Mapper。
 *
 * @author zhaoyuqing
 */
@Mapper
public interface EveAgentAuditMapper extends BaseMapper<EveAgentAuditDO> {

    /** Agent 请求没有浏览器租户上下文，显式实体租户字段作为唯一可信边界。 */
    @Override
    @InterceptorIgnore(tenantLine = "true")
    int insert(EveAgentAuditDO entity);
}
