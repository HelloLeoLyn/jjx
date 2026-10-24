package com.jjx.inventory.classify;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/inventory/material/classify")
@SaCheckPermission("inventory:material:edit")
@RequiredArgsConstructor
public class MaterialClassifyController extends BaseController {
    private final MaterialClassifyService service;
    @GetMapping("/rules")
    public Result<List<MaterialClassifyRule>> rules() { return Result.success(service.listRules()); }
    @PostMapping("/rules")
    public Result<MaterialClassifyRule> createRule(@RequestBody MaterialClassifyRule rule) { return Result.success(service.saveRule(rule, false, getUsername())); }
    @PutMapping("/rules")
    public Result<MaterialClassifyRule> updateRule(@RequestBody MaterialClassifyRule rule) { return Result.success(service.saveRule(rule, true, getUsername())); }
    @DeleteMapping("/rules")
    public Result<Void> deleteRule(@RequestParam Long id) { service.deleteRule(id); return Result.success(); }
    @PostMapping("/batch")
    public Result<MaterialClassifyService.BatchResult> classify(@RequestBody MaterialClassifyService.BatchRequest request) { return Result.success(service.classify(request, getUsername())); }
    @PostMapping("/apply-rules")
    public Result<MaterialClassifyService.ApplyResult> apply(@RequestParam(defaultValue = "false") boolean confirm) { return Result.success(service.applyRules(confirm, getUsername())); }
    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) { service.confirm(id, getUsername()); return Result.success(); }
}
