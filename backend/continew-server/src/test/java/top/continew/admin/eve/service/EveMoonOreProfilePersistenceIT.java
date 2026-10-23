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

package top.continew.admin.eve.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import top.continew.admin.LocalIntegrationEnvironment;
import top.continew.admin.eve.mapper.EveMoonOreProfileMapper;
import top.continew.admin.eve.model.entity.EveGameNotificationDO;
import top.continew.admin.eve.model.entity.EveMoonOreProfileDO;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 月矿堡矿种档案真实 MySQL 持久化集成测试。
 *
 * @author zhaoyuqing
 */
class EveMoonOreProfilePersistenceIT {

    private static final Long TENANT_ID = 991101L;
    private static final Long OTHER_TENANT_ID = 991102L;
    private static final Long CORPORATION_ID = 991201L;
    private static final Long OTHER_CORPORATION_ID = 991202L;
    private static final Long USER_ID = 991301L;
    private static final Long CHARACTER_REF_ID = 991302L;

    private Connection connection;
    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;
    private EveMoonOreProfileMapper mapper;
    private DataSourceTransactionManager transactionManager;
    private TransactionStatus transactionStatus;

    /**
     * 使用单连接同名临时表屏蔽真实业务表；仅构造 JDBC/MyBatis，不启动应用、调度器或任何外呼客户端。
     */
    @BeforeEach
    void setUp() throws Exception {
        LocalIntegrationEnvironment.configureOrSkip();
        String url = "jdbc:mysql://" + property("DB_HOST") + ":" + property("DB_PORT") + "/" + property("DB_NAME") + "?useSSL=false&allowPublicKeyRetrieval=true";
        connection = DriverManager.getConnection(url, property("DB_USER"), property("DB_PWD"));
        dataSource = new SingleConnectionDataSource(connection, true);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute(temporaryTableDdl("053_moon_ore_profile.sql", "eve_moon_ore_profile"));
        jdbc.execute(temporaryTableDdl("019_moon_extractions.sql", "eve_moon_extraction"));
        jdbc.execute(temporaryTableDdl("032_game_communication_notifications.sql", "eve_game_notification"));

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(EveMoonOreProfileMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(EveMoonOreProfileMapper.class);
        transactionManager = new DataSourceTransactionManager(dataSource);
        transactionStatus = transactionManager.getTransaction(new DefaultTransactionDefinition());
    }

    /** 回滚合成数据并关闭连接，临时表随连接一起销毁。 */
    @AfterEach
    void tearDown() {
        if (transactionManager != null && transactionStatus != null && !transactionStatus.isCompleted()) {
            transactionManager.rollback(transactionStatus);
        }
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    /** PENDING 占位必须幂等，且租户、军团边界均参与唯一性约束。 */
    @Test
    void shouldInsertUniquePendingWithinTenantAndCorporation() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 24, 10, 0);
        insertExtraction(-991711L, TENANT_ID, CORPORATION_ID, 991401L, 991501L, now.plusHours(1));
        insertExtraction(-991712L, TENANT_ID, OTHER_CORPORATION_ID, 991401L, 991501L, now.plusHours(1));
        insertExtraction(-991713L, OTHER_TENANT_ID, CORPORATION_ID, 991401L, 991501L, now.plusHours(1));
        assertThat(mapper.insertPending(TENANT_ID, CORPORATION_ID, 991401L, 991501L, USER_ID, now)).isEqualTo(1);
        assertThat(mapper.insertPending(TENANT_ID, CORPORATION_ID, 991401L, 991501L, USER_ID, now)).isZero();
        assertThat(mapper.insertPending(TENANT_ID, OTHER_CORPORATION_ID, 991401L, 991501L, USER_ID, now)).isEqualTo(1);
        assertThat(mapper.insertPending(OTHER_TENANT_ID, CORPORATION_ID, 991401L, 991501L, USER_ID, now)).isEqualTo(1);

        List<EveMoonOreProfileDO> profiles = mapper.selectProfiles(TENANT_ID, CORPORATION_ID, List.of(991401L));
        assertThat(profiles).singleElement().satisfies(profile -> {
            assertThat(profile.getStatus()).isEqualTo("PENDING");
            assertThat(profile.getMoonId()).isEqualTo(991501L);
            assertThat(profile.getOresJson()).isNull();
        });
    }

    /** 自动初始化只能原子升级 PENDING，一旦 READY 就不得被后续自动识别覆盖。 */
    @Test
    void shouldAtomicallyUpgradePendingWithoutOverwritingReadyProfile() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 24, 10, 0);
        insertExtraction(-991721L, CORPORATION_ID, 991402L, 991502L, now.plusHours(1));
        mapper.insertPending(TENANT_ID, CORPORATION_ID, 991402L, 991502L, USER_ID, now);
        EveMoonOreProfileDO first = readyProfile(991402L, 991502L, 991601L, now
            .minusDays(2), "沥青", "[{\"typeId\":45492,\"typeName\":\"沥青\",\"proportion\":1.0000000000}]");

        assertThat(mapper.updateReadyIfPending(first, USER_ID, now)).isEqualTo(1);

        EveMoonOreProfileDO second = readyProfile(991402L, 991502L, 991602L, now
            .minusDays(1), "晶冰石", "[{\"typeId\":16264,\"typeName\":\"晶冰石\",\"proportion\":1.0000000000}]");
        assertThat(mapper.updateReadyIfPending(second, USER_ID, now.plusMinutes(1))).isZero();

        EveMoonOreProfileDO stored = profile(991402L);
        assertThat(stored.getStatus()).isEqualTo("READY");
        assertThat(stored.getOreNames()).isEqualTo("沥青");
        assertThat(stored.getSourceNotificationId()).isEqualTo(991601L);
        assertThat(jdbc.queryForObject("""
            SELECT JSON_CONTAINS_PATH(ores_json, 'one', '$[0].volume', '$[0].quantity', '$[0].amount')
            FROM eve_moon_ore_profile
            WHERE tenant_id = ? AND corporation_ref_id = ? AND structure_id = ? AND deleted = 0
            """, Integer.class, TENANT_ID, CORPORATION_ID, 991402L)).isZero();
        assertThat(jdbc.queryForObject("""
            SELECT CAST(JSON_UNQUOTE(JSON_EXTRACT(ores_json, '$[0].proportion')) AS DECIMAL(12, 10))
            FROM eve_moon_ore_profile
            WHERE tenant_id = ? AND corporation_ref_id = ? AND structure_id = ? AND deleted = 0
            """, BigDecimal.class, TENANT_ID, CORPORATION_ID, 991402L)).isEqualByComparingTo("1");
    }

    /** 手动刷新只能接受更新的通知，旧通知不能清空档案或降低来源时间。 */
    @Test
    void shouldRejectStaleManualRefreshWithoutClearingReadyProfile() {
        LocalDateTime sourceTime = LocalDateTime.of(2026, 10, 20, 12, 0);
        LocalDateTime now = LocalDateTime.of(2026, 10, 24, 10, 0);
        insertExtraction(-991722L, CORPORATION_ID, 991403L, 991503L, now.plusHours(1));
        mapper.insertPending(TENANT_ID, CORPORATION_ID, 991403L, 991503L, USER_ID, now);
        EveMoonOreProfileDO current = readyProfile(991403L, 991503L, 991603L, sourceTime, "沥青", "[{\"typeId\":45492,\"typeName\":\"沥青\",\"proportion\":1}]");
        assertThat(mapper.updateReadyIfPending(current, USER_ID, now)).isEqualTo(1);
        String storedJson = profile(991403L).getOresJson();

        EveMoonOreProfileDO stale = readyProfile(991403L, 991503L, 991699L, sourceTime.minusMinutes(1), null, null);
        assertThat(mapper.forceUpdateReady(stale, USER_ID, now.plusMinutes(1))).isZero();
        EveMoonOreProfileDO sameTimeOlderId = readyProfile(991403L, 991503L, 991602L, sourceTime, null, null);
        assertThat(mapper.forceUpdateReady(sameTimeOlderId, USER_ID, now.plusMinutes(2))).isZero();

        EveMoonOreProfileDO stored = profile(991403L);
        assertThat(stored.getStatus()).isEqualTo("READY");
        assertThat(stored.getOresJson()).isEqualTo(storedJson);
        assertThat(stored.getOreNames()).isEqualTo("沥青");
        assertThat(stored.getSourceNotificationId()).isEqualTo(991603L);
        assertThat(stored.getSourceSentAt()).isEqualTo(sourceTime);
    }

    /** 新月球已成为 ACTIVE 后，旧月球慢请求不得回写，只有当前月球可以重置并升级档案。 */
    @Test
    void shouldRejectStaleMoonWritesAfterActiveMoonChanges() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 24, 10, 0);
        Long structureId = 991404L;
        Long oldMoonId = 991504L;
        Long newMoonId = 991505L;
        insertExtraction(-991723L, CORPORATION_ID, structureId, newMoonId, now.plusHours(1));
        assertThat(mapper.insertPending(TENANT_ID, CORPORATION_ID, structureId, oldMoonId, USER_ID, now.plusSeconds(1)))
            .isZero();
        assertThat(mapper.selectProfiles(TENANT_ID, CORPORATION_ID, List.of(structureId))).isEmpty();
        insertPendingProfile(structureId, oldMoonId, now);

        EveMoonOreProfileDO stale = readyProfile(structureId, oldMoonId, 991604L, now
            .minusDays(1), "沥青", "[{\"typeId\":45492,\"typeName\":\"沥青\",\"proportion\":1}]");
        assertThat(mapper.updateReadyIfPending(stale, USER_ID, now.plusMinutes(1))).isZero();
        assertThat(mapper.resetForMoonChange(TENANT_ID, CORPORATION_ID, structureId, newMoonId, USER_ID, now
            .plusMinutes(2))).isEqualTo(1);
        assertThat(mapper.resetForMoonChange(TENANT_ID, CORPORATION_ID, structureId, oldMoonId, USER_ID, now
            .plusMinutes(3))).isZero();

        EveMoonOreProfileDO current = readyProfile(structureId, newMoonId, 991605L, now, "晶冰石", "[{\"typeId\":16264,\"typeName\":\"晶冰石\",\"proportion\":1}]");
        assertThat(mapper.updateReadyIfPending(current, USER_ID, now.plusMinutes(4))).isEqualTo(1);
        assertThat(profile(structureId).getMoonId()).isEqualTo(newMoonId);
    }

    /** 矿种搜索必须按完整复合归属关联档案，并能稳定分页。 */
    @Test
    void shouldSearchOreNamesWithOwnershipAndPagination() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 24, 10, 0);
        insertExtraction(-991701L, CORPORATION_ID, 991411L, 991511L, now.plusHours(1));
        insertExtraction(-991702L, CORPORATION_ID, 991412L, 991512L, now.plusHours(2));
        insertExtraction(-991703L, CORPORATION_ID, 991413L, 991513L, now.plusHours(3));
        insertExtraction(-991704L, CORPORATION_ID, 991414L, 991514L, now.plusHours(4));
        insertReadyProfile(CORPORATION_ID, 991411L, 991511L, "沥青; 铬铁矿", now);
        insertReadyProfile(CORPORATION_ID, 991412L, 991512L, "沥青", now);
        insertReadyProfile(CORPORATION_ID, 991413L, 991513L, "晶冰石", now);
        insertReadyProfile(OTHER_CORPORATION_ID, 991414L, 991514L, "沥青", now);

        String ownershipJoin = """
            FROM eve_moon_extraction extraction
            WHERE extraction.tenant_id = ? AND extraction.corporation_ref_id = ?
              AND extraction.status = 'ACTIVE' AND extraction.deleted = 0
              AND EXISTS (
                  SELECT 1 FROM eve_moon_ore_profile profile
                  WHERE profile.tenant_id = extraction.tenant_id
                    AND profile.corporation_ref_id = extraction.corporation_ref_id
                    AND profile.structure_id = extraction.structure_id
                    AND profile.moon_id = extraction.moon_id
                    AND profile.deleted = 0 AND profile.ore_names LIKE ?
              )
            """;
        Long total = jdbc
            .queryForObject("SELECT COUNT(*) " + ownershipJoin, Long.class, TENANT_ID, CORPORATION_ID, "%沥青%");
        List<Long> firstPage = jdbc
            .queryForList("SELECT extraction.id " + ownershipJoin + " ORDER BY extraction.chunk_arrival_at, extraction.id LIMIT ? OFFSET ?", Long.class, TENANT_ID, CORPORATION_ID, "%沥青%", 1, 0);
        List<Long> secondPage = jdbc
            .queryForList("SELECT extraction.id " + ownershipJoin + " ORDER BY extraction.chunk_arrival_at, extraction.id LIMIT ? OFFSET ?", Long.class, TENANT_ID, CORPORATION_ID, "%沥青%", 1, 1);

        assertThat(total).isEqualTo(2L);
        assertThat(firstPage).containsExactly(-991701L);
        assertThat(secondPage).containsExactly(-991702L);
    }

    /** 月矿通知缓存必须同时匹配租户、本站用户和授权角色，不能跨任一边界读取。 */
    @Test
    void shouldSelectMoonNotificationsOnlyForExactOwnerTriple() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 24, 10, 0);
        insertNotification(-991801L, TENANT_ID, USER_ID, CHARACTER_REF_ID, 991901L, "MoonminingExtractionStarted", now);
        insertNotification(-991802L, TENANT_ID, USER_ID + 1, CHARACTER_REF_ID, 991902L, "MoonminingExtractionFinished", now
            .plusMinutes(1));
        insertNotification(-991803L, TENANT_ID, USER_ID, CHARACTER_REF_ID + 1, 991903L, "MoonminingLaserFired", now
            .plusMinutes(2));
        insertNotification(-991804L, OTHER_TENANT_ID, USER_ID, CHARACTER_REF_ID, 991904L, "MoonminingAutomaticFracture", now
            .plusMinutes(3));
        insertNotification(-991805L, TENANT_ID, USER_ID, CHARACTER_REF_ID, 991905L, "CorporationTaxNpcKilled", now
            .plusMinutes(4));
        insertNotification(-991806L, TENANT_ID, USER_ID, CHARACTER_REF_ID, 991906L, "MoonminingExtractionFinished", now
            .plusMinutes(5));

        List<EveGameNotificationDO> notifications = mapper
            .selectMoonNotifications(TENANT_ID, USER_ID, CHARACTER_REF_ID);

        assertThat(notifications).extracting(EveGameNotificationDO::getNotificationId)
            .containsExactly(991906L, 991901L);
    }

    /** 构造可供 Mapper 更新的 READY 档案。 */
    private static EveMoonOreProfileDO readyProfile(Long structureId,
                                                    Long moonId,
                                                    Long notificationId,
                                                    LocalDateTime sentAt,
                                                    String oreNames,
                                                    String oresJson) {
        EveMoonOreProfileDO profile = new EveMoonOreProfileDO();
        profile.setTenantId(TENANT_ID);
        profile.setCorporationRefId(CORPORATION_ID);
        profile.setStructureId(structureId);
        profile.setMoonId(moonId);
        profile.setStatus("READY");
        profile.setOresJson(oresJson);
        profile.setOreNames(oreNames);
        profile.setSourceNotificationId(notificationId);
        profile.setSourceSentAt(sentAt);
        return profile;
    }

    /** 查询当前测试军团的单个建筑档案。 */
    private EveMoonOreProfileDO profile(Long structureId) {
        return mapper.selectProfiles(TENANT_ID, CORPORATION_ID, List.of(structureId)).get(0);
    }

    /** 写入合成月矿时间线，用于验证矿种搜索关联。 */
    private void insertExtraction(Long id, Long corporationId, Long structureId, Long moonId, LocalDateTime arrivalAt) {
        insertExtraction(id, TENANT_ID, corporationId, structureId, moonId, arrivalAt);
    }

    /** 写入指定租户的合成月矿时间线，用于验证租户和军团边界。 */
    private void insertExtraction(Long id,
                                  Long tenantId,
                                  Long corporationId,
                                  Long structureId,
                                  Long moonId,
                                  LocalDateTime arrivalAt) {
        jdbc.update("""
            INSERT INTO eve_moon_extraction
                (id, tenant_id, corporation_ref_id, structure_id, structure_name, moon_id, moon_name,
                 extraction_start_at, chunk_arrival_at, natural_decay_at, status, last_seen_at,
                 create_user, create_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, 0)
            """, id, tenantId, corporationId, structureId, "测试矿堡" + structureId, moonId, "测试月球" + moonId, arrivalAt
            .minusDays(10), arrivalAt, arrivalAt.plusDays(2), arrivalAt.minusMinutes(1), USER_ID, arrivalAt
                .minusDays(10));
    }

    /** 直接构造旧周期遗留的 PENDING 档案，用于复现 ACTIVE 月球切换后的慢请求竞态。 */
    private void insertPendingProfile(Long structureId, Long moonId, LocalDateTime now) {
        jdbc.update("""
            INSERT INTO eve_moon_ore_profile
                (tenant_id, corporation_ref_id, structure_id, moon_id, status, create_user, create_time, deleted)
            VALUES (?, ?, ?, ?, 'PENDING', ?, ?, 0)
            """, TENANT_ID, CORPORATION_ID, structureId, moonId, USER_ID, now);
    }

    /** 写入搜索测试所需的 READY 矿种档案。 */
    private void insertReadyProfile(Long corporationId,
                                    Long structureId,
                                    Long moonId,
                                    String oreNames,
                                    LocalDateTime now) {
        jdbc.update("""
            INSERT INTO eve_moon_ore_profile
                (tenant_id, corporation_ref_id, structure_id, moon_id, status, ores_json, ore_names,
                 source_notification_id, source_sent_at, create_user, create_time, deleted)
            VALUES (?, ?, ?, ?, 'READY', JSON_ARRAY(JSON_OBJECT('typeId', 45492, 'typeName', ?, 'proportion', 1)),
                    ?, ?, ?, ?, ?, 0)
            """, TENANT_ID, corporationId, structureId, moonId, oreNames, oreNames, structureId, now
            .minusDays(1), USER_ID, now);
    }

    /** 写入合成通知，仅用于验证本地缓存读取边界。 */
    private void insertNotification(Long id,
                                    Long tenantId,
                                    Long userId,
                                    Long characterRefId,
                                    Long notificationId,
                                    String notificationType,
                                    LocalDateTime sentAt) {
        jdbc.update("""
            INSERT INTO eve_game_notification
                (id, tenant_id, user_id, character_ref_id, character_id, notification_id, is_read,
                 notification_type, category, content, sent_at, last_seen_at, create_user, create_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, 0, ?, 'MOON_INDUSTRY', 'synthetic-test-notification', ?, ?, ?, ?, 0)
            """, id, tenantId, userId, characterRefId, characterRefId + 1000000, notificationId, notificationType, sentAt, sentAt, userId, sentAt);
    }

    /** 从真实迁移资源提取指定建表语句，并改为连接级临时表。 */
    private static String temporaryTableDdl(String resourceName, String tableName) throws Exception {
        String sql = resource(resourceName);
        String marker = "CREATE TABLE `" + tableName + "`";
        int start = sql.indexOf(marker);
        assertThat(start).isGreaterThanOrEqualTo(0);
        int end = sql.indexOf(';', start);
        assertThat(end).isGreaterThan(start);
        return sql.substring(start, end).replaceFirst("CREATE TABLE", "CREATE TEMPORARY TABLE");
    }

    /** 读取交付中的 Liquibase 迁移，避免测试结构自行漂移。 */
    private static String resource(String name) throws Exception {
        try (var input = EveMoonOreProfilePersistenceIT.class.getClassLoader()
            .getResourceAsStream("db/changelog/mysql/eve/" + name)) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 读取 LocalIntegrationEnvironment 已设置的连接参数。 */
    private static String property(String key) {
        String value = System.getProperty(key);
        assertThat(value).isNotBlank();
        return value;
    }
}
