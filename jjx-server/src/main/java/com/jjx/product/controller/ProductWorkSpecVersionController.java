package com.jjx.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.jjx.common.core.result.Result;
import com.jjx.product.domain.entity.ProductWorkSpecVersion;
import com.jjx.product.service.ProductWorkSpecVersionService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 产品作业规范发布版本（dev-20261011-008）
 * 发布 = 固化不可变版本；列表/详情只读；发布权限点 product:work-spec:publish。
 */
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductWorkSpecVersionController {

    private final ProductWorkSpecVersionService service;

    @PostMapping("/{productId}/work-spec/publish")
    @SaCheckPermission("product:work-spec:publish")
    @Log(module = "产品作业规范", businessType = BusinessType.UPDATE, bizType = "'product'", bizId = "#productId")
    public Result<ProductWorkSpecVersion> publish(@PathVariable Long productId,
                                                  @RequestParam(required = false) String changeSummary) {
        return Result.success(service.publish(productId, changeSummary));
    }

    @GetMapping("/{productId}/work-spec/versions")
    @SaCheckPermission(value = {"product:spec:view", "engineering:spec:view"}, mode = SaMode.OR)
    public Result<List<ProductWorkSpecVersion>> list(@PathVariable Long productId) {
        return Result.success(service.list(productId));
    }

    @GetMapping("/work-spec/versions/{versionId}")
    @SaCheckPermission(value = {"product:spec:view", "engineering:spec:view", "sales:order:view", "production:order:view"}, mode = SaMode.OR)
    public Result<Map<String, Object>> detail(@PathVariable Long versionId) {
        return Result.success(service.detail(versionId));
    }

    /** 版本差异预览：已发布快照 vs 当前工作态（dev-20261011-023）。 */
    @GetMapping("/work-spec/versions/{versionId}/diff")
    @SaCheckPermission(value = {"product:spec:view", "engineering:spec:view", "sales:order:view", "production:order:view"}, mode = SaMode.OR)
    public Result<Map<String, Object>> diff(@PathVariable Long versionId) {
        return Result.success(service.diff(versionId));
    }

    /** 列表用：批量查「最新发布版本 + 有无未发布改动」（dev-20261011-027，只读）。 */
    @GetMapping("/work-spec/status")
    @SaCheckPermission(value = {"product:spec:view", "engineering:spec:view"}, mode = SaMode.OR)
    public Result<List<Map<String, Object>>> statuses(@RequestParam("productIds") List<Long> productIds) {
        return Result.success(service.statuses(productIds));
    }

    /** 任意两个已发布版本的字段级比较（dev-20261011-028，只读）。 */
    @GetMapping("/work-spec/compare")
    @SaCheckPermission(value = {"product:spec:view", "engineering:spec:view", "sales:order:view", "production:order:view"}, mode = SaMode.OR)
    public Result<Map<String, Object>> compare(@RequestParam Long versionA, @RequestParam Long versionB) {
        return Result.success(service.compare(versionA, versionB));
    }
}
