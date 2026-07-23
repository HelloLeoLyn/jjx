package com.jjx.sales.controller;

import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.sales.domain.dto.SalesLogQueryDTO;
import com.jjx.sales.domain.vo.SalesLogVO;
import com.jjx.sales.service.SalesLogService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 销售订单操作日志控制器
 */
@Tag(name = "销售订单操作日志", description = "销售订单操作日志相关接口")
@RestController
@RequestMapping("/sales/logs")
@RequiredArgsConstructor
@Validated
public class SalesLogController {

    private final SalesLogService salesLogService;

    /**
     * 分页查询操作日志
     */
    @Operation(summary = "分页查询操作日志")
    @SaCheckPermission("sales:order:view")
    @GetMapping
    public Result<PageResult<SalesLogVO>> pageQuery(@Valid SalesLogQueryDTO queryDTO) {
        PageResult<SalesLogVO> page = salesLogService.pageQuery(queryDTO);
        return Result.success(page);
    }

    /**
     * 根据日志ID查询
     */
    @Operation(summary = "根据日志ID查询")
    @SaCheckPermission("sales:order:view")
    @GetMapping("/{logId}")
    public Result<SalesLogVO> getById(
            @Parameter(description = "日志ID", required = true)
            @PathVariable @NotNull Long logId) {
        SalesLogVO logVO = salesLogService.getById(logId);
        return Result.success(logVO);
    }

    /**
     * 根据订单ID查询日志列表
     */
    @Operation(summary = "根据订单ID查询日志列表")
    @SaCheckPermission("sales:order:view")
    @GetMapping("/order/{orderId}")
    public Result<List<SalesLogVO>> getByOrderId(
            @Parameter(description = "订单ID", required = true)
            @PathVariable @NotNull Long orderId) {
        List<SalesLogVO> logs = salesLogService.getByOrderId(orderId);
        return Result.success(logs);
    }

    /**
     * 根据订单号查询日志列表
     */
    @Operation(summary = "根据订单号查询日志列表")
    @SaCheckPermission("sales:order:view")
    @GetMapping("/orderNo/{orderNo}")
    public Result<List<SalesLogVO>> getByOrderNo(
            @Parameter(description = "订单号", required = true)
            @PathVariable @NotNull String orderNo) {
        List<SalesLogVO> logs = salesLogService.getByOrderNo(orderNo);
        return Result.success(logs);
    }

    /**
     * 查询订单的最新操作日志
     */
    @Operation(summary = "查询订单的最新操作日志")
    @SaCheckPermission("sales:order:view")
    @GetMapping("/order/{orderId}/latest")
    public Result<SalesLogVO> getLatestByOrderId(
            @Parameter(description = "订单ID", required = true)
            @PathVariable @NotNull Long orderId) {
        SalesLogVO logVO = salesLogService.getLatestByOrderId(orderId);
        return Result.success(logVO);
    }

    /**
     * 根据操作类型查询日志
     */
    @Operation(summary = "根据操作类型查询日志")
    @SaCheckPermission("sales:order:view")
    @GetMapping("/type/{operationType}")
    public Result<PageResult<SalesLogVO>> getByOperationType(
            @Parameter(description = "操作类型", required = true)
            @PathVariable @NotNull String operationType,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        SalesLogQueryDTO queryDTO = new SalesLogQueryDTO();
        queryDTO.setOperationType(operationType);
        queryDTO.setPageNum(pageNum);
        queryDTO.setPageSize(pageSize);
        PageResult<SalesLogVO> page = salesLogService.pageQuery(queryDTO);
        return Result.success(page);
    }

    /**
     * 根据操作人查询日志
     */
    @Operation(summary = "根据操作人查询日志")
    @SaCheckPermission("sales:order:view")
    @GetMapping("/operator/{operatorId}")
    public Result<PageResult<SalesLogVO>> getByOperator(
            @Parameter(description = "操作人ID", required = true)
            @PathVariable @NotNull Long operatorId,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        SalesLogQueryDTO queryDTO = new SalesLogQueryDTO();
        queryDTO.setOperatorId(operatorId);
        queryDTO.setPageNum(pageNum);
        queryDTO.setPageSize(pageSize);
        PageResult<SalesLogVO> page = salesLogService.pageQuery(queryDTO);
        return Result.success(page);
    }

    /**
     * 导出操作日志
     */
    @Operation(summary = "导出操作日志")
    @SaCheckPermission("sales:order:view")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportLogs(@Valid SalesLogQueryDTO queryDTO) {
        // 设置默认时间范围（最近30天）
        if (queryDTO.getStartTime() == null) {
            queryDTO.setStartTime(LocalDateTime.now().minusDays(30));
        }
        if (queryDTO.getEndTime() == null) {
            queryDTO.setEndTime(LocalDateTime.now());
        }

        byte[] exportData = salesLogService.exportLogs(queryDTO);

        String fileName = "operation_logs_" +
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(exportData);
    }

    /**
     * 删除指定订单的所有日志
     */
    @Operation(summary = "删除指定订单的所有日志")
    @Log(module = "操作日志管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("sales:order:edit")
    @DeleteMapping("/order/{orderId}")
    public Result<Void> deleteLogsByOrderId(
            @Parameter(description = "订单ID", required = true)
            @PathVariable @NotNull Long orderId) {
        salesLogService.deleteByOrderId(orderId);
        return Result.success();
    }

    /**
     * 删除指定日志
     */
    @Operation(summary = "删除指定日志")
    @Log(module = "操作日志管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("sales:order:edit")
    @DeleteMapping("/{logId}")
    public Result<Void> deleteLog(
            @Parameter(description = "日志ID", required = true)
            @PathVariable @NotNull Long logId) {
        salesLogService.deleteById(logId);
        return Result.success();
    }

    /**
     * 批量删除日志
     */
    @Operation(summary = "批量删除日志")
    @Log(module = "操作日志管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("sales:order:edit")
    @DeleteMapping("/batch")
    public Result<Void> batchDeleteLogs(
            @RequestBody @NotEmpty(message = "日志ID列表不能为空") List<Long> logIds) {
        salesLogService.batchDeleteByIds(logIds);
        return Result.success();
    }

    /**
     * 获取操作类型统计
     */
    @Operation(summary = "获取操作类型统计")
    @SaCheckPermission("sales:order:view")
    @GetMapping("/stats/operation-type")
    public Result<List<Map<String, Object>>> getOperationTypeStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        List<Map<String, Object>> stats = salesLogService.getOperationTypeStats(startTime, endTime);
        return Result.success(stats);
    }

    /**
     * 获取操作人统计
     */
    @Operation(summary = "获取操作人统计")
    @SaCheckPermission("sales:order:view")
    @GetMapping("/stats/operator")
    public Result<List<Map<String, Object>>> getOperatorStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        List<Map<String, Object>> stats = salesLogService.getOperatorStats(startTime, endTime);
        return Result.success(stats);
    }

}
