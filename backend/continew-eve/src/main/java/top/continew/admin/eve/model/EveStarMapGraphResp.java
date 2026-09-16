package top.continew.admin.eve.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 按当前地图视图裁剪后的二维星图节点、连线和运营标注。 */
public record EveStarMapGraphResp(EveStarMapCoverageResp coverage, List<Node> nodes, List<Edge> edges,
                                  List<Annotation> annotations) {
    /** 星系节点。 */
    public record Node(Long systemId, String name, Long regionId, Long constellationId, BigDecimal securityStatus,
                       BigDecimal x, BigDecimal y, boolean hasStructure, boolean hasMoonExtraction,
                       boolean hasAsset, int trackedMemberCount) {
    }

    /** 经去重的星门连线。 */
    public record Edge(Long fromSystemId, Long toSystemId) {
    }

    /** 当前租户的有效人工运营标注。 */
    public record Annotation(Long id, Long systemId, String category, String title, String note, String colorKey,
                             LocalDateTime expiresAt) {
    }
}
