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

package top.continew.admin.eve.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import top.continew.admin.eve.model.entity.EveMarketRefreshQueueDO;
import top.continew.starter.data.mapper.BaseMapper;

import java.time.LocalDateTime;
import java.util.List;

/** 按需市场刷新队列 Mapper。 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface EveMarketRefreshQueueMapper extends BaseMapper<EveMarketRefreshQueueDO> {

    /** 以物品 ID 去重入队；历史需求只能升级，不能被后续普通报价请求降级。 */
    @Insert("""
        INSERT INTO eve_market_refresh_queue
            (type_id, include_history, attempt_count, task_version, next_attempt_at, create_time, update_time)
        VALUES (#{typeId}, #{includeHistory}, 0, 0, #{now}, #{now}, #{now})
        ON DUPLICATE KEY UPDATE
            task_version = task_version + IF(VALUES(include_history) > include_history, 1, 0),
            next_attempt_at = IF(VALUES(include_history) > include_history,
                                 LEAST(next_attempt_at, VALUES(next_attempt_at)), next_attempt_at),
            update_time = IF(VALUES(include_history) > include_history, VALUES(update_time), update_time),
            include_history = GREATEST(include_history, VALUES(include_history))
        """)
    void enqueue(@Param("typeId") int typeId,
                 @Param("includeHistory") boolean includeHistory,
                 @Param("now") LocalDateTime now);

    /** 读取当前到期任务；跨实例执行由服务层全局锁串行保护。 */
    @Select("""
        SELECT type_id, include_history, attempt_count, task_version, next_attempt_at, last_error, create_time, update_time
        FROM eve_market_refresh_queue
        WHERE next_attempt_at <= #{now}
        ORDER BY next_attempt_at ASC, create_time ASC
        LIMIT #{limit}
        """)
    List<EveMarketRefreshQueueDO> selectDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** 批量读取已存在的任务，轮询时避免对每个等待物品重复执行 upsert。 */
    @Select("""
        <script>
        SELECT type_id, include_history, attempt_count, task_version, next_attempt_at, last_error, create_time, update_time
        FROM eve_market_refresh_queue WHERE type_id IN
        <foreach collection="typeIds" item="typeId" open="(" close=")" separator=",">#{typeId}</foreach>
        </script>
        """)
    List<EveMarketRefreshQueueDO> selectQueued(@Param("typeIds") List<Integer> typeIds);

    /** 成功后删除任务；新的页面访问可按新鲜度再次入队。 */
    @Delete("DELETE FROM eve_market_refresh_queue WHERE type_id = #{typeId} AND task_version = #{taskVersion}")
    int deleteTask(@Param("typeId") int typeId, @Param("taskVersion") long taskVersion);

    /** 失败后保存摘要并按调用方计算的时间退避。 */
    @Update("""
        UPDATE eve_market_refresh_queue
        SET attempt_count = attempt_count + 1, next_attempt_at = #{nextAttemptAt},
            last_error = #{message}, update_time = #{now}
        WHERE type_id = #{typeId} AND task_version = #{taskVersion}
        """)
    int recordFailure(@Param("typeId") int typeId,
                       @Param("taskVersion") long taskVersion,
                       @Param("message") String message,
                       @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
                       @Param("now") LocalDateTime now);
}
