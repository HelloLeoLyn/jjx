package com.jjx.production.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.result.Result;
import com.jjx.production.domain.dto.ProductionOperationExecutionCreateDTO;
import com.jjx.production.domain.dto.ProductionOperationExecutionQueryDTO;
import com.jjx.production.domain.dto.ProductionOperationExecutionUpdateDTO;
import com.jjx.production.domain.vo.ProductionOperationExecutionVO;
import com.jjx.production.service.ProductionOperationExecutionService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 生产工序执行控制器
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/production/operation-execution")
@Tag(name = "生产工序执行管理")
public class ProductionOperationExecutionController {

    private final ProductionOperationExecutionService productionOperationExecutionService;

    @Operation(summary = "创建工序执行记录")
    @PostMapping
    @Log(module = "工序执行管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("production:operation-execution:add")
    public Result<Long> createExecution(@Validated @RequestBody ProductionOperationExecutionCreateDTO createDTO) {
        Long executionId = productionOperationExecutionService.createExecution(createDTO);
        return Result.success(executionId);
    }

    @Operation(summary = "更新工序执行记录")
    @PutMapping
    @Log(module = "工序执行管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("production:operation-execution:edit")
    public Result<Boolean> updateExecution(@Validated @RequestBody ProductionOperationExecutionUpdateDTO updateDTO) {
        boolean success = productionOperationExecutionService.updateExecution(updateDTO);
        return Result.success(success);
    }

    @Operation(summary = "删除工序执行记录")
    @DeleteMapping("/{executionId}")
    @Log(module = "工序执行管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("production:operation-execution:delete")
    public Result<Boolean> deleteExecution(@PathVariable Long executionId) {
        boolean success = productionOperationExecutionService.deleteExecution(executionId);
        return Result.success(success);
    }

    @Operation(summary = "批量删除工序执行记录")
    @DeleteMapping("/batch")
    @Log(module = "工序执行管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("production:operation-execution:delete")
    public Result<Boolean> batchDeleteExecution(@RequestBody List<Long> executionIds) {
        boolean success = productionOperationExecutionService.batchDeleteExecution(executionIds);
        return Result.success(success);
    }

    @Operation(summary = "根据ID获取工序执行详情")
    @GetMapping("/{executionId}")
    @SaCheckPermission("production:operation-execution:view")
    public Result<ProductionOperationExecutionVO> getExecutionById(@PathVariable Long executionId) {
        ProductionOperationExecutionVO executionVO = productionOperationExecutionService.getExecutionById(executionId);
        return Result.success(executionVO);
    }

    @Operation(summary = "查询工序执行列表")
    @GetMapping("/list")
    @SaCheckPermission("production:operation-execution:view")
    public Result<List<ProductionOperationExecutionVO>> queryExecutionList(ProductionOperationExecutionQueryDTO queryDTO) {
        List<ProductionOperationExecutionVO> executionList = productionOperationExecutionService.queryExecutionList(queryDTO);
        return Result.success(executionList);
    }

    @Operation(summary = "分页查询工序执行")
    @GetMapping("/page")
    @SaCheckPermission("production:operation-execution:view")
    public Result<Page<ProductionOperationExecutionVO>> queryExecutionPage(ProductionOperationExecutionQueryDTO queryDTO) {
        Page<ProductionOperationExecutionVO> executionPage = productionOperationExecutionService.queryExecutionPage(queryDTO);
        return Result.success(executionPage);
    }

    @Operation(summary = "开始工序执行")
    @PutMapping("/{executionId}/start")
    @Log(module = "工序执行管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("production:operation-execution:edit")
    public Result<Boolean> startExecution(@PathVariable Long executionId) {
        boolean success = productionOperationExecutionService.startExecution(executionId);
        return Result.success(success);
    }

    @Operation(summary = "暂停工序执行")
    @PutMapping("/{executionId}/pause")
    @Log(module = "工序执行管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("production:operation-execution:edit")
    public Result<Boolean> pauseExecution(@PathVariable Long executionId) {
        boolean success = productionOperationExecutionService.pauseExecution(executionId);
        return Result.success(success);
    }

    @Operation(summary = "工序首检/巡检（DEV-371）")
    @PutMapping("/{executionId}/quality-check")
    @Log(module = "工序执行管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("production:operation-execution:edit")
    public Result<Boolean> qualityCheck(@PathVariable Long executionId,
                                        @RequestParam String checkType,
                                        @RequestParam String checkResult,
                                        @RequestParam(required = false) String checkItems,
                                        @RequestParam(required = false) String remark) {
        boolean success = productionOperationExecutionService.qualityCheck(
                executionId, checkType, checkResult, checkItems, remark);
        return Result.success(success);
    }

    @Operation(summary = "完成工序执行")
    @PutMapping("/{executionId}/complete")
    @Log(module = "工序执行管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("production:operation-execution:edit")
    public Result<Boolean> completeExecution(@PathVariable Long executionId) {
        boolean success = productionOperationExecutionService.completeExecution(executionId);
        return Result.success(success);
    }

    @Operation(summary = "取消工序执行")
    @PutMapping("/{executionId}/cancel")
    @Log(module = "工序执行管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("production:operation-execution:edit")
    public Result<Boolean> cancelExecution(@PathVariable Long executionId) {
        boolean success = productionOperationExecutionService.cancelExecution(executionId);
        return Result.success(success);
    }

    @Operation(summary = "根据生产工单ID查询工序执行")
    @GetMapping("/order/{orderId}")
    @SaCheckPermission("production:operation-execution:view")
    public Result<List<ProductionOperationExecutionVO>> getExecutionsByOrderId(@PathVariable Long orderId) {
        List<ProductionOperationExecutionVO> executionList = productionOperationExecutionService.getExecutionsByOrderId(orderId);
        return Result.success(executionList);
    }

    @Operation(summary = "根据工序ID查询工序执行")
    @GetMapping("/process/{processId}")
    @SaCheckPermission("production:operation-execution:view")
    public Result<List<ProductionOperationExecutionVO>> getExecutionsByProcessId(@PathVariable Long processId) {
        List<ProductionOperationExecutionVO> executionList = productionOperationExecutionService.getExecutionsByProcessId(processId);
        return Result.success(executionList);
    }

    @Operation(summary = "导入工序执行数据")
    @PostMapping("/import")
    @Log(module = "工序执行管理", businessType = BusinessType.IMPORT)
    @SaCheckPermission("production:operation-execution:import")
    public Result<String> importExecutionData(@RequestBody List<ProductionOperationExecutionCreateDTO> importData) {
        productionOperationExecutionService.importExecutionData(importData);
        return Result.success("导入成功");
    }

    @Operation(summary = "导出工序执行数据")
    @PostMapping("/export")
    @SaCheckPermission("production:operation-execution:export")
    public Result<List<ProductionOperationExecutionVO>> exportExecutionData(@RequestBody ProductionOperationExecutionQueryDTO queryDTO) {
        List<ProductionOperationExecutionVO> exportData = productionOperationExecutionService.exportExecutionData(queryDTO);
        return Result.success(exportData);
    }

    @Operation(summary = "获取工序执行统计信息")
    @GetMapping("/statistics")
    @SaCheckPermission("production:operation-execution:view")
    public Result<Object> getExecutionStatistics(ProductionOperationExecutionQueryDTO queryDTO) {
        return Result.success(productionOperationExecutionService.getExecutionStatistics(queryDTO));
    }
}
