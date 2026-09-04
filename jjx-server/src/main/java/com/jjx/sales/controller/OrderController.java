package com.jjx.sales.controller;

import com.jjx.common.constant.LogActions;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.annotation.ValidationGroups;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.exception.BusinessExceptionEnum;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.sales.domain.dto.SalesOrderAddDTO;
import com.jjx.sales.domain.dto.SalesOrderEditDTO;
import com.jjx.sales.domain.dto.SalesOrderQueryDTO;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.vo.OrderReferValidationVO;
import com.jjx.sales.domain.vo.SalesOrderVO;
import com.jjx.sales.service.IOrderService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 销售订单控制器
 * 提供销售订单的增删改查接口
 */
@Tag(name = "销售订单管理")
@RestController
@RequestMapping("/sales/orders")
@RequiredArgsConstructor
public class OrderController extends BaseController {
    private final IOrderService orderService;
    /**
     * 获取销售订单列表
     */
    @Operation(summary = "获取销售订单列表")
    @SaCheckPermission("sales:order:view")
    @GetMapping
    public Result<PageResult<SalesOrderVO>> getOrders(SalesOrderQueryDTO dto) {
        return Result.success(orderService.pageQuery(dto));
    }

    /**
     * 获取销售订单详细信息
     */
    @Operation(summary = "获取销售订单详细信息")
    @SaCheckPermission("sales:order:view")
    @GetMapping("/{orderId}/validation")
    public Result<OrderReferValidationVO> validation(@PathVariable Long orderId) {
        OrderReferValidationVO salesOrder = orderService.validation(orderId);
        return Result.success(salesOrder);
    }
    /**
     * 获取销售订单详细信息
     */
    @Operation(summary = "获取销售订单详细信息")
    @SaCheckPermission("sales:order:view")
    @GetMapping("/{orderId}")
    public Result<SalesOrderVO> getOrder(@PathVariable Long orderId) {
        return Result.success(orderService.selectOrderById(orderId));
    }
    /**
     * 新增销售订单
     * 日志：手动写 order.create（带 traceId，见 OrderServiceImpl.saveOrderCreateLog）——不用 @Log 避免双写
     */
    @Operation(summary = "新增销售订单")
    @SaCheckPermission("sales:order:add")
    @PostMapping
    public Result<java.util.Map<String, Object>> addOrder(@Validated(ValidationGroups.Add.class) @RequestBody SalesOrderAddDTO dto) {
        Long orderId = orderService.insertOrder(dto);
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("orderId", orderId);
        data.put("traceId", dto.getTraceId());
        return Result.success(data);
    }

    /**
     * 复制订单（终态订单一键重新生成新草稿单）
     * 日志：新单/原单各写一条（带各自 traceId），见 OrderServiceImpl.copyOrder
     */
    @Operation(summary = "复制订单（重新生成新草稿单）")
    @SaCheckPermission("sales:order:add")
    @PostMapping("/{orderId}/copy")
    public Result<Long> copyOrder(@PathVariable Long orderId) {
        return Result.success(orderService.copyOrder(orderId));
    }

    /**
     * 修改销售订单
     * 日志：手动写 order.update（带字段级变更明细，见 OrderServiceImpl.saveOrderUpdateChangeLog）——不用 @Log 避免双写
     */
    @Operation(summary = "修改销售订单")
    @SaCheckPermission("sales:order:edit")
    @PutMapping("/{orderId}")
    public Result<Void> updateOrder(@PathVariable Long orderId, @Validated(ValidationGroups.Update.class) @RequestBody SalesOrderEditDTO dto) {
        if(!orderId.equals(dto.getOrderId())){
            throw new BusinessException(BusinessExceptionEnum.DATA_CONFLICT);
        }
        return toAjax(orderService.updateOrder(dto));
    }

    /**
     * 删除销售订单
     */
    @Operation(summary = "删除销售订单")
    @Log(module = "销售订单管理", businessType = BusinessType.DELETE, bizType = "'order'", bizId = "#orderIds[0]", action = LogActions.ORDER_DELETE)
    @SaCheckPermission("sales:order:delete")
    @DeleteMapping("/{orderIds}")
    public Result<Void> deleteOrders(@PathVariable Long[] orderIds) {
        return toAjax(orderService.deleteOrderByIds(orderIds));
    }

    /**
     * 导出销售订单列表
     */
    @Operation(summary = "导出销售订单列表")
    @SaCheckPermission("sales:order:export")
    @GetMapping("/export")
    public Result<String> exportOrders(SalesOrder order) {
        String filePath = orderService.exportOrderList(order);
        return Result.success(filePath);
    }



    /**
     * 导出销售订单Excel（单张表单）
     */
    @Operation(summary = "导出销售订单Excel")
    @SaCheckPermission("sales:order:export")
    @GetMapping("/export-excel/{orderId}")
    public void exportExcel(@PathVariable Long orderId, HttpServletResponse response) throws IOException {
        SalesOrderVO order = orderService.selectOrderById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        byte[] bytes = orderService.exportExcel(orderId);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(order.getOrderNo() + ".xlsx", StandardCharsets.UTF_8));
        response.getOutputStream().write(bytes);
    }

    
    /**
     * 创建产品实例
     */
    @Operation(summary = "创建产品实例")
    @Log(module = "销售订单管理", businessType = BusinessType.UPDATE, bizType = "'order'", bizId = "#orderId", action = LogActions.ORDER_CREATE_INSTANCES)
    @SaCheckPermission("sales:order:edit")
    @PutMapping("/create-instances/{orderId}")
    public Result<Void> createOrderInstances(@PathVariable Long orderId) {
        return toAjax(orderService.createInstances(orderId));
    }

    /**
     * 更新付款信息
     */
    @Operation(summary = "更新付款信息")
@Log(module = "销售订单管理", businessType = BusinessType.UPDATE, bizType = "'order'", bizId = "#orderId", bizStatus = "T(com.jjx.sales.enums.SalesPaymentStatusEnum).PAID.getLabel()", action = LogActions.ORDER_PAYMENT)
    @SaCheckPermission("sales:order:edit")
    @PutMapping("/payment/{orderId}")
    public Result<Void> updateOrderPayment(@PathVariable Long orderId,
                                           @RequestParam Double paidAmount) {
        return toAjax(orderService.updatePaymentInfo(orderId, paidAmount));
    }

    /**
     * 根据客户ID查询订单列表
     */
    @Operation(summary = "根据客户ID查询订单列表")
    @SaCheckPermission("sales:order:view")
    @GetMapping("/customer/{customerId}")
    public Result<List<SalesOrder>> getOrdersByCustomerId(@PathVariable Long customerId) {
        return Result.success(orderService.selectOrdersByCustomerId(customerId));
    }

    /**
     * 根据报价单ID查询订单
     */
    @Operation(summary = "根据报价单ID查询订单")
    @SaCheckPermission("sales:order:view")
    @GetMapping("/quotation/{quotationId}")
    public Result<SalesOrder> getOrderByQuotationId(@PathVariable Long quotationId) {
        return Result.success(orderService.selectOrderByQuotationId(quotationId));
    }

    /**
     * 生成订单号
     */
    @Operation(summary = "生成订单号")
    @SaCheckPermission("sales:order:add")
    @GetMapping("/order-no/next")
    public Result<String> generateOrderNo() {
        return Result.success(orderService.generateOrderNo());
    }

    /**
     * 检查订单号是否唯一
     */
    @Operation(summary = "检查订单号是否唯一")
    @SaCheckPermission("sales:order:add")
    @GetMapping("/order-no/{orderNo}/unique")
    public Result<Boolean> checkOrderNoUnique(@PathVariable String orderNo) {
        return Result.success(orderService.checkOrderNoUnique(orderNo));
    }

    /**
     * 获取订单统计信息
     */
    @Operation(summary = "获取订单统计信息")
    @SaCheckPermission("sales:order:view")
    @GetMapping("/statistics")
    public Result<Object> getOrderStatistics() {
        return Result.success(orderService.getOrderStatistics());
    }
}
