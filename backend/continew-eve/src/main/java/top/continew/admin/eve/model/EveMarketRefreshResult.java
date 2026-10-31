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

import java.util.List;

/**
 * 一批按需市场刷新结果。
 *
 * <p>调用方只能把 {@link #refreshedTypeIds()} 视为本次已确认可用的行情；失败或因预算延期的物品
 * 必须明确展示为未就绪，不能静默沿用旧价格参与估值或利润计算。</p>
 *
 * @author zhaoyuqing
 */
public record EveMarketRefreshResult(List<Integer> refreshedTypeIds,
                                     List<Integer> failedTypeIds,
                                     List<Integer> deferredTypeIds) {

    /** 构造不可变的刷新结果。 */
    public EveMarketRefreshResult {
        refreshedTypeIds = List.copyOf(refreshedTypeIds);
        failedTypeIds = List.copyOf(failedTypeIds);
        deferredTypeIds = List.copyOf(deferredTypeIds);
    }
}
