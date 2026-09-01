package com.jjx.sales.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.sales.domain.dto.SalesReturnQueryDTO;
import com.jjx.sales.domain.entity.SalesReturn;
import com.jjx.sales.enums.SalesReturnStatusEnum;
import com.jjx.sales.service.ISalesReturnService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "销售退货管理")
@RestController
@RequestMapping("/sales/returns")
@RequiredArgsConstructor
public class SalesReturnController {

    private final ISalesReturnService returnService;

    @Operation(summary = "分页查询退货单")
    @SaCheckPermission("sales:return:view")
    @GetMapping("/page")
    public Result<PageResult<SalesReturn>> page(SalesReturnQueryDTO query) {
        return Result.success(returnService.page(query));
    }

    @Operation(summary = "退货单详情")
    @SaCheckPermission("sales:return:view")
    @GetMapping("/{returnId}")
    public Result<SalesReturn> detail(@PathVariable Long returnId) {
        return Result.success(returnService.getById(returnId));
    }

    @Operation(summary = "创建退货单（申请中）")
    @Log(module = "销售退货管理", businessType = BusinessType.INSERT,
            bizType = "'sales_return'", bizId = "#result.data")
    @SaCheckPermission("sales:return:add")
    @PostMapping
    public Result<Long> create(@RequestBody Map<String, Object> params) {
        return Result.success(returnService.create(params));
    }

    @Operation(summary = "审核通过")
    @Log(module = "销售退货管理", businessType = BusinessType.APPROVE,
            bizType = "'sales_return'", bizId = "#returnId",
            bizStatus = "T(com.jjx.sales.enums.SalesReturnStatusEnum).APPROVED.getLabel()")
    @SaCheckPermission("sales:return:approve")
    @PutMapping("/{returnId}/approve")
    public Result<Void> approve(@PathVariable Long returnId,
                                @RequestParam(required = false) String approverName,
                                @RequestParam(required = false) String approveRemark) {
        returnService.approve(returnId, approverName, approveRemark);
        return Result.success();
    }

    @Operation(summary = "审核驳回")
    @Log(module = "销售退货管理", businessType = BusinessType.APPROVE,
            bizType = "'sales_return'", bizId = "#returnId")
    @SaCheckPermission("sales:return:approve")
    @PutMapping("/{returnId}/reject")
    public Result<Void> reject(@PathVariable Long returnId,
                               @RequestParam(required = false) String approverName,
                               @RequestParam(required = false) String approveRemark) {
        returnService.reject(returnId, approverName, approveRemark);
        return Result.success();
    }

    @Operation(summary = "收货确认（联动退货入库）")
    @Log(module = "销售退货管理", businessType = BusinessType.UPDATE,
            bizType = "'sales_return'", bizId = "#returnId",
            bizStatus = "T(com.jjx.sales.enums.SalesReturnStatusEnum).RECEIVED.getLabel()")
    @SaCheckPermission("sales:return:edit")
    @PutMapping("/{returnId}/receive")
    public Result<Void> receive(@PathVariable Long returnId,
                                @RequestParam(required = false) String receiverName,
                                @RequestParam(required = false) String remark) {
        returnService.receive(returnId, receiverName, remark);
        return Result.success();
    }
}
