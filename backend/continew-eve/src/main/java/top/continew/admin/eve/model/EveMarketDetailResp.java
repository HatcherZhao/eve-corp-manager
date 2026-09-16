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

/** 单物品市场详情，包含静态资料、报价、买卖盘及历史走势。 */
public record EveMarketDetailResp(EveStaticTypeReferenceResp type, EveMarketQuoteResp quote,
                                  List<EveMarketOrderResp> buyOrders, List<EveMarketOrderResp> sellOrders,
                                  List<EveMarketHistoryResp> history, boolean servedFromCache, String upstreamMessage) {
}
