package top.continew.admin.eve.model;

import java.time.LocalDateTime;

/** 星图公开底图的可用覆盖率和同步状态。 */
public record EveStarMapCoverageResp(long indexedSystemCount, long synchronizedSystemCount, long stargateCount,
                                     LocalDateTime lastSuccessfulAt, LocalDateTime lastFailureAt, String failureCode) {
}
