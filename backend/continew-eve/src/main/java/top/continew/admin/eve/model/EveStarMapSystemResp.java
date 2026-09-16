package top.continew.admin.eve.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 星系详情及与当前军团有关的只读运营摘要。 */
public record EveStarMapSystemResp(Long systemId, String name, Long regionId, String regionName, Long constellationId,
                                   String constellationName, BigDecimal securityStatus, List<Neighbor> neighbors,
                                   List<Structure> structures, List<Annotation> annotations,
                                   LocalDateTime synchronizedAt) {
    /** 相邻星系摘要。 */
    public record Neighbor(Long systemId, String name) {
    }

    /** 当前军团自有建筑摘要。 */
    public record Structure(Long structureId, String name, String typeName, String state, LocalDateTime fuelExpiresAt) {
    }

    /** 人工运营标注摘要。 */
    public record Annotation(Long id, String category, String title, String note, String colorKey,
                             LocalDateTime expiresAt) {
    }
}
