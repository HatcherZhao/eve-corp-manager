package top.continew.admin.eve.model.serenity;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 国服公开星门资料中构建跨星系连线所需的字段。 */
public record SerenityUniverseStargateResponse(@JsonProperty("stargate_id") Long stargateId,
                                              @JsonProperty("system_id") Long systemId,
                                              @JsonProperty("type_id") Integer typeId,
                                              SerenityUniversePosition position,
                                              Destination destination) {
    /** 对端星门及其所属星系。 */
    public record Destination(@JsonProperty("stargate_id") Long stargateId,
                              @JsonProperty("system_id") Long systemId) {
    }
}
