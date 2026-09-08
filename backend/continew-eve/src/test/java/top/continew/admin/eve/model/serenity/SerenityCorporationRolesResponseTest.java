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

package top.continew.admin.eve.model.serenity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 国服角色响应模型测试。
 *
 * @author zhaoyuqing
 */
class SerenityCorporationRolesResponseTest {

    /** 验证四类范围均可反序列化并保留未知角色。 */
    @Test
    void shouldDeserializeAndResolveAllScopes() throws Exception {
        String json = """
            {
              "roles": ["Director"],
              "roles_at_hq": ["Accountant"],
              "roles_at_base": ["Station_Manager"],
              "roles_at_other": ["Future_Role"]
            }
            """;

        SerenityCorporationRolesResponse response = new ObjectMapper()
            .readValue(json, SerenityCorporationRolesResponse.class);
        Map<CorporationRoleScope, java.util.List<CorporationRoleDefinition>> tree = response.resolveByScope();

        assertThat(tree).containsOnlyKeys(CorporationRoleScope.values());
        assertThat(tree.get(CorporationRoleScope.ROLES)).extracting(CorporationRoleDefinition::code)
            .containsExactly("Director");
        assertThat(tree.get(CorporationRoleScope.ROLES_AT_HQ)).extracting(CorporationRoleDefinition::code)
            .containsExactly("Accountant");
        assertThat(tree.get(CorporationRoleScope.ROLES_AT_BASE)).extracting(CorporationRoleDefinition::code)
            .containsExactly("Station_Manager");
        assertThat(tree.get(CorporationRoleScope.ROLES_AT_OTHER)).singleElement().satisfies(role -> {
            assertThat(role.code()).isEqualTo("Future_Role");
            assertThat(role.known()).isFalse();
        });
    }
}
