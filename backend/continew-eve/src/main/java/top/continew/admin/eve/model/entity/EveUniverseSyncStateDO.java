package top.continew.admin.eve.model.entity;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 公开宇宙拓扑可续跑同步进度。 */
@Data
@TableName("eve_universe_sync_state")
@InterceptorIgnore(tenantLine = "true")
public class EveUniverseSyncStateDO {

    /** 固定同步状态键。 */
    @TableId
    private String syncKey;
    /** 目录中声明的总数量。 */
    private Integer expectedCount;
    /** 已成功完成的数量。 */
    private Integer completedCount;
    /** 最近成功时间。 */
    private LocalDateTime lastSuccessAt;
    /** 最近失败时间。 */
    private LocalDateTime lastFailureAt;
    /** 脱敏失败分类。 */
    private String failureCode;
    /** 下次可重试时间。 */
    private LocalDateTime nextRetryAt;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
