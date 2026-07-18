package com.jjx.production.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.result.Result;
import com.jjx.production.domain.dto.ProductionOrderCreateDTO;
import com.jjx.production.domain.dto.ProductionOrderQueryDTO;
import com.jjx.production.domain.dto.ProductionOrderUpdateDTO;
import com.jjx.production.domain.vo.OrderStatisticsVO;
import com.jjx.production.domain.vo.ProductionOrderVO;
import com.jjx.production.service.ProductionOrderService;
import cn.dev33.satoken.annotation.SaCheckPermission;
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
 * 生产工单控制器
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/production/order")
@Tag(name = "生产工单管理")
public class ProductionOrderController {

    private final ProductionOrderService productionOrderService;

    @Operation(summary = "创建生产工单")
    @PostMapping
    @Log(module = "生产工单管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("production:order:add")
    public Result<Long> createOrder(@Validated @RequestBody ProductionOrderCreateDTO createDTO) {
        Long orderId = productionOrderService.createOrder(createDTO);
        return Result.success(orderId);
    }

    @Operation(summary = "更新生产工单")
    @PutMapping
    @Log(module = "生产工单管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("production:order:edit")
    public Result<Boolean> updateOrder(@Validated @RequestBody ProductionOrderUpdateDTO updateDTO) {
        boolean success = productionOrderService.updateOrder(updateDTO);
        return Result.success(success);
    }

    @Operation(summary = "删除生产工单")
    @DeleteMapping("/{orderId}")
    @Log(module = "生产工单管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("production:order:delete")
    public Result<Boolean> deleteOrder(@PathVariable Long orderId) {
        boolean success = productionOrderService.deleteOrder(orderId);
        return Result.success(success);
    }

    @Operation(summary = "批量删除生产工单")
    @DeleteMapping("/batch")
    @Log(module = "生产工单管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("production:order:delete")
    public Result<Boolean> batchDeleteOrder(@RequestBody List<Long> orderIds) {
        boolean success = productionOrderService.batchDeleteOrder(orderIds);
        return Result.success(success);
    }

    @Operation(summary = "根据ID获取生产工单详情")
    @GetMapping("/{orderId}")
    public Result<ProductionOrderVO> getOrderById(@PathVariable Long orderId) {
        ProductionOrderVO orderVO = productionOrderService.getOrderById(orderId);
        return Result.success(orderVO);
    }

    @Operation(summary = "根据编码获取生产工单详情")
    @GetMapping("/code/{orderCode}")
    public Result<ProductionOrderVO> getOrderByCode(@PathVariable String orderCode) {
        ProductionOrderVO orderVO = productionOrderService.getOrderByCode(orderCode);
        return Result.success(orderVO);
    }

    @Operation(summary = "查询生产工单列表")
    @GetMapping("/list")
    public Result<List<ProductionOrderVO>> queryOrderList(ProductionOrderQueryDTO queryDTO) {
        List<ProductionOrderVO> orderList = productionOrderService.queryOrderList(queryDTO);
        return Result.success(orderList);
    }

    @Operation(summary = "分页查询生产工单")
    @GetMapping("/page")
    public Result<Page<ProductionOrderVO>> queryOrderPage(ProductionOrderQueryDTO queryDTO) {
        Page<ProductionOrderVO> orderPage = productionOrderService.queryOrderPage(queryDTO);
        return Result.success(orderPage);
    }

    @Operation(summary = "启动生产工单")
    @PutMapping("/{orderId}/start")
    @Log(module = "生产工单管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("production:order:edit")
    public Result<Boolean> startOrder(@PathVariable Long orderId) {
        boolean success = productionOrderService.startOrder(orderId);
        return Result.success(success);
    }

    @Operation(summary = "暂停生产工单")
    @PutMapping("/{orderId}/pause")
    @Log(module = "生产工单管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("production:order:edit")
    public Result<Boolean> pauseOrder(@PathVariable Long orderId) {
        boolean success = productionOrderService.pauseOrder(orderId);
        return Result.success(success);
    }

    @Operation(summary = "完成生产工单")
    @PutMapping("/{orderId}/complete")
    @Log(module = "生产工单管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("production:order:edit")
    public Result<Boolean> completeOrder(@PathVariable Long orderId) {
        boolean success = productionOrderService.completeOrder(orderId);
        return Result.success(success);
    }

    @Operation(summary = "取消生产工单")
    @PutMapping("/{orderId}/cancel")
    @Log(module = "生产工单管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("production:order:edit")
    public Result<Boolean> cancelOrder(@PathVariable Long orderId) {
        boolean success = productionOrderService.cancelOrder(orderId);
        return Result.success(success);
    }

    @Operation(summary = "关闭生产工单")
    @PutMapping("/{orderId}/close")
    @Log(module = "生产工单管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("production:order:edit")
    public Result<Boolean> closeOrder(@PathVariable Long orderId) {
        boolean success = productionOrderService.closeOrder(orderId);
        return Result.success(success);
    }

    @Operation(summary = "检查工单编码是否存在")
    @GetMapping("/check-code/{orderCode}")
    public Result<Boolean> checkOrderCodeExists(@PathVariable String orderCode) {
        boolean exists = productionOrderService.checkOrderCodeExists(orderCode);
        return Result.success(exists);
    }

    @Operation(summary = "根据产品ID查询生产工单")
    @GetMapping("/product/{productId}")
    public Result<List<ProductionOrderVO>> getOrdersByProductId(@PathVariable Long productId) {
        List<ProductionOrderVO> orderList = productionOrderService.getOrdersByProductId(productId);
        return Result.success(orderList);
    }

    @Operation(summary = "根据工艺路线ID查询生产工单")
    @GetMapping("/routing/{routingId}")
    public Result<List<ProductionOrderVO>> getOrdersByRoutingId(@PathVariable Long routingId) {
        List<ProductionOrderVO> orderList = productionOrderService.getOrdersByRoutingId(routingId);
        return Result.success(orderList);
    }

    @Operation(summary = "复制生产工单")
    @PostMapping("/copy")
    @Log(module = "生产工单管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("production:order:add")
    public Result<Long> copyOrder(@RequestParam Long sourceOrderId,
                                  @RequestParam String targetOrderCode,
                                  @RequestParam String targetOrderName) {
        Long newOrderId = productionOrderService.copyOrder(sourceOrderId, targetOrderCode, targetOrderName);
        return Result.success(newOrderId);
    }

    @Operation(summary = "导入生产工单数据")
    @PostMapping("/import")
    @Log(module = "生产工单管理", businessType = BusinessType.IMPORT)
    @SaCheckPermission("production:order:add")
    public Result importOrderData(@RequestBody List<ProductionOrderCreateDTO> importData) {
        return productionOrderService.importOrderData(importData);
    }

    @Operation(summary = "导出生产工单数据")
    @PostMapping("/export")
    @Log(module = "生产工单管理", businessType = BusinessType.EXPORT)
    @SaCheckPermission("production:order:export")
    public Result<List<ProductionOrderVO>> exportOrderData(@RequestBody ProductionOrderQueryDTO queryDTO) {
        List<ProductionOrderVO> exportData = productionOrderService.exportOrderData(queryDTO);
        return Result.success(exportData);
    }

    @Operation(summary = "获取生产工单统计信息")
    @GetMapping("/statistics")
    public Result<OrderStatisticsVO> getOrderStatistics(ProductionOrderQueryDTO queryDTO) {
        return Result.success(productionOrderService.getOrderStatistics(queryDTO));
    }
}
