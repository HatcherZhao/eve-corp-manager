package top.continew.admin.eve.model.entity;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 国服公开星系的三维坐标和基础拓扑快照。 */
@Data
@TableName("eve_universe_system")
@InterceptorIgnore(tenantLine = "true")
public class EveUniverseSystemDO {

    /** 游戏星系 ID。 */
    @TableId
    private Long systemId;
    /** 所属星座 ID。 */
    private Long constellationId;
    /** 所属星域 ID。 */
    private Long regionId;
    /** 国服安全等级。 */
    private BigDecimal securityStatus;
    /** 官方三维 X 坐标。 */
    private BigDecimal positionX;
    /** 官方三维 Y 坐标。 */
    private BigDecimal positionY;
    /** 官方三维 Z 坐标。 */
    private BigDecimal positionZ;
    /** 星系内星门数。 */
    private Integer stargateCount;
    /** 国服缓存过期时间。 */
    private LocalDateTime sourceExpiresAt;
    /** 最近成功同步时间。 */
    private LocalDateTime synchronizedAt;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
