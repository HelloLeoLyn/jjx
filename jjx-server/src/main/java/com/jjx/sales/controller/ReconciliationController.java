package com.jjx.sales.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.sales.domain.entity.SalesDelivery;
import com.jjx.sales.domain.entity.SalesReceipt;
import com.jjx.sales.domain.entity.SalesReturn;
import com.jjx.sales.enums.SalesReceiptStatusEnum;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.SalesDeliveryMapper;
import com.jjx.sales.mapper.SalesReceiptMapper;
import com.jjx.sales.service.ISalesOrderProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务对账（任务 1299，2026-09-03）
 * 纯查询：按客户+期间列送货明细（送货单 1:1 订单明细，一单一发模型天然精确）+ 期间回款合计
 * 对账口径：送货单状态 已发货(2)/运输中(3)/已签收(4)；回款 = 未作废收款单 actual_amount
 */
@Tag(name = "业务对账")
@RestController
@RequestMapping("/sales/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController extends BaseController {

    private final SalesDeliveryMapper deliveryMapper;
    private final SalesReceiptMapper receiptMapper;
    private final OrderMapper orderMapper;
    private final ISalesOrderProductService orderProductService;

    @Operation(summary = "对账汇总（客户+期间：送货明细 + 回款合计）")
    @SaCheckPermission("sales:reconcile:view")
    @GetMapping
    public Result<Map<String, Object>> summary(@RequestParam Long customerId,
                                               @RequestParam(required = false) String startDate,
                                               @RequestParam(required = false) String endDate) {
        LocalDate start = startDate == null || startDate.isBlank() ? LocalDate.now().minusMonths(1) : LocalDate.parse(startDate);
        LocalDate end = endDate == null || endDate.isBlank() ? LocalDate.now() : LocalDate.parse(endDate);

        // 1. 该客户期间内已发货的送货单
        List<SalesDelivery> deliveries = deliveryMapper.selectList(new LambdaQueryWrapper<SalesDelivery>()
                .eq(SalesDelivery::getCustomerId, customerId)
                .ge(SalesDelivery::getDeliveryDate, start)
                .le(SalesDelivery::getDeliveryDate, end)
                .in(SalesDelivery::getDeliveryStatus, 2, 3, 4)
                .orderByAsc(SalesDelivery::getDeliveryDate));

        // 2. 每张送货单 → 订单明细行（一单一发，明细即订单产品行）
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SalesDelivery d : deliveries) {
            if (d.getOrderId() == null) {
                continue;
            }
            var order = orderMapper.selectById(d.getOrderId());
            var items = orderProductService.getListByOrderId(d.getOrderId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("deliveryId", d.getDeliveryId());
            row.put("deliveryNo", d.getDeliveryNo());
            row.put("deliveryDate", d.getDeliveryDate());
            row.put("deliveryStatus", d.getDeliveryStatus());
            row.put("orderId", d.getOrderId());
            row.put("orderNo", order == null ? null : order.getOrderNo());
            row.put("orderDate", order == null ? null : order.getOrderDate());
            row.put("totalAmount", d.getTotalAmount());
            row.put("items", items);
            rows.add(row);
        }

        // 3. 期间回款合计（未作废收款单）
        List<SalesReceipt> receipts = receiptMapper.selectList(new LambdaQueryWrapper<SalesReceipt>()
                .eq(SalesReceipt::getCustomerId, customerId)
                .ge(SalesReceipt::getReceiptDate, start)
                .le(SalesReceipt::getReceiptDate, end)
                .eq(SalesReceipt::getStatus, SalesReceiptStatusEnum.NORMAL.getValue()));
        BigDecimal paymentTotal = BigDecimal.ZERO;
        for (SalesReceipt r : receipts) {
            if (r.getActualAmount() != null) {
                paymentTotal = paymentTotal.add(r.getActualAmount());
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("customerId", customerId);
        data.put("startDate", start.toString());
        data.put("endDate", end.toString());
        data.put("deliveryCount", rows.size());
        data.put("rows", rows);
        data.put("paymentCount", receipts.size());
        data.put("paymentTotal", paymentTotal);
        return Result.success(data);
    }
}
