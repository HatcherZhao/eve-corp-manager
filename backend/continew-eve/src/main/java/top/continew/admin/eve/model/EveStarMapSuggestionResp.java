package top.continew.admin.eve.model;

/** 星系全局搜索结果，保留游戏层级但不向用户暴露内部数据库主键。 */
public record EveStarMapSuggestionResp(Long systemId, String name, String regionName, String constellationName,
                                       String breadcrumb) {
}
