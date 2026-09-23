package com.jjx.production.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.jjx.common.core.result.Result;
import com.jjx.production.domain.vo.ReworkTraceVO;
import com.jjx.production.service.ProductionReworkTraceService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 返工链投影（只读）—— dev-20260923-031 一期。
 *
 * <p>给页面贴「返工身份 + 进度 + 一句人话」用：工序执行页按 orderId 整单拿、不良台账按 ncrId 拿。</p>
 */
@RestController
@RequestMapping("/production/rework")
@RequiredArgsConstructor
public class ProductionReworkController {

    private final ProductionReworkTraceService reworkTraceService;

    @Operation(summary = "返工链投影（按工单/返工工序/不良单查，只读）")
    // 不良台账页（质量账号）也要读这条链 → 生产或质量任一查看权限即可（实测：质量账号 无 production 权限时返回 500 无此权限）
    @SaCheckPermission(value = {"production:operation-execution:view", "quality:ncr:view"}, mode = SaMode.OR)
    @GetMapping("/trace")
    public Result<List<ReworkTraceVO>> trace(@RequestParam(required = false) Long orderId,
                                            @RequestParam(required = false) Long executionId,
                                            @RequestParam(required = false) Long ncrId) {
        return Result.success(reworkTraceService.trace(orderId, executionId, ncrId));
    }
}
