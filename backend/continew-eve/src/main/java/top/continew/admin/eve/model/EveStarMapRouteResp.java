package top.continew.admin.eve.model;

import java.time.LocalDateTime;
import java.util.List;

/** 经国服路线接口验证的导航路线。 */
public record EveStarMapRouteResp(Long id, String title, String description, Long originSystemId,
                                  Long destinationSystemId, int jumpCount, LocalDateTime validatedAt,
                                  boolean archived, List<Point> points) {
    /** 路线节点。 */
    public record Point(int order, Long systemId, String systemName, String kind) {
    }
}
