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

import java.time.LocalDate;
import java.util.List;

/**
 * 当前军团月矿开采统计的图表聚合结果。
 *
 * @author zhaoyuqing
 */
public record EveMiningAnalyticsResp(CorporationOverview corporation, List<TimelinePoint> timeline,
                                     List<ObserverRanking> observers, List<CharacterRanking> characters) {

    /** 当前军团在筛选范围内的开采概览。 */
    public record CorporationOverview(String name, String ticker, long quantity, long entryCount, int observerCount,
                                      int characterCount, int mineralTypeCount) {
    }

    /** 单个开采日期的趋势统计。 */
    public record TimelinePoint(LocalDate recordedAt, long quantity, long entryCount) {
    }

    /** 单个月矿建筑或观察者的开采排名。 */
    public record ObserverRanking(String observerName, long quantity, long entryCount) {
    }

    /** 单个玩家的开采排名。 */
    public record CharacterRanking(String characterName, long quantity, long entryCount) {
    }
}
