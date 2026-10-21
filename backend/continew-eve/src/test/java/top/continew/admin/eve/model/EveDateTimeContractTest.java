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

package top.continew.admin.eve.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

/** 时间响应协议回归，覆盖两种存储口径及不同时区的运行环境。 */
class EveDateTimeContractTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    /** UTC 月矿与北京市场时间必须标明偏移量，不能受 JVM 时区影响。 */
    @Test
    void shouldSerializeExplicitOffsetsRegardlessOfJvmTimezone() throws Exception {
        TimeZone original = TimeZone.getDefault();
        try {
            for (String zone : new String[] {"UTC", "Asia/Shanghai", "America/New_York"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                var value = new MixedTime(LocalDateTime.of(2026, 10, 21, 4, 0), LocalDateTime
                    .of(2026, 10, 21, 12, 0), LocalDate.of(2026, 10, 21));
                var json = mapper.readTree(mapper.writeValueAsString(value));
                assertEquals("2026-10-21T04:00:00Z", json.path("utc").asText());
                assertEquals("2026-10-21T12:00:00+08:00", json.path("beijing").asText());
                assertEquals(LocalDate.of(2026, 10, 21), mapper.treeToValue(json.path("date"), LocalDate.class));
                assertTrue(mapper.readTree(mapper.writeValueAsString(new MixedTime(null, null, null)))
                    .path("utc")
                    .isNull());
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    /** 月矿响应本身必须携带 UTC 标记，而非只验证测试专用 DTO。 */
    @Test
    void shouldTagActualMoonExtractionTime() throws Exception {
        var arrival = LocalDateTime.of(2026, 10, 21, 4, 0);
        var response = new EveMoonExtractionResp(null, null, null, null, null, null, null, null, null, arrival, null, null, null, null, null, null);
        assertEquals("2026-10-21T04:00:00Z", mapper.readTree(mapper.writeValueAsString(response))
            .path("chunkArrivalAt")
            .asText());
    }

    /** 所有响应（含嵌套 record）的瞬时时间都必须声明来源时区。 */
    @Test
    void shouldDeclareTimezoneOnEveryResponseTime() throws Exception {
        Path root = Path.of("src/main/java/top/continew/admin/eve/model");
        try (var paths = Files.list(root)) {
            for (Path path : paths.filter(item -> item.getFileName().toString().endsWith("Resp.java")).toList()) {
                String name = path.getFileName().toString().replace(".java", "");
                assertExplicitTime(Class.forName("top.continew.admin.eve.model." + name));
            }
        }
    }

    /** 星图输入保留同一瞬时并兼容旧版北京裸时间，非法时间返回校验错误。 */
    @Test
    void shouldNormalizeAnnotationInputToBeijing() throws Exception {
        for (String value : new String[] {"2026-10-21T04:00:00Z", "2026-10-21T12:00:00+08:00", "2026-10-21T12:00:00"}) {
            assertEquals(LocalDateTime.of(2026, 10, 21, 12, 0), mapper
                .readValue("{\"expiresAt\":\"" + value + "\"}", AnnotationInput.class)
                .expiresAt());
        }
        assertNull(mapper.readValue("{\"expiresAt\":null}", AnnotationInput.class).expiresAt());
        assertThrows(Exception.class, () -> mapper.readValue("{\"expiresAt\":\"invalid\"}", AnnotationInput.class));
    }

    /** 递归检查嵌套响应，防止后续新增字段再次丢失时区。 */
    private void assertExplicitTime(Class<?> type) {
        if (type.isRecord()) {
            for (var field : type.getRecordComponents()) {
                if (field.getType() == LocalDateTime.class) {
                    assertNotNull(field.getAnnotation(EveDateTime.class), type.getSimpleName() + "." + field.getName());
                }
            }
        }
        for (Class<?> nested : type.getDeclaredClasses()) {
            assertExplicitTime(nested);
        }
    }

    /** 验证同一响应中两种存储时间不会混淆。 */
    record MixedTime(@EveDateTime LocalDateTime utc, @EveDateTime(zone = "Asia/Shanghai") LocalDateTime beijing,
                     LocalDate date) {
    }

    /** 星图反序列化契约。 */
    record AnnotationInput(@JsonDeserialize(using = EveBeijingDateTimeDeserializer.class) LocalDateTime expiresAt) {
    }
}
