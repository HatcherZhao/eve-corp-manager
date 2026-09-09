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

import java.time.LocalDateTime;

/**
 * EVE 静态物品类型资料列表项。
 *
 * @author zhaoyuqing
 */
public record EveStaticTypeReferenceResp(Integer typeId, String typeName, String typeDescription,
                                         String marketCategoryL1, String marketCategoryL2, String marketCategoryL3,
                                         String marketCategoryL4, String marketCategoryL5, String marketCategoryL6,
                                         LocalDateTime sourceUpdatedAt) {
}
