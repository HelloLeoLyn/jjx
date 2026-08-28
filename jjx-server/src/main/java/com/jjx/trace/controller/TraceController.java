package com.jjx.trace.controller;

import com.jjx.common.core.result.Result;
import com.jjx.common.core.page.PageResult;
import com.jjx.trace.service.TraceService;
import com.jjx.trace.domain.vo.UnifiedTraceEventVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "业务链路追踪")
@RestController
@RequestMapping("/api/trace")
@RequiredArgsConstructor
public class TraceController {

    private final TraceService traceService;

    @Operation(summary = "按业务单据查询统一事件流")
    @GetMapping("/events")
    public Result<PageResult<UnifiedTraceEventVO>> getEvents(
            @RequestParam String bizType,
            @RequestParam Long bizId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(traceService.getEvents(bizType, bizId, pageNum, pageSize));
    }

    @Operation(summary = "按trace_id查询完整业务链路")
    @GetMapping("/{traceId}")
    public Result<List<Map<String, Object>>> getTrace(@PathVariable String traceId) {
        return Result.success(traceService.getTraceByTraceId(traceId));
    }

    @Operation(summary = "按业务编号反查trace_id")
    @GetMapping("/search")
    public Result<List<Map<String, Object>>> searchTrace(@RequestParam String keyword) {
        return Result.success(traceService.searchTrace(keyword));
    }
}
