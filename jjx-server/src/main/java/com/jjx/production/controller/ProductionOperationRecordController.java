package com.jjx.production.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.result.Result;
import com.jjx.production.domain.dto.ProductionOperationRecordCreateDTO;
import com.jjx.production.domain.dto.ProductionOperationRecordQueryDTO;
import com.jjx.production.domain.dto.ProductionOperationRecordUpdateDTO;
import com.jjx.production.domain.vo.ProductionOperationRecordVO;
import com.jjx.production.service.ProductionOperationRecordService;
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
 * 生产工序记录控制器
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/production/operation-record")
@Tag(name = "生产工序记录管理")
public class ProductionOperationRecordController {

    private final ProductionOperationRecordService productionOperationRecordService;

    @Operation(summary = "创建工序记录")
    @PostMapping
    @Log(module = "工序记录管理", businessType = BusinessType.INSERT, bizType = "'production_record'", bizId = "#result.data")
    @SaCheckPermission("production:operation-record:add")
    public Result<Long> createRecord(@Validated @RequestBody ProductionOperationRecordCreateDTO createDTO) {
        Long recordId = productionOperationRecordService.createRecord(createDTO);
        return Result.success(recordId);
    }

    @Operation(summary = "更新工序记录")
    @PutMapping
    @Log(module = "工序记录管理", businessType = BusinessType.UPDATE, bizType = "'production_record'", bizId = "#updateDTO.recordId")
    @SaCheckPermission("production:operation-record:edit")
    public Result<Boolean> updateRecord(@Validated @RequestBody ProductionOperationRecordUpdateDTO updateDTO) {
        boolean success = productionOperationRecordService.updateRecord(updateDTO);
        return Result.success(success);
    }

    @Operation(summary = "删除工序记录")
    @DeleteMapping("/{recordId}")
    @Log(module = "工序记录管理", businessType = BusinessType.DELETE, bizType = "'production_record'", bizId = "#recordId")
    @SaCheckPermission("production:operation-record:delete")
    public Result<Boolean> deleteRecord(@PathVariable Long recordId) {
        boolean success = productionOperationRecordService.deleteRecord(recordId);
        return Result.success(success);
    }

    @Operation(summary = "批量删除工序记录")
    @DeleteMapping("/batch")
    @Log(module = "工序记录管理", businessType = BusinessType.DELETE, bizType = "'production_record'", bizId = "#recordIds[0]")
    @SaCheckPermission("production:operation-record:delete")
    public Result<Boolean> batchDeleteRecord(@RequestBody List<Long> recordIds) {
        boolean success = productionOperationRecordService.batchDeleteRecord(recordIds);
        return Result.success(success);
    }

    @Operation(summary = "根据ID获取工序记录详情")
    @GetMapping("/{recordId}")
    @SaCheckPermission("production:operation-record:view")
    public Result<ProductionOperationRecordVO> getRecordById(@PathVariable Long recordId) {
        ProductionOperationRecordVO recordVO = productionOperationRecordService.getRecordById(recordId);
        return Result.success(recordVO);
    }

    @Operation(summary = "查询工序记录列表")
    @GetMapping("/list")
    @SaCheckPermission("production:operation-record:view")
    public Result<List<ProductionOperationRecordVO>> queryRecordList(ProductionOperationRecordQueryDTO queryDTO) {
        List<ProductionOperationRecordVO> recordList = productionOperationRecordService.queryRecordList(queryDTO);
        return Result.success(recordList);
    }

    @Operation(summary = "分页查询工序记录")
    @GetMapping("/page")
    @SaCheckPermission("production:operation-record:view")
    public Result<Page<ProductionOperationRecordVO>> queryRecordPage(ProductionOperationRecordQueryDTO queryDTO) {
        Page<ProductionOperationRecordVO> recordPage = productionOperationRecordService.queryRecordPage(queryDTO);
        return Result.success(recordPage);
    }

    @Operation(summary = "根据工序执行ID查询工序记录")
    @GetMapping("/execution/{executionId}")
    @SaCheckPermission("production:operation-record:view")
    public Result<List<ProductionOperationRecordVO>> getRecordsByExecutionId(@PathVariable Long executionId) {
        List<ProductionOperationRecordVO> recordList = productionOperationRecordService.getRecordsByExecutionId(executionId);
        return Result.success(recordList);
    }

    @Operation(summary = "根据生产工单ID查询工序记录")
    @GetMapping("/order/{orderId}")
    @SaCheckPermission("production:operation-record:view")
    public Result<List<ProductionOperationRecordVO>> getRecordsByOrderId(@PathVariable Long orderId) {
        List<ProductionOperationRecordVO> recordList = productionOperationRecordService.getRecordsByOrderId(orderId);
        return Result.success(recordList);
    }

    @Operation(summary = "根据工序ID查询工序记录")
    @GetMapping("/process/{processId}")
    @SaCheckPermission("production:operation-record:view")
    public Result<List<ProductionOperationRecordVO>> getRecordsByProcessId(@PathVariable Long processId) {
        List<ProductionOperationRecordVO> recordList = productionOperationRecordService.getRecordsByProcessId(processId);
        return Result.success(recordList);
    }

    @Operation(summary = "导入工序记录数据")
    @PostMapping("/import")
    @Log(module = "工序记录管理", businessType = BusinessType.IMPORT, bizType = "'production_record'", bizId = "#importData[0].executionId")
    @SaCheckPermission("production:operation-record:import")
    public Result<String> importRecordData(@RequestBody List<ProductionOperationRecordCreateDTO> importData) {
        productionOperationRecordService.importRecordData(importData);
        return Result.success("导入成功");
    }

    @Operation(summary = "导出工序记录数据")
    @PostMapping("/export")
    @SaCheckPermission("production:operation-record:export")
    public Result<List<ProductionOperationRecordVO>> exportRecordData(@RequestBody ProductionOperationRecordQueryDTO queryDTO) {
        List<ProductionOperationRecordVO> exportData = productionOperationRecordService.exportRecordData(queryDTO);
        return Result.success(exportData);
    }

    @Operation(summary = "获取工序记录统计信息")
    @GetMapping("/statistics")
    @SaCheckPermission("production:operation-record:view")
    public Result<Object> getRecordStatistics(ProductionOperationRecordQueryDTO queryDTO) {
        return Result.success(productionOperationRecordService.getRecordStatistics(queryDTO));
    }
}
