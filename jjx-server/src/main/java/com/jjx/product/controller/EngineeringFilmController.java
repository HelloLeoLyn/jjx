package com.jjx.product.controller;

import com.jjx.common.core.result.Result;
import com.jjx.product.domain.dto.EngineeringFilmDTO;
import com.jjx.product.domain.vo.EngineeringFilmVO;
import com.jjx.product.service.IEngineeringFilmService;
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
    public Result<EngineeringFilmVO> create(
            @Valid @RequestPart("dto") EngineeringFilmDTO dto,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        EngineeringFilmVO vo = filmService.createFilm(dto, file);
        return Result.success(vo);
    }

    @Operation(summary = "更新菲林")
    @PutMapping("/{filmId}")
    public Result<EngineeringFilmVO> update(
            @PathVariable @NotNull Long filmId,
            @Valid @RequestPart("dto") EngineeringFilmDTO dto,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        dto.setFilmId(filmId);
        EngineeringFilmVO vo = filmService.updateFilm(dto, file);
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
    public Result<EngineeringFilmVO> createNewVersion(
            @PathVariable @NotNull Long filmId,
            @RequestParam String newVersion,
            @RequestParam(required = false) String changeLog,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        EngineeringFilmVO vo = filmService.createNewVersion(filmId, newVersion, changeLog, file);
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