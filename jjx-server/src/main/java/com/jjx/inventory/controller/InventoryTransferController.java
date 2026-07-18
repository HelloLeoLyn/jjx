package com.jjx.inventory.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.common.core.result.Result;
import com.jjx.inventory.dto.query.TransferQueryDTO;
import com.jjx.inventory.dto.vo.TransferVO;
import com.jjx.inventory.service.InventoryTransferService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 调拨管理Controller
 */
@RestController
@RequestMapping("/inventory/transfer")
@RequiredArgsConstructor
@Tag(name = "调拨管理", description = "调拨管理相关接口")
public class InventoryTransferController {

    private final InventoryTransferService transferService;

    @GetMapping("/list")
    @Operation(summary = "分页查询调拨单")
    @SaCheckPermission("inventory:transfer:view")
    public Result<IPage<TransferVO>> list(TransferQueryDTO query) {
        return Result.success(transferService.page(query));
    }

    @GetMapping("/{transferId}")
    @Operation(summary = "获取调拨单详情")
    @SaCheckPermission("inventory:transfer:view")
    public Result<TransferVO> getById(@PathVariable Long transferId) {
        return Result.success(transferService.getDetail(transferId));
    }

    @PostMapping("/create")
    @Operation(summary = "创建调拨单")
    @Log(module = "调拨管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("inventory:transfer:add")
    public Result<Long> create(@RequestBody Map<String, Object> params) {
        return Result.success(transferService.create(params));
    }

    @PostMapping("/submit-approve/{transferId}")
    @Operation(summary = "提交审批")
    @Log(module = "调拨管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("inventory:transfer:edit")
    public Result<Boolean> submitApprove(@PathVariable Long transferId) {
        return Result.success(transferService.submitApprove(transferId));
    }

    @PostMapping("/approve/{transferId}")
    @Operation(summary = "审批通过")
    @Log(module = "调拨管理", businessType = BusinessType.APPROVE)
    @SaCheckPermission("inventory:transfer:approve")
    public Result<Boolean> approve(@PathVariable Long transferId,
                                   @RequestParam Long approverId,
                                   @RequestParam String approverName,
                                   @RequestParam(required = false) String remark) {
        return Result.success(transferService.approve(transferId, approverId, approverName, remark));
    }

    @PostMapping("/reject/{transferId}")
    @Operation(summary = "审批驳回")
    @Log(module = "调拨管理", businessType = BusinessType.APPROVE)
    @SaCheckPermission("inventory:transfer:approve")
    public Result<Boolean> reject(@PathVariable Long transferId,
                                  @RequestParam Long approverId,
                                  @RequestParam String approverName,
                                  @RequestParam String remark) {
        return Result.success(transferService.reject(transferId, approverId, approverName, remark));
    }

    @PostMapping("/confirm-out/{transferId}")
    @Operation(summary = "调出确认")
    @Log(module = "调拨管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("inventory:transfer:edit")
    public Result<Boolean> confirmOut(@PathVariable Long transferId,
                                      @RequestParam Long operatorId,
                                      @RequestParam String operatorName) {
        return Result.success(transferService.confirmOut(transferId, operatorId, operatorName));
    }

    @PostMapping("/confirm-in/{transferId}")
    @Operation(summary = "调入确认")
    @Log(module = "调拨管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("inventory:transfer:edit")
    public Result<Boolean> confirmIn(@PathVariable Long transferId,
                                     @RequestParam Long operatorId,
                                     @RequestParam String operatorName) {
        return Result.success(transferService.confirmIn(transferId, operatorId, operatorName));
    }

    @PostMapping("/cancel/{transferId}")
    @Operation(summary = "取消调拨单")
    @Log(module = "调拨管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("inventory:transfer:edit")
    public Result<Boolean> cancel(@PathVariable Long transferId,
                                  @RequestParam String reason) {
        return Result.success(transferService.cancel(transferId, reason));
    }

    @GetMapping("/pending-approval")
    @Operation(summary = "查询待审批的调拨单")
    @SaCheckPermission("inventory:transfer:view")
    public Result<List<TransferVO>> getPendingApproval() {
        return Result.success(transferService.getPendingApproval());
    }

    @GetMapping("/processing")
    @Operation(summary = "查询进行中的调拨单")
    @SaCheckPermission("inventory:transfer:view")
    public Result<List<TransferVO>> getProcessing() {
        return Result.success(transferService.getProcessing());
    }

    @PostMapping("/update-status/{transferId}")
    @Operation(summary = "更新调拨单状态")
    @Log(module = "调拨管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("inventory:transfer:edit")
    public Result<Boolean> updateStatus(@PathVariable Long transferId,
                                        @RequestParam String status) {
        return Result.success(transferService.updateStatus(transferId, status));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "获取调拨仪表板数据")
    @SaCheckPermission("inventory:transfer:view")
    public Result<Map<String, Object>> getDashboard() {
        // 这里可以添加一些统计信息，比如待审批的调拨数量、进行中的调拨数量等
        return Result.success(Map.of(
                "pendingApprovalCount", 0,
                "processingCount", 0,
                "completedThisMonth", 0,
                "totalTransferAmount", 0
        ));
    }
}
