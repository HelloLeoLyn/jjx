package com.jjx.production.controller;

import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.production.domain.dto.QualityInspectionCreateDTO;
import com.jjx.production.domain.dto.QualityInspectionQueryDTO;
import com.jjx.production.domain.dto.QualityInspectionUpdateDTO;
import com.jjx.production.domain.vo.QualityInspectionVO;
import com.jjx.production.service.QualityInspectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "质量检验")
@RestController
@RequestMapping("/production/quality")
@RequiredArgsConstructor
public class QualityInspectionController {

    private final QualityInspectionService qualityService;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public Result<PageResult<QualityInspectionVO>> page(QualityInspectionQueryDTO query) {
        return Result.success(qualityService.page(query));
    }

    @Operation(summary = "查询详情")
    @GetMapping("/{id}")
    public Result<QualityInspectionVO> getById(@PathVariable Long id) {
        return Result.success(qualityService.getById(id));
    }

    @Operation(summary = "创建检验单")
    @PostMapping
    public Result<Long> create(@RequestBody QualityInspectionCreateDTO dto) {
        return Result.success(qualityService.create(dto));
    }

    @Operation(summary = "更新检验结果")
    @PutMapping
    public Result<Void> update(@RequestBody QualityInspectionUpdateDTO dto) {
        qualityService.update(dto);
        return Result.success();
    }

    @Operation(summary = "删除检验单")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        qualityService.delete(id);
        return Result.success();
    }
}
