package com.jjx.quality.controller;

import com.jjx.common.core.result.Result;
import com.jjx.quality.domain.entity.QualitySamplingPlan;
import com.jjx.quality.service.QualitySamplingPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 抽样方案（AQL）接口 —— dev-20260917-002
 */
@Tag(name = "质量管理-抽样方案")
@RestController
@RequestMapping("/quality/sampling-plan")
@RequiredArgsConstructor
public class QualitySamplingPlanController {

    private final QualitySamplingPlanService planService;

    @Operation(summary = "抽样方案列表")
    @GetMapping("/list")
    public Result<List<QualitySamplingPlan>> list() {
        return Result.success(planService.listAll());
    }

    @Operation(summary = "新增/修改抽样方案")
    @PostMapping
    public Result<QualitySamplingPlan> save(@RequestBody QualitySamplingPlan plan) {
        return Result.success(planService.savePlan(plan));
    }

    @Operation(summary = "删除抽样方案")
    @DeleteMapping("/{planId}")
    public Result<Boolean> delete(@PathVariable Long planId) {
        planService.delete(planId);
        return Result.success(true);
    }

    @Operation(summary = "按类型+批量匹配抽样方案（未配置返回 null，调用方降级为全检）")
    @GetMapping("/match")
    public Result<QualitySamplingPlan> match(@RequestParam String lotType, @RequestParam BigDecimal quantity) {
        return Result.success(planService.match(lotType, quantity));
    }
}
