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
import java.util.List;

/**
 * 军团资产树节点；物品箱仅在该物品确有子资产时标记为容器。
 *
 * @author zhaoyuqing
 */
public record EveCorporationAssetTreeNodeResp(String key, String title, String kind, Long itemId, Integer typeId,
                                              String typeName, String itemName, Integer quantity, String locationFlag,
                                              Boolean singleton, Boolean blueprintCopy, LocalDateTime lastSeenAt,
                                              LocalDateTime sourceExpiresAt,
                                              List<EveCorporationAssetTreeNodeResp> children) {
}
