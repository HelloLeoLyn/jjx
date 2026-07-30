package com.jjx.sales.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.sales.service.SalesReceiptService;
import com.jjx.sales.domain.entity.SalesReceipt;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
                                                  @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(receiptService.page(pageNum, pageSize));
    }

    @Operation(summary = "新增收款")
    @Log(module = "销售收款", businessType = BusinessType.INSERT, bizType = "'receipt'")
    @SaCheckPermission("sales:order:edit")
    @PostMapping
    public Result<Long> create(@RequestBody SalesReceipt receipt) {
        return Result.success(receiptService.create(receipt));
    }
}
