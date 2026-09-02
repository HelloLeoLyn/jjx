package com.jjx.biz.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.biz.domain.entity.BizRequirement;
import com.jjx.biz.domain.query.BizRequirementQuery;
import com.jjx.biz.enums.RequirementStatusEnum;
import com.jjx.biz.enums.RequirementTypeEnum;
import com.jjx.biz.service.IBizRequirementService;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 业务需求单控制器（2026-09-02 新建）
 * 通用需求载体：变更(ECN)/新增/改善/问题
 */
@Tag(name = "业务需求管理")
@RestController
@RequestMapping("/biz/requirement")
@RequiredArgsConstructor
public class BizRequirementController extends BaseController {

    private final IBizRequirementService requirementService;

    @Operation(summary = "分页查询需求单")
    @SaCheckPermission("biz:requirement:view")
    @GetMapping("/page")
    public Result<PageResult<BizRequirement>> page(BizRequirementQuery query) {
        IPage<BizRequirement> p = requirementService.page(query, getPageNum(), getPageSize());
        PageResult<BizRequirement> pr = new PageResult<>();
        pr.setRecords(p.getRecords());
        pr.setTotal(p.getTotal());
        return Result.success(pr);
    }

    @Operation(summary = "需求单详情")
    @SaCheckPermission("biz:requirement:view")
    @GetMapping("/{requirementId}")
    public Result<BizRequirement> getInfo(@PathVariable Long requirementId) {
        return Result.success(requirementService.getById(requirementId));
    }

    @Operation(summary = "新增需求单")
    @Log(module = "业务需求管理", businessType = BusinessType.INSERT, bizId = "#result.data")
    @SaCheckPermission("biz:requirement:add")
    @PostMapping
    public Result<Long> add(@RequestBody BizRequirement requirement) {
        return Result.success(requirementService.create(requirement));
    }

    @Operation(summary = "修改需求单")
    @Log(module = "业务需求管理", businessType = BusinessType.UPDATE, bizId = "#requirement.requirementId")
    @SaCheckPermission("biz:requirement:edit")
    @PutMapping
    public Result<Void> edit(@RequestBody BizRequirement requirement) {
        requirementService.update(requirement);
        return Result.success();
    }

    @Operation(summary = "删除需求单")
    @Log(module = "业务需求管理", businessType = BusinessType.DELETE, bizId = "#requirementIds")
    @SaCheckPermission("biz:requirement:remove")
    @DeleteMapping("/{requirementIds}")
    public Result<Void> remove(@PathVariable Long[] requirementIds) {
        requirementService.remove(requirementIds);
        return Result.success();
    }

    @Operation(summary = "提交评审")
    @Log(module = "业务需求管理", businessType = BusinessType.UPDATE, bizId = "#requirementId")
    @SaCheckPermission("biz:requirement:edit")
    @PutMapping("/submit/{requirementId}")
    public Result<Void> submit(@PathVariable Long requirementId) {
        requirementService.submit(requirementId);
        return Result.success();
    }

    @Operation(summary = "审核（通过/驳回）")
    @Log(module = "业务需求管理", businessType = BusinessType.UPDATE, bizId = "#requirementId", detail = "#approved ? '通过' : '驳回'")
    @SaCheckPermission("biz:requirement:edit")
    @PutMapping("/review/{requirementId}")
    public Result<Void> review(@PathVariable Long requirementId,
                               @RequestParam Boolean approved,
                               @RequestParam(required = false) String remark) {
        requirementService.review(requirementId, approved, remark);
        return Result.success();
    }

    @Operation(summary = "需求类型选项")
    @SaCheckPermission("biz:requirement:view")
    @GetMapping("/type-options")
    public Result<List<Map<String, Object>>> typeOptions() {
        List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (RequirementTypeEnum t : RequirementTypeEnum.values()) {
            list.add(java.util.Map.of("value", t.getValue(), "label", t.getLabel()));
        }
        return Result.success(list);
    }

    @Operation(summary = "状态选项")
    @SaCheckPermission("biz:requirement:view")
    @GetMapping("/status-options")
    public Result<List<Map<String, Object>>> statusOptions() {
        List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (RequirementStatusEnum s : RequirementStatusEnum.values()) {
            list.add(java.util.Map.of("value", s.getValue(), "label", s.getLabel()));
        }
        return Result.success(list);
    }
}
