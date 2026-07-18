package com.jjx.product.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.common.core.result.Result;
import com.jjx.product.domain.dto.ProductStandardProcessQueryDTO;
import com.jjx.product.domain.entity.ProductStandardProcess;
import com.jjx.product.domain.vo.ProductStandardProcessVO;
import com.jjx.product.service.IProductStandardProcessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 产品标准工序控制器
 */
@Tag(name = "产品标准工序管理", description = "产品标准工序相关接口")
@RestController
@RequestMapping("/product/standard-processes")
@RequiredArgsConstructor
@Validated
public class ProductStandardProcessController {

    private final IProductStandardProcessService processService;

    // ==================== 基础 CRUD ====================

    @Operation(summary = "分页查询标准工序")
    @GetMapping("/page")
    public Result<IPage<ProductStandardProcessVO>> pageQuery(ProductStandardProcessQueryDTO queryDTO) {
        return Result.success(processService.pageQuery(queryDTO));
    }

    @Operation(summary = "获取标准工序详情")
    @GetMapping("/{processId}")
    public Result<ProductStandardProcessVO> getById(
            @Parameter(description = "工序ID", required = true)
            @PathVariable @NotNull Long processId) {
        ProductStandardProcessVO vo = processService.getProcessById(processId);
        return Result.success(vo);
    }

    @Operation(summary = "创建标准工序")
    @PostMapping
    public Result<ProductStandardProcessVO> create(@Valid @RequestBody ProductStandardProcess process) {
        ProductStandardProcessVO vo = processService.createProcess(process);
        return Result.success(vo);
    }

    @Operation(summary = "更新标准工序")
    @PutMapping("/{processId}")
    public Result<ProductStandardProcessVO> update(
            @Parameter(description = "工序ID", required = true)
            @PathVariable @NotNull Long processId,
            @Valid @RequestBody ProductStandardProcess process) {
        process.setProcessId(processId);
        ProductStandardProcessVO vo = processService.updateProcess(process);
        return Result.success(vo);
    }

    @Operation(summary = "删除标准工序")
    @DeleteMapping("/{processId}")
    public Result<Void> delete(
            @Parameter(description = "工序ID", required = true)
            @PathVariable @NotNull Long processId) {
        processService.removeById(processId);
        return Result.success();
    }

    // ==================== 状态管理 ====================

    @Operation(summary = "启用工序")
    @PutMapping("/{processId}/enable")
    public Result<Void> enable(
            @Parameter(description = "工序ID", required = true)
            @PathVariable @NotNull Long processId) {
        processService.setEnabled(processId, true);
        return Result.success();
    }

    @Operation(summary = "禁用工序")
    @PutMapping("/{processId}/disable")
    public Result<Void> disable(
            @Parameter(description = "工序ID", required = true)
            @PathVariable @NotNull Long processId) {
        processService.setEnabled(processId, false);
        return Result.success();
    }

    // ==================== 查询接口 ====================

    @Operation(summary = "获取启用的工序列表")
    @GetMapping("/enabled")
    public Result<List<ProductStandardProcessVO>> getEnabledProcesses() {
        List<ProductStandardProcessVO> list = processService.getEnabledProcesses();
        return Result.success(list);
    }

    @Operation(summary = "根据工序类型获取工序列表")
    @GetMapping("/type/{processType}")
    public Result<List<ProductStandardProcessVO>> getByProcessType(
            @Parameter(description = "工序类型", required = true)
            @PathVariable @NotNull String processType) {
        List<ProductStandardProcessVO> list = processService.getByProcessType(processType);
        return Result.success(list);
    }

    @Operation(summary = "根据工序类别获取工序列表")
    @GetMapping("/category/{processCategory}")
    public Result<List<ProductStandardProcessVO>> getByProcessCategory(
            @Parameter(description = "工序类别", required = true)
            @PathVariable @NotNull String processCategory) {
        List<ProductStandardProcessVO> list = processService.getByProcessCategory(processCategory);
        return Result.success(list);
    }

    // ==================== 编码生成 ====================

    @Operation(summary = "生成下一个工序编码")
    @GetMapping("/generate-code")
    public Result<String> generateNextProcessCode(
            @Parameter(description = "工序类型（字典项Key）", required = true)
            @RequestParam @NotNull String processType,
            @Parameter(description = "工序类别（字典项Key）", required = true)
            @RequestParam @NotNull String processCategory) {
        String code = processService.generateNextProcessCode(processType, processCategory);
        return Result.success(code);
    }
}
