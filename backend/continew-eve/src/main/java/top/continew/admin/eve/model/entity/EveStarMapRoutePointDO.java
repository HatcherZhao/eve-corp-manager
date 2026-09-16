package top.continew.admin.eve.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.common.base.model.entity.TenantBaseDO;

/** 一条军团航线内按官方计算顺序保存的星系节点。 */
@Data
@TableName("eve_starmap_route_point")
public class EveStarMapRoutePointDO extends TenantBaseDO {

    /** 所属航线 ID。 */
    private Long routeId;
    /** 节点顺序。 */
    private Integer pointOrder;
    /** 星系 ID。 */
    private Long solarSystemId;
    /** ORIGIN、VIA、DESTINATION 或 COMPUTED。 */
    private String pointKind;
}
