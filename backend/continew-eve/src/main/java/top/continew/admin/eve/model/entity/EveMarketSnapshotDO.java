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

package top.continew.admin.eve.model.entity;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 全站共享的吉他市场物品缓存快照。
 *
 * <p>订单簿和历史走势保留官方 ESI 的后台同步快照，避免页面打开时重复访问上游。</p>
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_market_snapshot")
@InterceptorIgnore(tenantLine = "true")
public class EveMarketSnapshotDO {

    /** 游戏物品类型 ID。 */
    @TableId
    private Integer typeId;
    /** 吉他最高求购价。 */
    private BigDecimal highestBuyPrice;
    /** 吉他最低卖出价。 */
    private BigDecimal lowestSellPrice;
    /** 求购总量。 */
    private Long buyVolume;
    /** 卖出总量。 */
    private Long sellVolume;
    /** 上游报价统计更新时间。 */
    private LocalDateTime sourceUpdatedAt;
    /** 最近一次成功更新报价的时间。 */
    private LocalDateTime quoteSynchronizedAt;
    /** 官方订单响应的缓存截止时间；到期后必须重新校验行情。 */
    private LocalDateTime quoteNextRequestAt;
    /** 最近一次成功确认该报价可供当前业务批次使用的时间。 */
    private LocalDateTime quoteValidatedAt;
    /** 最近一次成功更新订单和历史的时间。 */
    private LocalDateTime detailSynchronizedAt;
    /** 最近一次成功更新价格历史的时间。 */
    private LocalDateTime historySynchronizedAt;
    /** 历史数据来源及区域版本；为空的旧缓存不得按官方日均价展示。 */
    private String historySource;
    /** 根据官方缓存期限及失败退避计算的下次历史请求时间。 */
    private LocalDateTime historyNextRequestAt;
    /** 最近抓取到的买单 JSON。 */
    private String buyOrdersJson;
    /** 最近抓取到的卖单 JSON。 */
    private String sellOrdersJson;
    /** 最近抓取到的每日历史 JSON。 */
    private String historyJson;
    /** 最近一次上游失败的脱敏摘要。 */
    private String lastFailureMessage;
    /** 最近一次订单查询失败摘要。 */
    private String orderFailureMessage;
    /** 最近一次订单查询失败时间。 */
    private LocalDateTime orderFailureAt;
    /** 最近一次历史查询失败摘要。 */
    private String historyFailureMessage;
    /** 最近一次历史查询失败时间。 */
    private LocalDateTime historyFailureAt;
    /** 最近一次访问该物品详情的时间，用于低频后台保温。 */
    private LocalDateTime lastAccessedAt;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
