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
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import top.continew.admin.LocalIntegrationEnvironment;
import top.continew.admin.eve.mapper.EveSyncJobMapper;
import top.continew.admin.eve.model.entity.EveSyncJobDO;
import top.continew.admin.eve.model.enums.EveSyncJobState;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自动同步任务重排的真实 MySQL 验证，使用连接级临时表隔离业务数据。
 *
 * @author zhaoyuqing
 */
class EveSyncJobSchedulePersistenceIT {

    private static final long TENANT_ID = 993001L;
    private static final long CORPORATION_REF_ID = 993101L;
    private static final LocalDateTime LAST_FINISHED_AT = LocalDateTime.of(2026, 10, 25, 10, 0);

    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;
    private EveSyncJobMapper mapper;
    private EveSyncJobService service;

    /** 建立单连接并创建同名临时表，确保更新语句在真实 MySQL 方言下执行。 */
    @BeforeEach
    void setUp() throws Exception {
        LocalIntegrationEnvironment.configureOrSkip();
        String url = "jdbc:mysql://" + property("DB_HOST") + ":" + property("DB_PORT") + "/" + property("DB_NAME") + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        Connection connection = DriverManager.getConnection(url, property("DB_USER"), property("DB_PWD"));
        dataSource = new SingleConnectionDataSource(connection, true);
        jdbc = new JdbcTemplate(dataSource);
        createTemporaryTables();

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(EveSyncJobMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(EveSyncJobMapper.class);
        service = new EveSyncJobService(mapper);
    }

    /** 关闭连接后临时表与测试数据由 MySQL 自动清理。 */
    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    /** 新周期早于官方缓存期限时，必须以官方缓存期限作为最早执行时间。 */
    @Test
    void shouldKeepSourceExpiryAsLowerBound() {
        LocalDateTime sourceExpiresAt = LAST_FINISHED_AT.plusHours(1);
        insertPendingJob(1L, LAST_FINISHED_AT.plusHours(6), LAST_FINISHED_AT.plusMinutes(10), 0, null);
        insertFreshness(sourceExpiresAt);

        boolean advanced = service.advanceHealthyPendingSchedule(job(1L), Duration.ofMinutes(30));

        assertThat(advanced).isTrue();
        assertThat(nextRunAt(1L)).isEqualTo(sourceExpiresAt);
    }

    /** 旧手动同步只有一分钟冷却时，只要新鲜度表保存了缓存期限就允许安全提前。 */
    @Test
    void shouldAdvanceLegacyManualScheduleWhenFreshnessProvesSourceExpiry() {
        insertPendingJob(2L, LAST_FINISHED_AT.plusHours(6), LAST_FINISHED_AT.plusMinutes(1), 0, null);
        insertFreshness(LAST_FINISHED_AT.plusMinutes(10));

        boolean advanced = service.advanceHealthyPendingSchedule(job(2L), Duration.ofMinutes(30));

        assertThat(advanced).isTrue();
        assertThat(nextRunAt(2L)).isEqualTo(LAST_FINISHED_AT.plusMinutes(30));
    }

    /** 无来源期限证明的旧手动任务不能被提前，避免缩短配置后绕过官方缓存。 */
    @Test
    void shouldNotAdvanceLegacyManualScheduleWithoutFreshnessProof() {
        LocalDateTime originalNextRunAt = LAST_FINISHED_AT.plusHours(6);
        insertPendingJob(3L, originalNextRunAt, LAST_FINISHED_AT.plusMinutes(1), 0, null);

        boolean advanced = service.advanceHealthyPendingSchedule(job(3L), Duration.ofMinutes(30));

        assertThat(advanced).isFalse();
        assertThat(nextRunAt(3L)).isEqualTo(originalNextRunAt);
    }

    /** 运行中、暂停和带失败状态的任务都不能被配置重排恢复或覆盖。 */
    @Test
    void shouldNotAdvanceRunningPausedOrFailedJobs() {
        insertJob(4L, EveSyncJobState.RUNNING, LAST_FINISHED_AT.plusHours(6), LAST_FINISHED_AT
            .plusMinutes(10), 0, null, "claim-4", LAST_FINISHED_AT.plusMinutes(20));
        insertJob(5L, EveSyncJobState.PAUSED, LAST_FINISHED_AT.plusHours(6), null, 0, null, null, null);
        insertPendingJob(6L, LAST_FINISHED_AT.plusHours(6), LAST_FINISHED_AT.plusMinutes(10), 1, "UPSTREAM_503");
        insertFreshness(LAST_FINISHED_AT.plusMinutes(10));

        assertThat(service.advanceHealthyPendingSchedule(job(4L), Duration.ofMinutes(30))).isFalse();
        assertThat(service.advanceHealthyPendingSchedule(job(5L), Duration.ofMinutes(30))).isFalse();
        assertThat(service.advanceHealthyPendingSchedule(job(6L), Duration.ofMinutes(30))).isFalse();
        assertThat(nextRunAt(4L)).isEqualTo(LAST_FINISHED_AT.plusHours(6));
        assertThat(nextRunAt(5L)).isEqualTo(LAST_FINISHED_AT.plusHours(6));
        assertThat(nextRunAt(6L)).isEqualTo(LAST_FINISHED_AT.plusHours(6));
    }

    /** 读取后若下次时间或冷却时间已被其他执行者修改，CAS 必须拒绝覆盖新状态。 */
    @Test
    void shouldNotOverwriteConcurrentNextRunOrCooldownChanges() {
        insertPendingJob(7L, LAST_FINISHED_AT.plusHours(6), LAST_FINISHED_AT.plusMinutes(10), 0, null);
        insertPendingJob(8L, LAST_FINISHED_AT.plusHours(6), LAST_FINISHED_AT.plusMinutes(10), 0, null);
        insertFreshness(LAST_FINISHED_AT.plusMinutes(10));
        EveSyncJobDO nextRunCandidate = job(7L);
        EveSyncJobDO cooldownCandidate = job(8L);
        LocalDateTime concurrentNextRunAt = LAST_FINISHED_AT.plusHours(7);
        LocalDateTime concurrentCooldownUntil = LAST_FINISHED_AT.plusHours(2);
        jdbc.update("UPDATE eve_sync_job SET next_run_at=? WHERE id=7", concurrentNextRunAt);
        jdbc.update("UPDATE eve_sync_job SET cooldown_until=? WHERE id=8", concurrentCooldownUntil);

        assertThat(service.advanceHealthyPendingSchedule(nextRunCandidate, Duration.ofMinutes(30))).isFalse();
        assertThat(service.advanceHealthyPendingSchedule(cooldownCandidate, Duration.ofMinutes(30))).isFalse();
        assertThat(nextRunAt(7L)).isEqualTo(concurrentNextRunAt);
        assertThat(cooldownUntil(8L)).isEqualTo(concurrentCooldownUntil);
    }

    /** 创建 Mapper 查询与重排 SQL 所需的最小同名临时表。 */
    private void createTemporaryTables() {
        jdbc.execute("""
            CREATE TEMPORARY TABLE eve_sync_job (
              id BIGINT PRIMARY KEY,
              tenant_id BIGINT NOT NULL,
              target_type VARCHAR(32) NOT NULL,
              target_ref_id BIGINT NOT NULL,
              module VARCHAR(64) NOT NULL,
              state VARCHAR(32) NOT NULL,
              next_run_at DATETIME NOT NULL,
              cooldown_until DATETIME NULL,
              last_started_at DATETIME NULL,
              last_finished_at DATETIME NULL,
              consecutive_failures INT NOT NULL DEFAULT 0,
              last_failure_code VARCHAR(64) NULL,
              claim_token VARCHAR(64) NULL,
              claim_expires_at DATETIME NULL,
              create_user BIGINT NULL,
              create_time DATETIME NULL,
              update_user BIGINT NULL,
              update_time DATETIME NULL,
              deleted BIGINT NOT NULL DEFAULT 0
            )
            """);
        jdbc.execute("""
            CREATE TEMPORARY TABLE eve_data_sync_status (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              tenant_id BIGINT NOT NULL,
              corporation_ref_id BIGINT NOT NULL,
              module VARCHAR(64) NOT NULL,
              last_successful_at DATETIME NULL,
              source_expires_at DATETIME NULL,
              last_failure_at DATETIME NULL,
              failure_code VARCHAR(64) NULL,
              deleted BIGINT NOT NULL DEFAULT 0
            )
            """);
    }

    /** 写入没有执行租约的等待任务。 */
    private void insertPendingJob(long id,
                                  LocalDateTime nextRunAt,
                                  LocalDateTime cooldownUntil,
                                  int failures,
                                  String failureCode) {
        insertJob(id, EveSyncJobState.PENDING, nextRunAt, cooldownUntil, failures, failureCode, null, null);
    }

    /** 构造指定状态、排期和租约的任务。 */
    private void insertJob(long id,
                           EveSyncJobState state,
                           LocalDateTime nextRunAt,
                           LocalDateTime cooldownUntil,
                           int failures,
                           String failureCode,
                           String claimToken,
                           LocalDateTime claimExpiresAt) {
        jdbc.update("""
            INSERT INTO eve_sync_job
              (id, tenant_id, target_type, target_ref_id, module, state, next_run_at, cooldown_until,
               last_finished_at, consecutive_failures, last_failure_code, claim_token, claim_expires_at,
               create_time, update_time, deleted)
            VALUES (?, ?, 'CORPORATION', ?, 'ASSETS', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """, id, TENANT_ID, CORPORATION_REF_ID, state
            .name(), nextRunAt, cooldownUntil, LAST_FINISHED_AT, failures, failureCode, claimToken, claimExpiresAt, LAST_FINISHED_AT, LAST_FINISHED_AT);
    }

    /** 写入军团快照的官方缓存期限证明。 */
    private void insertFreshness(LocalDateTime sourceExpiresAt) {
        jdbc.update("""
            INSERT INTO eve_data_sync_status
              (tenant_id, corporation_ref_id, module, last_successful_at, source_expires_at, deleted)
            VALUES (?, ?, 'ASSETS', ?, ?, 0)
            """, TENANT_ID, CORPORATION_REF_ID, LAST_FINISHED_AT, sourceExpiresAt);
    }

    /** 通过实际 Mapper 读取条件更新前的任务快照。 */
    private EveSyncJobDO job(long id) {
        return mapper.selectById(id);
    }

    /** 读取数据库中最终排期，避免仅验证 Mapper 返回值。 */
    private LocalDateTime nextRunAt(long id) {
        return jdbc.queryForObject("SELECT next_run_at FROM eve_sync_job WHERE id=?", LocalDateTime.class, id);
    }

    /** 读取数据库中最终冷却期限。 */
    private LocalDateTime cooldownUntil(long id) {
        return jdbc.queryForObject("SELECT cooldown_until FROM eve_sync_job WHERE id=?", LocalDateTime.class, id);
    }

    /** 获取专用集成环境加载后的连接配置，不输出配置值。 */
    private static String property(String key) {
        return System.getProperty(key);
    }
}
