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

package top.continew.admin.eve.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import org.junit.jupiter.api.Test;
import top.continew.admin.common.base.model.entity.TenantBaseDO;
import top.continew.starter.encrypt.field.annotation.FieldEncrypt;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EVE 身份与授权持久层模型契约测试。
 *
 * @author zhaoyuqing
 */
class EvePersistenceModelTest {

    private static final Map<Class<?>, String> TENANT_ENTITY_TABLES = Map
        .of(EveCharacterDO.class, "eve_character", EveCorporationDO.class, "eve_corporation", EveCorporationMemberDO.class, "eve_corporation_member", EveAuthorizationDO.class, "eve_authorization", EveCharacterRoleSnapshotDO.class, "eve_character_role_snapshot", EveAuthAuditDO.class, "eve_auth_audit");

    /** 验证六类业务实体均继承租户与审计字段，并映射到预期表。 */
    @Test
    void shouldMapAllTenantEntities() {
        TENANT_ENTITY_TABLES.forEach((entityType, tableName) -> {
            assertThat(TenantBaseDO.class).isAssignableFrom(entityType);
            assertThat(entityType.getAnnotation(TableName.class)).isNotNull()
                .extracting(TableName::value)
                .isEqualTo(tableName);
        });
    }

    /** 验证令牌字段启用字段加密且不会被 toString 泄漏。 */
    @Test
    void shouldEncryptAndHideTokens() throws Exception {
        Field accessToken = EveAuthorizationDO.class.getDeclaredField("accessToken");
        Field refreshToken = EveAuthorizationDO.class.getDeclaredField("refreshToken");
        assertThat(accessToken.getAnnotation(FieldEncrypt.class)).isNotNull();
        assertThat(refreshToken.getAnnotation(FieldEncrypt.class)).isNotNull();

        EveAuthorizationDO authorization = new EveAuthorizationDO();
        authorization.setAccessToken("access-secret-value");
        authorization.setRefreshToken("refresh-secret-value");

        assertThat(authorization.toString()).doesNotContain("access-secret-value", "refresh-secret-value");
    }

    /** 验证 Scope 与四类游戏角色使用 JSON TypeHandler 保存完整列表。 */
    @Test
    void shouldUseJsonHandlersForScopeAndRoleCollections() throws Exception {
        assertJsonField(EveAuthorizationDO.class, "scopes");
        assertJsonField(EveCharacterRoleSnapshotDO.class, "roles");
        assertJsonField(EveCharacterRoleSnapshotDO.class, "rolesAtHq");
        assertJsonField(EveCharacterRoleSnapshotDO.class, "rolesAtBase");
        assertJsonField(EveCharacterRoleSnapshotDO.class, "rolesAtOther");
        assertThat(EveAuthorizationDO.class.getAnnotation(TableName.class).autoResultMap()).isTrue();
        assertThat(EveCharacterRoleSnapshotDO.class.getAnnotation(TableName.class).autoResultMap()).isTrue();

        EveCharacterRoleSnapshotDO snapshot = new EveCharacterRoleSnapshotDO();
        snapshot.setIsCeo(true);
        snapshot.setRoles(List.of("Director"));
        snapshot.setRolesAtHq(List.of("Accountant"));
        snapshot.setRolesAtBase(List.of("Station_Manager"));
        snapshot.setRolesAtOther(List.of("Future_Role"));
        assertThat(snapshot.getIsCeo()).isTrue();
        assertThat(snapshot.getRolesAtOther()).containsExactly("Future_Role");
    }

    /** 验证角色只持久化所有者摘要，不保存 SSO owner 原值。 */
    @Test
    void shouldPersistOnlyOwnerHash() {
        assertThat(EveCharacterDO.class.getDeclaredFields()).extracting(Field::getName)
            .contains("ownerHash")
            .doesNotContain("owner");
    }

    /**
     * 验证字段使用 Jackson JSON TypeHandler。
     *
     * @param entityType 实体类型
     * @param fieldName  字段名
     */
    private static void assertJsonField(Class<?> entityType, String fieldName) throws Exception {
        TableField tableField = entityType.getDeclaredField(fieldName).getAnnotation(TableField.class);
        assertThat(tableField).isNotNull();
        assertThat(tableField.typeHandler()).isEqualTo(JacksonTypeHandler.class);
    }
}
