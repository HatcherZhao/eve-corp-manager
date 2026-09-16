package top.continew.admin.eve.model.entity;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 国服公开星门及其目的星系快照。 */
@Data
@TableName("eve_universe_stargate")
@InterceptorIgnore(tenantLine = "true")
public class EveUniverseStargateDO {

    /** 游戏星门 ID。 */
    @TableId
    private Long stargateId;
    /** 星门所属星系 ID。 */
    private Long systemId;
    /** 对端星门 ID。 */
    private Long destinationStargateId;
    /** 对端星系 ID。 */
    private Long destinationSystemId;
    /** 星门类型 ID。 */
    private Integer typeId;
    /** 官方 X 坐标。 */
    private BigDecimal positionX;
    /** 官方 Y 坐标。 */
    private BigDecimal positionY;
    /** 官方 Z 坐标。 */
    private BigDecimal positionZ;
    /** 国服缓存过期时间。 */
    private LocalDateTime sourceExpiresAt;
    /** 最近成功同步时间。 */
    private LocalDateTime synchronizedAt;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
