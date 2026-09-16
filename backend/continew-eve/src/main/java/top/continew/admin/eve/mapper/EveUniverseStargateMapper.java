package top.continew.admin.eve.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import top.continew.admin.eve.model.entity.EveUniverseStargateDO;
import top.continew.starter.data.mapper.BaseMapper;


/** 公开宇宙星门快照 Mapper。 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface EveUniverseStargateMapper extends BaseMapper<EveUniverseStargateDO> {
}
