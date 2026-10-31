/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package top.continew.admin.eve.model.entity;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 记录一组物品按需刷新的固定起点，确保大批量查询最终能够收敛。 */
@Data
@TableName("eve_market_refresh_batch")
@InterceptorIgnore(tenantLine = "true")
public class EveMarketRefreshBatchDO {

    /** 排序后的物品集合与历史需求生成的稳定键。 */
    @TableId
    private String batchKey;
    /** 是否要求区域成交历史。 */
    private Boolean includeHistory;
    /** 批次物品数量。 */
    private Integer typeCount;
    /** 本轮批次的固定行情起点。 */
    private LocalDateTime requestedAt;
    /** 全部物品满足本轮要求的时间。 */
    private LocalDateTime completedAt;
    /** 首次创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
