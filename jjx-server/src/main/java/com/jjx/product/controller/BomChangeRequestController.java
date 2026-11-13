package com.jjx.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.product.domain.dto.BomChangeRequestDTO;
import com.jjx.product.domain.entity.BomChangeRequest;
import com.jjx.product.domain.entity.BomChangeRequestItem;
import com.jjx.product.service.BomChangeRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** BOM 变更单（ECN-lite）：一次提交、一次审批、统一生效。 */
@RestController
@RequestMapping("/product/bom-change")
@RequiredArgsConstructor
@Tag(name = "BOM变更单")
public class BomChangeRequestController {

    private final BomChangeRequestService service;

    @Operation(summary = "新建变更单（草稿）")
    @PostMapping
    @SaCheckPermission("product:bom:edit")
    public Result<Long> create(@RequestBody BomChangeRequestDTO dto) {
        return Result.success(service.create(dto));
    }

    @Operation(summary = "提交变更单（逐张校验）")
    @PostMapping("/{changeId}/submit")
    @SaCheckPermission("product:bom:edit")
    public Result<BomChangeRequest> submit(@PathVariable Long changeId) {
        return Result.success(service.submit(changeId));
    }

    @Operation(summary = "审批通过")
    @PostMapping("/{changeId}/approve")
    @SaCheckPermission("product:bom:approve")
    public Result<BomChangeRequest> approve(@PathVariable Long changeId) {
        return Result.success(service.approve(changeId));
    }

    @Operation(summary = "生效（force=true 为管理员立即生效）")
    @PostMapping("/{changeId}/activate")
    @SaCheckPermission("product:bom:approve")
    public Result<BomChangeRequest> activate(@PathVariable Long changeId,
                                             @RequestParam(defaultValue = "false") boolean force) {
        return Result.success(service.activate(changeId, force));
    }

    @Operation(summary = "取消变更单")
    @PostMapping("/{changeId}/cancel")
    @SaCheckPermission("product:bom:edit")
    public Result<BomChangeRequest> cancel(@PathVariable Long changeId) {
        return Result.success(service.cancel(changeId));
    }

    @Operation(summary = "变更单分页")
    @GetMapping("/page")
    @SaCheckPermission("product:bom:view")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<BomChangeRequest>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        return Result.success(service.page(pageNum, pageSize, status));
    }

    @Operation(summary = "变更单详情")
    @GetMapping("/{changeId}")
    @SaCheckPermission("product:bom:view")
    public Result<BomChangeRequest> detail(@PathVariable Long changeId) {
        return Result.success(service.detail(changeId));
    }

    @Operation(summary = "变更单明细")
    @GetMapping("/{changeId}/items")
    @SaCheckPermission("product:bom:view")
    public Result<List<BomChangeRequestItem>> items(@PathVariable Long changeId) {
        return Result.success(service.itemsOf(changeId));
    }
}
