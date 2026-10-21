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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 按当前地图视图裁剪后的二维星图节点、连线和运营标注。 */
public record EveStarMapGraphResp(EveStarMapCoverageResp coverage, List<Node> nodes, List<Edge> edges,
                                  List<Annotation> annotations) {
    /** 星系节点。 */
    public record Node(Long systemId, String name, Long regionId, Long constellationId, BigDecimal securityStatus,
                       BigDecimal x, BigDecimal y, boolean hasStructure, boolean hasMoonExtraction, boolean hasAsset,
                       int trackedMemberCount) {
    }

    /** 经去重的星门连线。 */
    public record Edge(Long fromSystemId, Long toSystemId) {
    }

    /** 当前租户的有效人工运营标注。 */
    public record Annotation(Long id, Long systemId, String category, String title, String note, String colorKey,
                             @EveDateTime(zone = "Asia/Shanghai") LocalDateTime expiresAt) {
    }
}
