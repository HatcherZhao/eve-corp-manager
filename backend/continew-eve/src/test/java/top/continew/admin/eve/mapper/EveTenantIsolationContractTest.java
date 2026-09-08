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
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EVE Mapper 租户隔离与全局忽略点契约测试。
 *
 * @author zhaoyuqing
 */
class EveTenantIsolationContractTest {

    /** 登录后资源查询必须同时绑定租户、用户和角色引用。 */
    @Test
    void shouldBindTenantAndOwnershipInAuthenticatedQueries() throws Exception {
        assertSqlContains(EveAuthorizationMapper.class
            .getMethod("selectByUserCharacter", Long.class, Long.class, Long.class), "tenant_id = #{tenantId}", "user_id = #{userId}", "character_ref_id = #{characterRefId}");
        assertSqlContains(EveCharacterMapper.class
            .getMethod("selectActiveByUser", Long.class, Long.class), "tenant_id = #{tenantId}", "user_id = #{userId}");
        assertSqlContains(EveCharacterRoleSnapshotMapper.class
            .getMethod("selectLatest", Long.class, Long.class), "tenant_id = #{tenantId}", "character_ref_id = #{characterRefId}");
        assertSqlContains(EveCorporationMapper.class
            .getMethod("selectByTenantAndCorporationId", Long.class, Long.class), "tenant_id = #{tenantId}", "corporation_id = #{corporationId}");
        assertSqlContains(EveCorporationMemberMapper.class
            .getMethod("selectCurrent", Long.class, Long.class, Long.class), "tenant_id = #{tenantId}", "user_id = #{userId}", "character_ref_id = #{characterRefId}");
    }

    /** 绕过租户拦截的查询必须局限于认领前唯一探测或受控批处理。 */
    @Test
    void shouldKeepTenantInterceptorIgnorePointsExplicitAndBounded() throws Exception {
        Method preTenantAudit = EveAuthAuditMapper.class.getMethod("selectPreTenantByRequestId", String.class);
        assertTenantIgnored(preTenantAudit);
        assertSqlContains(preTenantAudit, "tenant_id IS NULL", "request_id = #{requestId}");

        Method characterLookup = EveCharacterMapper.class.getMethod("selectByExternalId", String.class, Long.class);
        assertTenantIgnored(characterLookup);
        assertSqlContains(characterLookup, "server = #{server}", "character_id = #{characterId}");

        Method corporationLookup = EveCorporationMapper.class.getMethod("selectByExternalId", String.class, Long.class);
        assertTenantIgnored(corporationLookup);
        assertSqlContains(corporationLookup, "server = #{server}", "corporation_id = #{corporationId}");

        Method staleAuthorization = EveAuthorizationMapper.class
            .getMethod("selectStaleActive", LocalDateTime.class, LocalDateTime.class, int.class);
        assertTenantIgnored(staleAuthorization);
        assertSqlContains(staleAuthorization, "status = 'ACTIVE'", "deleted = 0", "LIMIT #{limit}");
    }

    /** 校验方法显式声明跳过租户行拦截。 */
    private static void assertTenantIgnored(Method method) {
        InterceptorIgnore ignore = method.getAnnotation(InterceptorIgnore.class);
        assertThat(ignore).isNotNull();
        assertThat(ignore.tenantLine()).isEqualTo("true");
    }

    /** 校验注解 SQL 同时包含全部安全约束。 */
    private static void assertSqlContains(Method method, String... fragments) {
        Select select = method.getAnnotation(Select.class);
        assertThat(select).isNotNull();
        String sql = String.join(" ", select.value());
        assertThat(Arrays.stream(fragments)).allMatch(sql::contains);
    }
}
