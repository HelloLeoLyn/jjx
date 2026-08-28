package com.jjx.sales.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.common.utils.ExcelUtils;
import com.jjx.sales.domain.entity.SalesReceipt;
import com.jjx.sales.domain.vo.SalesReceiptExportVO;
import com.jjx.sales.service.SalesReceiptService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "销售收款管理")
@RestController
@RequestMapping("/sales/receipt")
@RequiredArgsConstructor
public class SalesReceiptController {
    private final SalesReceiptService receiptService;

    @Operation(summary = "收款列表")
    @SaCheckPermission("sales:order:view")
    @GetMapping("/page")
    public Result<PageResult<SalesReceipt>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "10") int pageSize,
                                                  @RequestParam(required = false) String receiptNo,
                                                  @RequestParam(required = false) String customerName,
                                                  @RequestParam(required = false) java.time.LocalDate startDate,
                                                  @RequestParam(required = false) java.time.LocalDate endDate,
                                                  @RequestParam(required = false) Integer status) {
        return Result.success(receiptService.page(pageNum, pageSize, receiptNo, customerName, startDate, endDate, status));
    }

    @Operation(summary = "收款详情")
    @SaCheckPermission("sales:order:view")
    @GetMapping("/{id}")
    public Result<SalesReceipt> getById(@PathVariable Long id) {
        return Result.success(receiptService.getById(id));
    }

    @Operation(summary = "记录收款单打印")
    @Log(module = "销售收款", businessType = BusinessType.OTHER, bizType = "'receipt_print'", bizId = "#id")
    @SaCheckPermission("sales:order:view")
    @PostMapping("/{id}/print-log")
    public Result<Void> printLog(@PathVariable Long id) { return Result.success(); }

    @Operation(summary = "新增收款")
    @Log(module = "销售收款", businessType = BusinessType.INSERT, bizType = "'receipt'")
    @SaCheckPermission("sales:order:edit")
    @PostMapping
    public Result<Long> create(@RequestBody SalesReceipt receipt) {
        return Result.success(receiptService.create(receipt));
    }

    /**
     * 导出销售收款单（DEV-720：Excel）
     */
    @Operation(summary = "导出销售收款单")
    @SaCheckPermission("sales:order:export")
    @GetMapping("/export")
    public void export(HttpServletResponse response) {
        PageResult<SalesReceipt> page = receiptService.page(1, 100000, null, null, null, null, null);
        List<SalesReceiptExportVO> rows = new ArrayList<>();
        if (page != null && page.getRecords() != null) {
            for (SalesReceipt r : page.getRecords()) {
                SalesReceiptExportVO row = new SalesReceiptExportVO();
                row.setReceiptNo(r.getReceiptNo());
                row.setCustomerName(r.getCustomerName());
                row.setReceiptDate(r.getReceiptDate());
                row.setReceiptTypeDesc(receiptTypeText(r.getReceiptType()));
                row.setPaymentMethodDesc(paymentMethodText(r.getPaymentMethod()));
                row.setReceiptAmount(r.getReceiptAmount());
                row.setCurrency(r.getCurrency());
                row.setStatusDesc(statusText(r.getStatus()));
                row.setRemark(r.getRemark());
                rows.add(row);
            }
        }
        if (rows.isEmpty()) {
            throw new com.jjx.common.exception.BusinessException("导出数据为空");
        }
        ExcelUtils.export(response, rows, SalesReceiptExportVO.class, "销售收款单");
    }

    private String receiptTypeText(Integer type) {
        if (type == null) return "";
        return switch (type) {
            case 1 -> "定金";
            case 2 -> "进度款";
            case 3 -> "尾款";
            default -> String.valueOf(type);
        };
    }

    private String paymentMethodText(Integer method) {
        if (method == null) return "";
        return switch (method) {
            case 1 -> "银行转账";
            case 2 -> "现金";
            case 3 -> "承兑汇票";
            default -> String.valueOf(method);
        };
    }

    private String statusText(Integer status) {
        if (status == null) return "";
        return switch (status) {
            case 0 -> "作废";
            case 1 -> "正常";
            default -> String.valueOf(status);
        };
    }
}
