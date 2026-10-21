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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import top.continew.admin.eve.mapper.EveMarketSnapshotMapper;
import top.continew.admin.eve.mapper.EveStaticTypeReferenceMapper;
import top.continew.admin.eve.model.entity.EveMarketSnapshotDO;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 真实 MySQL 连接级临时表验证，不启动应用，也不读写现有业务表。 */
class EveMarketSnapshotPersistenceIT {

    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;
    private EveMarketSnapshotMapper mapper;
    private EveStaticTypeReferenceMapper typeMapper;
    private EveMarketSnapshotStore store;

    /** 使用显式集成测试凭据，临时表只对本连接可见，断开连接后自动消失。 */
    @BeforeEach
    void setUp() throws Exception {
        String password = System.getenv("G008_IT_DB_PWD");
        Assumptions.assumeTrue(password != null && !password.isBlank(), "缺少 G008_IT_DB_PWD，跳过真实 MySQL 验证");
        String url = "jdbc:mysql://" + env("G008_IT_DB_HOST", "127.0.0.1") + ":" + env("G008_IT_DB_PORT", "3306") + "/" + env("G008_IT_DB_NAME", "eve_corp_manager") + "?useSSL=false&allowPublicKeyRetrieval=true";
        dataSource = new SingleConnectionDataSource(DriverManager
            .getConnection(url, env("G008_IT_DB_USER", "root"), password), true);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("SET SESSION sql_mode = 'STRICT_TRANS_TABLES'");
        String ddl = resource("039_jita_market.sql");
        jdbc.execute(ddl.substring(0, ddl.indexOf(';')).replace("CREATE TABLE", "CREATE TEMPORARY TABLE"));
        jdbc.execute(resource("040_market_history_warmup.sql"));
        jdbc.execute(resource("052_market_esi_cache_safety.sql"));
        jdbc.execute("CREATE TEMPORARY TABLE eve_static_type_reference (type_id INT PRIMARY KEY, market_category_l1 VARCHAR(50))");

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(EveMarketSnapshotMapper.class);
        configuration.addMapper(EveStaticTypeReferenceMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(EveMarketSnapshotMapper.class);
        typeMapper = new SqlSessionTemplate(factory.getObject()).getMapper(EveStaticTypeReferenceMapper.class);
        TransactionInterceptor transaction = new TransactionInterceptor();
        transaction.setTransactionManager(new DataSourceTransactionManager(dataSource));
        transaction.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory proxy = new ProxyFactory(new EveMarketSnapshotStore(mapper));
        proxy.addAdvice(transaction);
        store = (EveMarketSnapshotStore)proxy.getProxy();
    }

    /** 关闭连接即可清理临时表，不需要对持久化数据库执行删除或 DDL。 */
    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    /** 单侧归零必须写入 SQL NULL，整个物品消失必须清空旧订单，同时保留区域历史。 */
    @Test
    void shouldClearMissingOrdersAndNullPricesWithoutLosingHistory() {
        LocalDateTime before = LocalDateTime.now().minusHours(1).withNano(0);
        store.replaceOrders(List.of(snapshot(34, "8.70", "9.30", before), snapshot(35, "10", "11", before)), before);
        mapper.upsertHistory(34, "[]", "SERENITY_ESI_REGION_V1:10000002", before, before.plusDays(1));
        mapper.upsertHistory(35, "[]", "SERENITY_ESI_REGION_V1:10000002", before, before.plusDays(1));
        LocalDateTime now = LocalDateTime.now().withNano(0);

        store.replaceOrders(List.of(snapshot(34, null, "9.10", now)), now);

        EveMarketSnapshotDO present = mapper.selectById(34);
        assertThat(present.getHighestBuyPrice()).isNull();
        assertThat(present.getLowestSellPrice()).isEqualByComparingTo("9.10");
        assertThat(present.getQuoteSynchronizedAt()).isEqualTo(now);
        assertThat(present.getHistorySynchronizedAt()).isEqualTo(before);
        EveMarketSnapshotDO missing = mapper.selectById(35);
        assertThat(missing.getHighestBuyPrice()).isNull();
        assertThat(missing.getLowestSellPrice()).isNull();
        assertThat(missing.getBuyVolume()).isZero();
        assertThat(missing.getSellVolume()).isZero();
        assertThat(missing.getBuyOrdersJson()).isEqualTo("[]");
        assertThat(missing.getSellOrdersJson()).isEqualTo("[]");
        assertThat(missing.getHistorySource()).isEqualTo("SERENITY_ESI_REGION_V1:10000002");
        assertThat(missing.getHistorySynchronizedAt()).isEqualTo(before);

        store.replaceOrders(List.of(), now.plusMinutes(1));
        assertThat(mapper.selectById(34).getLowestSellPrice()).isNull();
    }

    /** 第二批写入失败时，前一批新增和已执行的消失订单清理必须一起回滚。 */
    @Test
    void shouldRollBackEntirePublicationWhenSecondBatchFails() {
        LocalDateTime before = LocalDateTime.now().minusHours(1).withNano(0);
        store.replaceOrders(List.of(snapshot(9999, "8", "9", before)), before);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        List<EveMarketSnapshotDO> snapshots = new ArrayList<>();
        for (int typeId = 1; typeId <= 501; typeId++) {
            snapshots.add(snapshot(typeId, "8", typeId == 501 ? "999999999999999999999999999999" : "9", now));
        }

        assertThatThrownBy(() -> store.replaceOrders(snapshots, now)).isInstanceOf(RuntimeException.class);

        assertThat(jdbc
            .queryForObject("SELECT COUNT(*) FROM eve_market_snapshot WHERE type_id BETWEEN 1 AND 501", Long.class))
            .isZero();
        assertThat(mapper.selectById(9999).getHighestBuyPrice()).isEqualByComparingTo("8");
        assertThat(mapper.selectById(9999).getQuoteSynchronizedAt()).isEqualTo(before);
    }

    /** 切换区域不能继承旧区域的成功缓存窗口，否则新区域历史会长时间空白。 */
    @Test
    void shouldIgnorePreviousRegionsSuccessCooldown() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbc.update("INSERT INTO eve_static_type_reference VALUES (34, '矿物')");
        mapper.upsertHistory(34, "[]", "SERENITY_ESI_REGION_V1:10000002", now, now.plusDays(2));

        assertThat(typeMapper.selectHistoryWarmupTypeIds(1, "SERENITY_ESI_REGION_V1:10000003", LocalDateTime.now()))
            .containsExactly(34);
        assertThat(typeMapper.selectHistoryWarmupTypeIds(1, "SERENITY_ESI_REGION_V1:10000002", LocalDateTime.now()))
            .isEmpty();

        mapper.recordHistoryFailure(34, "暂时不可用", "SERENITY_ESI_REGION_V1:10000003", now, now.plusHours(1));
        assertThat(typeMapper.selectHistoryWarmupTypeIds(1, "SERENITY_ESI_REGION_V1:10000003", LocalDateTime.now()))
            .isEmpty();
        assertThat(mapper.selectById(34).getHistoryJson()).isEqualTo("[]");
        assertThat(mapper.selectById(34).getHistorySource()).isNull();
    }

    /** 构造独立的订单字段更新，历史不应出现在此写入集合中。 */
    private static EveMarketSnapshotDO snapshot(int typeId, String buy, String sell, LocalDateTime now) {
        EveMarketSnapshotDO snapshot = new EveMarketSnapshotDO();
        snapshot.setTypeId(typeId);
        snapshot.setHighestBuyPrice(buy == null ? null : new BigDecimal(buy));
        snapshot.setLowestSellPrice(sell == null ? null : new BigDecimal(sell));
        snapshot.setBuyVolume(buy == null ? 0L : 20L);
        snapshot.setSellVolume(sell == null ? 0L : 10L);
        snapshot.setBuyOrdersJson(buy == null ? "[]" : "[{\"price\":" + buy + "}]");
        snapshot.setSellOrdersJson(sell == null ? "[]" : "[{\"price\":" + sell + "}]");
        snapshot.setSourceUpdatedAt(now);
        snapshot.setQuoteSynchronizedAt(now);
        snapshot.setDetailSynchronizedAt(now);
        snapshot.setCreateTime(now);
        snapshot.setUpdateTime(now);
        return snapshot;
    }

    /** 直接验证实际迁移资源，避免测试表结构与交付的 Liquibase 脱节。 */
    private static String resource(String name) throws Exception {
        try (var input = EveMarketSnapshotPersistenceIT.class.getClassLoader()
            .getResourceAsStream("db/changelog/mysql/eve/" + name)) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 集成测试只读取专用环境配置，不使用应用密钥。 */
    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
