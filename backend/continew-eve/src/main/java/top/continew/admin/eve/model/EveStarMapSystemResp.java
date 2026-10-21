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

/** 星系详情及与当前军团有关的只读运营摘要。 */
public record EveStarMapSystemResp(Long systemId, String name, Long regionId, String regionName, Long constellationId,
                                   String constellationName, BigDecimal securityStatus, List<Neighbor> neighbors,
                                   List<Structure> structures, List<Annotation> annotations,
                                   @EveDateTime LocalDateTime synchronizedAt) {
    /** 相邻星系摘要。 */
    public record Neighbor(Long systemId, String name) {
    }

    /** 当前军团自有建筑摘要。 */
    public record Structure(Long structureId, String name, String typeName, String state,
                            @EveDateTime LocalDateTime fuelExpiresAt) {
    }

    /** 人工运营标注摘要。 */
    public record Annotation(Long id, String category, String title, String note, String colorKey,
                             @EveDateTime(zone = "Asia/Shanghai") LocalDateTime expiresAt) {
    }
}
