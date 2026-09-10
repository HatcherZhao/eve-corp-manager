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

package top.continew.admin.eve.model.enums;

/**
 * EVE 数据模块面向用户展示的新鲜度状态。
 *
 * @author zhaoyuqing
 */
public enum EveDataFreshnessStatus {
    /** 最新完整快照仍在国服声明的有效期内。 */
    FRESH,
    /** 保留有快照，但其上游有效期已过。 */
    STALE,
    /** 最近一次同步失败，页面仍展示上一次完整快照。 */
    SYNC_FAILED,
    /** 尚未取得过可发布的完整快照。 */
    NO_DATA
}
