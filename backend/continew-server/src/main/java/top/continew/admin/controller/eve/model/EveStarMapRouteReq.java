package top.continew.admin.controller.eve.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 保存军团星图路线的请求。 */
public record EveStarMapRouteReq(@NotBlank @Size(max = 80) String title, @Size(max = 1000) String description,
                                 @NotNull Long originSystemId, @NotNull Long destinationSystemId,
                                 @Size(max = 20) List<Long> viaSystemIds) {
}
