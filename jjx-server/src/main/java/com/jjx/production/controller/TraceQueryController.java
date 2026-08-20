package com.jjx.production.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.production.domain.vo.OrderTraceVO;
import com.jjx.production.service.TraceQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * P4-B：生产履历查询（Trace Read Model，只读）
 * <p>
 * 统一订单履历主入口：GET /production/trace/order/{orderId}
 * 支持可选过滤：category（ORDER/EXECUTION/DISPATCH/WORK_REPORT/QUALITY）、executionId
 * 不提供 execution/report/quality 独立 trace 端点（V1 统一订单履历）。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/production/trace")
@Tag(name = "生产履历查询")
public class TraceQueryController {

    private final TraceQueryService traceQueryService;

    @Operation(summary = "生产订单履历（订单头 + 时间线事件）")
    @SaCheckPermission("production:order:view")
    @GetMapping("/order/{orderId}")
    public Result<OrderTraceVO> orderTrace(@PathVariable Long orderId,
                                           @RequestParam(required = false) String category,
                                           @RequestParam(required = false) Long executionId) {
        return Result.success(traceQueryService.getOrderTrace(orderId, category, executionId));
    }
}
