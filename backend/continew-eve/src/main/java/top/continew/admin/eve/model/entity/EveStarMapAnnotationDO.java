package top.continew.admin.eve.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.common.base.model.entity.TenantBaseDO;

import java.time.LocalDateTime;

/** 军团在本站维护的星系运营标注。 */
@Data
@TableName("eve_starmap_annotation")
public class EveStarMapAnnotationDO extends TenantBaseDO {

    /** 标注所在星系 ID。 */
    private Long solarSystemId;
    /** 标注分类。 */
    private String category;
    /** 标注标题。 */
    private String title;
    /** 标注说明。 */
    private String note;
    /** 预设颜色键。 */
    private String colorKey;
    /** 到期隐藏时间。 */
    private LocalDateTime expiresAt;
    /** 是否归档。 */
    private Boolean archived;
}
