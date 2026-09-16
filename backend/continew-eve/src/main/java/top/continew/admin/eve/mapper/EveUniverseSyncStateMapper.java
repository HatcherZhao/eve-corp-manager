package top.continew.admin.eve.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import top.continew.admin.eve.model.entity.EveUniverseSyncStateDO;
import top.continew.starter.data.mapper.BaseMapper;

/** 公开宇宙拓扑同步状态 Mapper。 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface EveUniverseSyncStateMapper extends BaseMapper<EveUniverseSyncStateDO> {
}
