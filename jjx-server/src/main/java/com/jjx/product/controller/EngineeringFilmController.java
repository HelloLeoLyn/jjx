package com.jjx.product.controller;

import com.jjx.common.core.result.Result;
import com.jjx.product.domain.vo.EngineeringFilmVO;
import com.jjx.product.service.IEngineeringFilmService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "产品菲林管理")
@RestController
@RequestMapping("/engineering/films")
@RequiredArgsConstructor
@Validated
public class EngineeringFilmController {

    private final IEngineeringFilmService filmService;

    @Operation(summary = "根据产品ID获取菲林列表")
    @SaCheckPermission("engineering:film:view")
    @GetMapping("/product/{productId}")
    public Result<List<EngineeringFilmVO>> getByProductId(
            @Parameter(description = "产品ID", required = true)
            @PathVariable @NotNull Long productId) {
        List<EngineeringFilmVO> list = filmService.getFilmsByProductId(productId);
        return Result.success(list);
    }

    @Operation(summary = "获取菲林详情")
    @SaCheckPermission("engineering:film:view")
    @GetMapping("/{filmId}")
    public Result<EngineeringFilmVO> getById(
            @Parameter(description = "菲林ID", required = true)
            @PathVariable @NotNull Long filmId) {
        EngineeringFilmVO vo = filmService.getFilmDetail(filmId);
        return Result.success(vo);
    }

    @Operation(summary = "全部菲林列表（总览页：按产品/类型/审批状态/关键字过滤）")
    @GetMapping("/page")
    @SaCheckPermission("engineering:film:view")
    public Result<List<EngineeringFilmVO>> page(
            @Parameter(description = "产品ID") @RequestParam(required = false) Long productId,
            @Parameter(description = "菲林类型") @RequestParam(required = false) String filmType,
            @Parameter(description = "审批状态：1草稿 2待审批 3已批准 4已驳回") @RequestParam(required = false) Integer approveStatus,
            @Parameter(description = "关键字：菲林编码/名称/产品编码/产品名称") @RequestParam(required = false) String keyword) {
        return Result.success(filmService.listFilms(productId, filmType, approveStatus, keyword));
    }

}
