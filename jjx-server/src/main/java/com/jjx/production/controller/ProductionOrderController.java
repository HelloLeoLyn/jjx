package com.jjx.production.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.result.Result;
import com.jjx.production.domain.dto.ConvertPlanToWorkOrdersDTO;
import com.jjx.production.domain.dto.ProductionOrderCreateDTO;
import com.jjx.production.domain.dto.ProductionOrderQueryDTO;
import com.jjx.production.domain.dto.ProductionOrderUpdateDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
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
    @Log(module = "生产工单管理", businessType = BusinessType.INSERT, bizType = "'production_order'", bizId = "#result.data")
    @SaCheckPermission("production:order:add")
    public Result<Long> createOrder(@Validated @RequestBody ProductionOrderCreateDTO createDTO) {
        Long orderId = productionOrderService.createOrder(createDTO);
        return Result.success(orderId);
    }

    @Operation(summary = "更新生产工单")
    @PutMapping
    @Log(module = "生产工单管理", businessType = BusinessType.UPDATE, bizType = "'production_order'", bizId = "#updateDTO.orderId")
    @SaCheckPermission("production:order:edit")
    public Result<Boolean> updateOrder(@Validated @RequestBody ProductionOrderUpdateDTO updateDTO) {
        boolean success = productionOrderService.updateOrder(updateDTO);
        return Result.success(success);
    }

    @Operation(summary = "删除生产工单")
    @DeleteMapping("/{orderId}")
    @Log(module = "生产工单管理", businessType = BusinessType.DELETE, bizType = "'production_order'", bizId = "#orderId")
    @SaCheckPermission("production:order:delete")
    public Result<Boolean> deleteOrder(@PathVariable Long orderId) {
        boolean success = productionOrderService.deleteOrder(orderId);
        return Result.success(success);
    }

    @Operation(summary = "批量删除生产工单")
    @DeleteMapping("/batch")
    @Log(module = "生产工单管理", businessType = BusinessType.DELETE, bizType = "'production_order'", bizId = "#orderIds[0]")
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
    @Log(module = "生产工单管理", businessType = BusinessType.UPDATE, bizType = "'production_order'", bizId = "#orderId", bizStatus = "T(com.jjx.production.enums.ProductionOrderStatusEnum).IN_PROGRESS.getLabel()", detail = "#attachmentIds")
    @SaCheckPermission("production:order:edit")
    public Result<Boolean> startOrder(@PathVariable Long orderId,
                                      // 仅供 @Log SpEL 取值，业务方法无需使用
                                      @RequestParam(required = false) String attachmentIds) {
        boolean success = productionOrderService.startOrder(orderId);
        return Result.success(success);
    }

    @Operation(summary = "暂停生产工单")
    @PutMapping("/{orderId}/pause")
    @Log(module = "生产工单管理", businessType = BusinessType.UPDATE, bizType = "'production_order'", bizId = "#orderId", bizStatus = "T(com.jjx.production.enums.ProductionOrderStatusEnum).PAUSED.getLabel()")
    @SaCheckPermission("production:order:edit")
    public Result<Boolean> pauseOrder(@PathVariable Long orderId) {
        boolean success = productionOrderService.pauseOrder(orderId);
        return Result.success(success);
    }

    @Operation(summary = "完成生产工单")
    @PutMapping("/{orderId}/complete")
    @Log(module = "生产工单管理", businessType = BusinessType.UPDATE, bizType = "'production_order'", bizId = "#orderId", bizStatus = "T(com.jjx.production.enums.ProductionOrderStatusEnum).COMPLETED.getLabel()", detail = "#attachmentIds")
    @SaCheckPermission("production:order:edit")
    public Result<Boolean> completeOrder(@PathVariable Long orderId,
                                         // 仅供 @Log SpEL 取值，业务方法无需使用
                                         @RequestParam(required = false) String attachmentIds) {
        boolean success = productionOrderService.completeOrder(orderId);
        return Result.success(success);
    }

    @Operation(summary = "重试完工入库（056：入库失败打标后重试，成功清除标记）")
    @PutMapping("/{orderId}/retry-inbound")
    @Log(module = "生产工单管理", businessType = BusinessType.UPDATE, bizType = "'production_order'", bizId = "#orderId")
    @SaCheckPermission("production:order:edit")
    public Result<Long> retryInbound(@PathVariable Long orderId) {
        return Result.success(productionOrderService.retryInbound(orderId));
    }

    @Operation(summary = "取消生产工单")
    @PutMapping("/{orderId}/cancel")
    @Log(module = "生产工单管理", businessType = BusinessType.UPDATE, bizType = "'production_order'", bizId = "#orderId", bizStatus = "T(com.jjx.production.enums.ProductionOrderStatusEnum).CANCELLED.getLabel()")
    @SaCheckPermission("production:order:edit")
    public Result<Boolean> cancelOrder(@PathVariable Long orderId) {
        boolean success = productionOrderService.cancelOrder(orderId);
        return Result.success(success);
    }

    @Operation(summary = "关闭生产工单")
    @PutMapping("/{orderId}/close")
    @Log(module = "生产工单管理", businessType = BusinessType.UPDATE, bizType = "'production_order'", bizId = "#orderId", bizStatus = "T(com.jjx.production.enums.ProductionOrderStatusEnum).CLOSED.getLabel()")
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
    @Log(module = "生产工单管理", businessType = BusinessType.INSERT, bizType = "'production_order'", bizId = "#result.data")
    @SaCheckPermission("production:order:add")
    public Result<Long> copyOrder(@RequestParam Long sourceOrderId,
                                  @RequestParam String targetOrderCode,
                                  @RequestParam String targetOrderName) {
        Long newOrderId = productionOrderService.copyOrder(sourceOrderId, targetOrderCode, targetOrderName);
        return Result.success(newOrderId);
    }

    @Operation(summary = "导入生产工单数据")
    @PostMapping("/import")
    @Log(module = "生产工单管理", businessType = BusinessType.IMPORT, bizType = "'production_order'", bizId = "#importData[0].orderNo")
    @SaCheckPermission("production:order:add")
    public Result importOrderData(@RequestBody List<ProductionOrderCreateDTO> importData) {
        return productionOrderService.importOrderData(importData);
    }

    @Operation(summary = "导出生产工单数据(Excel)")
    @GetMapping("/export")
    @Log(module = "生产工单管理", businessType = BusinessType.EXPORT, bizType = "'production_order'", bizId = "'export'")
    @SaCheckPermission("production:order:export")
    public void exportOrderData(ProductionOrderQueryDTO queryDTO, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        List<ProductionOrderVO> exportData = productionOrderService.exportOrderData(queryDTO);
        if (exportData == null || exportData.isEmpty()) {
            throw new com.jjx.common.exception.BusinessException("没有可导出的数据");
        }
        java.util.List<com.jjx.production.domain.vo.ProductionOrderExportVO> rows = new java.util.ArrayList<>();
        for (ProductionOrderVO vo : exportData) {
            com.jjx.production.domain.vo.ProductionOrderExportVO row = new com.jjx.production.domain.vo.ProductionOrderExportVO();
            row.setOrderNo(vo.getOrderNo());
            row.setOrderTypeDesc(vo.getOrderTypeDesc());
            row.setSalesOrderNo(vo.getSalesOrderNo());
            row.setProductCode(vo.getProductCode());
            row.setProductName(vo.getProductName());
            row.setProductSpec(vo.getProductSpec());
            row.setPlannedQuantity(vo.getPlannedQuantity());
            row.setCompletedQuantity(vo.getCompletedQuantity());
            row.setRemainingQuantity(vo.getRemainingQuantity());
            row.setCompletionPercentage(vo.getCompletionPercentage());
            row.setPlanStartDate(vo.getPlanStartDate());
            row.setPlanEndDate(vo.getPlanEndDate());
            row.setOrderStatusDesc(vo.getOrderStatusDesc());
            row.setApprovalStatusDesc(vo.getApprovalStatusDesc());
            row.setPriorityDesc(vo.getPriorityDesc());
            row.setDepartmentName(vo.getDepartmentName());
            row.setMaterialStatusDesc(vo.getMaterialStatusDesc());
            row.setRoutingName(vo.getRoutingName());
            row.setCreateBy(vo.getCreateBy());
            row.setCreateTime(vo.getCreateTime());
            row.setRemark(vo.getRemark());
            rows.add(row);
        }
        com.jjx.common.utils.ExcelUtils.export(response, rows, com.jjx.production.domain.vo.ProductionOrderExportVO.class, "生产订单");
    }

    @Operation(summary = "导出生产工单PDF（单张表单）")
    @Log(module = "生产工单管理", businessType = BusinessType.EXPORT, bizType = "'production_order'", bizId = "#orderId")
    @SaCheckPermission("production:order:export")
    @GetMapping("/export-pdf/{orderId}")
    public void exportPdf(@PathVariable Long orderId, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        ProductionOrderVO vo = productionOrderService.getOrderById(orderId);
        byte[] bytes = productionOrderService.exportPdf(orderId);
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=" + java.net.URLEncoder.encode(vo.getOrderNo() + ".pdf", java.nio.charset.StandardCharsets.UTF_8));
        response.getOutputStream().write(bytes);
    }

    @Operation(summary = "获取生产工单统计信息")
    @GetMapping("/statistics")
    public Result<OrderStatisticsVO> getOrderStatistics(ProductionOrderQueryDTO queryDTO) {
        return Result.success(productionOrderService.getOrderStatistics(queryDTO));
    }

    @Operation(summary = "获取排程甘特图数据")
    @SaCheckPermission("production:order:view")
    @GetMapping("/schedule/gantt")
    public Result<List<ProductionOrderVO>> getGanttData(
            @RequestParam(required = false) @Schema(description = "开始日期") String startDate,
            @RequestParam(required = false) @Schema(description = "结束日期") String endDate,
            @RequestParam(required = false) @Schema(description = "产品ID") Long productId,
            @RequestParam(required = false) @Schema(description = "订单类型") String orderType) {
        ProductionOrderQueryDTO queryDTO = new ProductionOrderQueryDTO();
        return Result.success(productionOrderService.queryOrderList(queryDTO));
    }

    @Operation(summary = "更新甘特图排期")
    @PutMapping("/schedule/gantt")
    @Log(module = "生产工单管理", businessType = BusinessType.UPDATE, bizType = "'production_order'", bizId = "#dto.orderId")
    @SaCheckPermission("production:order:edit")
    public Result<Boolean> updateGanttData(@RequestBody GanttUpdateDTO dto) {
        boolean success = productionOrderService.updateOrderPlanDate(dto.getOrderId(), dto.getPlanStartDate(), dto.getPlanEndDate());
        return Result.success(success);
    }

    @Data
    public static class GanttUpdateDTO {
        @Schema(description = "订单ID")
        @NotNull
        private Long orderId;
        @Schema(description = "订单类型")
        private String orderType;
        @Schema(description = "计划开始日期")
        private String planStartDate;
        @Schema(description = "计划结束日期")
        private String planEndDate;
        @Schema(description = "备注")
        private String remark;
    }

    @Operation(summary = "计划转工单")
    @PostMapping("/convert-plan-to-work-orders")
    @Log(module = "生产工单管理", businessType = BusinessType.INSERT, bizType = "'production_order'", bizId = "#dto.planId", bizStatus = "T(com.jjx.production.enums.ProductionOrderStatusEnum).PLANNED.getLabel()")
    @SaCheckPermission("production:order:add")
    public Result<List<Long>> convertPlanToWorkOrders(@Validated @RequestBody ConvertPlanToWorkOrdersDTO dto) {
        List<Long> orderIds = productionOrderService.convertPlanToWorkOrders(dto);
        return Result.success(orderIds);
    }

    @Operation(summary = "更新订单状态")
    @PutMapping("/status")
    @Log(module = "生产工单管理", businessType = BusinessType.UPDATE, bizType = "'production_order'", bizId = "#orderId", bizStatus = "T(com.jjx.production.enums.ProductionOrderStatusEnum).getByValue(#orderStatus)?.label", detail = "#attachmentIds")
    @SaCheckPermission("production:order:edit")
    public Result<Boolean> updateOrderStatus(@RequestParam Long orderId,
                                              @RequestParam Integer orderStatus,
                                              @RequestParam(required = false) String remark,
                                              // 仅供 @Log SpEL 取值，业务方法无需使用
                                              @RequestParam(required = false) String attachmentIds) {
        boolean success = productionOrderService.updateOrderStatus(orderId, orderStatus, remark);
        return Result.success(success);
    }

    @Operation(summary = "批量更新订单状态")
    @PutMapping("/batch-status")
    @Log(module = "生产工单管理", businessType = BusinessType.UPDATE, bizType = "'production_order'", bizId = "#dto.orderIds[0]")
    @SaCheckPermission("production:order:edit")
    public Result<Boolean> batchUpdateOrderStatus(@RequestBody BatchStatusUpdateDTO dto) {
        boolean success = productionOrderService.batchUpdateOrderStatus(dto.getOrderIds(), dto.getOrderStatus(), dto.getRemark());
        return Result.success(success);
    }

    @Data
    public static class BatchStatusUpdateDTO {
        @Schema(description = "订单ID列表")
        @NotEmpty(message = "订单ID列表不能为空")
        private List<Long> orderIds;
        @Schema(description = "目标状态")
        @NotNull(message = "目标状态不能为空")
        private Integer orderStatus;
        @Schema(description = "备注")
        private String remark;
    }
}
