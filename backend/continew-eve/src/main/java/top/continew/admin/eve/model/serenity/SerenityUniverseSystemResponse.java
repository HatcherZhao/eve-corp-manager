package top.continew.admin.eve.model.serenity;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/** 国服公开星系资料中构建星图所需的字段。 */
public record SerenityUniverseSystemResponse(@JsonProperty("system_id") Long systemId,
                                            @JsonProperty("constellation_id") Long constellationId,
                                            @JsonProperty("security_status") BigDecimal securityStatus,
                                            SerenityUniversePosition position,
                                            List<Long> stargates) {
}
