package com.jjx.sales.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.enums.SampleOrderStatusEnum;
import com.jjx.sales.service.ISampleOrderService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 样品单控制器
 * 独立于标准订单的样品单生命周期管理
 */
@Tag(name = "样品单管理")
@RestController
@RequestMapping("/sales/sample-order")
@RequiredArgsConstructor
public class SampleOrderController extends BaseController {

    private final ISampleOrderService sampleOrderService;

    @Operation(summary = "从报价单创建样品单")
    @Log(module = "样品单管理", businessType = BusinessType.INSERT, bizType = "'sample'", bizId = "#result.data.orderId", traceId = "#result.data.traceId", bizStatus = "1")
    @SaCheckPermission("sales:sample:add")
    @PostMapping("/create-from-quotation/{quotationId}")
    public Result<SalesOrder> createFromQuotation(
            @PathVariable Long quotationId,
            @RequestParam(required = false) Integer sampleQty,
            @RequestParam(required = false) String remark) {
        return Result.success(sampleOrderService.createFromQuotation(quotationId, sampleQty, remark));
    }

    @Operation(summary = "样品单详情")
    @SaCheckPermission("sales:sample:view")
    @GetMapping("/{orderId}")
    public Result<SalesOrder> getInfo(@PathVariable Long orderId) {
        return Result.success(sampleOrderService.selectById(orderId));
    }

    @Operation(summary = "样品单列表")
    @SaCheckPermission("sales:sample:view")
    @GetMapping("/list")
    public Result<List<SalesOrder>> list(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Integer sampleStatus,
            @RequestParam(required = false) Long salesPersonId) {
        return Result.success(sampleOrderService.selectSampleList(customerId, sampleStatus, salesPersonId));
    }

    @Operation(summary = "样品单提交审核")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId", bizStatus = "2")
    @SaCheckPermission("sales:sample:edit")
    @PutMapping("/submit-review/{orderId}")
    public Result<SalesOrder> submitReview(@PathVariable Long orderId) {
        return Result.success(sampleOrderService.submitReview(orderId));
    }

    @Operation(summary = "样品单审核通过（进入工程打样）")
    @Log(module = "样品单管理", businessType = BusinessType.APPROVE, bizType = "'sample'", bizId = "#orderId", bizStatus = "3")
    @SaCheckPermission("sales:sample:approve")
    @PutMapping("/approve/{orderId}")
    public Result<SalesOrder> approve(@PathVariable Long orderId,
                                      @RequestParam(required = false) String remark) {
        return Result.success(sampleOrderService.approveReview(orderId, remark));
    }

    @Operation(summary = "样品单审核驳回")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId", bizStatus = "9")
    @SaCheckPermission("sales:sample:approve")
    @PutMapping("/reject-review/{orderId}")
    public Result<SalesOrder> rejectReview(@PathVariable Long orderId,
                                           @RequestParam(required = false) String remark) {
        return Result.success(sampleOrderService.rejectReview(orderId, remark));
    }

    @Operation(summary = "工程接单（记录工程备注）")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId", bizStatus = "3")
    @SaCheckPermission("sales:sample:engineering")
    @PutMapping("/start-engineering/{orderId}")
    public Result<SalesOrder> startEngineering(@PathVariable Long orderId,
                                               @RequestParam(required = false) String engineeringNote) {
        return Result.success(sampleOrderService.startEngineering(orderId, engineeringNote));
    }

    @Operation(summary = "工程标记样品完成（待送样）")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId", bizStatus = "4")
    @SaCheckPermission("sales:sample:engineering")
    @PutMapping("/mark-ready/{orderId}")
    public Result<SalesOrder> markReady(@PathVariable Long orderId,
                                        @RequestParam(required = false) Integer sampleQty) {
        return Result.success(sampleOrderService.markSampleReady(orderId, sampleQty));
    }

    @Operation(summary = "销售送样登记")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId", bizStatus = "5")
    @SaCheckPermission("sales:sample:deliver")
    @PutMapping("/send-sample/{orderId}")
    public Result<SalesOrder> sendSample(@PathVariable Long orderId,
                                         @RequestParam(required = false) String trackingNo) {
        return Result.success(sampleOrderService.sendSample(orderId, trackingNo));
    }

    @Operation(summary = "客户确认样品OK")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId", bizStatus = "6")
    @SaCheckPermission("sales:sample:confirm")
    @PutMapping("/confirm/{orderId}")
    public Result<SalesOrder> confirm(@PathVariable Long orderId,
                                      @RequestParam(required = false) String clientName) {
        return Result.success(sampleOrderService.confirmSample(orderId, clientName));
    }

    @Operation(summary = "客户退回样品（多轮迭代）")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId", bizStatus = "9")
    @SaCheckPermission("sales:sample:confirm")
    @PutMapping("/reject-sample/{orderId}")
    public Result<SalesOrder> rejectSample(@PathVariable Long orderId,
                                           @RequestParam(required = false) String rejectReason) {
        return Result.success(sampleOrderService.rejectSample(orderId, rejectReason));
    }

    @Operation(summary = "样品转量产（生成标准订单）")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId", bizStatus = "7")
    @SaCheckPermission("sales:sample:convert")
    @PutMapping("/convert-to-production/{orderId}")
    public Result<SalesOrder> convertToProduction(@PathVariable Long orderId) {
        return Result.success(sampleOrderService.convertToProduction(orderId));
    }

    @Operation(summary = "获取样品单状态选项")
    @SaCheckPermission("sales:sample:view")
    @GetMapping("/status-options")
    public Result<List<Map<String, Object>>> getStatusOptions() {
        List<Map<String, Object>> options = new ArrayList<>();
        for (SampleOrderStatusEnum status : SampleOrderStatusEnum.values()) {
            Map<String, Object> item = new HashMap<>();
            item.put("value", status.getCode());
            item.put("label", status.getName());
            item.put("description", status.getDescription());
            item.put("terminal", status.isTerminal());
            options.add(item);
        }
        return Result.success(options);
    }
}
