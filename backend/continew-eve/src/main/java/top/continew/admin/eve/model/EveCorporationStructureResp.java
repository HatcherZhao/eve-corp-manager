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
 * 军团建筑管理页面的安全展示数据。
 *
 * @author zhaoyuqing
 */
public record EveCorporationStructureResp(Long structureId, Integer typeId, String typeName, String structureName,
                                          Long solarSystemId, String solarSystemName, String state,
                                          LocalDateTime fuelExpiresAt, LocalDateTime stateTimerStartAt,
                                          LocalDateTime stateTimerEndAt, LocalDateTime unanchorsAt,
                                          List<Service> services, String status, LocalDateTime lastSeenAt,
                                          LocalDateTime sourceExpiresAt) {
    /** 建筑服务展示项。 */
    public record Service(String name, String state) {
    }
}
