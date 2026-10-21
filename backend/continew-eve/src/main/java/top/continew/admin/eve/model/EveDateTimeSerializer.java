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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** 仅为显式标注的响应字段附带时区，不影响上游解析及内部缓存。 */
public class EveDateTimeSerializer extends JsonSerializer<LocalDateTime> implements ContextualSerializer {

    private final ZoneId zone;

    /** Jackson 创建序列化器时默认采用 ESI 的 UTC 时区。 */
    public EveDateTimeSerializer() {
        this(ZoneId.of("UTC"));
    }

    /** 为单个字段固定原始存储时区。 */
    private EveDateTimeSerializer(ZoneId zone) {
        this.zone = zone;
    }

    /** 保留同一瞬时并输出显式偏移量。 */
    @Override
    public void serialize(LocalDateTime value,
                          JsonGenerator generator,
                          SerializerProvider provider) throws IOException {
        generator.writeString(value.atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    /** 按字段注解创建独立实例，避免不同来源时间互相污染。 */
    @Override
    public JsonSerializer<?> createContextual(SerializerProvider provider, BeanProperty property) {
        EveDateTime annotation = property == null ? null : property.getAnnotation(EveDateTime.class);
        return annotation == null ? this : new EveDateTimeSerializer(ZoneId.of(annotation.zone()));
    }
}
