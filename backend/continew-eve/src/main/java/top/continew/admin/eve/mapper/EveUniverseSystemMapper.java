package top.continew.admin.eve.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import top.continew.admin.eve.model.entity.EveUniverseSystemDO;
import top.continew.starter.data.mapper.BaseMapper;

import java.util.List;

/** 公开宇宙星系快照 Mapper。 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface EveUniverseSystemMapper extends BaseMapper<EveUniverseSystemDO> {

    /** 将国服星系目录按主键去重写入待同步索引，不覆盖已完成的坐标快照。 */
    @Insert({"<script>", "INSERT IGNORE INTO eve_universe_system (system_id) VALUES",
        "<foreach collection='systemIds' item='systemId' separator=','>(#{systemId})</foreach>", "</script>"})
    int insertIgnoreSystemIds(@org.apache.ibatis.annotations.Param("systemIds") List<Long> systemIds);

    /** 读取尚未完成详情同步的少量星系，避免一次任务挤占国服请求配额。 */
    @Select("SELECT system_id FROM eve_universe_system WHERE synchronized_at IS NULL ORDER BY system_id ASC LIMIT #{limit}")
    List<Long> selectPendingSystemIds(int limit);

    /** 优先返回当前已同步军团建筑所在的未完成星系，使地图先可用于军团运营。 */
    @Select("SELECT universe.system_id FROM eve_universe_system AS universe "
        + "INNER JOIN (SELECT DISTINCT solar_system_id FROM eve_corporation_structure "
        + "WHERE status = 'ACTIVE' AND deleted = 0 AND solar_system_id IS NOT NULL) AS structure "
        + "ON structure.solar_system_id = universe.system_id "
        + "WHERE universe.synchronized_at IS NULL ORDER BY universe.system_id ASC LIMIT #{limit}")
    List<Long> selectPendingStructureSystemIds(int limit);

    /** 统计目录已发现的星系数量。 */
    @Select("SELECT COUNT(*) FROM eve_universe_system")
    long countIndexedSystems();

    /** 统计已取得完整坐标和星门目录的星系数量。 */
    @Select("SELECT COUNT(*) FROM eve_universe_system WHERE synchronized_at IS NOT NULL")
    long countSynchronizedSystems();
}
