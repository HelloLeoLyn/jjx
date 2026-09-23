package com.jjx.sales.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.core.result.Result;
import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.sales.domain.entity.SalesDelivery;
import com.jjx.sales.domain.entity.SalesCustomer;
import com.jjx.sales.domain.entity.SalesReceipt;
import com.jjx.sales.domain.entity.SalesReturn;
import com.jjx.sales.enums.SalesReceiptStatusEnum;
import com.jjx.sales.enums.PaymentTermTypeEnum;
import com.jjx.sales.enums.ReconciliationDueStatusEnum;
import com.jjx.sales.enums.SalesDeliveryStatusEnum;
import com.jjx.sales.mapper.CustomerMapper;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.SalesDeliveryMapper;
import com.jjx.sales.mapper.SalesReceiptMapper;
import com.jjx.sales.service.ISalesOrderProductService;
import com.jjx.sales.util.PaymentDueDateCalculator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务对账（任务 1299，2026-09-03）
 * 纯查询：按客户+期间列送货明细（送货单按发货单逐张列示，支持一单多批发货）+ 期间回款合计
 * 对账口径：送货单状态 已发货(2)/已签收(4)（运输中(3) 2026-09-21 dev-20260921-039 已退场，无写入点）；
 *           回款 = 未作废收款单 actual_amount
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
    private final CustomerMapper customerMapper;

    @Operation(summary = "对账汇总（客户+期间：送货明细 + 回款合计）")
    @SaCheckPermission("sales:reconcile:view")
    @GetMapping
    public Result<Map<String, Object>> summary(@RequestParam Long customerId,
                                               @RequestParam(required = false) String startDate,
                                               @RequestParam(required = false) String endDate,
                                               @RequestParam(required = false) ReconciliationDueStatusEnum dueStatus) {
        LocalDate start = startDate == null || startDate.isBlank() ? LocalDate.now().minusMonths(1) : LocalDate.parse(startDate);
        LocalDate end = endDate == null || endDate.isBlank() ? LocalDate.now() : LocalDate.parse(endDate);

        SalesCustomer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw new BusinessException("客户不存在");
        }
        PaymentTermTypeEnum termType = PaymentTermTypeEnum.parse(customer.getPaymentTermType());

        // 1. 正式对账只取所选签收期间内的已签收送货单
        List<SalesDelivery> deliveries = deliveryMapper.selectList(new LambdaQueryWrapper<SalesDelivery>()
                .eq(SalesDelivery::getCustomerId, customerId)
                .ge(SalesDelivery::getCustomerReceiveDate, start)
                .le(SalesDelivery::getCustomerReceiveDate, end)
                .eq(SalesDelivery::getDeliveryStatus, SalesDeliveryStatusEnum.RECEIVED.getValue())
                .orderByAsc(SalesDelivery::getCustomerReceiveDate));

        // 2. 每张送货单 → 订单明细行（一单一发，明细即订单产品行）
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SalesDelivery d : deliveries) {
            if (d.getOrderId() == null) {
                continue;
            }
            var order = orderMapper.selectById(d.getOrderId());
            var items = orderProductService.getListByOrderId(d.getOrderId());
            LocalDate receiveDate = d.getCustomerReceiveDate() instanceof java.sql.Date sqlDate
                    ? sqlDate.toLocalDate()
                    : d.getCustomerReceiveDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate dueDate = PaymentDueDateCalculator.calculate(termType, customer.getCreditDays(), receiveDate);
            ReconciliationDueStatusEnum rowDueStatus = resolveDueStatus(termType, dueDate, LocalDate.now());
            if (dueStatus != null && rowDueStatus != dueStatus) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("deliveryId", d.getDeliveryId());
            row.put("deliveryNo", d.getDeliveryNo());
            row.put("deliveryDate", d.getDeliveryDate());
            row.put("customerReceiveDate", receiveDate);
            row.put("dueDate", dueDate);
            row.put("dueStatus", rowDueStatus.name());
            row.put("daysUntilDue", dueDate == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), dueDate));
            row.put("overdueDays", dueDate != null && dueDate.isBefore(LocalDate.now())
                    ? ChronoUnit.DAYS.between(dueDate, LocalDate.now()) : 0);
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
        data.put("paymentTermType", customer.getPaymentTermType());
        data.put("creditDays", customer.getCreditDays());
        data.put("creditStartBasis", customer.getCreditStartBasis());
        data.put("rows", rows);
        data.put("paymentCount", receipts.size());
        data.put("paymentTotal", paymentTotal);
        return Result.success(data);
    }

    private ReconciliationDueStatusEnum resolveDueStatus(PaymentTermTypeEnum type, LocalDate dueDate, LocalDate today) {
        if (type == PaymentTermTypeEnum.PREPAID || dueDate == null) {
            return ReconciliationDueStatusEnum.PREPAID;
        }
        if (dueDate.isBefore(today)) {
            return ReconciliationDueStatusEnum.OVERDUE;
        }
        if (dueDate.isEqual(today)) {
            return ReconciliationDueStatusEnum.DUE_TODAY;
        }
        return ReconciliationDueStatusEnum.NOT_DUE;
    }
}
