package com.jjx.production.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.constant.LogActions;
import com.jjx.common.core.result.Result;
import com.jjx.production.domain.dto.WorkSpecUsageRegisterDTO;
import com.jjx.production.domain.vo.WorkSpecUsageVO;
import com.jjx.production.service.WorkSpecUsageService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工单换版执行区间（dev-20261011-013）
 * 权限复用生产工单：编辑=production:order:edit，查看=production:order:view，不新增权限点。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/production/work-spec-usage")
@Tag(name = "工单换版执行区间")
public class WorkSpecUsageController {

    private final WorkSpecUsageService workSpecUsageService;

    @Operation(summary = "登记工单换版执行区间（换版）")
    @PostMapping
    @Log(module = "生产工单管理", businessType = BusinessType.UPDATE, bizType = "'production_order'", bizId = "#dto.workOrderId", action = LogActions.PROD_ORDER_EDIT)
    @SaCheckPermission("production:order:edit")
    public Result<WorkSpecUsageVO> register(@Validated @RequestBody WorkSpecUsageRegisterDTO dto) {
        return Result.success(workSpecUsageService.register(dto));
    }

    @Operation(summary = "查询工单执行区间列表")
    @GetMapping
    @SaCheckPermission("production:order:view")
    public Result<List<WorkSpecUsageVO>> list(@RequestParam Long workOrderId) {
        return Result.success(workSpecUsageService.list(workOrderId));
    }

    @Operation(summary = "删除工单执行区间记录")
    @DeleteMapping("/{id}")
    @Log(module = "生产工单管理", businessType = BusinessType.DELETE, bizType = "'work_spec_usage'", bizId = "#id", action = LogActions.PROD_ORDER_EDIT)
    @SaCheckPermission("production:order:edit")
    public Result<Boolean> delete(@PathVariable Long id) {
        workSpecUsageService.delete(id);
        return Result.success(true);
    }
}
