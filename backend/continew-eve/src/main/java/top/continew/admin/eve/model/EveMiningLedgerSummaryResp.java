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
import java.time.LocalDateTime;

/**
 * 当前筛选条件下的采矿账本汇总。
 *
 * @author zhaoyuqing
 */
public record EveMiningLedgerSummaryResp(long quantity, long entryCount, int observerCount, int characterCount,
                                         int mineralTypeCount, LocalDate latestRecordedAt,
                                         LocalDateTime latestSynchronizedAt) {
}
