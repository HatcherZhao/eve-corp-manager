package top.continew.admin.controller.eve.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 新建或更新军团星图运营标注的请求。 */
public record EveStarMapAnnotationReq(@NotNull Long systemId, @NotBlank @Size(max = 32) String category,
                                      @NotBlank @Size(max = 80) String title, @Size(max = 1000) String note,
                                      @Size(max = 24) String colorKey, LocalDateTime expiresAt) {
}
