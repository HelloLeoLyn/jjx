package com.jjx.product.controller;

import com.jjx.common.core.result.Result;
import com.jjx.product.domain.dto.ProductFilmDTO;
import com.jjx.product.domain.vo.ProductFilmVO;
import com.jjx.product.service.IProductFilmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "产品菲林管理")
@RestController
@RequestMapping("/api/v1/product/films")
@RequiredArgsConstructor
@Validated
public class ProductFilmController {

    private final IProductFilmService filmService;

    @Operation(summary = "根据产品ID获取菲林列表")
    @GetMapping("/product/{productId}")
    public Result<List<ProductFilmVO>> getByProductId(
            @Parameter(description = "产品ID", required = true)
            @PathVariable @NotNull Long productId) {
        List<ProductFilmVO> list = filmService.getFilmsByProductId(productId);
        return Result.success(list);
    }

    @Operation(summary = "获取菲林详情")
    @GetMapping("/{filmId}")
    public Result<ProductFilmVO> getById(
            @Parameter(description = "菲林ID", required = true)
            @PathVariable @NotNull Long filmId) {
        ProductFilmVO vo = filmService.getFilmDetail(filmId);
        return Result.success(vo);
    }

    @Operation(summary = "创建菲林")
    @PostMapping
    public Result<ProductFilmVO> create(
            @Valid @RequestPart("dto") ProductFilmDTO dto,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        ProductFilmVO vo = filmService.createFilm(dto, file);
        return Result.success(vo);
    }

    @Operation(summary = "更新菲林")
    @PutMapping("/{filmId}")
    public Result<ProductFilmVO> update(
            @PathVariable @NotNull Long filmId,
            @Valid @RequestPart("dto") ProductFilmDTO dto,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        dto.setFilmId(filmId);
        ProductFilmVO vo = filmService.updateFilm(dto, file);
        return Result.success(vo);
    }

    @Operation(summary = "删除菲林")
    @DeleteMapping("/{filmId}")
    public Result<Void> delete(
            @Parameter(description = "菲林ID", required = true)
            @PathVariable @NotNull Long filmId) {
        filmService.deleteFilm(filmId);
        return Result.success();
    }

    @Operation(summary = "提交审批")
    @PostMapping("/{filmId}/submit")
    public Result<Void> submitApprove(
            @Parameter(description = "菲林ID", required = true)
            @PathVariable @NotNull Long filmId) {
        filmService.submitApprove(filmId);
        return Result.success();
    }

    @Operation(summary = "审批通过")
    @PutMapping("/{filmId}/approve")
    public Result<Void> approve(
            @Parameter(description = "菲林ID", required = true)
            @PathVariable @NotNull Long filmId,
            @RequestParam(required = false) String remark) {
        filmService.approve(filmId, remark);
        return Result.success();
    }

    @Operation(summary = "审批驳回")
    @PutMapping("/{filmId}/reject")
    public Result<Void> reject(
            @Parameter(description = "菲林ID", required = true)
            @PathVariable @NotNull Long filmId,
            @RequestParam String remark) {
        filmService.reject(filmId, remark);
        return Result.success();
    }

    @Operation(summary = "创建新版本")
    @PostMapping("/{filmId}/new-version")
    public Result<ProductFilmVO> createNewVersion(
            @PathVariable @NotNull Long filmId,
            @RequestParam String newVersion,
            @RequestParam(required = false) String changeLog,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        ProductFilmVO vo = filmService.createNewVersion(filmId, newVersion, changeLog, file);
        return Result.success(vo);
    }

    @Operation(summary = "设为当前版本")
    @PutMapping("/{filmId}/set-current")
    public Result<Void> setCurrentVersion(
            @Parameter(description = "菲林ID", required = true)
            @PathVariable @NotNull Long filmId) {
        filmService.setCurrentVersion(filmId);
        return Result.success();
    }

    @Operation(summary = "下发生产")
    @PutMapping("/{filmId}/release")
    public Result<Void> releaseToProduction(
            @Parameter(description = "菲林ID", required = true)
            @PathVariable @NotNull Long filmId) {
        filmService.releaseToProduction(filmId);
        return Result.success();
    }
}