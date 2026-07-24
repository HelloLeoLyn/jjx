package com.jjx.sales.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.sales.domain.entity.SalesInvoice;
import com.jjx.sales.service.SalesInvoiceService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
                                                  @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(invoiceService.page(pageNum, pageSize));
    }

    @Operation(summary = "发票详情")
    @SaCheckPermission("sales:order:view")
    @GetMapping("/{id}")
    public Result<SalesInvoice> getById(@PathVariable Long id) {
        return Result.success(invoiceService.getById(id));
    }

    @Operation(summary = "新增发票")
    @Log(module = "销售发票", businessType = BusinessType.INSERT)
    @SaCheckPermission("sales:order:edit")
    @PostMapping
    public Result<Long> create(@RequestBody SalesInvoice invoice) {
        return Result.success(invoiceService.create(invoice));
    }

    @Operation(summary = "删除发票")
    @Log(module = "销售发票", businessType = BusinessType.DELETE)
    @SaCheckPermission("sales:order:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        invoiceService.delete(id);
        return Result.success();
    }
}
