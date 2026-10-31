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

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import top.continew.admin.eve.client.SerenityStaticDataClient;
import top.continew.admin.eve.model.entity.EveStaticTypeReferenceDO;
import top.continew.admin.eve.model.serenity.SerenityUniverseTypeResponse;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 官方市场分类路径构建测试。 */
class EveOfficialTypeReferenceServiceTest {

    /** 叶子物品必须继承完整父级路径，非市场物品由发布流程保留为空分类。 */
    @Test
    void shouldBuildMarketCategoryPathFromOfficialGroups() {
        var groups = new LinkedHashMap<Integer, EveOfficialTypeReferenceService.GroupStage>();
        groups.put(4, new EveOfficialTypeReferenceService.GroupStage(4, null, "舰船", List.of()));
        groups.put(1361, new EveOfficialTypeReferenceService.GroupStage(1361, 4, "护卫舰", List.of()));
        groups.put(5, new EveOfficialTypeReferenceService.GroupStage(5, 1361, "标准护卫舰", List.of(587)));

        var categories = EveOfficialTypeReferenceService.buildCategories(groups);

        assertThat(categories.get(587)).containsExactly("舰船", "护卫舰", "标准护卫舰");
        assertThat(categories).doesNotContainKey(34);
    }

    /** MySQL INT UNSIGNED 可能由 Connector/J 返回 Long，父市场组读取必须安全转换。 */
    @Test
    void shouldConvertUnsignedParentGroupIdReturnedAsLong() {
        assertThat(EveOfficialTypeReferenceService.integerOrNull(1361L)).isEqualTo(1361);
        assertThat(EveOfficialTypeReferenceService.integerOrNull(null)).isNull();
    }

    /** 有未解析名称时必须保留正式快照并继续重试，不能进入发布阶段。 */
    @Test
    void shouldNotPublishIncompleteOfficialDirectory() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT phase FROM eve_static_type_sync_state WHERE id = 1", String.class))
            .thenReturn("NAMES");
        when(jdbcTemplate.queryForList(contains("name_attempts < 3"), eq(Long.class))).thenReturn(List.of());
        when(jdbcTemplate
            .queryForObject("SELECT COUNT(*) FROM eve_static_type_stage WHERE type_name IS NULL", Long.class))
            .thenReturn(2L);
        EveOfficialTypeReferenceService service = service(mock(SerenityStaticDataClient.class), mock(EveStaticReferenceService.class), jdbcTemplate, mock(RedissonClient.class));

        assertThatThrownBy(() -> service.advance(1))
            .isInstanceOf(top.continew.starter.core.exception.BusinessException.class)
            .hasMessageContaining("2 个类型未返回")
            .hasMessageContaining("未发布");

        verify(jdbcTemplate).update("UPDATE eve_static_type_stage SET name_attempts = 0 WHERE type_name IS NULL");
        verify(jdbcTemplate).update(contains("SET last_error = ?"), contains("2 个类型未返回"));
    }

    /** 已成功取得且仍在有效期内的官方说明必须直接复用，不能占用上游额度。 */
    @Test
    void shouldReuseFreshOfficialTypeDetail() {
        SerenityStaticDataClient client = mock(SerenityStaticDataClient.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EveStaticReferenceService references = mock(EveStaticReferenceService.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(34))).thenReturn(List
            .of(new EveOfficialTypeReferenceService.TypeDetailState("官方说明", LocalDateTime.now().minusDays(1))));
        when(references.findTypes(Set.of(34))).thenReturn(Map.of(34, type(34, "三钛合金", "官方说明")));

        var result = service(client, references, jdbcTemplate, mock(RedissonClient.class)).refreshTypeDetail(34);

        assertThat(result.typeDescription()).isEqualTo("官方说明");
        verifyNoInteractions(client);
    }

    /** 等待同类型锁期间由其他实例完成刷新后，本实例必须复用结果而非再次访问官方接口。 */
    @Test
    void shouldReuseDetailRefreshedByAnotherInstanceWhileWaitingForLock() throws InterruptedException {
        SerenityStaticDataClient client = mock(SerenityStaticDataClient.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EveStaticReferenceService references = mock(EveStaticReferenceService.class);
        RedissonClient redisson = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redisson.getLock("eve:reference:official-type-detail:34")).thenReturn(lock);
        when(lock.tryLock(30, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(34))).thenReturn(List
            .of(new EveOfficialTypeReferenceService.TypeDetailState("旧说明", null)), List
                .of(new EveOfficialTypeReferenceService.TypeDetailState("官方说明", LocalDateTime.now())));
        when(references.findTypes(Set.of(34))).thenReturn(Map.of(34, type(34, "三钛合金", "官方说明")));

        var result = service(client, references, jdbcTemplate, redisson).refreshTypeDetail(34);

        assertThat(result.typeDescription()).isEqualTo("官方说明");
        verifyNoInteractions(client);
        verify(lock).unlock();
    }

    /** 过期说明在跨实例锁内刷新，成功时间与说明一起提交。 */
    @Test
    void shouldRefreshExpiredOfficialTypeDetailInsideDistributedLock() throws InterruptedException {
        SerenityStaticDataClient client = mock(SerenityStaticDataClient.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EveStaticReferenceService references = mock(EveStaticReferenceService.class);
        RedissonClient redisson = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redisson.getLock("eve:reference:official-type-detail:34")).thenReturn(lock);
        when(lock.tryLock(30, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(34))).thenReturn(List
            .of(new EveOfficialTypeReferenceService.TypeDetailState("旧说明", LocalDateTime.now().minusDays(31))));
        when(client.getType(34)).thenReturn(new SerenityUniverseTypeResponse(34, "三钛合金", "官方说明", 18, true));
        when(references.findTypes(Set.of(34))).thenReturn(Map.of(34, type(34, "三钛合金", "官方说明")));

        var result = service(client, references, jdbcTemplate, redisson).refreshTypeDetail(34);

        assertThat(result.typeDescription()).isEqualTo("官方说明");
        verify(client).getType(34);
        verify(jdbcTemplate).update(contains("official_detail_updated_at = NOW()"), eq("三钛合金"), eq("官方说明"), eq(34));
        verify(lock).unlock();
    }

    /** 上游失败时不能覆盖已有说明或伪造成功时间。 */
    @Test
    void shouldKeepExistingDetailWhenOfficialRequestFails() throws InterruptedException {
        SerenityStaticDataClient client = mock(SerenityStaticDataClient.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RedissonClient redisson = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redisson.getLock("eve:reference:official-type-detail:34")).thenReturn(lock);
        when(lock.tryLock(30, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(34))).thenReturn(List
            .of(new EveOfficialTypeReferenceService.TypeDetailState("旧说明", null)));
        when(client.getType(34)).thenThrow(new IllegalStateException("upstream failed"));

        assertThatThrownBy(() -> service(client, mock(EveStaticReferenceService.class), jdbcTemplate, redisson)
            .refreshTypeDetail(34)).isInstanceOf(IllegalStateException.class);

        verify(jdbcTemplate, never()).update(contains("official_detail_updated_at = NOW()"), any(), any(), any());
        verify(lock).unlock();
    }

    private static EveOfficialTypeReferenceService service(SerenityStaticDataClient client,
                                                           EveStaticReferenceService references,
                                                           JdbcTemplate jdbcTemplate,
                                                           RedissonClient redisson) {
        return new EveOfficialTypeReferenceService(client, references, jdbcTemplate, mock(org.springframework.transaction.support.TransactionTemplate.class), mock(com.fasterxml.jackson.databind.ObjectMapper.class), redisson);
    }

    private static EveStaticTypeReferenceDO type(int typeId, String name, String description) {
        EveStaticTypeReferenceDO type = new EveStaticTypeReferenceDO();
        type.setTypeId(typeId);
        type.setTypeName(name);
        type.setTypeDescription(description);
        return type;
    }
}
