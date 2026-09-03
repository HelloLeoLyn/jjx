package com.jjx.sales.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.common.utils.ExcelUtils;
import com.jjx.sales.domain.entity.SalesInvoice;
import com.jjx.sales.domain.vo.SalesInvoiceExportVO;
import com.jjx.sales.service.SalesInvoiceService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "销售发票管理")
@RestController
@RequestMapping("/sales/invoice")
@RequiredArgsConstructor
public class SalesInvoiceController {
    private final SalesInvoiceService invoiceService;

    @Operation(summary = "发票列表")
    @SaCheckPermission("sales:order:view")
    @GetMapping("/page")
    public Result<PageResult<SalesInvoice>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "10") int pageSize,
                                                  @RequestParam(required = false) String invoiceNo,
                                                  @RequestParam(required = false) String customerName,
                                                  @RequestParam(required = false) java.time.LocalDate startDate,
                                                  @RequestParam(required = false) java.time.LocalDate endDate,
                                                  @RequestParam(required = false) Integer status) {
        return Result.success(invoiceService.page(pageNum, pageSize, invoiceNo, customerName, startDate, endDate, status));
    }

    @Operation(summary = "发票详情")
    @SaCheckPermission("sales:order:view")
    @GetMapping("/{id}")
    public Result<SalesInvoice> getById(@PathVariable Long id) {
        return Result.success(invoiceService.getById(id));
    }

    @Operation(summary = "记录发票打印")
    @Log(module = "销售发票", businessType = BusinessType.OTHER, bizType = "'invoice_print'", bizId = "#id")
    @SaCheckPermission("sales:order:view")
    @PostMapping("/{id}/print-log")
    public Result<Void> printLog(@PathVariable Long id) { return Result.success(); }

    @Operation(summary = "新增发票")
    @Log(module = "销售发票", businessType = BusinessType.INSERT, bizType = "'invoice'")
    @SaCheckPermission("sales:order:edit")
    @PostMapping
    public Result<Long> create(@RequestBody SalesInvoice invoice) {
        return Result.success(invoiceService.create(invoice));
    }

    @Operation(summary = "修改发票")
    @Log(module = "销售发票", businessType = BusinessType.UPDATE, bizType = "'invoice'", bizId = "#invoice.invoiceId", bizStatus = "T(com.jjx.sales.enums.SalesInvoiceStatusEnum).getByValue(#invoice.status)?.label")
    @SaCheckPermission("sales:order:edit")
    @PutMapping("/{invoiceId}")
    public Result<Void> update(@PathVariable Long invoiceId, @RequestBody SalesInvoice invoice) {
        invoice.setInvoiceId(invoiceId);
        if (!invoiceService.update(invoice)) {
            return Result.error("修改发票失败");
        }
        return Result.success();
    }

    @Operation(summary = "删除发票")
    @Log(module = "销售发票", businessType = BusinessType.DELETE, bizType = "'invoice'")
    @SaCheckPermission("sales:order:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        invoiceService.delete(id);
        return Result.success();
    }

    /**
     * 导出销售发票（DEV-720：Excel）
     */
    @Operation(summary = "导出销售发票")
    @SaCheckPermission("sales:order:export")
    @GetMapping("/export")
    public void export(HttpServletResponse response) {
        PageResult<SalesInvoice> page = invoiceService.page(1, 100000, null, null, null, null, null);
        List<SalesInvoiceExportVO> rows = new ArrayList<>();
        if (page != null && page.getRecords() != null) {
            for (SalesInvoice inv : page.getRecords()) {
                SalesInvoiceExportVO row = new SalesInvoiceExportVO();
                row.setInvoiceNo(inv.getInvoiceNo());
                row.setCustomerName(inv.getCustomerName());
                row.setInvoiceDate(inv.getInvoiceDate());
                row.setInvoiceTypeDesc(invoiceTypeText(inv.getInvoiceType()));
                row.setInvoiceAmount(inv.getInvoiceAmount());
                row.setTaxAmount(inv.getTaxAmount());
                row.setTotalAmount(inv.getTotalAmount());
                row.setCurrency(inv.getCurrency());
                row.setStatusDesc(statusText(inv.getStatus()));
                row.setRemark(inv.getRemark());
                rows.add(row);
            }
        }
        if (rows.isEmpty()) {
            throw new com.jjx.common.exception.BusinessException("导出数据为空");
        }
        ExcelUtils.export(response, rows, SalesInvoiceExportVO.class, "销售发票");
    }

    private String invoiceTypeText(Integer type) {
        if (type == null) return "";
        return switch (type) {
            case 1 -> "增值税专用发票";
            case 2 -> "增值税普通发票";
            case 3 -> "电子发票";
            default -> String.valueOf(type);
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
