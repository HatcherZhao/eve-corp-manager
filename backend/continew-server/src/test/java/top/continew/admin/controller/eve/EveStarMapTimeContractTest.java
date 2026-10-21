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

package top.continew.admin.controller.eve;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import top.continew.admin.controller.eve.model.EveStarMapAnnotationReq;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证星图真实请求模型不会丢弃输入时区。 */
class EveStarMapTimeContractTest {

    /** 新版带偏移请求与旧版北京裸时间应写入同一北京时间。 */
    @Test
    void shouldNormalizeAnnotationExpiry() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        for (String value : new String[] {"2026-10-21T04:00:00Z", "2026-10-21T12:00:00+08:00", "2026-10-21T12:00:00"}) {
            var request = mapper
                .readValue("{\"systemId\":1,\"category\":\"note\",\"title\":\"test\",\"expiresAt\":\"" + value + "\"}", EveStarMapAnnotationReq.class);
            assertEquals(LocalDateTime.of(2026, 10, 21, 12, 0), request.expiresAt());
        }
    }
}
