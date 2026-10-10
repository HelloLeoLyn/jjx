package com.jjx.sales.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.sales.domain.dto.SampleDefectRecordDTO;
import com.jjx.sales.domain.entity.SalesSampleDefectRecord;
import com.jjx.sales.domain.entity.SalesSampleRequisitionSign;
import com.jjx.sales.service.ISampleRequisitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 样品需求单会签（QR-065）+ 打样不良原因及改善记录（dev-20261010-028）
 * 均挂样品单，权限点由「角色管理」分配。
 */
@Tag(name = "样品需求单会签与打样不良记录")
@RestController
@RequestMapping("/sales/sample-order")
@RequiredArgsConstructor
public class SampleRequisitionController extends BaseController {

    private final ISampleRequisitionService sampleRequisitionService;

    @Operation(summary = "样品需求单会签记录")
    @SaCheckPermission(value = {"sales:sample:view", "engineering:sample:workbench"}, mode = SaMode.OR)
    @GetMapping("/{orderId}/requisition-signs")
    public Result<List<SalesSampleRequisitionSign>> listSigns(@PathVariable Long orderId) {
        return Result.success(sampleRequisitionService.listSigns(orderId));
    }

    @Operation(summary = "样品需求单会签（业务/核准/部门主管三选一，权限点决定可签位）")
    @SaCheckPermission(value = {"sales:sample:reqsign:sales", "sales:sample:reqsign:approve", "sales:sample:reqsign:dept"}, mode = SaMode.OR)
    @PutMapping("/{orderId}/requisition-sign")
    public Result<SalesSampleRequisitionSign> sign(@PathVariable Long orderId,
                                                   @RequestParam String role,
                                                   @RequestParam Boolean approved,
                                                   @RequestParam(required = false) String comment) {
        return Result.success(sampleRequisitionService.sign(orderId, role, approved, comment));
    }

    @Operation(summary = "打样不良原因及改善记录列表")
    @SaCheckPermission(value = {"sales:sample:view", "engineering:sample:workbench"}, mode = SaMode.OR)
    @GetMapping("/{orderId}/defects")
    public Result<List<SalesSampleDefectRecord>> listDefects(@PathVariable Long orderId) {
        return Result.success(sampleRequisitionService.listDefects(orderId));
    }

    @Operation(summary = "新增打样不良原因及改善记录")
    @SaCheckPermission("engineering:sample:defect:record")
    @PostMapping("/{orderId}/defects")
    public Result<SalesSampleDefectRecord> addDefect(@PathVariable Long orderId,
                                                     @Valid @RequestBody SampleDefectRecordDTO dto) {
        return Result.success(sampleRequisitionService.addDefect(orderId, dto));
    }

    @Operation(summary = "删除打样不良记录")
    @SaCheckPermission("engineering:sample:defect:record")
    @DeleteMapping("/defects/{id}")
    public Result<Void> deleteDefect(@PathVariable Long id) {
        sampleRequisitionService.deleteDefect(id);
        return Result.success();
    }
}
