/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package top.continew.admin.eve.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import top.continew.admin.eve.model.entity.EveMarketRefreshBatchDO;
import top.continew.starter.data.mapper.BaseMapper;

import java.time.LocalDateTime;

/** 按需市场刷新批次 Mapper。 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface EveMarketRefreshBatchMapper extends BaseMapper<EveMarketRefreshBatchDO> {

    /**
     * 创建或复用同一物品集合的批次；最近完成的批次继续复用，过期后才开启新一轮。
     */
    @Insert("""
        INSERT INTO eve_market_refresh_batch
            (batch_key, include_history, type_count, requested_at, completed_at, create_time, update_time)
        VALUES (#{batchKey}, #{includeHistory}, #{typeCount}, #{now}, NULL, #{now}, #{now})
        ON DUPLICATE KEY UPDATE
            requested_at = IF(completed_at IS NOT NULL AND completed_at < #{renewBefore}, #{now}, requested_at),
            completed_at = IF(completed_at IS NOT NULL AND completed_at < #{renewBefore}, NULL, completed_at),
            type_count = VALUES(type_count), include_history = VALUES(include_history), update_time = #{now}
        """)
    void startOrReuse(@Param("batchKey") String batchKey,
                      @Param("includeHistory") boolean includeHistory,
                      @Param("typeCount") int typeCount,
                      @Param("now") LocalDateTime now,
                      @Param("renewBefore") LocalDateTime renewBefore);

    /** 读取当前批次状态。 */
    @Select("""
        SELECT batch_key, include_history, type_count, requested_at, completed_at, create_time, update_time
        FROM eve_market_refresh_batch WHERE batch_key = #{batchKey}
        """)
    EveMarketRefreshBatchDO selectBatch(@Param("batchKey") String batchKey);

    /** 仅完成当前请求起点对应的批次，避免并发开启的新一轮被旧请求误完成。 */
    @Update("""
        UPDATE eve_market_refresh_batch SET completed_at = #{completedAt}, update_time = #{completedAt}
        WHERE batch_key = #{batchKey} AND requested_at = #{requestedAt} AND completed_at IS NULL
        """)
    int markCompleted(@Param("batchKey") String batchKey,
                      @Param("requestedAt") LocalDateTime requestedAt,
                      @Param("completedAt") LocalDateTime completedAt);
}
