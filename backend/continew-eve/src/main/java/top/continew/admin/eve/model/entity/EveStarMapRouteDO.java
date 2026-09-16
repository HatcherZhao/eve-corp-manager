package top.continew.admin.eve.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.common.base.model.entity.TenantBaseDO;

import java.time.LocalDateTime;

/** 军团保存的、已按国服路线验证的航线。 */
@Data
@TableName("eve_starmap_route")
public class EveStarMapRouteDO extends TenantBaseDO {

    /** 航线名称。 */
    private String title;
    /** 航线说明。 */
    private String description;
    /** 起点星系 ID。 */
    private Long originSystemId;
    /** 终点星系 ID。 */
    private Long destinationSystemId;
    /** 国服计算跳数。 */
    private Integer jumpCount;
    /** 最近验证时间。 */
    private LocalDateTime validatedAt;
    /** 是否归档。 */
    private Boolean archived;
}
