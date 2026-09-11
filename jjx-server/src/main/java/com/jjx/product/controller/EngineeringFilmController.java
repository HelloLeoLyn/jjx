package com.jjx.product.controller;

import com.jjx.common.core.result.Result;
import com.jjx.product.domain.dto.EngineeringFilmDTO;
import com.jjx.product.domain.vo.EngineeringFilmVO;
import com.jjx.product.service.IEngineeringFilmService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    @GetMapping("/product/{productId}")
    public Result<List<EngineeringFilmVO>> getByProductId(
            @Parameter(description = "产品ID", required = true)
            @PathVariable @NotNull Long productId) {
        List<EngineeringFilmVO> list = filmService.getFilmsByProductId(productId);
        return Result.success(list);
    }

    @Operation(summary = "获取菲林详情")
    @GetMapping("/{filmId}")
    public Result<EngineeringFilmVO> getById(
            @Parameter(description = "菲林ID", required = true)
            @PathVariable @NotNull Long filmId) {
        EngineeringFilmVO vo = filmService.getFilmDetail(filmId);
        return Result.success(vo);
    }

    @Operation(summary = "创建菲林")
    @PostMapping
    @SaCheckPermission("engineering:film:edit")
    public Result<EngineeringFilmVO> create(
            @Valid @RequestBody EngineeringFilmDTO dto) {
        EngineeringFilmVO vo = filmService.createFilm(dto);
        return Result.success(vo);
    }

    @Operation(summary = "更新菲林")
    @PutMapping("/{filmId}")
    @SaCheckPermission("engineering:film:edit")
    public Result<EngineeringFilmVO> update(
            @PathVariable @NotNull Long filmId,
            @Valid @RequestBody EngineeringFilmDTO dto) {
        dto.setFilmId(filmId);
        EngineeringFilmVO vo = filmService.updateFilm(dto);
        return Result.success(vo);
    }

    @Operation(summary = "删除菲林")
    @DeleteMapping("/{filmId}")
    @SaCheckPermission("engineering:film:delete")
    public Result<Void> delete(
            @Parameter(description = "菲林ID", required = true)
            @PathVariable @NotNull Long filmId) {
        filmService.deleteFilm(filmId);
        return Result.success();
    }

    @Operation(summary = "提交审批")
    @PostMapping("/{filmId}/submit")
    @SaCheckPermission("engineering:film:submit")
    public Result<Void> submitApprove(
            @Parameter(description = "菲林ID", required = true)
            @PathVariable @NotNull Long filmId) {
        filmService.submitApprove(filmId);
        return Result.success();
    }

    @Operation(summary = "审批通过")
    @PutMapping("/{filmId}/approve")
    @SaCheckPermission("engineering:film:approve")
    public Result<Void> approve(
            @Parameter(description = "菲林ID", required = true)
            @PathVariable @NotNull Long filmId,
            @RequestParam(required = false) String remark) {
        filmService.approve(filmId, remark);
        return Result.success();
    }

    @Operation(summary = "审批驳回")
    @PutMapping("/{filmId}/reject")
    @SaCheckPermission("engineering:film:reject")
    public Result<Void> reject(
            @Parameter(description = "菲林ID", required = true)
            @PathVariable @NotNull Long filmId,
            @RequestParam String remark) {
        filmService.reject(filmId, remark);
        return Result.success();
    }

    @Operation(summary = "创建新版本")
    @PostMapping("/{filmId}/new-version")
    @SaCheckPermission("engineering:film:edit")
    public Result<EngineeringFilmVO> createNewVersion(
            @PathVariable @NotNull Long filmId,
            @RequestParam(required = false) String newVersion,
            @RequestParam(required = false) String changeLog) {
        EngineeringFilmVO vo = filmService.createNewVersion(filmId, newVersion, changeLog);
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

    @Operation(summary = "设为当前版本")
    @PutMapping("/{filmId}/set-current")
    @SaCheckPermission("engineering:film:edit")
    public Result<Void> setCurrentVersion(
            @Parameter(description = "菲林ID", required = true)
            @PathVariable @NotNull Long filmId) {
        filmService.setCurrentVersion(filmId);
        return Result.success();
    }

    @Operation(summary = "下发生产")
    @PutMapping("/{filmId}/release")
    @SaCheckPermission("engineering:film:release")
    public Result<Void> releaseToProduction(
            @Parameter(description = "菲林ID", required = true)
            @PathVariable @NotNull Long filmId) {
        filmService.releaseToProduction(filmId);
        return Result.success();
    }
}