package top.continew.admin.controller.eve;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.controller.eve.model.EveStarMapAnnotationReq;
import top.continew.admin.controller.eve.model.EveStarMapRouteReq;
import top.continew.admin.eve.model.EveStarMapCoverageResp;
import top.continew.admin.eve.model.EveStarMapGraphResp;
import top.continew.admin.eve.model.EveStarMapRouteResp;
import top.continew.admin.eve.model.EveStarMapSuggestionResp;
import top.continew.admin.eve.model.EveStarMapSystemResp;
import top.continew.admin.eve.service.EveStarMapService;

import java.util.List;

/**
 * EVE 公开星图、军团运营标注和路线接口。
 *
 * @author zhaoyuqing
 */
@Tag(name = "EVE 星图与导航")
@RestController
@RequiredArgsConstructor
@RequestMapping("/eve/starmap")
public class EveStarMapController {

    private final EveStarMapService starMapService;

    /** 查询公开底图的同步覆盖率。 */
    @GetMapping("/coverage")
    @Operation(summary = "查询星图同步覆盖率")
    @SaCheckPermission("eve:starmap:view")
    public EveStarMapCoverageResp coverage() {
        return starMapService.coverage();
    }

    /** 查询受节点上限保护的当前地图视图。 */
    @GetMapping("/graph")
    @Operation(summary = "查询二维星图")
    @SaCheckPermission("eve:starmap:view")
    public EveStarMapGraphResp graph(@RequestParam(required = false) Long regionId) {
        return starMapService.graph(regionId);
    }

    /** 根据中文或英文名称联想星系。 */
    @GetMapping("/systems/suggest")
    @Operation(summary = "搜索星系")
    @SaCheckPermission("eve:starmap:view")
    public List<EveStarMapSuggestionResp> suggest(@RequestParam @jakarta.validation.constraints.Size(max = 80) String keyword) {
        return starMapService.suggestSystems(keyword);
    }

    /** 查询星系邻接关系和当前用户有权查看的军团运营信息。 */
    @GetMapping("/systems/{systemId}")
    @Operation(summary = "查询星系详情")
    @SaCheckPermission("eve:starmap:view")
    public EveStarMapSystemResp system(@PathVariable @Min(1) Long systemId) {
        return starMapService.system(systemId);
    }

    /** 创建当前军团的运营标注。 */
    @PostMapping("/annotations")
    @Operation(summary = "创建星图运营标注")
    @SaCheckPermission("eve:starmap:annotation:manage")
    public EveStarMapGraphResp.Annotation createAnnotation(@Valid @RequestBody EveStarMapAnnotationReq req) {
        return starMapService.createAnnotation(req.systemId(), req.category(), req.title(), req.note(), req.colorKey(), req.expiresAt());
    }

    /** 更新当前军团的运营标注。 */
    @PutMapping("/annotations/{annotationId}")
    @Operation(summary = "更新星图运营标注")
    @SaCheckPermission("eve:starmap:annotation:manage")
    public EveStarMapGraphResp.Annotation updateAnnotation(@PathVariable @Min(1) Long annotationId,
                                                           @Valid @RequestBody EveStarMapAnnotationReq req) {
        return starMapService.updateAnnotation(annotationId, req.systemId(), req.category(), req.title(), req.note(), req.colorKey(), req.expiresAt());
    }

    /** 归档当前军团的运营标注。 */
    @DeleteMapping("/annotations/{annotationId}")
    @Operation(summary = "归档星图运营标注")
    @SaCheckPermission("eve:starmap:annotation:manage")
    public void archiveAnnotation(@PathVariable @Min(1) Long annotationId) {
        starMapService.archiveAnnotation(annotationId);
    }

    /** 预览当前国服拓扑下的一条路线，不写入军团数据。 */
    @PostMapping("/routes/preview")
    @Operation(summary = "预览国服路线")
    @SaCheckPermission("eve:starmap:view")
    public EveStarMapRouteResp previewRoute(@Valid @RequestBody EveStarMapRouteReq req) {
        return starMapService.previewRoute(req.originSystemId(), req.destinationSystemId(), req.viaSystemIds());
    }

    /** 保存经过国服路线接口验证的当前军团航线。 */
    @PostMapping("/routes")
    @Operation(summary = "保存军团航线")
    @SaCheckPermission("eve:starmap:route:manage")
    public EveStarMapRouteResp saveRoute(@Valid @RequestBody EveStarMapRouteReq req) {
        return starMapService.saveRoute(req.title(), req.description(), req.originSystemId(), req.destinationSystemId(), req.viaSystemIds());
    }

    /** 查询当前军团保存的路线库。 */
    @GetMapping("/routes")
    @Operation(summary = "查询军团路线库")
    @SaCheckPermission("eve:starmap:view")
    public List<EveStarMapRouteResp> routes() {
        return starMapService.routes();
    }

}
