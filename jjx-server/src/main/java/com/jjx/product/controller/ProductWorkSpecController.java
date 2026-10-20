package com.jjx.product.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jjx.common.core.result.Result;
import com.jjx.product.domain.dto.ProductWorkSpecDTO;
import com.jjx.product.domain.dto.ProductWorkSpecDraftDTO;
import com.jjx.product.domain.dto.PrintSpecRemarksDTO;
import com.jjx.product.domain.dto.ColorCheckDTO;
import com.jjx.product.service.ProductWorkSpecService;
import com.jjx.product.service.ProductWorkSpecChangeService;
import com.jjx.common.core.page.PageResult;
import com.jjx.trace.domain.vo.UnifiedTraceEventVO;
import com.jjx.trace.service.TraceService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/product/{productId}/work-spec")
@RequiredArgsConstructor
public class ProductWorkSpecController {
    private final ProductWorkSpecService service;
    private final ProductWorkSpecChangeService changes;
    private final TraceService traceService;

    @GetMapping("/change-sources")
    @SaCheckPermission("product:edit")
    public Result<ObjectNode> changeSources(@PathVariable Long productId, @RequestParam(required = false) Long before) {
        return Result.success(changes.list(productId, before));
    }

    @GetMapping
    @SaCheckLogin
    public Result<ObjectNode> get(@PathVariable Long productId) { return Result.success(service.get(productId)); }

    @PutMapping("/print-remarks")
    @SaCheckPermission("product:edit")
    public Result<ObjectNode> savePrintRemarks(@PathVariable Long productId, @Validated @RequestBody PrintSpecRemarksDTO dto) {
        return Result.success(service.savePrintRemarks(productId, dto));
    }

    @GetMapping("/print-remarks/history")
    @SaCheckPermission("product:edit")
    public Result<ObjectNode> printRemarksHistory(@PathVariable Long productId, @RequestParam(required = false) Long before) {
        return Result.success(service.printRemarksHistory(productId, before));
    }

    @PutMapping("/color-check")
    @SaCheckPermission("product:edit")
    public Result<ObjectNode> saveColorCheck(@PathVariable Long productId, @Validated @RequestBody ColorCheckDTO dto) {
        return Result.success(service.saveColorCheck(productId, dto));
    }

    @PutMapping
    @SaCheckPermission("product:edit")
    @Log(module = "产品作业规范", businessType = BusinessType.UPDATE, bizType = "'product'", bizId = "#productId")
    public Result<ObjectNode> save(@PathVariable Long productId, @Validated @RequestBody ProductWorkSpecDTO dto) {
        return Result.success(service.save(productId, dto));
    }

    @PutMapping("/draft")
    @SaCheckPermission("product:edit")
    public Result<ObjectNode> saveDraft(@PathVariable Long productId, @Validated @RequestBody ProductWorkSpecDraftDTO dto) {
        return Result.success(service.saveDraft(productId, dto));
    }
    @PostMapping("/claim")
    @SaCheckPermission("product:edit")
    public Result<ObjectNode> claim(@PathVariable Long productId) { return Result.success(service.claim(productId)); }

    @PostMapping("/transfer")
    @SaCheckPermission("product:edit")
    public Result<ObjectNode> transfer(@PathVariable Long productId, @Validated @RequestBody ProductWorkSpecDraftDTO dto) {
        return Result.success(service.transfer(productId, dto));
    }

    @PostMapping("/draft/from-version")
    @SaCheckPermission("product:edit")
    public Result<ObjectNode> upgradeDraft(@PathVariable Long productId, @Validated @RequestBody ProductWorkSpecDraftDTO dto) {
        return Result.success(service.upgradeDraft(productId, dto));
    }
    @GetMapping("/responsible-users")
    @SaCheckPermission("product:edit")
    public Result<java.util.List<java.util.Map<String,Object>>> responsibleUsers() { return Result.success(service.responsibleUsers()); }
    /**
     * @deprecated 规范操作记录统一走 /api/trace/events + scope=work_spec；这里只转调同一查询，
     * 不再保留 ProductWorkSpecMapper.draftHistory 第二口径。保留入口仅为兼容旧客户端。
     */
    @Deprecated
    @GetMapping("/history")
    @SaCheckPermission(value={"product:spec:view","engineering:spec:view"},mode=cn.dev33.satoken.annotation.SaMode.OR)
    public Result<PageResult<UnifiedTraceEventVO>> history(@PathVariable Long productId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(traceService.getEventsByBiz("product", productId,
                TraceService.SCOPE_WORK_SPEC, pageNum, pageSize));
    }

    @PostMapping("/confirm")
    @SaCheckPermission("product:status:approve")
    @Log(module = "产品作业规范确认", businessType = BusinessType.UPDATE, bizType = "'product'", bizId = "#productId")
    public Result<ObjectNode> confirm(@PathVariable Long productId, @Validated @RequestBody Confirmation dto) {
        return Result.success(service.confirm(productId, dto.revision(), dto.sourceRevision()));
    }
    public record Confirmation(@NotBlank String revision, @NotBlank String sourceRevision) { }
}
