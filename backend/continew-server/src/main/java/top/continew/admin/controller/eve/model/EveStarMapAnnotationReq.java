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

package top.continew.admin.controller.eve.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import top.continew.admin.eve.model.EveBeijingDateTimeDeserializer;

import java.time.LocalDateTime;

/** 新建或更新军团星图运营标注的请求。 */
public record EveStarMapAnnotationReq(@NotNull Long systemId, @NotBlank @Size(max = 32) String category,
                                      @NotBlank @Size(max = 80) String title, @Size(max = 1000) String note,
                                      @Size(max = 24) String colorKey,
                                      @JsonDeserialize(using = EveBeijingDateTimeDeserializer.class) LocalDateTime expiresAt) {
}
