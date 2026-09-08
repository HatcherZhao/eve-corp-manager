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

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.ContiNewAdminApplication;
import top.continew.admin.LocalIntegrationEnvironment;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.eve.mapper.EveAuthorizationMapper;
import top.continew.admin.eve.mapper.EveCharacterMapper;
import top.continew.admin.eve.model.entity.EveAuthorizationDO;
import top.continew.admin.eve.model.enums.EveAuthorizationStatus;
import top.continew.admin.eve.model.enums.EveAuthorizationVerificationStatus;
import top.continew.admin.eve.model.enums.EveCapability;
import top.continew.admin.eve.model.enums.EveCapabilityStatus;
import top.continew.starter.extension.tenant.annotation.TenantIgnore;
import top.continew.starter.extension.tenant.context.TenantContext;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用真实 MySQL、Redis db14 和 HTTP 请求链验证 A/B 军团租户隔离。
 *
 * @author zhaoyuqing
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EveTenantIsolationIT {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final Long PACKAGE_ID = 991400L;
    private static final Long TENANT_A = 991401L;
    private static final Long TENANT_B = 991402L;
    private static final Long USER_A = 991411L;
    private static final Long USER_B = 991412L;
    private static final Long USER_A_DIRECTOR = 991413L;
    private static final Long USER_A_STATION_MANAGER = 991414L;
    private static final Long USER_A_ACCOUNTANT = 991415L;
    private static final Long USER_B_DIRECTOR = 991416L;
    private static final Long USER_A_ORPHAN = 991417L;
    private static final Long CORPORATION_REF_A = 991421L;
    private static final Long CORPORATION_REF_B = 991422L;
    private static final Long CORPORATION_A = 991431L;
    private static final Long CORPORATION_B = 991432L;
    private static final Long CHARACTER_REF_A = 991441L;
    private static final Long CHARACTER_REF_B = 991442L;
    private static final Long CHARACTER_REF_A_DIRECTOR = 991443L;
    private static final Long CHARACTER_REF_A_STATION_MANAGER = 991444L;
    private static final Long CHARACTER_REF_A_ACCOUNTANT = 991445L;
    private static final Long CHARACTER_REF_B_DIRECTOR = 991446L;
    private static final Long CHARACTER_REF_A_ORPHAN = 991447L;
    private static final Long CHARACTER_A = 991451L;
    private static final Long CHARACTER_B = 991452L;
    private static final Long CHARACTER_A_DIRECTOR = 991453L;
    private static final Long CHARACTER_A_STATION_MANAGER = 991454L;
    private static final Long CHARACTER_A_ACCOUNTANT = 991455L;
    private static final Long CHARACTER_B_DIRECTOR = 991456L;
    private static final Long CHARACTER_A_ORPHAN = 991457L;
    private static final Long AUTHORIZATION_A = 991461L;
    private static final Long AUTHORIZATION_B = 991462L;
    private static final Long AUTHORIZATION_A_DIRECTOR = 991463L;
    private static final Long AUTHORIZATION_A_STATION_MANAGER = 991464L;
    private static final Long AUTHORIZATION_A_ACCOUNTANT = 991465L;
    private static final Long AUTHORIZATION_B_DIRECTOR = 991466L;
    private static final Long AUTHORIZATION_A_ORPHAN = 991467L;
    private static final Long MEMBER_A = 991471L;
    private static final Long MEMBER_B = 991472L;
    private static final Long MEMBER_A_DIRECTOR = 991473L;
    private static final Long MEMBER_A_STATION_MANAGER = 991474L;
    private static final Long MEMBER_A_ACCOUNTANT = 991475L;
    private static final Long MEMBER_B_DIRECTOR = 991476L;
    private static final Long SNAPSHOT_A = 991481L;
    private static final Long SNAPSHOT_B = 991482L;
    private static final Long SNAPSHOT_A_DIRECTOR = 991483L;
    private static final Long SNAPSHOT_A_STATION_MANAGER = 991484L;
    private static final Long SNAPSHOT_A_ACCOUNTANT = 991485L;
    private static final Long SNAPSHOT_B_DIRECTOR = 991486L;
    private static final String REDIS_PROBE_KEY = "eve:test:tenant-isolation:9914";

    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbcTemplate;
    private RedissonClient redissonClient;
    private EveCharacterMapper characterMapper;
    private EveAuthorizationMapper authorizationMapper;
    private EveCapabilityPolicy capabilityPolicy;
    private ObjectMapper objectMapper;
    private HttpClient httpClient;
    private int port;
    private String tokenA;
    private String tokenB;

    /** 启动完整应用，写入两组互不相属的租户测试数据并签发本站会话。 */
    @BeforeAll
    void setUp() {
        LocalIntegrationEnvironment.configureOrSkip();
        context = new SpringApplicationBuilder(ContiNewAdminApplication.class, IntegrationFixtureConfiguration.class)
            .run("--server.port=0");
        jdbcTemplate = context.getBean(JdbcTemplate.class);
        redissonClient = context.getBean(RedissonClient.class);
        characterMapper = context.getBean(EveCharacterMapper.class);
        authorizationMapper = context.getBean(EveAuthorizationMapper.class);
        capabilityPolicy = context.getBean(EveCapabilityPolicy.class);
        objectMapper = context.getBean(ObjectMapper.class);
        port = ((WebServerApplicationContext)context).getWebServer().getPort();
        httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();

        warmUpRedisDatabase();
        cleanupDatabase();
        insertPackage();
        insertTenant(TENANT_A, "eve_it_a");
        insertTenant(TENANT_B, "eve_it_b");
        insertCorporation(CORPORATION_REF_A, TENANT_A, CORPORATION_A, "隔离测试军团A", "ITA");
        insertCorporation(CORPORATION_REF_B, TENANT_B, CORPORATION_B, "隔离测试军团B", "ITB");
        insertCharacter(CHARACTER_REF_A, TENANT_A, USER_A, CHARACTER_A, CORPORATION_A, "隔离角色A");
        insertCharacter(CHARACTER_REF_B, TENANT_B, USER_B, CHARACTER_B, CORPORATION_B, "隔离角色B");
        insertCharacter(CHARACTER_REF_A_DIRECTOR, TENANT_A, USER_A_DIRECTOR, CHARACTER_A_DIRECTOR, CORPORATION_A, "总监角色A");
        insertCharacter(CHARACTER_REF_A_STATION_MANAGER, TENANT_A, USER_A_STATION_MANAGER, CHARACTER_A_STATION_MANAGER, CORPORATION_A, "设施角色A");
        insertCharacter(CHARACTER_REF_A_ACCOUNTANT, TENANT_A, USER_A_ACCOUNTANT, CHARACTER_A_ACCOUNTANT, CORPORATION_A, "会计角色A");
        insertCharacter(CHARACTER_REF_B_DIRECTOR, TENANT_B, USER_B_DIRECTOR, CHARACTER_B_DIRECTOR, CORPORATION_B, "总监角色B");
        insertCharacter(CHARACTER_REF_A_ORPHAN, TENANT_A, USER_A_ORPHAN, CHARACTER_A_ORPHAN, CORPORATION_A, "无成员关系角色A");
        insertMember(MEMBER_A, TENANT_A, CORPORATION_REF_A, CHARACTER_REF_A, USER_A);
        insertMember(MEMBER_B, TENANT_B, CORPORATION_REF_B, CHARACTER_REF_B, USER_B);
        insertMember(MEMBER_A_DIRECTOR, TENANT_A, CORPORATION_REF_A, CHARACTER_REF_A_DIRECTOR, USER_A_DIRECTOR);
        insertMember(MEMBER_A_STATION_MANAGER, TENANT_A, CORPORATION_REF_A, CHARACTER_REF_A_STATION_MANAGER, USER_A_STATION_MANAGER);
        insertMember(MEMBER_A_ACCOUNTANT, TENANT_A, CORPORATION_REF_A, CHARACTER_REF_A_ACCOUNTANT, USER_A_ACCOUNTANT);
        insertMember(MEMBER_B_DIRECTOR, TENANT_B, CORPORATION_REF_B, CHARACTER_REF_B_DIRECTOR, USER_B_DIRECTOR);
        insertAuthorization(AUTHORIZATION_A, TENANT_A, USER_A, CHARACTER_REF_A, EveCapability.MEMBERS.getScope());
        insertAuthorization(AUTHORIZATION_B, TENANT_B, USER_B, CHARACTER_REF_B, EveCapability.ASSETS.getScope());
        insertAuthorization(AUTHORIZATION_A_DIRECTOR, TENANT_A, USER_A_DIRECTOR, CHARACTER_REF_A_DIRECTOR, EveCapability.ASSETS
            .getScope());
        insertAuthorization(AUTHORIZATION_A_STATION_MANAGER, TENANT_A, USER_A_STATION_MANAGER, CHARACTER_REF_A_STATION_MANAGER, EveCapability.STRUCTURES
            .getScope());
        insertAuthorization(AUTHORIZATION_A_ACCOUNTANT, TENANT_A, USER_A_ACCOUNTANT, CHARACTER_REF_A_ACCOUNTANT, EveCapability.MINING
            .getScope());
        insertAuthorization(AUTHORIZATION_B_DIRECTOR, TENANT_B, USER_B_DIRECTOR, CHARACTER_REF_B_DIRECTOR, null);
        insertAuthorization(AUTHORIZATION_A_ORPHAN, TENANT_A, USER_A_ORPHAN, CHARACTER_REF_A_ORPHAN, EveCapability.ASSETS
            .getScope());
        insertRoleSnapshot(SNAPSHOT_A, TENANT_A, CHARACTER_REF_A, CHARACTER_A, true, null);
        insertRoleSnapshot(SNAPSHOT_B, TENANT_B, CHARACTER_REF_B, CHARACTER_B, false, null);
        insertRoleSnapshot(SNAPSHOT_A_DIRECTOR, TENANT_A, CHARACTER_REF_A_DIRECTOR, CHARACTER_A_DIRECTOR, false, "Director");
        insertRoleSnapshot(SNAPSHOT_A_STATION_MANAGER, TENANT_A, CHARACTER_REF_A_STATION_MANAGER, CHARACTER_A_STATION_MANAGER, false, "Station_Manager");
        insertRoleSnapshot(SNAPSHOT_A_ACCOUNTANT, TENANT_A, CHARACTER_REF_A_ACCOUNTANT, CHARACTER_A_ACCOUNTANT, false, "Accountant");
        insertRoleSnapshot(SNAPSHOT_B_DIRECTOR, TENANT_B, CHARACTER_REF_B_DIRECTOR, CHARACTER_B_DIRECTOR, false, "Director");
        tokenA = createSession(USER_A, TENANT_A);
        tokenB = createSession(USER_B, TENANT_B);
    }

    /** 精确删除测试数据、测试会话和 Redis 探针，不影响其他开发数据。 */
    @AfterAll
    void tearDown() {
        try {
            if (context != null) {
                closeSession(USER_A);
                closeSession(USER_B);
                redissonClient.getBucket(REDIS_PROBE_KEY).delete();
                cleanupDatabase();
            }
        } finally {
            UserContextHolder.clearContext();
            if (context != null) {
                context.close();
            }
        }
    }

    /** 两个真实 HTTP 会话只能读取各自军团上下文。 */
    @Test
    void shouldIsolateCorporationContextThroughHttp() throws Exception {
        HttpResponse<String> responseA = getContext(tokenA, TENANT_A);
        HttpResponse<String> responseB = getContext(tokenB, TENANT_B);

        assertThat(responseA.statusCode()).isEqualTo(200);
        assertThat(responseA.body()).contains("隔离测试军团A", "隔离角色A").doesNotContain("隔离测试军团B", "隔离角色B");
        assertThat(responseB.statusCode()).isEqualTo(200);
        assertThat(responseB.body()).contains("隔离测试军团B", "隔离角色B").doesNotContain("隔离测试军团A", "隔离角色A");
    }

    /** A 用户篡改请求租户为 B 时必须在进入 EVE 资源前返回真实 HTTP 403。 */
    @Test
    void shouldRejectForgedTenantHeaderThroughHttp() throws Exception {
        HttpResponse<String> response = getContext(tokenA, TENANT_B);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("\"code\":\"403\"", "您当前没有访问该租户的权限").doesNotContain("隔离测试军团B", "隔离角色B");
    }

    /** 真实 Mapper 查询必须同时约束租户和用户，角色及授权绑定均不能跨租户复用。 */
    @Test
    void shouldRejectCrossTenantBindingOwnershipInMysql() {
        withTenant(TENANT_A, () -> {
            assertThat(characterMapper.selectActiveByUser(TENANT_A, USER_A)).singleElement()
                .satisfies(character -> assertThat(character.getCharacterId()).isEqualTo(CHARACTER_A));
            assertThat(characterMapper.selectActiveByUser(TENANT_A, USER_B)).isEmpty();
            assertThat(authorizationMapper.selectOwnedById(TENANT_A, USER_A, AUTHORIZATION_B)).isNull();
        });
        withTenant(TENANT_B, () -> assertThat(authorizationMapper.selectOwnedById(TENANT_B, USER_B, AUTHORIZATION_B))
            .isNotNull());
    }

    /** 租户共享候选必须具备完整授权、角色、军团与成员关系，并排除其他租户。 */
    @Test
    void shouldSelectOnlyCompleteTenantAuthorizationChains() {
        withTenant(TENANT_A, () -> {
            List<EveAuthorizationDO> candidates = authorizationMapper.selectTenantCandidates(TENANT_A);

            assertThat(candidates).extracting(EveAuthorizationDO::getId)
                .containsExactlyInAnyOrder(AUTHORIZATION_A, AUTHORIZATION_A_DIRECTOR, AUTHORIZATION_A_STATION_MANAGER, AUTHORIZATION_A_ACCOUNTANT)
                .doesNotContain(AUTHORIZATION_B, AUTHORIZATION_B_DIRECTOR, AUTHORIZATION_A_ORPHAN);
            assertThat(candidates).extracting(EveAuthorizationDO::getUserId)
                .containsExactlyInAnyOrder(USER_A, USER_A_DIRECTOR, USER_A_STATION_MANAGER, USER_A_ACCOUNTANT);
        });
    }

    /** 普通查看者可分别复用同租户 CEO、总监、设施管理员与会计的数据源。 */
    @Test
    void shouldEvaluateCapabilitiesFromDifferentTenantUsers() {
        withTenant(TENANT_A, () -> {
            assertCapabilityAvailable(EveCapability.MEMBERS);
            assertCapabilityAvailable(EveCapability.ASSETS);
            assertCapabilityAvailable(EveCapability.STRUCTURES);
            assertCapabilityAvailable(EveCapability.MINING);
        });
    }

    /** Scope 与游戏角色分属同租户不同授权时不得拼接为可用能力。 */
    @Test
    void shouldNotCombineScopeAndRoleAcrossTenantAuthorizations() {
        withTenant(TENANT_B, () -> assertThat(capabilityPolicy.evaluate(EveCapability.ASSETS, TENANT_B, Set
            .of(EveCapability.ASSETS.getSitePermission())).status()).isEqualTo(EveCapabilityStatus.MISSING_GAME_ROLE));
    }

    /** Redis 客户端必须连接专用 db14，测试键只做精确写入和删除。 */
    @Test
    void shouldUseRedisDatabase14() {
        assertThat(redissonClient.getConfig().useSingleServer().getDatabase()).isEqualTo(14);
        RBucket<String> bucket = redissonClient.getBucket(REDIS_PROBE_KEY);
        bucket.set("ok", Duration.ofMinutes(1));
        assertThat(bucket.get()).isEqualTo("ok");
        assertThat(bucket.delete()).isTrue();
    }

    /** 只读访问探针键，提前完成 db14 连接池初始化，不改变 Redis 数据。 */
    private void warmUpRedisDatabase() {
        assertThat(redissonClient.getConfig().useSingleServer().getDatabase()).isEqualTo(14);
        redissonClient.getBucket(REDIS_PROBE_KEY).isExists();
    }

    /** 创建可被真实 HTTP 线程读取的 Sa-Token 用户会话。 */
    private String createSession(Long userId, Long tenantId) {
        HttpRequest request = HttpRequest.newBuilder(URI
            .create("http://127.0.0.1:" + port + "/__eve_it/session?userId=" + userId + "&tenantId=" + tenantId))
            .timeout(HTTP_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("无法创建租户隔离集成测试会话: " + summarizeResponse(response));
            }
            JsonNode body = objectMapper.readTree(response.body());
            String token = body.isTextual() ? body.asText() : body.path("data").asText();
            if (token.isBlank()) {
                throw new IllegalStateException("无法创建租户隔离集成测试会话: " + summarizeResponse(response));
            }
            return token;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("无法创建租户隔离集成测试会话", e);
        }
    }

    /** 仅提取状态码和业务错误字段，禁止把响应数据中的会话令牌写入异常。 */
    private String summarizeResponse(HttpResponse<String> response) {
        try {
            JsonNode body = objectMapper.readTree(response.body());
            return "HTTP " + response.statusCode() + ", code=" + body.path("code").asText("") + ", msg=" + body
                .path("msg")
                .asText("");
        } catch (Exception e) {
            return "HTTP " + response.statusCode() + ", 响应摘要解析失败";
        }
    }

    /** 通过测试 HTTP 夹具清理指定登录 ID 的全部会话。 */
    private void closeSession(Long userId) {
        HttpRequest request = HttpRequest.newBuilder(URI
            .create("http://127.0.0.1:" + port + "/__eve_it/session/logout?userId=" + userId))
            .timeout(HTTP_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        try {
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            throw new IllegalStateException("无法清理租户隔离集成测试会话", e);
        }
    }

    /** 请求当前 EVE 上下文，同时显式传递请求租户 ID。 */
    private HttpResponse<String> getContext(String token, Long tenantId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/eve/me/context"))
            .header("Authorization", "Bearer " + token)
            .header("X-Tenant-Id", tenantId.toString())
            .timeout(HTTP_TIMEOUT)
            .GET()
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** 在指定租户上下文中执行真实 Mapper 查询，并在完成后清理线程变量。 */
    private void withTenant(Long tenantId, Runnable action) {
        TenantContext tenantContext = new TenantContext();
        tenantContext.setTenantId(tenantId);
        TenantContextHolder.setContext(tenantContext);
        try {
            action.run();
        } finally {
            TenantContextHolder.clear();
        }
    }

    /** 写入测试租户引用的独立套餐，避免依赖目标数据库的初始化数据。 */
    private void insertPackage() {
        jdbcTemplate.update("""
            INSERT INTO tenant_package (id, name, sort, status, create_user, create_time, deleted)
            VALUES (?, 'EVE 租户隔离集成测试套餐', 999, 1, 1, ?, 0)
            """, PACKAGE_ID, LocalDateTime.now());
    }

    /** 写入可由租户 Provider 校验的启用租户。 */
    private void insertTenant(Long tenantId, String code) {
        jdbcTemplate.update("""
            INSERT INTO tenant (id, name, code, status, package_id, create_user, create_time, deleted)
            VALUES (?, ?, ?, 1, ?, 1, ?, 0)
            """, tenantId, "EVE IT " + code, code, PACKAGE_ID, LocalDateTime.now());
    }

    /** 写入租户唯一的 EVE 军团绑定。 */
    private void insertCorporation(Long id, Long tenantId, Long corporationId, String name, String ticker) {
        jdbcTemplate.update("""
            INSERT INTO eve_corporation
                (id, tenant_id, server, corporation_id, name, ticker, status, create_user, create_time, deleted)
            VALUES (?, ?, 'serenity', ?, ?, ?, 'ACTIVE', 1, ?, 0)
            """, id, tenantId, corporationId, name, ticker, LocalDateTime.now());
    }

    /** 写入租户和本站用户共同拥有的 EVE 角色绑定。 */
    private void insertCharacter(Long id,
                                 Long tenantId,
                                 Long userId,
                                 Long characterId,
                                 Long corporationId,
                                 String name) {
        jdbcTemplate.update("""
            INSERT INTO eve_character
                (id, tenant_id, user_id, server, character_id, corporation_id, owner_hash, name,
                 status, is_primary, create_user, create_time, deleted)
            VALUES (?, ?, ?, 'serenity', ?, ?, ?, ?, 'ACTIVE', b'1', 1, ?, 0)
            """, id, tenantId, userId, characterId, corporationId, "owner-" + characterId, name, LocalDateTime.now());
    }

    /** 写入有效军团成员关系。 */
    private void insertMember(Long id, Long tenantId, Long corporationRefId, Long characterRefId, Long userId) {
        jdbcTemplate.update("""
            INSERT INTO eve_corporation_member
                (id, tenant_id, corporation_ref_id, character_ref_id, user_id, status,
                 create_user, create_time, deleted)
            VALUES (?, ?, ?, ?, ?, 'ACTIVE', 1, ?, 0)
            """, id, tenantId, corporationRefId, characterRefId, userId, LocalDateTime.now());
    }

    /** 写入用于验证授权归属与 Scope 条件的有效记录。 */
    private void insertAuthorization(Long id, Long tenantId, Long userId, Long characterRefId, String scope) {
        EveAuthorizationDO authorization = new EveAuthorizationDO();
        authorization.setId(id);
        authorization.setTenantId(tenantId);
        authorization.setCharacterRefId(characterRefId);
        authorization.setUserId(userId);
        authorization.setServer("serenity");
        authorization.setScopes(scope == null ? List.of() : List.of(scope));
        authorization.setRefreshToken("integration-refresh-token-" + id);
        authorization.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        authorization.setStatus(EveAuthorizationStatus.ACTIVE);
        authorization.setFailureCount(0);
        authorization.setLastVerificationStatus(EveAuthorizationVerificationStatus.VALID);
        authorization.setCreateUser(1L);
        authorization.setCreateTime(LocalDateTime.now());
        authorization.setDeleted(0L);
        withTenant(tenantId, () -> authorizationMapper.insert(authorization));
    }

    /** 写入指定角色的最新军团权限快照。 */
    private void insertRoleSnapshot(Long id,
                                    Long tenantId,
                                    Long characterRefId,
                                    Long characterId,
                                    boolean ceo,
                                    String role) {
        jdbcTemplate.update("""
            INSERT INTO eve_character_role_snapshot
                (id, tenant_id, character_ref_id, server, character_id, is_ceo, roles,
                 roles_at_hq, roles_at_base, roles_at_other, captured_at, source_expires_at,
                 create_user, create_time, deleted)
            VALUES (?, ?, ?, 'serenity', ?, ?, IF(? IS NULL, JSON_ARRAY(), JSON_ARRAY(?)),
                    JSON_ARRAY(), JSON_ARRAY(), JSON_ARRAY(), ?, ?, 1, ?, 0)
            """, id, tenantId, characterRefId, characterId, ceo, role, role, LocalDateTime.now(), LocalDateTime.now()
            .plusMinutes(30), LocalDateTime.now());
    }

    /** 断言指定模块可由租户共享数据源提供。 */
    private void assertCapabilityAvailable(EveCapability capability) {
        assertThat(capabilityPolicy.evaluate(capability, TENANT_A, Set.of(capability.getSitePermission())).status())
            .isEqualTo(EveCapabilityStatus.AVAILABLE);
    }

    /** 按固定测试租户逆依赖顺序清理，不使用全表删除或 FLUSHDB。 */
    private void cleanupDatabase() {
        jdbcTemplate.update("DELETE FROM eve_character_role_snapshot WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        jdbcTemplate.update("DELETE FROM eve_authorization WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        jdbcTemplate.update("DELETE FROM eve_corporation_member WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        jdbcTemplate.update("DELETE FROM eve_character WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        jdbcTemplate.update("DELETE FROM eve_corporation WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        jdbcTemplate.update("DELETE FROM tenant WHERE id IN (?, ?)", TENANT_A, TENANT_B);
        jdbcTemplate.update("DELETE FROM tenant_package WHERE id = ?", PACKAGE_ID);
    }

    /** 仅在当前集成测试 ApplicationContext 中注册会话夹具。 */
    @Configuration(proxyBeanMethods = false)
    static class IntegrationFixtureConfiguration {

        /** 创建测试专用会话控制器。 */
        @Bean
        IntegrationSessionController integrationSessionController() {
            return new IntegrationSessionController();
        }
    }

    /** 通过真实 Web 上下文创建和清理 Sa-Token 会话，不进入生产构建。 */
    @RestController
    @SaIgnore
    @TenantIgnore
    static class IntegrationSessionController {

        /** 创建绑定指定用户和租户的测试会话。 */
        @PostMapping("/__eve_it/session")
        String create(@RequestParam Long userId, @RequestParam Long tenantId) {
            StpUtil.login(userId);
            UserContext userContext = new UserContext(Set.of(), Set.of(), 0);
            userContext.setId(userId);
            userContext.setTenantId(tenantId);
            userContext.setUsername("eve_it_" + userId);
            UserContextHolder.setContext(userContext);
            return StpUtil.getTokenValue();
        }

        /** 清理指定登录 ID 的全部测试会话。 */
        @PostMapping("/__eve_it/session/logout")
        void logout(@RequestParam Long userId) {
            StpUtil.logout(userId);
        }
    }
}
