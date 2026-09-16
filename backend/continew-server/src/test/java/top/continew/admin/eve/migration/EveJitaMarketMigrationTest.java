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

package top.continew.admin.eve.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 吉他市场迁移契约测试。
 *
 * @author zhaoyuqing
 */
class EveJitaMarketMigrationTest {

    /** 市场缓存表和成员查看、管理角色刷新权限必须随同菜单迁移一次性创建。 */
    @Test
    void shouldCreateSharedJitaMarketCacheAndPermissions() throws IOException {
        String master = resource("db/changelog/db.changelog-master.yaml");
        String migrationPath = "db/changelog/mysql/eve/039_jita_market.sql";
        String migration = resource(migrationPath);

        assertThat(master.indexOf(migrationPath)).isGreaterThan(master.indexOf("038_root_eve_menus.sql"));
        assertThat(migration)
            .contains("CREATE TABLE `eve_market_snapshot`", "`highest_buy_price`", "`history_json`", "(20090, '市场与估价'", "(20091, '吉他市场'", "'eve:market:view'", "'eve:market:sync'", "'corp_member'");
        assertThat(resource("db/changelog/mysql/eve/040_market_history_warmup.sql"))
            .contains("`history_synchronized_at`", "idx_eve_market_snapshot_history");
        assertThat(resource("db/changelog/mysql/eve/041_market_menu_icon.sql"))
            .contains("SET `icon` = 'area-chart'", "20090, 20091");
        assertThat(resource("db/changelog/mysql/eve/042_moon_mining_compression_valuation.sql"))
            .contains("CREATE TABLE `eve_mining_compression_mapping`", "`compression_ratio`", "(45490, 62463, 100)");
        assertThat(resource("db/changelog/mysql/eve/043_asset_valuation.sql"))
            .contains("idx_eve_corporation_asset_quote_priority", "`status`, `deleted`, `type_id`");
    }

    /** 读取类路径迁移资源。 */
    private static String resource(String path) throws IOException {
        try (InputStream input = EveJitaMarketMigrationTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("迁移资源 %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
