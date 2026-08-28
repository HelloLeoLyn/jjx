package com.jjx.trace.controller;

import com.jjx.common.core.result.Result;
import com.jjx.common.core.page.PageResult;
import com.jjx.trace.service.TraceService;
import com.jjx.trace.domain.vo.TraceReviewVO;
import com.jjx.trace.domain.vo.UnifiedTraceEventVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "业务链路追踪")
@RestController
@RequestMapping("/api/trace")
@RequiredArgsConstructor
public class TraceController {

    private final TraceService traceService;

    @Operation(summary = "按 trace_id 查询操作流水（主表，分页）")
    @GetMapping("/events")
    public Result<PageResult<UnifiedTraceEventVO>> getEvents(
            @RequestParam String traceId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(traceService.getEvents(traceId, pageNum, pageSize));
    }

    @Operation(summary = "按业务单据查询审核流水（review_flow + 报价 sales_quotation_flow）")
    @GetMapping("/reviews")
    public Result<List<TraceReviewVO>> reviews(@RequestParam String bizType, @RequestParam Long bizId) {
        return Result.success(traceService.reviewList(bizType, bizId));
    }
}
