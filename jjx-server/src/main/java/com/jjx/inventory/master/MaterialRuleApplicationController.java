package com.jjx.inventory.master;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory/material/master/rules")
@RequiredArgsConstructor
public class MaterialRuleApplicationController extends BaseController {
    private final MaterialRuleApplicationService service;

    public record SpecDraft(Long categoryId, MaterialAttributeSchema.SpecPattern pattern) { }

    @PostMapping("/spec-preview")
    @SaCheckPermission("inventory:material:view")
    public Result<MaterialMasterService.SpecPreview> specification(@RequestBody SpecDraft request) {
        return Result.success(service.previewSpecification(request.categoryId(), request.pattern()));
    }

    @GetMapping("/changes")
    @SaCheckPermission("inventory:material:view")
    public Result<MaterialRuleApplicationService.Preview> preview(@RequestParam Long categoryId,
            @RequestParam(defaultValue = "true") boolean applyName,
            @RequestParam(defaultValue = "true") boolean applySpecification,
            @RequestParam(defaultValue = "false") boolean applyModel,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(service.preview(new MaterialRuleApplicationService.Options(categoryId, applyName, applySpecification, applyModel),
                pageNum, pageSize, keyword));
    }

    @PostMapping("/apply")
    @SaCheckPermission("inventory:material:edit")
    public Result<MaterialRuleApplicationService.Applied> apply(@RequestBody MaterialRuleApplicationService.ApplyRequest request) {
        return Result.success(service.apply(request, com.jjx.system.utils.SecurityUtils.getUsername(),
                getUserId(), com.jjx.system.utils.SecurityUtils.getRealName()));
    }
}
