package com.jjx.product.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jjx.common.core.result.Result;
import com.jjx.product.domain.dto.ProductWorkSpecDTO;
import com.jjx.product.service.ProductWorkSpecService;
import com.jjx.product.service.ProductWorkSpecChangeService;
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

    @GetMapping("/change-sources")
    @SaCheckPermission("product:edit")
    public Result<ObjectNode> changeSources(@PathVariable Long productId, @RequestParam(required = false) Long before) {
        return Result.success(changes.list(productId, before));
    }

    @GetMapping
    @SaCheckLogin
    public Result<ObjectNode> get(@PathVariable Long productId) { return Result.success(service.get(productId)); }

    @PutMapping
    @SaCheckPermission("product:edit")
    @Log(module = "产品作业规范", businessType = BusinessType.UPDATE, bizType = "'product'", bizId = "#productId")
    public Result<ObjectNode> save(@PathVariable Long productId, @Validated @RequestBody ProductWorkSpecDTO dto) {
        return Result.success(service.save(productId, dto));
    }

    @PostMapping("/confirm")
    @SaCheckPermission("product:status:approve")
    @Log(module = "产品作业规范确认", businessType = BusinessType.UPDATE, bizType = "'product'", bizId = "#productId")
    public Result<ObjectNode> confirm(@PathVariable Long productId, @Validated @RequestBody Confirmation dto) {
        return Result.success(service.confirm(productId, dto.revision(), dto.sourceRevision()));
    }
    public record Confirmation(@NotBlank String revision, @NotBlank String sourceRevision) { }
}
