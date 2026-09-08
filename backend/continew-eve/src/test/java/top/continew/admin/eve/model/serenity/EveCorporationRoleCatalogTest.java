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

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 游戏军团角色目录测试。
 *
 * @author zhaoyuqing
 */
class EveCorporationRoleCatalogTest {

    private static final Set<String> SWAGGER_ROLE_CODES = Set
        .of("Account_Take_1", "Account_Take_2", "Account_Take_3", "Account_Take_4", "Account_Take_5", "Account_Take_6", "Account_Take_7", "Accountant", "Auditor", "Communications_Officer", "Config_Equipment", "Config_Starbase_Equipment", "Container_Take_1", "Container_Take_2", "Container_Take_3", "Container_Take_4", "Container_Take_5", "Container_Take_6", "Container_Take_7", "Contract_Manager", "Diplomat", "Director", "Factory_Manager", "Fitting_Manager", "Hangar_Query_1", "Hangar_Query_2", "Hangar_Query_3", "Hangar_Query_4", "Hangar_Query_5", "Hangar_Query_6", "Hangar_Query_7", "Hangar_Take_1", "Hangar_Take_2", "Hangar_Take_3", "Hangar_Take_4", "Hangar_Take_5", "Hangar_Take_6", "Hangar_Take_7", "Junior_Accountant", "Personnel_Manager", "Rent_Factory_Facility", "Rent_Office", "Rent_Research_Facility", "Security_Officer", "Skill_Plan_Manager", "Starbase_Defense_Operator", "Starbase_Fuel_Technician", "Station_Manager", "Trader");

    /** 验证目录完整覆盖 Swagger 快照中的 49 个角色。 */
    @Test
    void shouldMatchSwaggerRoleCodes() {
        Set<String> actualCodes = EveCorporationRoleCatalog.all()
            .stream()
            .map(CorporationRoleDefinition::code)
            .collect(Collectors.toSet());

        assertThat(actualCodes).hasSize(49).containsExactlyInAnyOrderElementsOf(SWAGGER_ROLE_CODES);
        assertThat(EveCorporationRoleCatalog.all()).allSatisfy(role -> {
            assertThat(role.displayName()).isNotBlank();
            assertThat(role.description()).isNotBlank();
            assertThat(role.category()).isNotNull();
            assertThat(role.capabilities()).isNotNull();
            assertThat(role.known()).isTrue();
        });
    }

    /** 验证关键游戏角色关联对应系统候选能力。 */
    @Test
    void shouldExposeImportantCapabilityMetadata() {
        assertThat(EveCorporationRoleCatalog.resolve("Director").capabilities())
            .contains(EveSystemCapability.CORPORATION_ADMINISTRATION, EveSystemCapability.CORPORATION_ASSET_READ, EveSystemCapability.CORPORATION_MEMBER_TRACKING);
        assertThat(EveCorporationRoleCatalog.resolve("Station_Manager").capabilities())
            .contains(EveSystemCapability.STRUCTURE_MANAGEMENT, EveSystemCapability.MINING_EXTRACTION_READ);
        assertThat(EveCorporationRoleCatalog.resolve("Accountant").capabilities())
            .contains(EveSystemCapability.MINING_LEDGER_READ);
    }

    /** 验证国服新增角色不会因目录未更新而丢失。 */
    @Test
    void shouldPreserveUnknownRoleCode() {
        CorporationRoleDefinition role = EveCorporationRoleCatalog.resolve("New_Serenity_Role");

        assertThat(role.code()).isEqualTo("New_Serenity_Role");
        assertThat(role.displayName()).isEqualTo("New_Serenity_Role");
        assertThat(role.category()).isEqualTo(CorporationRoleCategory.UNKNOWN);
        assertThat(role.known()).isFalse();
    }
}
